package dev.materialshare.common;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * Steps that can be taken back, newest first. A step is the list of operations that undo it. Quick repeats of the same
 * kind of edit (a mouse wheel turning on one number) merge into one step that goes back to the state before the first.
 */
public final class UndoStack<T> {
    private record Step<T>(List<T> undo, String key, long time) {
    }

    private final Deque<Step<T>> steps = new ArrayDeque<>();
    private final int maxSteps;
    private final long mergeMs;

    public UndoStack(int maxSteps, long mergeMs) {
        this.maxSteps = maxSteps;
        this.mergeMs = mergeMs;
    }

    public void push(List<T> undo, String mergeKey, long nowMs) {
        if (undo.isEmpty()) {
            return;
        }
        Step<T> top = steps.peekFirst();
        if (mergeKey != null && top != null && mergeKey.equals(top.key) && nowMs - top.time <= mergeMs) {
            steps.pollFirst();
            steps.addFirst(new Step<>(top.undo, mergeKey, nowMs));
            return;
        }
        steps.addFirst(new Step<>(List.copyOf(undo), mergeKey, nowMs));
        while (steps.size() > maxSteps) {
            steps.pollLast();
        }
    }

    public Optional<List<T>> pop() {
        Step<T> top = steps.pollFirst();
        return top == null ? Optional.empty() : Optional.of(top.undo);
    }

    public boolean isEmpty() {
        return steps.isEmpty();
    }

    public int size() {
        return steps.size();
    }

    public void clear() {
        steps.clear();
    }
}
