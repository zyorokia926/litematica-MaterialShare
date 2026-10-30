package dev.materialshare.fapi;

public final class PacketByteBufs {
    private PacketByteBufs() {
    }

    public static net.minecraft.network.PacketByteBuf create() {
        return new net.minecraft.network.PacketByteBuf(io.netty.buffer.Unpooled.buffer());
    }
}
