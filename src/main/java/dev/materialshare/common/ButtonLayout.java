package dev.materialshare.common;

import java.util.ArrayList;
import java.util.List;

/** Fits a row of buttons into the space that is free, so that none of them ever overlaps another thing. */
public final class ButtonLayout {
    private ButtonLayout() {
    }

    /**
     * {@code options} lists the buttons by importance; each has its possible widths from the fullest label to the
     * shortest. Shorter labels are tried first; if that is not enough, the least important buttons are left out.
     * Returns the chosen width of each button that is shown (a prefix of the list), empty if not even the first fits.
     */
    public static List<Integer> fit(int available, int gap, List<int[]> options) {
        for (int shown = options.size(); shown >= 1; shown--) {
            int levels = 0;
            for (int i = 0; i < shown; i++) {
                levels = Math.max(levels, options.get(i).length);
            }
            for (int level = 0; level < levels; level++) {
                List<Integer> widths = new ArrayList<>();
                int total = gap * (shown - 1);
                for (int i = 0; i < shown; i++) {
                    int[] choices = options.get(i);
                    int w = choices[Math.min(level, choices.length - 1)];
                    widths.add(w);
                    total += w;
                }
                if (total <= available) {
                    return widths;
                }
            }
        }
        return List.of();
    }
}
