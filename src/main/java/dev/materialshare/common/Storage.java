package dev.materialshare.common;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The places where a project keeps its materials: boxes of blocks, and what the chests inside held when the server last
 * looked (kept per chunk, so chunks that are not loaded right now keep their last known contents).
 */
public final class Storage {
    public static final int MAX_AREAS = 16;
    /** A chunk's list stays short even with a chest full of different items. */
    public static final int MAX_KINDS_PER_CHUNK = 300;

    private final List<Area> areas = new ArrayList<>();
    private final Map<String, Map<String, Integer>> chunks = new LinkedHashMap<>();

    public List<Area> areas() {
        return areas;
    }

    public boolean isEmpty() {
        return areas.isEmpty();
    }

    /** False when the box is invalid, the list is full or the same box is already there. */
    public boolean add(Area area) {
        if (!area.valid() || areas.size() >= MAX_AREAS) {
            return false;
        }
        for (Area a : areas) {
            if (a.equals(area)) {
                return false;
            }
        }
        areas.add(area);
        return true;
    }

    /** Takes a box away; what was counted in the chunks stays until the next scan replaces it. */
    public boolean remove(int index) {
        if (index < 0 || index >= areas.size()) {
            return false;
        }
        areas.remove(index);
        chunks.clear();
        return true;
    }

    public boolean contains(String dim, int x, int y, int z) {
        for (Area a : areas) {
            if (a.contains(dim, x, y, z)) {
                return true;
            }
        }
        return false;
    }

    public static String chunkKey(String dim, int chunkX, int chunkZ) {
        return dim + "|" + chunkX + "," + chunkZ;
    }

    /** Replaces what a chunk held. Returns whether that changed anything. */
    public boolean setChunk(String key, Map<String, Integer> counts) {
        Map<String, Integer> before = chunks.get(key);
        if (counts.isEmpty()) {
            return chunks.remove(key) != null;
        }
        if (counts.equals(before)) {
            return false;
        }
        chunks.put(key, new LinkedHashMap<>(counts));
        return true;
    }

    public Map<String, Map<String, Integer>> chunks() {
        return chunks;
    }

    /** What is kept inside shulker boxes and bundles is stored under the item id with this prefix. */
    public static final String INSIDE = "@box:";

    /** Everything in all the boxes, per item (what is inside shulker boxes included). */
    public Map<String, Integer> totals() {
        Map<String, Integer> total = new LinkedHashMap<>();
        for (Map<String, Integer> counts : chunks.values()) {
            counts.forEach((id, n) -> total.merge(id.startsWith(INSIDE) ? id.substring(INSIDE.length()) : id, n, Integer::sum));
        }
        return total;
    }

    /** The part of {@link #totals()} that lies inside shulker boxes and bundles. */
    public Map<String, Integer> insideTotals() {
        Map<String, Integer> total = new LinkedHashMap<>();
        for (Map<String, Integer> counts : chunks.values()) {
            counts.forEach((id, n) -> {
                if (id.startsWith(INSIDE)) {
                    total.merge(id.substring(INSIDE.length()), n, Integer::sum);
                }
            });
        }
        return total;
    }

    public Storage copy() {
        Storage copy = new Storage();
        copy.areas.addAll(areas);
        chunks.forEach((k, v) -> copy.chunks.put(k, new LinkedHashMap<>(v)));
        return copy;
    }
}
