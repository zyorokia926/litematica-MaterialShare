package dev.materialshare.fapi;

/** The end of every server tick (through the v0 tick callback of Fabric API). */
public final class ServerTickEvents {
    public static final Event<EndTick> END_SERVER_TICK = new Event<>();

    static {
        net.fabricmc.fabric.api.event.server.ServerTickCallback.EVENT.register(server -> {
            for (EndTick l : END_SERVER_TICK.listeners()) {
                l.onEndTick(server);
            }
        });
    }

    private ServerTickEvents() {
    }

    public interface EndTick {
        void onEndTick(net.minecraft.server.MinecraftServer server);
    }
}
