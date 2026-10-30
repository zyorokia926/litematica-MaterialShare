package dev.materialshare.fapi;

/** The server is starting (through the v0 start callback of Fabric API). */
public final class ServerLifecycleEvents {
    public static final Event<ServerStarting> SERVER_STARTING = new Event<>();

    static {
        net.fabricmc.fabric.api.event.server.ServerStartCallback.EVENT.register(server -> {
            for (ServerStarting l : SERVER_STARTING.listeners()) {
                l.onServerStarting(server);
            }
        });
    }

    private ServerLifecycleEvents() {
    }

    public interface ServerStarting {
        void onServerStarting(net.minecraft.server.MinecraftServer server);
    }
}
