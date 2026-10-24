package dev.materialshare.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.materialshare.common.Entry;
import dev.materialshare.common.Project;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;

/**
 * Chat commands, for a quick look without opening a screen:
 * {@code /materialshare progress | mine | projects | select <name>}. Everything is read from what this client already
 * knows.
 */
public final class ClientCommands {
    private static final int MINE_LINES = 12;

    private ClientCommands() {
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommandManager.literal("materialshare")
                .executes(c -> {
                    help();
                    return 1;
                })
                .then(ClientCommandManager.literal("help").executes(c -> {
                    help();
                    return 1;
                }))
                .then(ClientCommandManager.literal("progress").executes(c -> {
                    progress();
                    return 1;
                }))
                .then(ClientCommandManager.literal("mine").executes(c -> {
                    mine();
                    return 1;
                }))
                .then(ClientCommandManager.literal("storage").executes(c -> {
                    openStorage();
                    return 1;
                }).then(ClientCommandManager.literal("clear").executes(c -> {
                    StorageMarks.clearWithMessage();
                    return 1;
                })))
                .then(ClientCommandManager.literal("surplus").executes(c -> {
                    surplus();
                    return 1;
                }))
                .then(ClientCommandManager.literal("projects").executes(c -> {
                    projects();
                    return 1;
                }))
                .then(ClientCommandManager.literal("select")
                        .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                                .suggests((ctx, builder) -> CommandSource.suggestMatching(
                                        ClientProjectCache.projects().stream().map(Project::name).toList(), builder))
                                .executes(c -> {
                                    select(StringArgumentType.getString(c, "name"));
                                    return 1;
                                }))));
    }

    private static void say(String text) {
        var player = MinecraftClient.getInstance().player;
        if (player != null) {
            player.sendMessage(Text.literal(text), false);
        }
    }

    private static void help() {
        say("§a[Litematica MaterialShare]§r " + Lang.t("cmd.help.title"));
        String[][] commands = {{"help", "cmd.help.help"}, {"progress", "cmd.help.progress"}, {"mine", "cmd.help.mine"},
            {"surplus", "cmd.help.surplus"}, {"storage", "cmd.help.storage"}, {"storage clear", "cmd.help.storageclear"}, {"projects", "cmd.help.projects"}, {"select <" + Lang.t("cmd.help.name") + ">", "cmd.help.select"}};
        for (String[] command : commands) {
            say("  §e/materialshare " + command[0] + "§r");
            say("    §7" + Lang.t(command[1]));
        }
        say("§7" + Lang.t("cmd.help.footer"));
    }

    /** Opens the storage boxes screen of the selected project (the chat link after marking two corners uses this). */
    private static void openStorage() {
        var project = ClientProjectCache.selected();
        if (project.isEmpty()) {
            say("§e" + Lang.t("cmd.noproject"));
            return;
        }
        String name = project.get().name();
        MinecraftClient.getInstance().send(() -> fi.dy.masa.malilib.gui.GuiBase.openGui(new StorageScreen(null, name, 0, "")));
    }

    private static void surplus() {
        var project = ClientProjectCache.selected();
        if (project.isEmpty()) {
            say("§e" + Lang.t("cmd.noproject"));
            return;
        }
        var offers = ClientProjectCache.surplus(project.get().name());
        if (offers.isEmpty()) {
            say("§7" + Lang.t("cmd.surplus.none", project.get().name()));
            return;
        }
        say("§a[Litematica MaterialShare]§r " + Lang.t("cmd.surplus.title", project.get().name(), offers.size()));
        for (var o : offers) {
            say("  §d" + UiText.itemName(o.itemId()) + " ×" + o.count() + "§r  §7" + ClientProjectCache.nameOf(o.player()));
        }
    }

    private static void projects() {
        List<Project> shown = new ArrayList<>();
        for (Project p : ClientProjectCache.projects()) {
            if (!p.archived()) {
                shown.add(p);
            }
        }
        if (shown.isEmpty()) {
            say(Lang.t("cmd.projects.none"));
            return;
        }
        say("§a[Litematica MaterialShare]§r " + Lang.t("cmd.projects.title", shown.size()));
        String selected = ClientProjectCache.selected().map(Project::name).orElse("");
        for (Project p : shown) {
            say("  " + (p.name().equals(selected) ? "§a▶ §r" : "  ") + p.name() + "  §7" + p.totalCollected() + "/"
                    + p.totalRequired() + " (" + percent(p) + "%)");
        }
    }

    private static void select(String name) {
        if (!ClientProjectCache.exists(name)) {
            say("§c" + Lang.t("cmd.select.none", name));
            return;
        }
        ProjectNames.choose(name);
        say("§a[Litematica MaterialShare]§r " + Lang.t("cmd.select.done", name));
    }

    private static int percent(Project p) {
        return p.totalRequired() == 0 ? 0 : (int) (100L * p.totalCollected() / p.totalRequired());
    }

    private static void progress() {
        var project = ClientProjectCache.selected();
        if (project.isEmpty()) {
            say("§e" + Lang.t("cmd.noproject"));
            return;
        }
        printProgress(project.get().name());
    }

    /** Prints how far a project is. */
    static void printProgress(String name) {
        Project p = ClientProjectCache.projects().stream().filter(x -> x.name().equals(name)).findFirst().orElse(null);
        if (p == null) {
            return;
        }
        int done = 0;
        for (Entry e : p.items().values()) {
            if (e.collectedTotal() >= e.required()) {
                done++;
            }
        }
        say("§a[Litematica MaterialShare]§r §f" + p.name() + "§r  " + p.totalCollected() + "/" + p.totalRequired() + " ("
                + percent(p) + "%)");
        say("  " + Lang.t("cmd.progress.items", done, p.items().size() - done));
        if (p.isComplete()) {
            say("  §a" + Lang.t("cmd.progress.finished"));
        }
    }

    private static void mine() {
        var project = ClientProjectCache.selected();
        UUID self = RowOverlay.selfId();
        if (project.isEmpty() || self == null) {
            say("§e" + Lang.t("cmd.noproject"));
            return;
        }
        List<Map.Entry<String, Entry>> open = new ArrayList<>();
        for (Map.Entry<String, Entry> item : project.get().items().entrySet()) {
            Integer mine = item.getValue().claims().get(self);
            if (mine != null && item.getValue().collectedTotal() < item.getValue().required()) {
                open.add(item);
            }
        }
        open.sort((a, b) -> Boolean.compare(b.getValue().priority(), a.getValue().priority()));
        if (open.isEmpty()) {
            say("§a" + Lang.t("cmd.mine.none", project.get().name()));
            return;
        }
        say("§a[Litematica MaterialShare]§r " + Lang.t("cmd.mine.title", project.get().name(), open.size()));
        for (int i = 0; i < Math.min(MINE_LINES, open.size()); i++) {
            Entry e = open.get(i).getValue();
            int mineCount = e.claims().get(self);
            int goal = e.targetOf(self) > 0 ? e.targetOf(self) : e.required();
            say("  " + (e.priority() ? "§6★§r " : "  ") + UiText.itemName(open.get(i).getKey()) + "  §e" + mineCount + "/" + goal
                    + "§r  §7" + Lang.t("hud.left", Math.max(0, goal - mineCount)));
        }
        if (open.size() > MINE_LINES) {
            say("  §7" + Lang.t("cmd.mine.more", open.size() - MINE_LINES));
        }
    }
}
