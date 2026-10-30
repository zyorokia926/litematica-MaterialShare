package dev.materialshare;

import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * The play networking of the server side. From Minecraft 1.20.5 on every packet is a typed payload, so each channel of
 * this mod is registered as a payload that just carries the bytes ({@link Raw}); the rest of the mod keeps working
 * with a channel name and a PacketByteBuf.
 */
public final class McNet {
    private McNet() {
    }

    public interface Receiver {
        void receive(MinecraftServer server, ServerPlayerEntity player, ServerPlayNetworkHandler handler, PacketByteBuf buf,
                     PacketSender responseSender);
    }

    /** A packet of this mod: its bytes. */
    public record Raw(CustomPayload.Id<Raw> id, byte[] data) implements CustomPayload {
        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return id;
        }

        public PacketByteBuf buf() {
            return new PacketByteBuf(Unpooled.wrappedBuffer(data));
        }
    }

    static final Map<Identifier, CustomPayload.Id<Raw>> IDS = new HashMap<>();
    private static boolean done;

    public static CustomPayload.Id<Raw> id(Identifier channel) {
        return IDS.get(channel);
    }

    public static Raw wrap(Identifier channel, PacketByteBuf buf) {
        byte[] bytes = new byte[buf.readableBytes()];
        buf.getBytes(buf.readerIndex(), bytes);
        return new Raw(IDS.get(channel), bytes);
    }

    /** Called once at the start, before any packet is sent or received: registers the payload of every channel in both directions. */
    public static synchronized void init() {
        if (done) {
            return;
        }
        done = true;
        try {
            for (Field field : dev.materialshare.common.Packets.class.getFields()) {
                if (field.getType() != Identifier.class || field.get(null).equals(dev.materialshare.common.Packets.HELLO)) {
                    continue; // HELLO is a login packet
                }
                Identifier channel = (Identifier) field.get(null);
                CustomPayload.Id<Raw> id = new CustomPayload.Id<>(channel);
                IDS.put(channel, id);
                PacketCodec<RegistryByteBuf, Raw> codec = new PacketCodec<>() {
                    @Override
                    public Raw decode(RegistryByteBuf buf) {
                        byte[] bytes = new byte[buf.readableBytes()];
                        buf.readBytes(bytes);
                        return new Raw(id, bytes);
                    }

                    @Override
                    public void encode(RegistryByteBuf buf, Raw payload) {
                        buf.writeBytes(payload.data());
                    }
                };
                PayloadTypeRegistry.playC2S().register(id, codec);
                PayloadTypeRegistry.playS2C().register(id, codec);
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void receive(Identifier channel, Receiver receiver) {
        ServerPlayNetworking.registerGlobalReceiver(IDS.get(channel), (payload, context) ->
                receiver.receive(context.server(), context.player(), context.player().networkHandler, payload.buf(),
                        context.responseSender()));
    }

    public static void send(ServerPlayerEntity player, Identifier channel, PacketByteBuf buf) {
        ServerPlayNetworking.send(player, wrap(channel, buf));
    }

    public static boolean canSend(ServerPlayerEntity player, Identifier channel) {
        return ServerPlayNetworking.canSend(player, IDS.get(channel));
    }
}
