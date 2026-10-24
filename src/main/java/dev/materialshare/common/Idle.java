package dev.materialshare.common;

/** Who has not touched a project for a while. */
public final class Idle {
    /** From this many days without any action a person is shown as "not moving". */
    public static final int DAYS = 3;
    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private Idle() {
    }

    /** Whole days since {@code last} (0 when never seen or in the future). */
    public static int days(long last, long now) {
        return last <= 0 || now <= last ? 0 : (int) Math.min(Integer.MAX_VALUE, (now - last) / DAY_MS);
    }

    public static boolean idle(long last, long now) {
        return last > 0 && days(last, now) >= DAYS;
    }
}
