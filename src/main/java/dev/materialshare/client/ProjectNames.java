package dev.materialshare.client;

import dev.materialshare.client.config.Configs;
import dev.materialshare.common.ListSelection;
import dev.materialshare.common.Packets;
import dev.materialshare.common.ProjectRegistry;
import fi.dy.masa.litematica.gui.GuiMaterialList;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.Message;
import fi.dy.masa.malilib.util.InfoUtils;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

/**
 * A material list is shared as the project that has the same name as its schematic: no name has to be typed in.
 * Opening a list selects its project; if nobody shares that schematic yet, one click starts sharing it.
 */
public final class ProjectNames {
        private static final int WAIT_TICKS = 60;

    private record Pending(Screen screen, String project, Runnable action, int[] ticksLeft) {
    }

    private static final ListSelection SELECTION = new ListSelection();
    /** Projects a list was shared under a name of the player's own choosing (kept for this session). */
    private static final Map<String, java.util.Set<String>> CUSTOM = new java.util.HashMap<>();
    private static Pending pending;

    private ProjectNames() {
    }

    /**
     * The name the list is known by. For a placement it is the schematic's FILE name: Syncmatica shares the file
     * under that name, and a player who loads the shared copy gets it back as "<file name>.litematic". The
     * placement's own display name can differ (and differs right after sharing), so it is not used.
     */
    private static String rawName(MaterialListBase list) {
        if (list instanceof PlacementHolder holder) {
            try {
                var placement = holder.materialshare$placement();
                java.io.File file = placement == null ? null : placement.getSchematicFile();
                if (file != null && !file.getName().isBlank()) {
                    return file.getName();
                }
            } catch (RuntimeException e) {
                // a placement without a usable file: fall back to its name
            }
        }
        return list.getName();
    }

    /** The project name for a material list: its schematic's name, made valid. */
    public static String forList(MaterialListBase list) {
        String name = ProjectRegistry.suggestName(rawName(list));
        return name.isEmpty() ? Lang.t("names.unnamed") : name;
    }

    /** Remembers that this list's project was given a name of the player's choosing. */
    static void rememberCustom(MaterialListBase list, String project) {
        CUSTOM.computeIfAbsent(forList(list), k -> new java.util.HashSet<>()).add(project);
    }

    /** Whether a project belongs to this list: the schematic's own project, one of its parts, or one named for it. */
    public static boolean belongs(MaterialListBase list, String project) {
        String base = forList(list);
        return ProjectRegistry.belongsToList(base, project)
                || CUSTOM.getOrDefault(base, java.util.Set.of()).contains(project);
    }

    /** The projects of this list that exist, the whole schematic's first. Nothing of other lists is ever included. */
    public static java.util.List<String> projectsFor(MaterialListBase list) {
        java.util.List<String> names = new java.util.ArrayList<>(ProjectRegistry.projectsOfList(forList(list),
                ClientProjectCache.projects().stream().map(p -> p.name()).toList()));
        for (String custom : CUSTOM.getOrDefault(forList(list), java.util.Set.of())) {
            if (ClientProjectCache.exists(custom) && !names.contains(custom)) {
                names.add(custom);
            }
        }
        return names;
    }

    /** True for a list that has no name of its own (Litematica calls it "Unnamed", or it is blank). */
    public static boolean isUnnamed(MaterialListBase list) {
        String name = ProjectRegistry.suggestName(rawName(list));
        return name.isEmpty() || name.equalsIgnoreCase("Unnamed") || name.equals(Lang.t("names.unnamed"));
    }

    /**
     * For an unnamed list, the dialog that asks what the project should be called. A name that is taken joins that
     * project (so everyone with the same unnamed schematic can type the same name and end up together).
     */
    public static fi.dy.masa.malilib.gui.GuiTextInput nameDialog(MaterialListBase list, Screen screen) {
        return new fi.dy.masa.malilib.gui.GuiTextInput(ProjectRegistry.MAX_NAME_LENGTH, Lang.t("name.title"), "",
                screen, (fi.dy.masa.malilib.interfaces.IStringConsumerFeedback) text -> {
                    String name = text.strip();
                    if (!ProjectRegistry.isValidName(name)) {
                        InfoUtils.showGuiMessage(Message.MessageType.ERROR, Lang.t("shareas.invalid"));
                        return false;
                    }
                    rememberCustom(list, name);
                    if (ClientProjectCache.exists(name)) {
                        choose(name);
                        InfoUtils.showGuiMessage(Message.MessageType.INFO, Lang.t("name.joined", name));
                        GuiBase.openGui(new GuiMaterialList(list));
                    } else {
                        startSharingAs(list, name, screen);
                    }
                    return true;
                });
    }

