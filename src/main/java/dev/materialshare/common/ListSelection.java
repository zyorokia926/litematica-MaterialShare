package dev.materialshare.common;

/**
 * Decides which project is selected when a material list is opened: the list's own project (named after its
 * schematic), unless the player deliberately deselected it or picked another one by hand.
 */
public final class ListSelection {
    /** {@code change == false}: leave the current selection alone. Otherwise select {@code project} (null = none). */
    public record Decision(boolean change, String project) {
        static final Decision KEEP = new Decision(false, null);
    }

    private String lastContext;
    private String deselectedFor;

    public Decision onListOpened(String listProject, boolean exists, boolean hasSelection, boolean enabled) {
        if (!enabled || listProject.equals(deselectedFor)) {
            return Decision.KEEP;
        }
        if (!listProject.equals(lastContext)) {
            lastContext = listProject;
            return new Decision(true, exists ? listProject : null);
        }
        if (!hasSelection && exists) {
            return new Decision(true, listProject);
        }
        return Decision.KEEP;
    }

    /** The player pressed "deselect" for this list: do not select its project again behind their back. */
    public void userDeselected(String listProject) {
        lastContext = listProject;
        deselectedFor = listProject;
    }

    /** The player picked a project (or started sharing): automatic selection is welcome again. */
    public void userChose() {
        deselectedFor = null;
    }
}
