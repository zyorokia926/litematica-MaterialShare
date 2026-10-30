package dev.materialshare.fapi;

/** The end of every client tick (through the v0 tick callback of Fabric API). */
public final class ClientTickEvents {
    public static final Event<EndTick> END_CLIENT_TICK = new Event<>();

    static {
        net.fabricmc.fabric.api.event.client.ClientTickCallback.EVENT.register(client -> {
            for (EndTick l : END_CLIENT_TICK.listeners()) {
                l.onEndTick(client);
            }
        });
    }

    private ClientTickEvents() {
    }

    public interface EndTick {
        void onEndTick(net.minecraft.client.MinecraftClient client);
    }
}
