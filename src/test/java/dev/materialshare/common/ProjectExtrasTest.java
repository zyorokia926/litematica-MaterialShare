package dev.materialshare.common;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProjectExtrasTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    @Test
    void noteIsCleanedAndTruncated() {
        assertEquals("a b", Project.cleanNote("a\u0000 b\u0007"));
        assertEquals("", Project.cleanNote(null));
        String clean = Project.cleanNote("x".repeat(500));
        assertEquals(Project.MAX_NOTE, clean.length());
        // never cut a surrogate pair in half
        String emoji = "a".repeat(Project.MAX_NOTE - 1) + "😀";
        String cut = Project.cleanNote(emoji);
        assertFalse(Character.isHighSurrogate(cut.charAt(cut.length() - 1)));
    }

    @Test
    void sectionSignIsRemovedFromNotes() {
        assertFalse(Project.cleanNote("§cred").contains("§"));
    }

    @Test
    void completeNeedsEveryItemDone() {
        Project p = new Project("p");
        assertFalse(p.isComplete());
        p.merge(Map.of("minecraft:stone", 2, "minecraft:dirt", 1));
        p.setClaim(A, "minecraft:stone", true);
        p.setClaim(A, "minecraft:dirt", true);
        p.setCollected(A, "minecraft:stone", 2);
        assertFalse(p.isComplete());
        p.setCollected(A, "minecraft:dirt", 1);
        assertTrue(p.isComplete());
    }

    @Test
    void itemDoneDetectsOnlyTheTransition() {
        Entry before = new Entry(10);
        before.claims().put(A, 9);
        Entry after = new Entry(10);
        after.claims().put(A, 10);
        assertTrue(Completion.itemDone(before, after));
        assertFalse(Completion.itemDone(after, after)); // was already done
        assertFalse(Completion.itemDone(after, before)); // went backwards
        assertTrue(Completion.itemDone(null, after)); // a brand new, already finished item
    }

    @Test
    void historyKeepsOnlyTheNewestEvents() {
        History h = new History();
        for (int i = 0; i < History.MAX + 20; i++) {
            h.add(new History.Event(i, A, History.Kind.COLLECTED, "minecraft:item" + i, A, i));
        }
        assertEquals(History.MAX, h.events().size());
        assertEquals(History.MAX + 19, h.latestFirst().get(0).time());
    }

    @Test
    void importNeverLowersAndCapsAtRequired() {
        assertEquals(30, Counts.imported(0, 30, 64));
        assertEquals(64, Counts.imported(0, 500, 64));
        assertEquals(50, Counts.imported(50, 10, 64)); // items already handed in are not forgotten
        assertEquals(0, Counts.imported(0, -5, 64));
    }

    @Test
    void quickRepeatedCountChangesOfOneItemShareOneLine() {
        History h = new History();
        h.add(new History.Event(0, A, History.Kind.COLLECTED, "minecraft:stone", A, 1));
        h.add(new History.Event(2_000, A, History.Kind.COLLECTED, "minecraft:stone", A, 2));
        h.add(new History.Event(4_000, A, History.Kind.COLLECTED, "minecraft:stone", A, 3));
        assertEquals(1, h.events().size());
        assertEquals(3, h.events().get(0).value());
        h.add(new History.Event(60_000, A, History.Kind.COLLECTED, "minecraft:stone", A, 4)); // a pause: new line
        assertEquals(2, h.events().size());
    }
}
