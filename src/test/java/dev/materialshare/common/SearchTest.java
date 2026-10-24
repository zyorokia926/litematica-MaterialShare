package dev.materialshare.common;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class SearchTest {
    @Test
    void emptyQueryShowsEverything() {
        assertTrue(Search.matches("", "Castle"));
        assertTrue(Search.matches("   ", "Castle"));
    }

    @Test
    void oneSharedCharacterIsEnough() {
        assertTrue(Search.matches("zzc", "Castle"));
        assertTrue(Search.matches("城", "大きな城 / 塔"));
    }

    @Test
    void noSharedCharacterHides() {
        assertFalse(Search.matches("xyz", "Castle"));
    }

    @Test
    void caseAndWidthDoNotMatter() {
        assertTrue(Search.matches("CASTLE", "castle"));
        assertTrue(Search.matches("ＣＡＳ", "castle"));
    }

    @Test
    void spacesInTheQueryAreNotCharactersToMatch() {
        assertFalse(Search.matches(" ", "a b") && !Search.matches("", "a b"));
        assertFalse(Search.matches("q ", "a b"));
    }

    @Test
    void moreMatchingCharactersComeFirst() {
        List<String> sorted = Search.filter("castle", List.of("farm", "cat", "castle", "tower", "zzz"), s -> s);
        assertEquals(List.of("castle", "cat", "tower", "farm"), sorted); // farm shares only the "a"
    }

    @Test
    void equalScoresKeepTheirOrder() {
        List<String> sorted = Search.filter("a", List.of("b a", "a b", "xa"), s -> s);
        assertEquals(List.of("b a", "a b", "xa"), sorted);
    }
}
