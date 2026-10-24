package dev.materialshare.common;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ListSelectionTest {
    @Test
    void firstOpen_selectsTheListsProjectIfItExists() {
        ListSelection s = new ListSelection();
        ListSelection.Decision d = s.onListOpened("Castle", true, false, true);
        assertTrue(d.change());
        assertEquals("Castle", d.project());
    }

    @Test
    void firstOpen_withoutProjectClearsTheSelection() {
        ListSelection s = new ListSelection();
        ListSelection.Decision d = s.onListOpened("Castle", false, true, true);
        assertTrue(d.change());
        assertNull(d.project());
    }

    @Test
    void reopeningTheSameList_keepsAHandPickedProject() {
        ListSelection s = new ListSelection();
        s.onListOpened("Castle", true, false, true);
        assertFalse(s.onListOpened("Castle", true, true, true).change());
    }

    @Test
    void projectThatAppearsLater_isSelectedWhenNothingIsSelected() {
        ListSelection s = new ListSelection();
        s.onListOpened("Castle", false, false, true);
        ListSelection.Decision d = s.onListOpened("Castle", true, false, true);
        assertTrue(d.change());
        assertEquals("Castle", d.project());
    }

    @Test
    void deselect_isNotUndoneByTheNextOpen() {
        ListSelection s = new ListSelection();
        s.onListOpened("Castle", true, false, true);
        s.userDeselected("Castle");
        assertFalse(s.onListOpened("Castle", true, false, true).change());
        assertFalse(s.onListOpened("Castle", true, false, true).change());
    }

    @Test
    void choosingAProject_endsTheDeselect() {
        ListSelection s = new ListSelection();
        s.userDeselected("Castle");
        s.userChose();
        s.onListOpened("Other", true, false, true);
        assertTrue(s.onListOpened("Castle", true, false, true).change());
    }

    @Test
    void anotherList_isHandledNormallyAfterADeselect() {
        ListSelection s = new ListSelection();
        s.onListOpened("Castle", true, false, true);
        s.userDeselected("Castle");
        ListSelection.Decision d = s.onListOpened("Farm", true, false, true);
        assertTrue(d.change());
        assertEquals("Farm", d.project());
    }

    @Test
    void disabled_neverChangesAnything() {
        ListSelection s = new ListSelection();
        assertFalse(s.onListOpened("Castle", true, false, false).change());
    }
}
