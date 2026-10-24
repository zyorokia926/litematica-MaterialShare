package dev.materialshare.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Remembers the claims this player has just made themselves, so that when the server reports "you were given this
 * item" it can be told apart from somebody else assigning the player.
 */
final class OwnActions {
    private static final long WINDOW_MS = 8000;
    private static final Map<String, Long> RECENT = new HashMap<>();

    private OwnActions() {
    }

    private static String key(String project, String itemId) {
        return project + "\n" + itemId;
    }

    static void noteClaim(String project, String itemId, UUID target) {
        if (target != null && target.equals(RowOverlay.selfId())) {
            RECENT.put(key(project, itemId), System.currentTimeMillis());
        }
    }

    private static long quietUntil;

    /** The player let go of this item (or reset assignments) themselves: the server's answer is not news. */
    static void noteRelease(String project, String itemId, UUID target) {
        if (target != null && target.equals(RowOverlay.selfId())) {
            RECENT.put("release\n" + key(project, itemId), System.currentTimeMillis());
        }
    }

    /** After a reset the player started, claims vanish for a moment without it being somebody else's doing. */
    static void quietFor(long ms) {
        quietUntil = System.currentTimeMillis() + ms;
    }

    /** True (once) when the player let go of this item a moment ago, or started a reset a moment ago. */
    static boolean consumeRelease(String project, String itemId) {
        long now = System.currentTimeMillis();
        if (now < quietUntil) {
            return true;
        }
        RECENT.values().removeIf(t -> now - t > WINDOW_MS);
        return RECENT.remove("release\n" + key(project, itemId)) != null;
    }

    /** True (once) when the player claimed this item a moment ago. */
    static boolean consume(String project, String itemId) {
        long now = System.currentTimeMillis();
        RECENT.values().removeIf(t -> now - t > WINDOW_MS);
        return RECENT.remove(key(project, itemId)) != null;
    }
}
