package dev.materialshare.common;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A part of a schematic by height ("layers 1 to 10", counted from the bottom of the schematic) and what it needs per
 * item. Assignments stay with the whole project; a zone only narrows what the list and the HUD show.
 */
public record Zone(String name, int fromLayer, int toLayer, Map<String, Integer> required) {
    public static final int MAX_ZONES = 16;
    /** Higher than any world is tall. */
    public static final int MAX_LAYER = 4096;
    /** Keeps the packet a client sends (ZONE_ADD) far below its 32767-byte limit. */
    public static final int MAX_KINDS = 600;

    /** A zone with a cleaned name, the layers in order and only positive counts. */
    public static Zone of(String name, int a, int b, Map<String, Integer> required) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        required.forEach((id, n) -> {
            if (n != null && n > 0 && counts.size() < MAX_KINDS) {
                counts.put(id, n);
            }
        });
        return new Zone(Entry.cleanText(name), Math.min(a, b), Math.max(a, b), Map.copyOf(counts));
    }

    public boolean valid() {
        return !name.isBlank() && fromLayer >= 1 && toLayer <= MAX_LAYER && fromLayer <= toLayer;
    }

    public int requiredOf(String itemId) {
        return required.getOrDefault(itemId, 0);
    }

    public boolean needs(String itemId) {
        return requiredOf(itemId) > 0;
    }

    /** How many of what this zone needs are collected: the project's total per item, up to the zone's amount. */
    public int collected(Project project) {
        int sum = 0;
        for (Map.Entry<String, Integer> r : required.entrySet()) {
            Entry e = project.items().get(r.getKey());
            sum += e == null ? 0 : Math.min(e.collectedTotal(), r.getValue());
        }
        return sum;
    }

    public int totalRequired() {
        int sum = 0;
        for (int n : required.values()) {
            sum += n;
        }
        return sum;
    }

    public int percent(Project project) {
        int total = totalRequired();
        return total == 0 ? 100 : (int) (100L * collected(project) / total);
    }

    /** Parses "1-10 Walls" (also "1~10", "1〜10"; the name may be left out). Null when it is not a range. */
    public static Parsed parse(String text) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\\s*(\\d{1,4})\\s*[-~〜～]\\s*(\\d{1,4})\\s*(.*)$")
                .matcher(text == null ? "" : text);
        if (!m.matches()) {
            return null;
        }
        int a = Integer.parseInt(m.group(1));
        int b = Integer.parseInt(m.group(2));
        return new Parsed(Math.min(a, b), Math.max(a, b), Entry.cleanText(m.group(3)));
    }

    public record Parsed(int from, int to, String name) {
    }
}
