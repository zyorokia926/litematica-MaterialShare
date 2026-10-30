package dev.materialshare.client;

import dev.materialshare.common.Project;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** All projects known to this client, plus the one currently shown in the material list screen. */
public final class ClientProjectCache {
    private static final Map<String, Project> PROJECTS = new LinkedHashMap<>();
    private static final Map<UUID, String> NAMES = new LinkedHashMap<>();
    private static dev.materialshare.common.Packets.HistoryReply history;
    private static int historyVersion;
    private static String selected;
    private static int version;
    private static int namesVersion;
    private static java.util.List<dev.materialshare.common.Packets.TrashEntry> trash = java.util.List.of();
    private static java.util.List<dev.materialshare.common.Packets.FeedEntry> feed = java.util.List.of();
    private static int feedVersion;
    private static final Map<String, java.util.List<dev.materialshare.common.Packets.Offer>> SURPLUS = new LinkedHashMap<>();
    private static final Map<String, dev.materialshare.common.Packets.StorageData> STORAGE = new LinkedHashMap<>();
    private static final Map<String, Map<UUID, Long>> ACTIVITY = new LinkedHashMap<>();
    /** What lies in your own ender chest (the server sends it when it changes). */
    private static Map<String, Integer> ender = Map.of();
    private static final Map<String, java.util.List<dev.materialshare.common.Zone>> ZONES = new LinkedHashMap<>();
    /** The zone each project is narrowed to, chosen by you alone (not shared). */
    private static final Map<String, String> CHOSEN_ZONE = new LinkedHashMap<>();
    private static final Map<String, Long> ACTIVITY_ASKED = new LinkedHashMap<>();
    private ClientProjectCache() {
    }

    /** Incremented on every change coming from the server, so screens can refresh themselves. */
    public static int version() {
        return version;
    }

    /** Incremented only when the list of known players changes (the player picker refreshes on this alone). */
    public static int namesVersion() {
        return namesVersion;
    }

    public static boolean exists(String name) {
        return PROJECTS.containsKey(name);
    }

    public static Collection<Project> projects() {
        return PROJECTS.values();
    }

    public static Optional<Project> get(String name) {
        return Optional.ofNullable(PROJECTS.get(name));
    }

    public static Optional<Project> selected() {
        return selected == null ? Optional.empty() : Optional.ofNullable(PROJECTS.get(selected));
    }

    public static void select(String nameOrNull) {
        selected = nameOrNull;
    }

    private static String selectWhenArrives;

    /** Selects a project as soon as the server reports it (after a rename or a copy, which arrive a moment later). */
    public static void selectWhenArrives(String name) {
        selectWhenArrives = name;
    }

    /** Every player the server knows (online now or seen before). */
    public static Map<UUID, String> players() {
        Map<UUID, String> current = new LinkedHashMap<>();
        NAMES.forEach((id, name) -> current.put(id, nameOf(id)));
        return java.util.Collections.unmodifiableMap(current);
    }

    /** Changes the shown version so open screens redraw (a name or a skin arrived from Mojang). */
    static void touch() {
        version++;
        namesVersion++;
    }

    /** The name Mojang has now if it is known, else the one the server remembers. */
    public static String nameOf(UUID uuid) {
        MojangProfiles.request(uuid);
        String current = MojangProfiles.name(uuid);
        if (current != null) {
            return current;
        }
        return NAMES.getOrDefault(uuid, uuid.toString().substring(0, 8));
    }

    static void setZones(dev.materialshare.common.Packets.ZoneList list) {
        if (list.zones().isEmpty()) {
            ZONES.remove(list.project());
        } else {
            ZONES.put(list.project(), list.zones());
        }
        version++;
    }

    public static java.util.List<dev.materialshare.common.Zone> zones(String project) {
        return ZONES.getOrDefault(project, java.util.List.of());
    }

    /** The zone you narrowed this project to; empty when none is chosen (or it was removed). */
    public static Optional<dev.materialshare.common.Zone> chosenZone(String project) {
        String name = CHOSEN_ZONE.get(project);
        return name == null ? Optional.empty() : zones(project).stream().filter(z -> z.name().equals(name)).findFirst();
    }

    /** The chosen zone of the selected project. */
    public static Optional<dev.materialshare.common.Zone> chosenZone() {
        return selected().flatMap(p -> chosenZone(p.name()));
    }

    static void chooseZone(String project, String zone) {
        if (zone == null) {
            CHOSEN_ZONE.remove(project);
        } else {
            CHOSEN_ZONE.put(project, zone);
        }
        version++;
    }

    static void setEnder(Map<String, Integer> counts) {
        ender = Map.copyOf(counts);
        version++;
    }

    /** Your ender chest's counts, or nothing when the setting to count it is off. */
    static Map<String, Integer> ender() {
        return dev.materialshare.client.config.Configs.Generic.COUNT_ENDER.getBooleanValue() ? ender : Map.of();
    }

    static void replaceAll(Collection<Project> projects) {
        PROJECTS.clear();
        SURPLUS.clear();
        STORAGE.clear();
        ACTIVITY.clear();
        REMOVED.clear();
        ZONES.clear();
        projects.forEach(p -> PROJECTS.put(p.name(), p));
        version++;
        dropStaleSelection();
    }

    static void put(Project project) {
        PROJECTS.put(project.name(), project);
        if (project.name().equals(selectWhenArrives)) {
            selected = project.name();
            selectWhenArrives = null;
        }
        version++;
    }

