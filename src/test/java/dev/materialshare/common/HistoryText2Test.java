package dev.materialshare.common;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class HistoryText2Test {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final Translator TR = (key, args) -> key + java.util.Arrays.toString(args);

    @Test
    void archiveAndRestoreHaveText() {
        String archived = HistoryText.describe(new History.Event(0, A, History.Kind.ARCHIVE, "", null, 1), id -> "Cara", s -> s, TR);
        String unarchived = HistoryText.describe(new History.Event(0, A, History.Kind.ARCHIVE, "", null, 0), id -> "Cara", s -> s, TR);
        String restored = HistoryText.describe(new History.Event(0, A, History.Kind.RESTORED, "", null, 0), id -> "Cara", s -> s, TR);
        assertTrue(archived.contains("history.archived"));
        assertTrue(unarchived.contains("history.unarchived"));
        assertTrue(restored.contains("history.restored"));
    }

    @Test
    void oldOrdinalsKeepTheirMeaning() {
        assertEquals(History.Kind.IMPORT, History.Kind.of(6));
        assertEquals(History.Kind.ARCHIVE, History.Kind.of(7));
        assertEquals(History.Kind.RESTORED, History.Kind.of(8));
    }
}
