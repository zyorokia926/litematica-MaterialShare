package dev.materialshare.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** The latest edits of a project: who did what, so a surprising change can be traced. */
public final class History {
    public static final int MAX = 100;

    public enum Kind {
        CLAIM, UNCLAIM, COLLECTED, BULK, CREATED, NOTE, IMPORT, ARCHIVE, RESTORED,
        /** An old feature (deadlines) that was taken out; the entries are kept in saved data only so that numbering stays. */
        DEADLINE, ITEM_INFO, TARGET, TRANSFER, DELIVERED, ITEM_REMOVED, RENAMED, CLONED, UPDATED, SURPLUS, SURPLUS_TAKEN, RESET_CLAIMS, ITEM_RESTORED;

        public static Kind of(int ordinal) {
            Kind[] all = values();
            return ordinal >= 0 && ordinal < all.length ? all[ordinal] : COLLECTED;
        }
    }

    /** {@code target} is the player the edit is about; {@code value} is the count (or the number of items for BULK). */
    public record Event(long time, UUID actor, Kind kind, String itemId, UUID target, int value) {
    }

    private final List<Event> events = new ArrayList<>();

    /** Repeats of one number being changed (a turning mouse wheel) within this time replace each other. */
    private static final long MERGE_MS = 10_000;

    public void add(Event event) {
        if (event.kind() == Kind.COLLECTED && !events.isEmpty()) {
            Event last = events.get(events.size() - 1);
            if (last.kind() == Kind.COLLECTED && java.util.Objects.equals(last.actor(), event.actor())
                    && java.util.Objects.equals(last.target(), event.target())
                    && java.util.Objects.equals(last.itemId(), event.itemId())
                    && event.time() - last.time() <= MERGE_MS) {
                events.set(events.size() - 1, event);
                return;
            }
        }
        events.add(event);
        while (events.size() > MAX) {
            events.remove(0);
        }
    }

    /** Oldest first. */
    public List<Event> events() {
        return events;
    }

    public List<Event> latestFirst() {
        List<Event> copy = new ArrayList<>(events);
        Collections.reverse(copy);
        return copy;
    }
}
