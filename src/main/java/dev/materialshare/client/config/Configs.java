package dev.materialshare.client.config;

import com.google.common.collect.ImmutableList;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.config.options.ConfigInteger;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.hotkeys.KeybindSettings;

/** All settings of the mod, in malilib's config system (same screen style and key binding UI as Litematica). */
public final class Configs {
    private Configs() {
    }

    public static final class Generic {
        public static final ConfigBoolean SHOW_OVERLAY = new ConfigBoolean("materialshareShowOverlay", true,
                "Shows the assignment column to the right of every material list row.", "Show the assignment column");
        public static final ConfigBoolean SHOW_FACES = new ConfigBoolean("materialshareShowFaces", true,
                "Shows the skin face next to each assignee's name.", "Show faces");
        public static final ConfigBoolean SHOW_TOOLTIPS = new ConfigBoolean("materialshareShowTooltips", true,
                "Shows an explanation when the cursor is over a button or an assignee.", "Show tooltips");
        public static final ConfigBoolean HIGHLIGHT_DONE_ROWS = new ConfigBoolean("materialshareHighlightDoneRows", true,
                "Highlights rows whose required amount is collected in light green.", "Highlight finished rows");
        public static final ConfigBoolean CLICK_SOUNDS = new ConfigBoolean("materialshareClickSounds", true,
                "Plays a click sound when you press the assignment buttons.", "Play click sounds");
        public static final ConfigBoolean AUTO_SELECT_PROJECT = new ConfigBoolean("materialshareAutoSelectProject", true,
                "Selects the project named like the schematic when you open its material list.", "Select the same-named project automatically");
        public static final ConfigBoolean NOTIFY_DONE = new ConfigBoolean("materialshareNotifyDone", true,
                "Shows a notice in the corner of the screen when materials or the whole project are complete.", "Notify when done");
        public static final ConfigBoolean NOTIFY_CHAT = new ConfigBoolean("materialshareNotifyChat", true,
                "Also writes the completion notices into the chat.", "Also notify in chat");
        public static final ConfigBoolean NOTIFY_ONLY_MINE = new ConfigBoolean("materialshareNotifyOnlyMine", false,
                "Only tells about materials you are assigned to and projects you take part in.", "Only notify about my own");
        public static final ConfigBoolean NOTIFY_ASSIGNED = new ConfigBoolean("materialshareNotifyAssigned", true,
                "Tells you when somebody else makes you a claimant of an item, or takes you off one.", "Notify when somebody assigns me or removes me");
        public static final ConfigBoolean SHOW_DELIVERY = new ConfigBoolean("materialshareShowDelivery", true,
                "Shows the \"handed in\" counts and the delivery button (for teams that keep a shared storage).", "Use the handed-in step");
        public static final ConfigBoolean WHEEL_NEEDS_CTRL = new ConfigBoolean("materialshareWheelNeedsCtrl", false,
                "The mouse wheel changes a count only while Ctrl is held (so scrolling the list never changes one by accident).",
                "Wheel needs Ctrl");
        public static final ConfigBoolean SHOW_INTRO = new ConfigBoolean("materialshareShowIntro", true,
                "Shows a short how-to once when you first open a shared material list (turn it on again to see it once more).",
                "Show the first-time tips");
        public static final ConfigBoolean SHOW_HUD = new ConfigBoolean("materialshareShowHud", true,
                "Shows the items you still have to collect in a box on the game screen (drag it into place with the button above the list).", "Show the HUD");
        public static final ConfigInteger HUD_LINES = new ConfigInteger("materialshareHudLines", 6, 1, 20,
                "How many items the on-screen list shows at most.");
        public static final ConfigBoolean SHOW_STORAGE = new ConfigBoolean("materialshareShowStorage", true,
                "Draws the storage boxes of the selected schematic in the world, each in its own colour.",
                "Show the storage boxes in the world");
        public static final ConfigBoolean SHOW_STACKS = new ConfigBoolean("materialshareShowStacks", true,
                "Also shows counts as stacks (for example 10 stacks + 60) in the tooltips.", "Show counts as stacks too");
        public static final ConfigBoolean AUTO_IMPORT = new ConfigBoolean("materialshareAutoImport", true,
                "Keeps your collected counts in step with what you carry, even while no screen is open (never lowers them).",
                "Update my collected counts automatically");
        public static final ConfigBoolean COUNT_ENDER = new ConfigBoolean("materialshareCountEnder", true,
                "Also counts what lies in your ender chest (shulker boxes inside included) as what you have: in the tooltips, the automatic counts and \"take what I carry\".",
                "Count my ender chest too");
        public static final ConfigInteger WHEEL_STEP = new ConfigInteger("materialshareWheelStep", 1, 1, 64,
                "Amount changed per wheel step over an assignee.");
        public static final ConfigInteger WHEEL_STEP_SHIFT = new ConfigInteger("materialshareWheelStepShift", 64, 1, 3456,
                "Amount changed per wheel step with Shift held (64 = one stack).");

