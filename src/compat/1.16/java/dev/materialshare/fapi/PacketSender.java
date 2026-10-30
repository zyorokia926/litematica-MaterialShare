package dev.materialshare.fapi;

/** Sends a packet back to where one came from. */
public interface PacketSender {
    void sendPacket(net.minecraft.util.Identifier channel, net.minecraft.network.PacketByteBuf buf);
}
