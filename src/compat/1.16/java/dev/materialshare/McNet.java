package dev.materialshare;

import dev.materialshare.fapi.PacketSender;
import net.fabricmc.fabric.api.network.ServerSidePacketRegistry;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/** The play networking of the server side; Fabric API for 1.16 and 1.16.1 only has the old "v0" packet registry. */
public final class McNet {
    private McNet() {
    }

    public interface Receiver {
        void receive(MinecraftServer server, ServerPlayerEntity player, ServerPlayNetworkHandler handler, PacketByteBuf buf,
                     PacketSender responseSender);
    }

    /** Called once at the start, before any packet is sent or received. */
    public static void init() {
    }

    public static void receive(Identifier channel, Receiver receiver) {
        ServerSidePacketRegistry.INSTANCE.register(channel, (context, buf) -> {
            ServerPlayerEntity player = (ServerPlayerEntity) context.getPlayer();
            receiver.receive(player.getServer(), player, player.networkHandler, buf, (c, b) -> send(player, c, b));
        });
    }

    public static void send(ServerPlayerEntity player, Identifier channel, PacketByteBuf buf) {
        ServerSidePacketRegistry.INSTANCE.sendToPlayer(player, channel, buf);
    }

    public static boolean canSend(ServerPlayerEntity player, Identifier channel) {
        return ServerSidePacketRegistry.INSTANCE.canPlayerReceive(player, channel);
    }
}
