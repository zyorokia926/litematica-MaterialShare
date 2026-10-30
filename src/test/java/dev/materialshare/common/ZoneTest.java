package dev.materialshare.common;

import static org.junit.jupiter.api.Assertions.*;

import dev.materialshare.server.MaterialShareState;
import io.netty.buffer.Unpooled;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import org.junit.jupiter.api.Test;

class ZoneTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    @Test
    void parsesRangesAndNames() {
        assertEquals(new Zone.Parsed(1, 10, "Base"), Zone.parse("1-10 Base"));
        assertEquals(new Zone.Parsed(3, 7, "壁"), Zone.parse(" 7〜3 壁 "));
        assertEquals(new Zone.Parsed(1, 2, ""), Zone.parse("1~2"));
        assertNull(Zone.parse("Base"));
        assertNull(Zone.parse(""));
        assertNull(Zone.parse(null));
    }

    @Test
    void cleansAndOrders() {
        Zone z = Zone.of("  Base§ ", 10, 1, Map.of("minecraft:stone", 5, "minecraft:dirt", 0));
        assertEquals("Base", z.name());
        assertEquals(1, z.fromLayer());
        assertEquals(10, z.toLayer());
        assertEquals(Map.of("minecraft:stone", 5), z.required());
        assertTrue(z.valid());
        assertFalse(Zone.of("", 1, 2, Map.of()).valid());
        assertFalse(Zone.of("x", 0, 2, Map.of()).valid());
        Map<String, Integer> many = new HashMap<>();
        for (int i = 0; i < Zone.MAX_KINDS + 50; i++) {
            many.put("minecraft:item" + i, 1);
        }
        assertEquals(Zone.MAX_KINDS, Zone.of("x", 1, 2, many).required().size());
    }

    @Test
    void progressIsCappedByWhatTheZoneNeeds() {
        Project p = new Project("castle");
        p.merge(Map.of("minecraft:stone", 100, "minecraft:glass", 10));
        p.setClaim(A, "minecraft:stone", true);
        p.setCollected(A, "minecraft:stone", 80);
        Zone z = Zone.of("Base", 1, 5, Map.of("minecraft:stone", 40, "minecraft:glass", 10));
        // 80 stone collected, the zone needs only 40 of them; no glass yet
        assertEquals(40, z.collected(p));
        assertEquals(50, z.totalRequired());
        assertEquals(80, z.percent(p));
        assertTrue(z.needs("minecraft:glass"));
        assertFalse(z.needs("minecraft:dirt"));
    }

    @Test
    void projectKeepsAtMostSixteenAndReplacesByName() {
        Project p = new Project("castle");
        for (int i = 0; i < Zone.MAX_ZONES; i++) {
            assertTrue(p.putZone(Zone.of("z" + i, 1, 2, Map.of("minecraft:stone", 1))));
        }
        assertFalse(p.putZone(Zone.of("one more", 1, 2, Map.of("minecraft:stone", 1))));
        assertTrue(p.putZone(Zone.of("z3", 4, 9, Map.of("minecraft:stone", 2))));
        assertEquals(Zone.MAX_ZONES, p.zones().size());
        assertEquals(4, p.zones().get(3).fromLayer());
        assertTrue(p.removeZone("z3"));
        assertFalse(p.removeZone("z3"));
        assertEquals(Zone.MAX_ZONES - 1, p.copyAs("copy", false).zones().size());
    }

    @Test
    void roundTripsThroughPacketsAndNbt() {
        MaterialShareState state = new MaterialShareState();
        Project p = state.registry().getOrCreate("castle");
        p.putZone(Zone.of("Base", 1, 10, Map.of("minecraft:stone", 640, "minecraft:oak_planks", 12)));
        p.putZone(Zone.of("壁", 11, 30, Map.of("minecraft:glass", 3)));

        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        Packets.writeZones(buf, p);
        Packets.ZoneList list = Packets.readZones(buf);
        assertEquals("castle", list.project());
        assertEquals(p.zones(), list.zones());
        assertEquals(0, buf.readableBytes());

        PacketByteBuf add = new PacketByteBuf(Unpooled.buffer());
        Packets.writeZoneAdd(add, "castle", p.zones().get(1));
        Packets.ZoneAdd read = Packets.readZoneAdd(add);
        assertEquals("castle", read.project());
        assertEquals(p.zones().get(1), read.zone());

        MaterialShareState restored = MaterialShareState.fromNbt(state.writeNbt(new NbtCompound()));
        assertEquals(p.zones(), restored.registry().get("castle").orElseThrow().zones());
    }
}
