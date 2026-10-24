package dev.materialshare.client;

import dev.materialshare.common.Counts;
import dev.materialshare.common.Entry;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import fi.dy.masa.malilib.gui.Message;
import fi.dy.masa.malilib.util.InfoUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.screen.Screen;

/** Copies what the player carries (Litematica's "available" count) into their collected counts. */
public final class Importer {
    private static int ticks;
    private static String lastProject = "";
    private static final java.util.Map<String, Integer> lastCarried = new java.util.HashMap<>();

    private Importer() {
    }

    /** For every item the player has claimed: collected = max(collected, min(carried, required)). */
    public static int run(MaterialListBase list, boolean verbose) {
        var project = ClientProjectCache.selected();
        UUID self = RowOverlay.selfId();
        if (project.isEmpty() || self == null) {
            if (verbose) {
                InfoUtils.showGuiMessage(Message.MessageType.WARNING, Lang.t("import.pick"));
            }
            return 0;
        }
        var player = net.minecraft.client.MinecraftClient.getInstance().player;
        if (player == null) {
            return 0;
        }
        // Litematica counts what the player carries only when asked (its own "available" column does this on refresh)
        fi.dy.masa.litematica.materials.MaterialListUtils.updateAvailableCounts(list.getMaterialsAll(), player);
        List<Undo.Op> before = new ArrayList<>();
        List<Object[]> sends = new ArrayList<>();
        for (MaterialListEntry row : list.getMaterialsAll()) {
            String id = RowOverlay.itemId(row);
            Entry e = project.get().items().get(id);
            if (e == null || !e.claims().containsKey(self)) {
                continue;
            }
            int current = e.claims().get(self);
            int next = Counts.imported(current, row.getCountAvailable(), e.required());
            if (next != current) {
                before.add(Undo.before(project.get().name(), id, self));
                sends.add(new Object[] {id, next});
            }
        }
        send(project.get().name(), before, sends, verbose);
        if (verbose) {
            InfoUtils.showGuiMessage(sends.isEmpty() ? Message.MessageType.INFO : Message.MessageType.SUCCESS,
                    sends.isEmpty() ? Lang.t("import.none") : Lang.t("import.done", sends.size()));
        }
        return sends.size();
    }

    /** Shift + click on your own name in a row: just that row. */
    public static void runRow(MaterialListEntry row) {
        var project = ClientProjectCache.selected();
        UUID self = RowOverlay.selfId();
        if (project.isEmpty() || self == null) {
            return;
        }
        String id = RowOverlay.itemId(row);
        Entry e = project.get().items().get(id);
        if (e == null || !e.claims().containsKey(self)) {
            return;
        }
        int current = e.claims().get(self);
        int next = Counts.imported(current, row.getCountAvailable(), e.required());
        if (next == current) {
            InfoUtils.showGuiMessage(Message.MessageType.INFO, Lang.t("import.row.none", current));
            return;
        }
        send(project.get().name(), List.of(Undo.before(project.get().name(), id, self)),
                List.<Object[]>of(new Object[] {id, next}), true);
        InfoUtils.showGuiMessage(Message.MessageType.SUCCESS, Lang.t("import.row.done", next));
    }

    /** Only imports the player asked for can be undone; the automatic ones would otherwise be redone at once. */
    private static void send(String project, List<Undo.Op> before, List<Object[]> sends, boolean undoable) {
        if (sends.isEmpty()) {
            return;
        }
        if (undoable) {
            Undo.recordMany(before);
        }
        java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (Object[] s : sends) {
            counts.put((String) s[0], (Integer) s[1]);
        }
        ClientNet.sendImport(project, counts);
    }

    /**
     * With the setting on, your collected counts follow what you carry, whether or not a screen is open: every few
     * seconds, for the items you are assigned to in the selected project (counts only ever go up).
     */
    public static void tick(net.minecraft.client.MinecraftClient client) {
        if (!dev.materialshare.client.config.Configs.Generic.AUTO_IMPORT.getBooleanValue() || client.player == null
                || client.world == null) {
            ticks = 0;
            return;
        }
        if (++ticks < 60) {
            return;
        }
        ticks = 0;
        var project = ClientProjectCache.selected();
        UUID self = RowOverlay.selfId();
        if (project.isEmpty() || self == null) {
            return;
        }
        java.util.Map<String, Integer> carried = Inventory.counts(client.player);
        java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        String name = project.get().name();
        boolean first = !name.equals(lastProject);
        if (first) {
            lastProject = name;
            lastCarried.clear();
        }
        project.get().items().forEach((id, e) -> {
            Integer current = e.claims().get(self);
            int have = carried.getOrDefault(id, 0);
            Integer before = lastCarried.put(id, have);
            if (current != null) {
                // Only what was picked up since the last look is added. Setting the count to what is carried would
                // undo a number the player just changed by hand (the wheel) while the items are still in the inventory.
                int next = before == null ? Counts.imported(current, have, e.required())
                        : have > before ? Math.min(e.required(), current + (have - before)) : current;
                if (next != current) {
                    counts.put(id, next);
                }
            }
        });
        if (!counts.isEmpty()) {
            ClientNet.sendImport(project.get().name(), counts);
        }
    }

