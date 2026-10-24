package dev.materialshare.common;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class ButtonLayoutTest {
    // priority order: project (most important), filter, undo; each with widths from the fullest label to the shortest
    private static final List<int[]> BUTTONS = List.of(new int[] {120, 80, 50}, new int[] {90, 40}, new int[] {40});

    @Test
    void everythingFitsWithTheFullLabels() {
        assertEquals(List.of(120, 90, 40), ButtonLayout.fit(300, 4, BUTTONS));
    }

    @Test
    void labelsGetShorterBeforeAnyButtonIsDropped() {
        // 80 + 40 + 40 + two gaps of 4 = 168
        assertEquals(List.of(80, 40, 40), ButtonLayout.fit(170, 4, BUTTONS));
    }

    @Test
    void theLeastImportantButtonsAreDroppedFirst() {
        // even the shortest three (50 + 40 + 40 + 8 = 138) do not fit in 100
        assertEquals(List.of(50, 40), ButtonLayout.fit(100, 4, BUTTONS)); // 50 + 40 + 4
        assertEquals(List.of(50), ButtonLayout.fit(60, 4, BUTTONS));
    }

    @Test
    void nothingIsPlacedWhenEvenTheMainButtonDoesNotFit() {
        assertTrue(ButtonLayout.fit(40, 4, BUTTONS).isEmpty());
        assertTrue(ButtonLayout.fit(0, 4, BUTTONS).isEmpty());
        assertTrue(ButtonLayout.fit(-30, 4, BUTTONS).isEmpty());
    }

    @Test
    void theTotalNeverExceedsTheSpace() {
        for (int space = 0; space < 400; space++) {
            List<Integer> widths = ButtonLayout.fit(space, 4, BUTTONS);
            int total = widths.stream().mapToInt(Integer::intValue).sum() + 4 * Math.max(0, widths.size() - 1);
            assertTrue(total <= Math.max(space, 0), "space " + space + " used " + total);
        }
    }
}
