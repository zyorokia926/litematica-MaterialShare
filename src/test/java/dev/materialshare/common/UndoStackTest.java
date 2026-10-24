package dev.materialshare.common;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class UndoStackTest {
    @Test
    void popReturnsNewestStepFirst() {
        UndoStack<String> s = new UndoStack<>(10, 1000);
        s.push(List.of("a"), null, 0);
        s.push(List.of("b"), null, 10);
        assertEquals(List.of("b"), s.pop().orElseThrow());
        assertEquals(List.of("a"), s.pop().orElseThrow());
        assertTrue(s.pop().isEmpty());
    }

    @Test
    void oldestStepsDropWhenFull() {
        UndoStack<Integer> s = new UndoStack<>(2, 1000);
        s.push(List.of(1), null, 0);
        s.push(List.of(2), null, 1);
        s.push(List.of(3), null, 2);
        assertEquals(2, s.size());
        assertEquals(List.of(3), s.pop().orElseThrow());
        assertEquals(List.of(2), s.pop().orElseThrow());
    }

    @Test
    void quickRepeatsOfTheSameEditMergeKeepingTheFirstState() {
        UndoStack<String> s = new UndoStack<>(10, 1000);
        s.push(List.of("before 0"), "stone", 0);
        s.push(List.of("before 1"), "stone", 500);
        s.push(List.of("before 2"), "stone", 1200); // within 1000 ms of the previous edit, so still merged
        assertEquals(1, s.size());
        assertEquals(List.of("before 0"), s.pop().orElseThrow());
    }

    @Test
    void differentKeyOrALongPauseStartsANewStep() {
        UndoStack<String> s = new UndoStack<>(10, 1000);
        s.push(List.of("x"), "stone", 0);
        s.push(List.of("y"), "dirt", 100);
        s.push(List.of("z"), "dirt", 5000);
        assertEquals(3, s.size());
    }

    @Test
    void emptyStepsAreIgnored() {
        UndoStack<String> s = new UndoStack<>(10, 1000);
        s.push(List.of(), null, 0);
        assertTrue(s.isEmpty());
    }

    @Test
    void clearForgetsEverything() {
        UndoStack<String> s = new UndoStack<>(10, 1000);
        s.push(List.of("a"), null, 0);
        s.clear();
        assertTrue(s.isEmpty());
    }
}
