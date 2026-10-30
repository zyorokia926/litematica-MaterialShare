package dev.materialshare.fapi;

/** The client left a server (fired by ClientPlayNetworkHandlerMixin). */
public final class ClientPlayConnectionEvents {
    public static final Event<Disconnect> DISCONNECT = new Event<>();

    private ClientPlayConnectionEvents() {
    }

    public interface Disconnect {
        void onPlayDisconnect(net.minecraft.client.network.ClientPlayNetworkHandler handler, net.minecraft.client.MinecraftClient client);
    }
}
