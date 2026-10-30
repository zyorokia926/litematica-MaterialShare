package dev.materialshare.fapi;

/** See {@link ServerLoginConnectionEvents}: nothing is asked at login on 1.16 and 1.16.1. */
public final class ClientLoginNetworking {
    private ClientLoginNetworking() {
    }

    public interface Handler {
        java.util.concurrent.CompletableFuture<net.minecraft.network.PacketByteBuf> receive(net.minecraft.client.MinecraftClient client, Object handler,
                net.minecraft.network.PacketByteBuf buf, java.util.function.Consumer<Object> listenerAdder);
    }

    public static void registerGlobalReceiver(net.minecraft.util.Identifier channel, Handler handler) {
    }
}
