package dev.materialshare.client;

import dev.materialshare.common.Project;
import dev.materialshare.common.ProjectRegistry;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiConfirmAction;
import fi.dy.masa.malilib.gui.GuiTextInput;
import fi.dy.masa.malilib.gui.Message;
import fi.dy.masa.malilib.interfaces.IConfirmationListener;
import fi.dy.masa.malilib.interfaces.IStringConsumerFeedback;
import fi.dy.masa.malilib.util.InfoUtils;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.gui.screen.Screen;

/** The things done to a whole project that need a question first (note, copy, rename, update from the design). */
final class ProjectActions {
    private ProjectActions() {
    }

    static void editNote(Screen screen, Project project) {
        GuiBase.openGui(new GuiTextInput(Project.MAX_NOTE, Lang.t("note.title", UiText.escapeFormat(project.name())),
                project.note(), screen, (IStringConsumerFeedback) text -> {
                    ClientNet.sendNote(project.name(), text);
                    return true;
                }));
    }

    /** A project name of this list ("<schematic> / <label>") from what the player typed; empty when it is not valid. */
    private static String nameFor(MaterialListBase list, String label) {
        return ProjectRegistry.partName(ProjectNames.forList(list), label.strip());
    }

    /** A second project for another schematic of the same name: "<schematic> / <label>", so it stays tied to this list. */
    static void shareAs(MaterialListBase list, Screen screen) {
        GuiBase.openGui(new GuiTextInput(ProjectRegistry.MAX_NAME_LENGTH, Lang.t("shareas.title"), "2", screen,
                (IStringConsumerFeedback) text -> {
                    String name = nameFor(list, text);
                    if (text.isBlank() || !ProjectRegistry.isValidName(name)) {
                        InfoUtils.showGuiMessage(Message.MessageType.ERROR, Lang.t("shareas.invalid"));
                        return false;
                    }
                    if (ClientProjectCache.exists(name)) {
                        InfoUtils.showGuiMessage(Message.MessageType.ERROR, Lang.t("shareas.exists"));
                        return false;
                    }
                    ProjectNames.startSharingAs(list, name, screen);
                    return true;
                }));
    }

    /** A copy of the project: the same items, optionally with the same people (nothing collected yet). */
    static void clone(MaterialListBase list, Project project, Screen screen, boolean keepClaims) {
        GuiBase.openGui(new GuiTextInput(ProjectRegistry.MAX_NAME_LENGTH, Lang.t("clone.title", UiText.escapeFormat(project.name())),
                "2", screen, (IStringConsumerFeedback) text -> {
                    String name = nameFor(list, text);
                    if (text.isBlank() || !ProjectRegistry.isValidName(name)) {
                        InfoUtils.showGuiMessage(Message.MessageType.ERROR, Lang.t("shareas.invalid"));
                        return false;
                    }
                    if (ClientProjectCache.exists(name)) {
                        InfoUtils.showGuiMessage(Message.MessageType.ERROR, Lang.t("shareas.exists"));
                        return false;
                    }
                    ClientProjectCache.selectWhenArrives(name);
                    ClientNet.sendClone(project.name(), name, keepClaims);
                    return true;
                }));
    }

    /** Only projects that are not the schematic's own can be renamed (they would no longer belong to the list). */
    static boolean canRename(MaterialListBase list, Project project) {
        return !project.name().equals(ProjectNames.forList(list));
    }

    static void rename(MaterialListBase list, Project project, Screen screen) {
        String base = ProjectNames.forList(list);
        String current = project.name().startsWith(base + " / ") ? project.name().substring(base.length() + 3) : "";
        GuiBase.openGui(new GuiTextInput(ProjectRegistry.MAX_NAME_LENGTH, Lang.t("rename.title", UiText.escapeFormat(project.name())),
                current, screen, (IStringConsumerFeedback) text -> {
                    String name = nameFor(list, text);
                    if (text.isBlank() || !ProjectRegistry.isValidName(name)) {
                        InfoUtils.showGuiMessage(Message.MessageType.ERROR, Lang.t("shareas.invalid"));
                        return false;
                    }
                    if (name.equals(project.name())) {
                        return true;
                    }
                    if (ClientProjectCache.exists(name)) {
                        InfoUtils.showGuiMessage(Message.MessageType.ERROR, Lang.t("shareas.exists"));
                        return false;
                    }
                    ClientProjectCache.selectWhenArrives(name);
                    ClientNet.sendRename(project.name(), name);
                    return true;
                }));
    }

    /** What the schematic needs for this project: the part's items for a part project, else the whole list. */
    static Map<String, Integer> requiredFor(MaterialListBase list, String project) {
        Optional<String> region = ProjectRegistry.partOf(ProjectNames.forList(list), project, Parts.regions(list));
        return region.map(r -> Parts.required(list, r)).orElseGet(() -> ProjectNames.required(list));
    }

    /** Brings the project's required amounts in line with the schematic as it is now (nothing is removed). */
    static void updateFromSchematic(MaterialListBase list, Project project, Screen screen) {
        Map<String, Integer> wanted = requiredFor(list, project.name());
        int added = 0;
        int changed = 0;
        for (Map.Entry<String, Integer> e : wanted.entrySet()) {
            dev.materialshare.common.Entry have = project.items().get(e.getKey());
            if (have == null) {
                added++;
            } else if (have.required() != e.getValue()) {
                changed++;
            }
        }
        int obsolete = (int) project.items().keySet().stream().filter(id -> !wanted.containsKey(id)).count();
        if (wanted.isEmpty()) {
            InfoUtils.showGuiMessage(Message.MessageType.WARNING, Lang.t("update.empty"));
            return;
        }
        if (added + changed == 0) {
            InfoUtils.showGuiMessage(Message.MessageType.INFO, Lang.t("update.same", obsolete));
            return;
        }
        int a = added;
        int c = changed;
        GuiBase.openGui(new GuiConfirmAction(320, Lang.t("update.title"), new IConfirmationListener() {
            @Override
            public boolean onActionConfirmed() {
                ClientNet.sendUpdateRequired(project.name(), wanted);
                return false;
            }

            @Override
            public boolean onActionCancelled() {
                return false;
            }
        }, screen, Lang.t("update.confirm", a, c, obsolete)));
    }
}
