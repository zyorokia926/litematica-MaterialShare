package dev.materialshare.common;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class HistoryTextTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private static final Translator STUB = (key, args) -> key + java.util.Arrays.toString(args);

    private static String text(History.Event e) {
        return HistoryText.describe(e, id -> id.equals(A) ? "Alice" : "Bob", id -> "石", STUB);
    }

    @Test
    void claimForSomeoneElseNamesBoth() {
        String t = text(new History.Event(0, A, History.Kind.CLAIM, "minecraft:stone", B, 0));
        assertTrue(t.contains("claim.other") && t.contains("Alice") && t.contains("Bob") && t.contains("石"));
    }

    @Test
    void claimForYourselfDoesNotRepeatTheName() {
        String t = text(new History.Event(0, A, History.Kind.CLAIM, "minecraft:stone", A, 0));
        assertTrue(t.contains("claim.self"));
        assertEquals(1, t.split("Alice", -1).length - 1);
    }

    @Test
    void countsAndBulkAreShown() {
        assertTrue(text(new History.Event(0, A, History.Kind.COLLECTED, "minecraft:stone", A, 30)).contains("30"));
        assertTrue(text(new History.Event(0, A, History.Kind.BULK, "", null, 12)).contains("12"));
        assertTrue(text(new History.Event(0, A, History.Kind.CREATED, "", null, 5)).contains("history.created"));
        assertTrue(text(new History.Event(0, A, History.Kind.IMPORT, "", null, 4)).contains("4"));
        assertTrue(text(new History.Event(0, A, History.Kind.NOTE, "", null, 0)).contains("history.note"));
    }

    @Test
    void unknownActorIsHandled() {
        assertFalse(HistoryText.describe(new History.Event(0, null, History.Kind.NOTE, "", null, 0), id -> "x",
                id -> "y", STUB).isEmpty());
    }
}