    /** One item changed (claim or count): replaces just that item of the cached project. */
    static void putItem(dev.materialshare.common.Packets.ItemUpdate update) {
        Project project = PROJECTS.get(update.project());
        if (project != null) {
            boolean wasComplete = project.isComplete();
            dev.materialshare.common.Entry old = project.items().get(update.itemId());
            project.items().put(update.itemId(), update.entry());
            version++;
            UUID self = RowOverlay.selfId();
            if (update.entry().claims().containsKey(self) && (old == null || !old.claims().containsKey(self))
                    && !OwnActions.consume(project.name(), update.itemId())) {
                Notifier.assigned(project.name(), update.itemId());
            }
            if (old != null && old.claims().containsKey(self) && !update.entry().claims().containsKey(self)
                    && !OwnActions.consumeRelease(project.name(), update.itemId())) {
                Notifier.unassigned(project.name(), update.itemId());
            }
            if (dev.materialshare.common.Completion.itemDone(old, update.entry())) {
                Notifier.itemDone(project.name(), update.itemId(), update.entry().claims().containsKey(self));
            }
            if (!wasComplete && project.isComplete()) {
                boolean mine = project.items().values().stream().anyMatch(e -> e.claims().containsKey(self));
                Notifier.projectDone(project.name(), mine);
            }
        }
    }

    static void setTrash(java.util.List<dev.materialshare.common.Packets.TrashEntry> list) {
        trash = list;
        version++;
    }

    /** The deleted projects of every schematic, newest deletion last. */
    public static java.util.List<dev.materialshare.common.Packets.TrashEntry> trash() {
        return trash;
    }

    static void setFeed(java.util.List<dev.materialshare.common.Packets.FeedEntry> list) {
        feed = list;
        feedVersion++;
        version++;
    }

    /** The latest changes of every project, newest first. */
    public static java.util.List<dev.materialshare.common.Packets.FeedEntry> feed() {
        return feed;
    }

    public static int feedVersion() {
        return feedVersion;
    }

    private static final java.util.Map<String, java.util.List<String>> REMOVED = new java.util.HashMap<>();

    static void setRemoved(dev.materialshare.common.Packets.RemovedList list) {
        REMOVED.put(list.project(), list.itemIds());
        version++;
    }

    /** Items deleted from a project that can still be restored (newest last). */
    public static java.util.List<String> removed(String project) {
        return REMOVED.getOrDefault(project, java.util.List.of());
    }

    static void setSurplus(dev.materialshare.common.Packets.SurplusList list) {
        SURPLUS.put(list.project(), list.offers());
        version++;
    }

    /** The spare items offered in a project. */
    public static java.util.List<dev.materialshare.common.Packets.Offer> surplus(String project) {
        return SURPLUS.getOrDefault(project, java.util.List.of());
    }

    /** The offers of one item (empty when nobody has spare pieces of it). */
    public static java.util.List<dev.materialshare.common.Packets.Offer> surplusOf(String project, String itemId) {
        return surplus(project).stream().filter(o -> o.itemId().equals(itemId)).toList();
    }

    static void setStorage(dev.materialshare.common.Packets.StorageData data) {
        if (data.areas().isEmpty() && data.stock().isEmpty()) {
            STORAGE.remove(data.project());
        } else {
            STORAGE.put(data.project(), data);
        }
        version++;
    }

    /** The storage boxes of a project and what the chests in them hold (empty when it has none). */
    public static dev.materialshare.common.Packets.StorageData storage(String project) {
        return STORAGE.getOrDefault(project, new dev.materialshare.common.Packets.StorageData(project, java.util.List.of(), Map.of(), Map.of()));
    }

    static void setActivity(dev.materialshare.common.Packets.ActivityData data) {
        ACTIVITY.put(data.project(), data.last());
        version++;
    }

    /** Whole days since the player last did something in the project; 0 when unknown or recent. */
    public static int idleDays(String project, UUID who) {
        Long last = ACTIVITY.getOrDefault(project, Map.of()).get(who);
        return last == null ? 0 : dev.materialshare.common.Idle.days(last, System.currentTimeMillis());
    }

    public static boolean idle(String project, UUID who) {
        Long last = ACTIVITY.getOrDefault(project, Map.of()).get(who);
        return last != null && dev.materialshare.common.Idle.idle(last, System.currentTimeMillis());
    }

    /** Asks the server when people were last active (at most every 30 seconds per project). */
    public static void requestActivity(String project) {
        long now = System.currentTimeMillis();
        Long asked = ACTIVITY_ASKED.get(project);
        if (asked == null || now - asked > 30_000) {
            ACTIVITY_ASKED.put(project, now);
            ClientNet.requestActivity(project);
        }
    }

    static void setHistory(dev.materialshare.common.Packets.HistoryReply reply) {
        history = reply;
        historyVersion++;
    }

    public static java.util.List<dev.materialshare.common.History.Event> historyOf(String project) {
        return history != null && history.project().equals(project) ? history.events() : java.util.List.of();
    }

    public static int historyVersion() {
        return historyVersion;
    }

    static void remove(String name) {
        PROJECTS.remove(name);
        ZONES.remove(name);
        SURPLUS.remove(name);
        STORAGE.remove(name);
        ACTIVITY.remove(name);
        REMOVED.remove(name);
        version++;
        dropStaleSelection();
    }

    static void setNames(Map<UUID, String> names) {
        NAMES.clear();
        NAMES.putAll(names);
        version++;
        namesVersion++;
    }

    public static void clear() {
        PROJECTS.clear();
        NAMES.clear();
        trash = java.util.List.of();
        feed = java.util.List.of();
        SURPLUS.clear();
        STORAGE.clear();
        ACTIVITY.clear();
        ACTIVITY_ASKED.clear();
        REMOVED.clear();
        ZONES.clear();
        CHOSEN_ZONE.clear();
        ender = Map.of();
        selected = null;
        version++;
        namesVersion++;
    }

    private static void dropStaleSelection() {
        if (selected != null && !PROJECTS.containsKey(selected)) {
            selected = null;
        }
    }
}