    /** What the list needs, per item (the list shows count * multiplier). */
    public static Map<String, Integer> required(MaterialListBase list) {
        Map<String, Integer> required = new LinkedHashMap<>();
        int multiplier = Math.max(1, list.getMultiplier());
        for (MaterialListEntry entry : list.getMaterialsAll()) {
            long needed = (long) entry.getCountTotal() * multiplier;
            required.merge(RowOverlay.itemId(entry), (int) Math.min(needed, Integer.MAX_VALUE),
                    (a, b) -> (int) Math.min((long) a + b, Integer.MAX_VALUE));
        }
        return required;
    }

    /**
     * Selects the list's own project when the list is opened (or when it becomes available later). A project the
     * player picked by hand on the project screen is kept while the same list stays open.
     */
    public static void autoSelect(MaterialListBase list) {
        String name = forList(list);
        // a list never keeps showing another schematic's project (left selected from a list opened earlier)
        ClientProjectCache.selected().filter(p -> !belongs(list, p.name()))
                .ifPresent(p -> ClientProjectCache.select(null));
        ListSelection.Decision decision = SELECTION.onListOpened(name, ClientProjectCache.exists(name),
                ClientProjectCache.selected().isPresent(), Configs.Generic.AUTO_SELECT_PROJECT.getBooleanValue());
        if (decision.change()) {
            ClientProjectCache.select(decision.project());
        }
    }

    /** "選択解除" on the project screen: the list's project is not selected again when the list is reopened. */
    public static void deselect(MaterialListBase list) {
        SELECTION.userDeselected(forList(list));
        ClientProjectCache.select(null);
    }

    /** The player picked a project by hand. */
    public static void choose(String project) {
        SELECTION.userChose();
        ClientProjectCache.select(project);
    }

    /** Creates the project for this list on the server, selects it and refreshes the list screen when it is there. */
    public static void startSharing(MaterialListBase list, Screen screen) {
        Map<String, Integer> required = required(list);
        if (required.isEmpty()) {
            InfoUtils.showGuiMessage(Message.MessageType.ERROR,
                    Lang.t("share.empty"));
            return;
        }
        startSharingAs(list, forList(list), screen);
    }

    private static final java.util.Set<String> AUTO_TRIED = new java.util.HashSet<>();

    /**
     * A schematic shared with Syncmatica simply is the thing that gets managed: its list is set up without any button.
     * Only tried once per name and session; unnamed schematics and schematics with parts still ask (a name, a part).
     */
    public static boolean autoStart(MaterialListBase list, Screen screen) {
        String name = forList(list);
        if (AUTO_TRIED.contains(name) || !SyncmaticaCheck.isShared(list) || required(list).isEmpty()) {
            return false;
        }
        AUTO_TRIED.add(name);
        startSharingAs(list, name, screen);
        return true;
    }

    /** Same, under another name (for a second schematic that happens to have the same name). */
    public static void startSharingAs(MaterialListBase list, String name, Screen screen) {
        startSharingAs(list, name, required(list), screen);
    }

    /** Same, with the items the project is made of given (a part of a schematic has only its own items). */
    public static void startSharingAs(MaterialListBase list, String name, Map<String, Integer> required,
            Screen screen) {
        if (!SyncmaticaCheck.isShared(list)) {
            InfoUtils.showGuiMessage(Message.MessageType.ERROR, Lang.t("share.needsync"));
            return;
        }
        if (required.isEmpty()) {
            InfoUtils.showGuiMessage(Message.MessageType.ERROR,
                    Lang.t("share.empty"));
            return;
        }
        ClientNet.sendCreate(name, required);
        pending = new Pending(screen, name, () -> GuiBase.openGui(new GuiMaterialList(list)), new int[] {WAIT_TICKS});
    }

    /** The server refused to create the project: say why, and stop waiting. */
    public static void onCreateFailed(String name, int reason) {
        if (pending != null && pending.project.equals(name)) {
            pending = null;
        }
        InfoUtils.showGuiMessage(Message.MessageType.ERROR, reason == Packets.FAIL_TOO_MANY
                ? Lang.t("create.toomany", ProjectRegistry.MAX_PROJECTS)
                : Lang.t("create.badname", name));
    }

    /** Selects the new project and re-opens the list screen as soon as the server has answered. */
    public static void tick(MinecraftClient client) {
        if (pending == null) {
            return;
        }
        boolean sameScreen = client.currentScreen == pending.screen
                || (pending.screen instanceof ProjectScreen && client.currentScreen instanceof ProjectScreen);
        if (!sameScreen) { // (a project screen re-opens itself when the list of projects changes)
            pending = null;
        } else if (ClientProjectCache.exists(pending.project)) {
            Runnable action = pending.action;
            choose(pending.project);
            pending = null;
            action.run();
        } else if (--pending.ticksLeft[0] <= 0) {
            pending = null;
            InfoUtils.showGuiMessage(Message.MessageType.ERROR, Lang.t("share.timeout"));
        }
    }
}
