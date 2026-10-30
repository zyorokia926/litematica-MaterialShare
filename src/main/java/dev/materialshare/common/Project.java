package dev.materialshare.common;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

public final class Project {
    public static final int MAX_ITEMS = 1500;
    /** Keeps a project's packets small: 1500 items x 20 claimants stays below the 1 MiB packet limit. */
    public static final int MAX_CLAIMANTS = 20;
    private static final Pattern ITEM_ID = Pattern.compile("([a-z0-9_.-]+:)?[a-z0-9_./-]+");

    public static final int MAX_NOTE = 200;

    /** Control characters and the section sign are dropped; the text is cut to {@link #MAX_NOTE} without splitting a character. */
    public static String cleanNote(String text) {
        if (text == null) {
            return "";
        }
        String clean = text.replaceAll("[\\p{Cntrl}§]", "").strip();
        if (clean.length() <= MAX_NOTE) {
            return clean;
        }
        int end = MAX_NOTE;
        if (Character.isHighSurrogate(clean.charAt(end - 1))) {
            end--;
        }
        return clean.substring(0, end);
    }

    public static boolean isValidItemId(String id) {
        return id != null && id.length() <= 128 && ITEM_ID.matcher(id).matches();
    }

    private final String name;
    private final Map<String, Entry> items = new LinkedHashMap<>();
    private final History history = new History();
    private String note = "";
    /** Spare items players offer to the others: item id to (player to count). */
    private final Map<String, Map<UUID, Integer>> surplus = new LinkedHashMap<>();

    /** A project keeps this many offers at most (item and player together). */
    public static final int MAX_OFFERS = 200;
    public static final int MAX_SURPLUS_COUNT = 1_000_000;
    private boolean archived;
    private final Storage storage = new Storage();
    private final java.util.List<Zone> zones = new java.util.ArrayList<>();
    /** When each player last did something in this project (milliseconds); for the "not moving" hint. */
    private final Map<UUID, Long> activity = new LinkedHashMap<>();

