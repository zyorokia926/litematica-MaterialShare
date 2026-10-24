package dev.materialshare.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TotalsTest {
    private final UUID me = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    private Project project(String name, Map<String, Integer> items) {
        Project p = new Project(name);
        p.merge(items);
        return p;
    }

    @Test
    void addsUpTheSameMaterialAcrossProjects() {
        Project a = project("a", Map.of("iron", 100, "dirt", 10));
        Project b = project("b", Map.of("iron", 50));
        for (Project p : List.of(a, b)) {
            p.setClaim(me, "iron", true);
        }
        a.setClaim(me, "dirt", true);
        a.setCollected(me, "iron", 30);
        List<Totals.Item> totals = Totals.of(List.of(a, b), me);
        assertEquals("iron", totals.get(0).itemId());
        assertEquals(70 + 50, totals.get(0).left());
        assertEquals(2, totals.get(0).projects());
        assertEquals(10, totals.get(1).left());
    }

    @Test
    void finishedItemsAndOtherPeoplesItemsAreLeftOut() {
        Project a = project("a", Map.of("iron", 10, "gold", 10));
        a.setClaim(me, "iron", true);
        a.setCollected(me, "iron", 10);
        a.setClaim(other, "gold", true);
        assertTrue(Totals.of(List.of(a), me).isEmpty());
    }
}
