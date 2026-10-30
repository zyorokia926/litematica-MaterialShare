package dev.materialshare.fapi;

/** See {@link ServerLoginConnectionEvents}: nothing is sent at login on 1.16 and 1.16.1. */
public final class ServerLoginNetworking {
    private ServerLoginNetworking() {
    }

    public interface Handler {
        void receive(net.minecraft.server.MinecraftServer server, LoginHandler handler, boolean understood, net.minecraft.network.PacketByteBuf buf,
                     Object synchronizer, PacketSender responseSender);
    }

    public interface LoginHandler {
        void disconnect(net.minecraft.text.Text reason);
    }

    public static void registerGlobalReceiver(net.minecraft.util.Identifier channel, Handler handler) {
    }
}