    public Project(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public Map<String, Entry> items() {
        return items;
    }

    /** Adds items that are not yet present; existing items keep their required count. */
    public boolean merge(Map<String, Integer> required) {
        boolean changed = false;
        for (Map.Entry<String, Integer> e : required.entrySet()) {
            if (items.size() >= MAX_ITEMS) {
                break;
            }
            if (e.getValue() > 0 && isValidItemId(e.getKey()) && !items.containsKey(e.getKey())) {
                items.put(e.getKey(), new Entry(e.getValue()));
                changed = true;
            }
        }
        return changed;
    }

    public boolean setClaim(UUID target, String itemId, boolean on) {
        Entry entry = items.get(itemId);
        if (entry == null) {
            return false;
        }
        if (on) {
            if (entry.claims().size() >= MAX_CLAIMANTS && !entry.claims().containsKey(target)) {
                return false;
            }
            return entry.claims().putIfAbsent(target, 0) == null;
        }
        entry.targets().remove(target);
        entry.delivered().remove(target);
        return entry.claims().remove(target) != null;
    }

    public boolean setCollected(UUID target, String itemId, int n) {
        Entry entry = items.get(itemId);
        if (entry == null || !entry.claims().containsKey(target)) {
            return false;
        }
        int value = Math.max(0, Math.min(n, entry.required()));
        entry.claims().put(target, value);
        entry.delivered().computeIfPresent(target, (k, v) -> Math.min(v, value));
        return true;
    }

    /** What a claimant promises to collect: 0 removes the promise. The claimant must have the item. */
    public boolean setTarget(UUID target, String itemId, int n) {
        Entry entry = items.get(itemId);
        if (entry == null || !entry.claims().containsKey(target)) {
            return false;
        }
        int value = Math.max(0, Math.min(n, entry.required()));
        Integer before = entry.targets().get(target);
        if (value == 0) {
            return entry.targets().remove(target) != null;
        }
        entry.targets().put(target, value);
        return before == null || before != value;
    }

    /** How much of a claimant's collected amount is in the storage (never more than they collected). */
    public boolean setDelivered(UUID target, String itemId, int n) {
        Entry entry = items.get(itemId);
        if (entry == null || !entry.claims().containsKey(target)) {
            return false;
        }
        int value = Math.max(0, Math.min(n, entry.claims().get(target)));
        Integer before = entry.delivered().get(target);
        if (value == 0) {
            return entry.delivered().remove(target) != null;
        }
        entry.delivered().put(target, value);
        return before == null || before != value;
    }

    /**
     * Hands a claim over to another player: the collected count, the promise and the delivered count go with it (they
     * add to what the other player already has). Refused when the new player would make the claimant list too long.
     */
    public boolean transfer(String itemId, UUID from, UUID to) {
        Entry entry = items.get(itemId);
        if (entry == null || from.equals(to) || !entry.claims().containsKey(from)) {
            return false;
        }
        boolean already = entry.claims().containsKey(to);
        if (!already && entry.claims().size() >= MAX_CLAIMANTS) {
            return false;
        }
        int collected = entry.claims().remove(from);
        int target = entry.targets().getOrDefault(from, 0);
        int delivered = entry.delivered().getOrDefault(from, 0);
        entry.targets().remove(from);
        entry.delivered().remove(from);
        int newCollected = Math.min(entry.required(), entry.claims().getOrDefault(to, 0) + collected);
        entry.claims().put(to, newCollected);
        if (target > 0 || entry.targets().containsKey(to)) {
            entry.targets().put(to, Math.min(entry.required(), entry.targets().getOrDefault(to, 0) + target));
        }
        int newDelivered = Math.min(newCollected, entry.delivered().getOrDefault(to, 0) + delivered);
        if (newDelivered > 0) {
            entry.delivered().put(to, newDelivered);
        }
        return true;
    }

    /** Takes every player off every item; returns how many items were changed. */
    public int clearAllAssignments() {
        int changed = 0;
        for (Entry entry : items.values()) {
            if (entry.clearAssignments()) {
                changed++;
            }
        }
        return changed;
    }

    /** Deleted items are kept (with their assignments) so they can be brought back; the oldest go first. */
    public static final int MAX_REMOVED = 100;
    private final Map<String, Entry> removed = new LinkedHashMap<>();

    public Map<String, Entry> removed() {
        return removed;
    }

    public Storage storage() {
        return storage;
    }

    public java.util.List<Zone> zones() {
        return zones;
    }

    /** Adds a zone, or replaces the one with the same name. False when it is invalid or the list is full. */
    public boolean putZone(Zone zone) {
        if (!zone.valid()) {
            return false;
        }
        for (int i = 0; i < zones.size(); i++) {
            if (zones.get(i).name().equals(zone.name())) {
                zones.set(i, zone);
                return true;
            }
        }
        if (zones.size() >= Zone.MAX_ZONES) {
            return false;
        }
        zones.add(zone);
        return true;
    }

    public boolean removeZone(String name) {
        return zones.removeIf(z -> z.name().equals(name));
    }

    public Map<UUID, Long> activity() {
        return activity;
    }

    public void touch(UUID who, long now) {
        if (who != null) {
            activity.put(who, now);
        }
    }

    /**
     * A player put items into (or took items out of) a storage chest: their handed-in count moves with it, and the
     * collected count rises along if it was lower. Only items the player is assigned to count. Returns whether
     * anything changed.
     */
    public boolean deposit(UUID who, String itemId, int delta) {
        Entry entry = items.get(itemId);
        if (entry == null || delta == 0 || !entry.claims().containsKey(who)) {
            return false;
        }
        int next = Math.max(0, entry.delivered().getOrDefault(who, 0) + delta);
        if (next > entry.claims().get(who)) {
            setCollected(who, itemId, next);
        }
        return setDelivered(who, itemId, next);
    }

    /** Takes one player off every item; returns how many items were changed. */
    public int clearPlayer(UUID player) {
        int changed = 0;
        for (Entry entry : items.values()) {
            if (entry.removePlayer(player)) {
                changed++;
            }
        }
        return changed;
    }

    public boolean removeItem(String itemId) {
        Entry entry = items.remove(itemId);
        if (entry == null) {
            return false;
        }
        removed.remove(itemId);
        removed.put(itemId, entry);
        while (removed.size() > MAX_REMOVED) {
            removed.remove(removed.keySet().iterator().next());
        }
        return true;
    }

    /** Brings a deleted item back as it was; false if it is not in the deleted list or the item exists again. */
    public boolean restoreItem(String itemId) {
        if (items.containsKey(itemId) || items.size() >= MAX_ITEMS) {
            return false;
        }
        Entry entry = removed.remove(itemId);
        if (entry == null) {
            return false;
        }
        items.put(itemId, entry);
        return true;
    }

    /** Result of {@link #updateRequired}. */
    public record Update(int added, int changed) {
    }

    /** The design changed: adds new items, sets new required amounts, and keeps every item that is no longer needed. */
    public Update updateRequired(Map<String, Integer> required) {
        int added = 0;
        int changed = 0;
        for (Map.Entry<String, Integer> e : required.entrySet()) {
            if (e.getValue() <= 0 || !isValidItemId(e.getKey())) {
                continue;
            }
            Entry entry = items.get(e.getKey());
            if (entry == null) {
                if (items.size() < MAX_ITEMS) {
                    items.put(e.getKey(), new Entry(e.getValue()));
                    added++;
                }
            } else if (entry.required() != e.getValue()) {
                entry.setRequired(e.getValue());
                changed++;
            }
        }
        return new Update(added, changed);
    }

    /** A copy under another name. {@code everything} also keeps the history, the note and the collected counts. */
    public Project copyAs(String newName, boolean everything) {
        return copyAs(newName, everything, everything);
    }

    public Project copyAs(String newName, boolean everything, boolean keepClaims) {
        Project copy = new Project(newName);
        for (Map.Entry<String, Entry> e : items.entrySet()) {
            Entry from = e.getValue();
            Entry to = new Entry(from.required());
            to.setPriority(from.priority());
            to.setSubstitute(from.substitute());
            if (keepClaims) {
                from.claims().forEach((id, n) -> to.claims().put(id, everything ? n : 0));
                if (everything) {
                    to.delivered().putAll(from.delivered());
                }
                to.targets().putAll(from.targets());
            }
            copy.items.put(e.getKey(), to);
        }
        copy.zones.addAll(zones);
        if (everything) {
            copy.note = note;
            copy.archived = archived;
            history.events().forEach(copy.history::add);
            surplus.forEach((id, m) -> copy.surplus.put(id, new LinkedHashMap<>(m)));
            copy.activity.putAll(activity);
        }
        copy.storage.areas().addAll(storage.areas());
        if (everything) {
            copy.storage.chunks().putAll(storage.copy().chunks());
        }
        return copy;
    }

    public Map<String, Map<UUID, Integer>> surplus() {
        return surplus;
    }

    public int offerCount() {
        return surplus.values().stream().mapToInt(Map::size).sum();
    }

    /** A player offers {@code n} spare pieces of an item (0 takes the offer back). False when nothing changed. */
    public boolean setSurplus(UUID who, String itemId, int n) {
        if (!isValidItemId(itemId)) {
            return false;
        }
        int value = Math.max(0, Math.min(n, MAX_SURPLUS_COUNT));
        Map<UUID, Integer> offers = surplus.get(itemId);
        Integer before = offers == null ? null : offers.get(who);
        if (value == 0) {
            if (before == null) {
                return false;
            }
            offers.remove(who);
            if (offers.isEmpty()) {
                surplus.remove(itemId);
            }
            return true;
        }
        if (before == null && offerCount() >= MAX_OFFERS) {
            return false;
        }
        if (before != null && before == value) {
            return false;
        }
        surplus.computeIfAbsent(itemId, k -> new LinkedHashMap<>()).put(who, value);
        return true;
    }

    /** Somebody takes pieces from an offer: returns how many were really taken (never more than offered). */
    public int takeSurplus(UUID from, String itemId, int n) {
        Map<UUID, Integer> offers = surplus.get(itemId);
        Integer offered = offers == null ? null : offers.get(from);
        if (offered == null || n <= 0) {
            return 0;
        }
        int taken = Math.min(n, offered);
        setSurplus(from, itemId, offered - taken);
        return taken;
    }

    public History history() {
        return history;
    }

    /** Archived projects are tucked away on the archive screen; they keep all their data. */
    public boolean archived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    public String note() {
        return note;
    }

    public void setNote(String text) {
        this.note = cleanNote(text);
    }

    /** True when the project has items and every one of them has been collected. */
    public boolean isComplete() {
        return !items.isEmpty() && items.values().stream().allMatch(e -> e.collectedTotal() >= e.required());
    }

    public int totalCollected(String itemId) {
        Entry entry = items.get(itemId);
        return entry == null ? 0 : entry.collectedTotal();
    }

    public int totalCollected() {
        // each item counts at most its required amount, so over-collecting one item cannot hide another
        return items.values().stream().mapToInt(e -> Math.min(e.collectedTotal(), e.required())).sum();
    }

    public int totalRequired() {
        return items.values().stream().mapToInt(Entry::required).sum();
    }
}