        public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(SHOW_OVERLAY, SHOW_FACES,
                SHOW_TOOLTIPS, HIGHLIGHT_DONE_ROWS, CLICK_SOUNDS, AUTO_SELECT_PROJECT, NOTIFY_DONE, NOTIFY_CHAT, NOTIFY_ONLY_MINE, NOTIFY_ASSIGNED, SHOW_DELIVERY, WHEEL_NEEDS_CTRL, SHOW_INTRO, SHOW_HUD, HUD_LINES, SHOW_STORAGE, SHOW_STACKS, AUTO_IMPORT, COUNT_ENDER, WHEEL_STEP,
                WHEEL_STEP_SHIFT);

        private Generic() {
        }
    }

    /** Where the on-screen box sits: a share (0 to 1000) of the free room, so 1000 / 1000 is the bottom right corner. */
    public static final class Hud {
        public static final ConfigInteger X = new ConfigInteger("materialshareHudX", 1000, 0, 1000, "Horizontal place of the on-screen list.");
        public static final ConfigInteger Y = new ConfigInteger("materialshareHudY", 1000, 0, 1000, "Vertical place of the on-screen list.");
        public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(X, Y);

        private Hud() {
        }
    }

    public static final class Hotkeys {
        public static final ConfigHotkey OPEN_CONFIG_GUI = new ConfigHotkey("materialshareOpenConfigGui", "",
                KeybindSettings.RELEASE_EXCLUSIVE, "Opens this settings screen.", "Open the settings screen");
        public static final ConfigHotkey OPEN_PROJECT_SCREEN = new ConfigHotkey("materialshareOpenProjectScreen", "",
                KeybindSettings.GUI, "Opens the project screen while the material list is open.", "Open the project screen");
        public static final ConfigHotkey TOGGLE_OVERLAY = new ConfigHotkey("materialshareToggleOverlay", "",
                KeybindSettings.GUI, "Toggles the \"Show the assignment column\" setting.", "Toggle the assignment column");

        public static final ConfigHotkey UNDO = new ConfigHotkey("materialshareUndo", "",
                KeybindSettings.GUI, "Undoes your last action while the material list is open.", "Undo last action");
        public static final ConfigHotkey CYCLE_FILTER = new ConfigHotkey("materialshareCycleFilter", "",
                KeybindSettings.GUI, "Cycles the shown rows (All / Mine / Unclaimed / Incomplete) while the material list is open.",
                "Cycle the row filter");

        public static final ConfigHotkey OPEN_BREAKDOWN = new ConfigHotkey("materialshareOpenBreakdown", "",
                KeybindSettings.RELEASE_EXCLUSIVE, "Opens the raw materials of the selected project (worked out from the recipes).",
                "Open the raw materials screen");
        public static final ConfigHotkey TOGGLE_HUD = new ConfigHotkey("materialshareToggleHud", "",
                KeybindSettings.RELEASE_EXCLUSIVE, "Shows or hides the list of items you still have to collect on the game screen.",
                "Toggle the on-screen list");
        public static final ConfigHotkey CLAIM_HELD = new ConfigHotkey("materialshareClaimHeld", "",
                KeybindSettings.RELEASE_EXCLUSIVE, "Makes the item in your main hand yours in the selected project.",
                "Take the item I am holding");

        public static final ConfigHotkey MARK_STORAGE_A = new ConfigHotkey("materialshareMarkStorageA", "",
                KeybindSettings.RELEASE_EXCLUSIVE, "Marks the first corner of a storage box at the block you look at.",
                "Mark storage corner A");
        public static final ConfigHotkey MARK_STORAGE_B = new ConfigHotkey("materialshareMarkStorageB", "",
                KeybindSettings.RELEASE_EXCLUSIVE, "Marks the opposite corner of a storage box at the block you look at.",
                "Mark storage corner B");

        public static final ConfigHotkey CLEAR_STORAGE_MARKS = new ConfigHotkey("materialshareClearStorageMarks", "",
                KeybindSettings.RELEASE_EXCLUSIVE, "Takes away the two storage corner marks (for a corner marked by mistake).",
                "Clear the storage corner marks");
        public static final ConfigHotkey OPEN_HELP = new ConfigHotkey("materialshareOpenHelp", "",
                KeybindSettings.RELEASE_EXCLUSIVE, "Opens the how-to screen at any time.", "Open the how-to");

        public static final ImmutableList<IHotkey> HOTKEY_LIST = ImmutableList.of(OPEN_CONFIG_GUI,
                OPEN_PROJECT_SCREEN, OPEN_BREAKDOWN, TOGGLE_OVERLAY, TOGGLE_HUD, CLAIM_HELD, UNDO, CYCLE_FILTER,
                MARK_STORAGE_A, MARK_STORAGE_B, CLEAR_STORAGE_MARKS, OPEN_HELP);

        private Hotkeys() {
        }
    }
}
