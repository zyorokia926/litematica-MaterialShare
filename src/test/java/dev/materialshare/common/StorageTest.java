package dev.materialshare.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StorageTest {
    private static final UUID ALICE = UUID.randomUUID();

    @Test
    void areaOrdersCornersAndChecksSize() {
        Area a = Area.between("Main", "minecraft:overworld", 10, 70, 5, -2, 64, 20);
        assertEquals(-2, a.minX());
        assertEquals(10, a.maxX());
        assertEquals(64, a.minY());
        assertTrue(a.valid());
        assertTrue(a.contains("minecraft:overworld", 0, 66, 10));
        assertFalse(a.contains("minecraft:the_nether", 0, 66, 10));
        assertFalse(Area.between("Big", "minecraft:overworld", 0, 0, 0, 300, 10, 10).valid());
        assertFalse(Area.between("", "minecraft:overworld", 0, 0, 0, 1, 1, 1).valid());
    }

    @Test
    void storageKeepsSeveralBoxesAndSumsChunks() {
        Storage s = new Storage();
        assertTrue(s.add(Area.between("A", "d", 0, 0, 0, 5, 5, 5)));
        assertTrue(s.add(Area.between("B", "d", 100, 0, 100, 105, 5, 105)));
        assertFalse(s.add(Area.between("A", "d", 0, 0, 0, 5, 5, 5)));
        s.setChunk(Storage.chunkKey("d", 0, 0), Map.of("minecraft:stone", 64));
        s.setChunk(Storage.chunkKey("d", 6, 6), Map.of("minecraft:stone", 36, "minecraft:dirt", 1));
        assertEquals(100, s.totals().get("minecraft:stone"));
        assertFalse(s.setChunk(Storage.chunkKey("d", 0, 0), Map.of("minecraft:stone", 64)));
        assertTrue(s.setChunk(Storage.chunkKey("d", 0, 0), Map.of()));
        assertEquals(36, s.totals().get("minecraft:stone"));
        assertTrue(s.remove(0));
        assertTrue(s.totals().isEmpty());
        assertEquals(1, s.areas().size());
    }

    @Test
    void limitsBoxes() {
        Storage s = new Storage();
        for (int i = 0; i < Storage.MAX_AREAS; i++) {
            assertTrue(s.add(Area.between("n" + i, "d", i, 0, 0, i, 1, 1)));
        }
        assertFalse(s.add(Area.between("extra", "d", 50, 0, 0, 50, 1, 1)));
    }

    @Test
    void depositMovesHandedInAndRaisesCollected() {
        Project p = new Project("p");
        p.merge(Map.of("minecraft:stone", 100));
        assertFalse(p.deposit(ALICE, "minecraft:stone", 10)); // not assigned: nothing is credited
        p.setClaim(ALICE, "minecraft:stone", true);
        assertTrue(p.deposit(ALICE, "minecraft:stone", 30));
        Entry e = p.items().get("minecraft:stone");
        assertEquals(30, e.delivered().get(ALICE));
        assertEquals(30, e.claims().get(ALICE)); // collected cannot be lower than handed in
        assertTrue(p.deposit(ALICE, "minecraft:stone", -50));
        assertFalse(e.delivered().containsKey(ALICE)); // never below zero
        assertTrue(p.deposit(ALICE, "minecraft:stone", 500));
        assertEquals(100, e.delivered().get(ALICE)); // capped by the required amount
    }

    @Test
    void idleAfterThreeDays() {
        long day = 24L * 3600 * 1000;
        assertFalse(Idle.idle(0, 10 * day));
        assertFalse(Idle.idle(9 * day, 10 * day));
        assertTrue(Idle.idle(7 * day, 10 * day));
        assertEquals(3, Idle.days(7 * day, 10 * day));
    }

    @Test
    void copyKeepsBoxes() {
        Project p = new Project("p");
        p.storage().add(Area.between("A", "d", 0, 0, 0, 1, 1, 1));
        p.touch(ALICE, 5);
        assertEquals(1, p.copyAs("q", false).storage().areas().size());
        assertEquals(5L, p.copyAs("q", true).activity().get(ALICE));
    }
}
