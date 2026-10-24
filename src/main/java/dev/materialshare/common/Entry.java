package dev.materialshare.common;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class Entry {
    private int required;
    private final Map<UUID, Integer> claims = new LinkedHashMap<>();
    /** What each claimant has promised to collect (only claimants who were given a share are listed). */
    private final Map<UUID, Integer> targets = new LinkedHashMap<>();
    /** How much of each claimant's collected amount has been handed in to the shared storage. */
    private final Map<UUID, Integer> delivered = new LinkedHashMap<>();
    private boolean priority;
    private String substitute = "";

    public static final int MAX_TEXT = 60;

    public Entry(int required) {
        this.required = required;
    }

    /** Control characters and the section sign are dropped; cut to {@link #MAX_TEXT} without splitting a character. */
    public static String cleanText(String text) {
        if (text == null) {
            return "";
        }
        String clean = text.replaceAll("[\\p{Cntrl}§]", "").strip();
        if (clean.length() <= MAX_TEXT) {
            return clean;
        }
        int end = MAX_TEXT;
        if (Character.isHighSurrogate(clean.charAt(end - 1))) {
            end--;
        }
        return clean.substring(0, end);
    }

    public int required() {
        return required;
    }

    /** A new required amount (the design changed): counts that would now be too high are cut down. */
    public void setRequired(int required) {
        this.required = Math.max(1, required);
        claims.replaceAll((k, v) -> Math.min(v, this.required));
        targets.replaceAll((k, v) -> Math.min(v, this.required));
        delivered.replaceAll((k, v) -> Math.min(v, claims.getOrDefault(k, 0)));
    }

    public Map<UUID, Integer> claims() {
        return claims;
    }

    public Map<UUID, Integer> targets() {
        return targets;
    }

    public Map<UUID, Integer> delivered() {
        return delivered;
    }

    public int targetOf(UUID player) {
        return targets.getOrDefault(player, 0);
    }

    public int targetTotal() {
        return targets.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int deliveredTotal() {
        return delivered.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** Sum of every claimant's collected count (may exceed required). */
    public int collectedTotal() {
        return claims.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** Takes one player off the item (count, target and hand-in go too). */
    public boolean removePlayer(UUID player) {
        boolean had = claims.remove(player) != null;
        had |= targets.remove(player) != null;
        had |= delivered.remove(player) != null;
        return had;
    }

    /** Takes everybody off the item (their counts, targets and hand-ins go too). */
    public boolean clearAssignments() {
        boolean had = !claims.isEmpty() || !targets.isEmpty() || !delivered.isEmpty();
        claims.clear();
        targets.clear();
        delivered.clear();
        return had;
    }

    /** Marked by players as "needed first". */
    public boolean priority() {
        return priority;
    }

    public void setPriority(boolean priority) {
        this.priority = priority;
    }

    /** A note about what may be used instead (for example "cobbled deepslate is fine too"). */
    public String substitute() {
        return substitute;
    }

    public void setSubstitute(String substitute) {
        this.substitute = cleanText(substitute);
    }
}
