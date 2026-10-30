package dev.materialshare;

import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/** The play networking of the server side (Fabric API changed it for Minecraft 1.20.5, so it is behind this class). */
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
        ServerPlayNetworking.registerGlobalReceiver(channel, receiver::receive);
    }

    public static void send(ServerPlayerEntity player, Identifier channel, PacketByteBuf buf) {
        ServerPlayNetworking.send(player, channel, buf);
    }

    public static boolean canSend(ServerPlayerEntity player, Identifier channel) {
        return ServerPlayNetworking.canSend(player, channel);
    }
}
