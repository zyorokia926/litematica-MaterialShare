package dev.materialshare.common;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Dividing a required amount among players. */
public final class Split {
    private Split() {
    }

    /** Everyone gets the same share; the rest (a few items) goes to the first players, so the shares add up exactly. */
    public static Map<UUID, Integer> even(int required, List<UUID> players) {
        Map<UUID, Integer> shares = new LinkedHashMap<>();
        if (players.isEmpty() || required <= 0) {
            return shares;
        }
        int base = required / players.size();
        int rest = required % players.size();
        for (int i = 0; i < players.size(); i++) {
            int share = base + (i < rest ? 1 : 0);
            if (share > 0) {
                shares.put(players.get(i), share);
            }
        }
        return shares;
    }

    /** What is still not promised to anybody. */
    public static int unassigned(Entry entry) {
        return Math.max(0, entry.required() - entry.targetTotal());
    }
}
