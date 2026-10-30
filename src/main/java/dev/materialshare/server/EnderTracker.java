package dev.materialshare.server;

import dev.materialshare.common.Packets;
import io.netty.buffer.Unpooled;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.inventory.Inventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Tells each player what lies in their own ender chest (only the server knows it while the chest is closed), so their
 * "what I have" counts can include it. Looked at once a second, sent only when it changed.
 */
final class EnderTracker {
    private static final int INTERVAL_TICKS = 20;
    private static final Map<UUID, Map<String, Integer>> LAST_SENT = new HashMap<>();

    private EnderTracker() {
    }

    static void register() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST_SENT.remove(handler.getPlayer().getUuid()));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % INTERVAL_TICKS != 0) {
                return;
            }
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                // a client that cannot hear the channel yet gets it on a later look
                if (!dev.materialshare.McNet.canSend(player, Packets.ENDER)) {
                    continue;
                }
                Map<String, Integer> counts = counts(player.getEnderChestInventory());
                if (counts.equals(LAST_SENT.get(player.getUuid()))) {
                    continue;
                }
                LAST_SENT.put(player.getUuid(), counts);
                PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
                Packets.writeCounts(buf, counts);
                dev.materialshare.McNet.send(player, Packets.ENDER, buf);
            }
        });
    }

    /** Every item in the chest, what lies inside shulker boxes and bundles counted as well. */
    static Map<String, Integer> counts(Inventory inventory) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int i = 0; i < inventory.size(); i++) {
            StorageTracker.count(inventory.getStack(i), counts, null, 0);
        }
        return counts;
    }
}
