package dev.materialshare;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

/** The play networking of the client side (see {@link McNet}). */
public final class McClientNet {
    private McClientNet() {
    }

    public interface Receiver {
        void receive(MinecraftClient client, ClientPlayNetworkHandler handler, PacketByteBuf buf, PacketSender responseSender);
    }

    public static void receive(Identifier channel, Receiver receiver) {
        ClientPlayNetworking.registerGlobalReceiver(channel, receiver::receive);
    }

    public static void send(Identifier channel, PacketByteBuf buf) {
        ClientPlayNetworking.send(channel, buf);
    }

    public static boolean canSend(Identifier channel) {
        return ClientPlayNetworking.canSend(channel);
    }
}