    /**
     * "Take what I carry": every item of the project that you are carrying and nobody has taken yet becomes yours,
     * with the amount you carry (up to what is required). Returns how many items.
     */
    public static int claimFromInventory(boolean verbose) {
        var project = ClientProjectCache.selected();
        UUID self = RowOverlay.selfId();
        var player = net.minecraft.client.MinecraftClient.getInstance().player;
        if (project.isEmpty() || self == null || player == null) {
            if (verbose) {
                InfoUtils.showGuiMessage(Message.MessageType.WARNING, Lang.t("import.pick"));
            }
            return 0;
        }
        java.util.Map<String, Integer> carried = Inventory.counts(player);
        List<dev.materialshare.common.Assignments.Assignment> claims = new ArrayList<>();
        java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        project.get().items().forEach((id, e) -> {
            int have = carried.getOrDefault(id, 0);
            if (have > 0 && e.claims().isEmpty()) {
                claims.add(new dev.materialshare.common.Assignments.Assignment(id, self));
                counts.put(id, Math.min(have, e.required()));
            }
        });
        if (claims.isEmpty()) {
            if (verbose) {
                InfoUtils.showGuiMessage(Message.MessageType.INFO, Lang.t("takecarried.none"));
            }
            return 0;
        }
        ClientNet.sendBulk(project.get().name(), claims);
        ClientNet.sendImport(project.get().name(), counts);
        if (verbose) {
            InfoUtils.showGuiMessage(Message.MessageType.SUCCESS, Lang.t("takecarried.done", claims.size()));
        }
        return claims.size();
    }

    /** The hotkey: the item in the main hand becomes yours (with the amount you carry). */
    public static void claimHeld() {
        var client = net.minecraft.client.MinecraftClient.getInstance();
        var project = ClientProjectCache.selected();
        UUID self = RowOverlay.selfId();
        if (client.player == null || self == null) {
            return;
        }
        var stack = client.player.getMainHandStack();
        if (stack.isEmpty()) {
            client.player.sendMessage(net.minecraft.text.Text.literal(Lang.t("held.empty")), true);
            return;
        }
        if (project.isEmpty()) {
            client.player.sendMessage(net.minecraft.text.Text.literal(Lang.t("import.pick")), true);
            return;
        }
        String id = Inventory.idOf(stack);
        Entry e = project.get().items().get(id);
        String name = stack.getName().getString();
        if (e == null) {
            client.player.sendMessage(net.minecraft.text.Text.literal(Lang.t("held.notneeded", name)), true);
            return;
        }
        if (e.claims().containsKey(self)) {
            client.player.sendMessage(net.minecraft.text.Text.literal(Lang.t("held.already", name)), true);
            return;
        }
        int have = Inventory.counts(client.player).getOrDefault(id, 0);
        ClientNet.sendClaim(project.get().name(), id, self, true);
        if (have > 0) {
            ClientNet.sendImport(project.get().name(), java.util.Map.of(id, Math.min(have, e.required())));
        }
        client.player.sendMessage(net.minecraft.text.Text.literal(Lang.t("held.claimed", name)), true);
    }

    /** Marks everything you collected as handed in (so teammates can see it is in the storage). */
    public static int deliverAll(boolean verbose) {
        var project = ClientProjectCache.selected();
        UUID self = RowOverlay.selfId();
        if (project.isEmpty() || self == null) {
            return 0;
        }
        java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        project.get().items().forEach((id, e) -> {
            Integer collected = e.claims().get(self);
            if (collected != null && collected > 0 && e.delivered().getOrDefault(self, 0) < collected) {
                counts.put(id, collected);
            }
        });
        if (counts.isEmpty()) {
            if (verbose) {
                InfoUtils.showGuiMessage(Message.MessageType.INFO, Lang.t("deliver.none"));
            }
            return 0;
        }
        ClientNet.sendBulkDelivered(project.get().name(), counts);
        if (verbose) {
            InfoUtils.showGuiMessage(Message.MessageType.SUCCESS, Lang.t("deliver.done", counts.size()));
        }
        return counts.size();
    }
}
