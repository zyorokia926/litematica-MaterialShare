package dev.materialshare;

import dev.materialshare.fapi.PacketSender;
import net.fabricmc.fabric.api.network.ClientSidePacketRegistry;
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
        ClientSidePacketRegistry.INSTANCE.register(channel, (context, buf) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            receiver.receive(client, client.getNetworkHandler(), buf, McClientNet::send);
        });
    }

    public static void send(Identifier channel, PacketByteBuf buf) {
        ClientSidePacketRegistry.INSTANCE.sendToServer(channel, buf);
    }

    public static boolean canSend(Identifier channel) {
        return ClientSidePacketRegistry.INSTANCE.canServerReceive(channel);
    }
}
