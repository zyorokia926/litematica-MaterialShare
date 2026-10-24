package dev.materialshare.common;

import static org.junit.jupiter.api.Assertions.*;

import dev.materialshare.server.MaterialShareState;
import io.netty.buffer.Unpooled;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import org.junit.jupiter.api.Test;

class PacketsTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private static Project sample() {
        Project p = new Project("castle");
        p.merge(Map.of("minecraft:stone", 64, "minecraft:oak_log", 32));
        p.setClaim(A, "minecraft:stone", true);
        p.setClaim(B, "minecraft:stone", true);
        p.setCollected(A, "minecraft:stone", 10);
        p.setCollected(B, "minecraft:stone", 20);
        return p;
    }

    private static void assertSameProject(Project expected, Project actual) {
        assertEquals(expected.name(), actual.name());
        assertEquals(expected.items().keySet(), actual.items().keySet());
        for (String id : expected.items().keySet()) {
            assertEquals(expected.items().get(id).required(), actual.items().get(id).required());
            assertEquals(expected.items().get(id).claims(), actual.items().get(id).claims());
        }
    }

    @Test
    void projectRoundTrip() {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        Packets.writeProject(buf, sample());
        assertSameProject(sample(), Packets.readProject(buf));
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void namesRoundTrip() {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        Packets.writeNames(buf, Map.of(A, "Alice", B, "Bob"));
        assertEquals(Map.of(A, "Alice", B, "Bob"), Packets.readNames(buf));
    }

    @Test
    void stateNbtRoundTrip() {
        MaterialShareState state = new MaterialShareState();
        state.registry().getOrCreate("castle").merge(Map.of("minecraft:stone", 64));
        Project p = state.registry().get("castle").orElseThrow();
        p.setClaim(A, "minecraft:stone", true);
        p.setCollected(A, "minecraft:stone", 7);
        state.names().put(A, "Alice");

        NbtCompound nbt = state.writeNbt(new NbtCompound());
        MaterialShareState restored = MaterialShareState.fromNbt(nbt);

        assertSameProject(p, restored.registry().get("castle").orElseThrow());
        assertEquals(Map.of(A, "Alice"), restored.names());
    }

    @Test
    void itemUpdateRoundTrip() {
        Project p = sample();
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        Packets.writeItem(buf, p.name(), "minecraft:stone", p.items().get("minecraft:stone"));
        Packets.ItemUpdate update = Packets.readItem(buf);
        assertEquals("castle", update.project());
        assertEquals("minecraft:stone", update.itemId());
        assertEquals(64, update.entry().required());
        assertEquals(p.items().get("minecraft:stone").claims(), update.entry().claims());
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void noteSurvivesPacketAndNbt() {
        Project p = sample();
        p.setNote("材料はチェスト");
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        Packets.writeProject(buf, p);
        assertEquals("材料はチェスト", Packets.readProject(buf).note());

        MaterialShareState state = new MaterialShareState();
        state.registry().getOrCreate("castle").setNote("メモ");
        MaterialShareState restored = MaterialShareState.fromNbt(state.writeNbt(new NbtCompound()));
        assertEquals("メモ", restored.registry().get("castle").orElseThrow().note());
    }

    @Test
    void historyRoundTripsThroughPacketAndNbt() {
        History.Event event = new History.Event(1234L, A, History.Kind.COLLECTED, "minecraft:stone", B, 42);
        History.Event bulk = new History.Event(2000L, B, History.Kind.BULK, "", null, 7);
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        Packets.writeHistory(buf, "castle", java.util.List.of(event, bulk));
        Packets.HistoryReply reply = Packets.readHistory(buf);
        assertEquals("castle", reply.project());
        assertEquals(java.util.List.of(event, bulk), reply.events());
        assertEquals(0, buf.readableBytes());

        MaterialShareState state = new MaterialShareState();
        Project p = state.registry().getOrCreate("castle");
        p.history().add(event);
        p.history().add(bulk);
        MaterialShareState restored = MaterialShareState.fromNbt(state.writeNbt(new NbtCompound()));
        assertEquals(java.util.List.of(event, bulk), restored.registry().get("castle").orElseThrow().history().events());
    }

    @Test
    void bulkClaimRoundTrips() {
        java.util.List<Assignments.Assignment> list = java.util.List.of(
                new Assignments.Assignment("minecraft:stone", A), new Assignments.Assignment("minecraft:dirt", B));
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        Packets.writeBulk(buf, "castle", list);
        Packets.Bulk bulk = Packets.readBulk(buf);
        assertEquals("castle", bulk.project());
        assertEquals(list, bulk.assignments());
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void bulkClaimReadIsCapped() {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeString("castle", Packets.MAX_STRING);
        buf.writeVarInt(Integer.MAX_VALUE); // a hostile length must not make the server read forever
        assertTrue(Packets.readBulk(buf).assignments().size() <= Project.MAX_ITEMS);
    }

    @Test
    void importRoundTripsAndIsCapped() {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        Packets.writeImport(buf, "castle", java.util.Map.of("minecraft:stone", 30));
        Packets.Import read = Packets.readImport(buf);
        assertEquals("castle", read.project());
        assertEquals(java.util.Map.of("minecraft:stone", 30), read.counts());
        assertEquals(0, buf.readableBytes());

        PacketByteBuf hostile = new PacketByteBuf(Unpooled.buffer());
        hostile.writeString("castle", Packets.MAX_STRING);
        hostile.writeVarInt(Integer.MAX_VALUE);
        assertTrue(Packets.readImport(hostile).counts().isEmpty());
    }

    @Test
    void chunksStayUnderThePayloadLimit() {
        // the server accepts 32767 bytes per client payload; a chunk of the longest item ids must fit
        java.util.List<Assignments.Assignment> chunk = new java.util.ArrayList<>();
        String longest = "minecraft:" + "x".repeat(60);
        for (int i = 0; i < Packets.BULK_CHUNK; i++) {
            chunk.add(new Assignments.Assignment(longest, A));
        }
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        Packets.writeBulk(buf, "p".repeat(64), chunk);
        assertTrue(buf.readableBytes() < 32767, "was " + buf.readableBytes());
    }
}
