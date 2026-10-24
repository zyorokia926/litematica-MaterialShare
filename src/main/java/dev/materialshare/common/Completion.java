package dev.materialshare.common;

/** Detects the moment an item gets finished, so the players can be told. */
public final class Completion {
    private Completion() {
    }

    private static boolean done(Entry e) {
        return e != null && e.required() > 0 && e.collectedTotal() >= e.required();
    }

    public static boolean itemDone(Entry before, Entry after) {
        return done(after) && !done(before);
    }
}
