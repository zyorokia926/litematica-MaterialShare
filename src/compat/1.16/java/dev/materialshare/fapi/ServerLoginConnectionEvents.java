package dev.materialshare.fapi;

/**
 * 1.16 and 1.16.1 have no login networking in Fabric API: the version check at login is left out (a player with another
 * version of this mod can join; the help screen still says which protocol the server speaks).
 */
public final class ServerLoginConnectionEvents {
    public static final Event<QueryStart> QUERY_START = new Event<>();

    private ServerLoginConnectionEvents() {
    }

    public interface Sender {
        void sendPacket(net.minecraft.util.Identifier channel, net.minecraft.network.PacketByteBuf buf);
    }

    public interface QueryStart {
        void onLoginStart(Object handler, net.minecraft.server.MinecraftServer server, Sender sender, Object synchronizer);
    }
}
