package dev.materialshare.common;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AssignmentsTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private static Project project() {
        Project p = new Project("castle");
        p.merge(Map.of("minecraft:stone", 640, "minecraft:dirt", 300, "minecraft:glass", 300, "minecraft:oak_log", 64));
        return p;
    }

    @Test
    void allToTakesOnlyUnclaimedItems() {
        Project p = project();
        p.setClaim(B, "minecraft:dirt", true);
        List<Assignments.Assignment> result = Assignments.allTo(p, A);
        assertEquals(3, result.size());
        assertTrue(result.stream().allMatch(a -> a.player().equals(A)));
        assertFalse(result.stream().anyMatch(a -> a.itemId().equals("minecraft:dirt")));
    }

    @Test
    void spreadBalancesByRequiredAmount() {
        List<Assignments.Assignment> result = Assignments.spread(project(), List.of(A, B));
        assertEquals(4, result.size());
        long a = result.stream().filter(x -> x.player().equals(A))
                .mapToLong(x -> project().items().get(x.itemId()).required()).sum();
        long b = result.stream().filter(x -> x.player().equals(B))
                .mapToLong(x -> project().items().get(x.itemId()).required()).sum();
        assertEquals(1304, a + b);
        assertTrue(Math.abs(a - b) <= 640, "loads " + a + " vs " + b);
    }

    @Test
    void spreadWithNobodyDoesNothing() {
        assertTrue(Assignments.spread(project(), List.of()).isEmpty());
    }

    @Test
    void spreadIsDeterministic() {
        assertEquals(Assignments.spread(project(), List.of(A, B)), Assignments.spread(project(), List.of(A, B)));
    }

    @Test
    void spreadAlsoBalancesTheNumberOfKinds() {
        Project p = new Project("kinds");
        p.items().put("minecraft:stone", new Entry(100));
        for (int i = 0; i < 8; i++) {
            p.items().put("minecraft:item_" + i, new Entry(10));
        }
        List<Assignments.Assignment> result = Assignments.spread(p, List.of(A, B));
        long a = result.stream().filter(x -> x.player().equals(A)).count();
        long b = result.size() - a;
        assertTrue(Math.abs(a - b) <= 4, "kinds " + a + " vs " + b);
        assertTrue(a >= 1 && b >= 1);
    }
}
