package dev.materialshare.client;

import dev.materialshare.common.Entry;
import dev.materialshare.common.Packets;
import dev.materialshare.common.Project;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

/**
 * Finding a material in the storage: a click on an item's total in the list asks the server which storage chests hold
 * it; they are drawn through walls and the nearest distance shows above the hotbar until the same click again. An
 * open storage chest gets a button that takes out what the schematic still misses.
 */
public final class Pickup {
    /** Ask again now and then: what the chests hold changes while you take things out. */
    private static final int REFRESH_TICKS = 100;

    private static String project;
    private static String itemId;
    private static List<Packets.Spot> spots = List.of();
    private static int ticks;

    private Pickup() {
    }

    public static boolean active(String project, String itemId) {
        return itemId != null && itemId.equals(Pickup.itemId) && project.equals(Pickup.project);
    }

    /** Starts finding the item, or stops when it is the one being found already. */
    static void toggle(String project, String itemId) {
        if (active(project, itemId)) {
            clear();
            show(Lang.t("find.cleared"));
            return;
        }
        Pickup.project = project;
        Pickup.itemId = itemId;
        spots = List.of();
        ticks = 0;
        ClientNet.sendFind(project, itemId);
    }

    static void clear() {
        project = null;
        itemId = null;
        spots = List.of();
    }

    static void onFound(Packets.Found found) {
        if (!active(found.project(), found.itemId())) {
            return;
        }
        boolean first = spots.isEmpty();
        spots = found.spots();
        if (spots.isEmpty()) {
            show(Lang.t("find.none", UiText.itemName(found.itemId())));
            clear();
        } else if (first) {
            showNearest();
        }
    }

    /** The chests to draw in this dimension. */
    static List<BlockPos> shown(String dimension) {
        List<BlockPos> out = new ArrayList<>();
        for (Packets.Spot s : spots) {
            if (s.dimension().equals(dimension)) {
                out.add(new BlockPos(s.x(), s.y(), s.z()));
            }
        }
        return out;
    }

    private static void show(String text) {
        var player = MinecraftClient.getInstance().player;
        if (player != null) {
            player.sendMessage(dev.materialshare.McCompat.literal(text), true);
        }
    }

    private static void showNearest() {
        var client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || spots.isEmpty()) {
            return;
        }
        String dim = client.world.getRegistryKey().getValue().toString();
        double best = Double.MAX_VALUE;
        int total = 0;
        for (Packets.Spot s : spots) {
            total += s.count();
            if (s.dimension().equals(dim)) {
                best = Math.min(best, Math.sqrt(client.player.squaredDistanceTo(s.x() + 0.5, s.y() + 0.5, s.z() + 0.5)));
            }
        }
        String name = UiText.itemName(itemId);
        show(best == Double.MAX_VALUE ? Lang.t("find.otherdim", name, spots.size(), total)
                : Lang.t("find.near", name, (int) Math.round(best), spots.size(), total));
    }

    public static void tick(MinecraftClient client) {
        if (itemId == null) {
            return;
        }
        if (client.world == null || ClientProjectCache.selected().map(p -> !p.name().equals(project)).orElse(true)) {
            clear(); // left the server or chose another project
            return;
        }
        ticks++;
        if (ticks % 20 == 0 && client.currentScreen == null) {
            showNearest();
        }
        if (ticks % REFRESH_TICKS == 0) {
            ClientNet.sendFind(project, itemId);
        }
    }

    /**
     * What to take out of an open chest: per item of the selected project (only the chosen zone's, if any), what the
     * schematic still misses in the world minus what you carry. Without a material list, what the item still needs.
     */
    static Map<String, Integer> wish() {
        Map<String, Integer> wish = new LinkedHashMap<>();
        var client = MinecraftClient.getInstance();
        Project p = ClientProjectCache.selected().orElse(null);
        if (p == null || client.player == null) {
            return wish;
        }
        var zone = ClientProjectCache.chosenZone(p.name());
        Map<String, Integer> carried = Inventory.counts(client.player);
        Map<String, Integer> missing = new LinkedHashMap<>();
        var list = fi.dy.masa.litematica.data.DataManager.getMaterialList();
        if (list != null) {
            for (var entry : list.getMaterialsAll()) {
                missing.merge(RowOverlay.itemId(entry), entry.getCountMissing(), Integer::sum);
            }
        }
        if (p.items().keySet().stream().noneMatch(missing::containsKey)) {
            missing.clear(); // the open list is of another schematic
        }
        for (Map.Entry<String, Entry> item : p.items().entrySet()) {
            String id = item.getKey();
            if (zone.isPresent() && !zone.get().needs(id)) {
                continue;
            }
            int need = missing.isEmpty() ? item.getValue().required() : missing.getOrDefault(id, 0);
            if (zone.isPresent()) {
                need = Math.min(need, zone.get().requiredOf(id));
            }
            int n = need - carried.getOrDefault(id, 0);
            if (n > 0) {
                wish.put(id, n);
            }
        }
        return wish;
    }

    /** Whether you stand in one of the selected project's storage boxes (the take-out button shows only there). */
    static boolean inStorage(MinecraftClient client) {
        Project p = ClientProjectCache.selected().orElse(null);
        if (p == null || client.player == null || client.world == null) {
            return false;
        }
        String dim = client.world.getRegistryKey().getValue().toString();
        BlockPos at = client.player.getBlockPos();
        for (var a : ClientProjectCache.storage(p.name()).areas()) {
            if (a.dimension().equals(dim) && at.getX() >= a.minX() - 6 && at.getX() <= a.maxX() + 6 && at.getY() >= a.minY() - 6
                    && at.getY() <= a.maxY() + 6 && at.getZ() >= a.minZ() - 6 && at.getZ() <= a.maxZ() + 6) {
                return true;
            }
        }
        return false;
    }

    /** Adds the take-out button to a chest-like screen opened inside a storage box. */
    static void onScreenInit(MinecraftClient client, net.minecraft.client.gui.screen.Screen screen, int width, int height) {
        int rows;
        if (screen instanceof net.minecraft.client.gui.screen.ingame.GenericContainerScreen g) {
            rows = g.getScreenHandler().getRows();
        } else if (screen instanceof net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen) {
            rows = 3;
        } else {
            return;
        }
        if (!inStorage(client)) {
            return;
        }
        Project p = ClientProjectCache.selected().orElseThrow();
        int guiHeight = 114 + rows * 18;
        int y = Math.max(2, (height - guiHeight) / 2 - 22);
        String label = Lang.t("take.button");
        int w = Math.min(176, client.textRenderer.getWidth(label) + 16);
        net.fabricmc.fabric.api.client.screen.v1.Screens.getButtons(screen).add(
                dev.materialshare.McClientCompat.button(width / 2 - 88, y, w, 20, label, () -> {
                    Map<String, Integer> wish = wish();
                    if (wish.isEmpty()) {
                        show(Lang.t("take.nothing"));
                        return;
                    }
                    ClientNet.sendTake(p.name(), wish);
                }));
    }
}
