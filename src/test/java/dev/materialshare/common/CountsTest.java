package dev.materialshare.common;

import static org.junit.jupiter.api.Assertions.*;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class CountsTest {
    @Test
    void parsesPlainNumbersAndFormatting() {
        assertEquals(OptionalInt.of(12), Counts.parse("12"));
        assertEquals(OptionalInt.of(7), Counts.parse("  7 "));
        assertEquals(OptionalInt.of(1000), Counts.parse("1,000"));
        assertEquals(OptionalInt.of(1000), Counts.parse("1 000"));
        assertEquals(OptionalInt.of(12), Counts.parse("１２")); // full-width digits from a Japanese IME
    }

    @Test
    void clampsToTheIntRange() {
        assertEquals(OptionalInt.of(0), Counts.parse("-5"));
        assertEquals(OptionalInt.of(Integer.MAX_VALUE), Counts.parse("99999999999999999999"));
    }

    @Test
    void rejectsNonNumbers() {
        assertTrue(Counts.parse("").isEmpty());
        assertTrue(Counts.parse("abc").isEmpty());
        assertTrue(Counts.parse("12a").isEmpty());
        assertTrue(Counts.parse(null).isEmpty());
    }
}
