package dev.materialshare.client;

/** Hotkey entry points. */
public final class ProjectsEntry {
    private ProjectsEntry() {
    }

    /** The hotkey: the raw-materials screen of the selected project, from anywhere. */
    public static void openBreakdown() {
        var project = ClientProjectCache.selected();
        if (project.isEmpty()) {
            fi.dy.masa.malilib.util.InfoUtils.showGuiMessage(fi.dy.masa.malilib.gui.Message.MessageType.WARNING, Lang.t("import.pick"));
            return;
        }
        fi.dy.masa.malilib.gui.GuiBase.openGui(new BreakdownScreen(null, project.get().name(), 0, ""));
    }
}
