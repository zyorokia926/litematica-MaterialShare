package dev.materialshare.common;

import java.util.Optional;

/**
 * The server and the client must run the same protocol: an old client would silently ignore packets it does not know
 * (it would simply never see other players' edits). The versions are compared when the player logs in.
 */
public final class ProtocolCheck {
    /** Bump when packets change. 1 = whole-project updates, 2 = per-item updates, create refusals, name lookups, 3 = notes, history, bulk assignment, 4 = chunked bulk packets, inventory import, 5 = archive flag, trash, 6 = deadline, item priority and location, 7 = renamed channels, 8 = shares, hand-in, transfers, copies, activity feed, 9 = progress readings for the finish estimate, 10 = shared spare items, 11 = reset of assignments, deleted items that can be restored, 12 = reset of one player's assignments, 13 = no storage place memo, 14 = storage boxes with chest contents and who handed items in, last-action times, 15 = what lies inside shulker boxes is counted separately, 16 = the finish-time estimate was taken out, 17 = the server sends each player what lies in their ender chest, 18 = zones (layers of a schematic), 19 = finding items in the storage and taking them out of an open chest. */
    public static final int PROTOCOL_VERSION = 19;
    /** What an old client (which sent nothing) counts as. */
    public static final int UNKNOWN = 0;

    private ProtocolCheck() {
    }

    public static Optional<String> problem(int server, int client) {
        if (server == client) {
            return Optional.empty();
        }
        String clientText = client == UNKNOWN ? "古いver / an older version" : String.valueOf(client);
        return Optional.of("Litematica MaterialShare のverが、サーバーと合っていません(サーバー: " + server + " / あなた: " + clientText
                + ")。サーバーと同じverの jar を入れてください。 / Litematica MaterialShare does not match the server (server: "
                + server + ", you: " + clientText + "). Install the same version as the server.");
    }
}
