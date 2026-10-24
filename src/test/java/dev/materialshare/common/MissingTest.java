package dev.materialshare.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MissingTest {
    private final UUID a = UUID.randomUUID();

    @Test
    void finishedItemsAreLeftOut() {
        Project p = new Project("x");
        p.merge(Map.of("a", 10, "b", 10));
        p.setClaim(a, "a", true);
        p.setCollected(a, "a", 10);
        List<Missing.Item> items = Missing.of(p);
        assertEquals(1, items.size());
        assertEquals("b", items.get(0).itemId());
    }

    @Test
    void nobodyFirstThenTheMostLeft() {
        Project p = new Project("x");
        p.merge(Map.of("taken", 100, "small", 5, "big", 50));
        p.setClaim(a, "taken", true);
        List<Missing.Item> items = Missing.of(p);
        assertEquals(List.of("big", "small", "taken"), items.stream().map(Missing.Item::itemId).toList());
        assertEquals(2, Missing.unassigned(items));
        assertTrue(items.get(2).people().contains(a));
        assertEquals(100, items.get(2).left());
    }

    @Test
    void emptyProjectHasNothingMissing() {
        assertTrue(Missing.of(new Project("x")).isEmpty());
    }
}
