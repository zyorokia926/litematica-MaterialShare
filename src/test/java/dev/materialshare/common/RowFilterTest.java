package dev.materialshare.common;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class RowFilterTest {
    private static final UUID ME = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private static Entry entry(int required, UUID who, int collected) {
        Entry e = new Entry(required);
        if (who != null) {
            e.claims().put(who, collected);
        }
        return e;
    }

    @Test
    void allShowsEverything() {
        assertTrue(RowFilter.ALL.matches(null, ME));
        assertTrue(RowFilter.ALL.matches(entry(10, OTHER, 10), ME));
    }

    @Test
    void mineNeedsMyClaim() {
        assertFalse(RowFilter.MINE.matches(null, ME));
        assertFalse(RowFilter.MINE.matches(entry(10, OTHER, 0), ME));
        assertTrue(RowFilter.MINE.matches(entry(10, ME, 0), ME));
    }

    @Test
    void unclaimedIncludesUnregisteredItems() {
        assertTrue(RowFilter.UNCLAIMED.matches(null, ME));
        assertTrue(RowFilter.UNCLAIMED.matches(entry(10, null, 0), ME));
        assertFalse(RowFilter.UNCLAIMED.matches(entry(10, OTHER, 0), ME));
    }

    @Test
    void incompleteHidesFinishedItems() {
        assertTrue(RowFilter.INCOMPLETE.matches(null, ME));
        assertTrue(RowFilter.INCOMPLETE.matches(entry(10, ME, 9), ME));
        assertFalse(RowFilter.INCOMPLETE.matches(entry(10, ME, 10), ME));
    }

    @Test
    void nextCyclesThroughAllAndWraps() {
        RowFilter f = RowFilter.ALL;
        for (int i = 0; i < RowFilter.values().length; i++) {
            f = f.next();
        }
        assertEquals(RowFilter.ALL, f);
        assertEquals(RowFilter.MINE, RowFilter.ALL.next());
    }

    @Test
    void personShowsTheItemsOfThatPlayer() {
        assertTrue(RowFilter.PERSON.matches(null, ME, null), "nobody chosen yet: everything");
        assertTrue(RowFilter.PERSON.matches(entry(10, OTHER, 0), ME, OTHER));
        assertFalse(RowFilter.PERSON.matches(entry(10, ME, 0), ME, OTHER));
        assertFalse(RowFilter.PERSON.matches(null, ME, OTHER));
    }
}
