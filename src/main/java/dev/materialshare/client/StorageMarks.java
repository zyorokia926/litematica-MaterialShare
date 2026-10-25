package dev.materialshare.client;

import dev.materialshare.common.Area;
import dev.materialshare.common.Entry;
import fi.dy.masa.malilib.gui.Message;
import fi.dy.masa.malilib.util.InfoUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

/** The two corners a player marks in the world before registering a storage box. */
public final class StorageMarks {
    private static BlockPos a;
    private static BlockPos b;
    private static String dim = "";

    private StorageMarks() {
    }

    static BlockPos a() {
        return a;
    }

    static BlockPos b() {
        return b;
    }

    static String dimension() {
        return dim;
    }

    /** The block looked at (within 128 blocks), else the one the player stands in. */
    private static BlockPos pointed(MinecraftClient client) {
        HitResult hit = client.player.raycast(128, 0, false);
        if (hit.getType() == HitResult.Type.BLOCK) {
            return ((net.minecraft.util.hit.BlockHitResult) hit).getBlockPos();
        }
        return client.player.getBlockPos();
    }

    /** Marks corner A ({@code first}) or B at the place the player looks at. */
    public static void mark(boolean first) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            return;
        }
        String here = client.world.getRegistryKey().getValue().toString();
        if (!here.equals(dim)) {
            a = null;
            b = null;
            dim = here;
        }
        BlockPos pos = pointed(client);
        if (first) {
            a = pos;
        } else {
            b = pos;
        }
        InfoUtils.showGuiOrInGameMessage(Message.MessageType.INFO,
                Lang.t(first ? "storage.marked.a" : "storage.marked.b", pos.getX(), pos.getY(), pos.getZ()));
        if (ready()) {
            announceRegister(client);
        }
    }

    /** Both corners are marked: a chat line that opens the registration screen when clicked. */
    private static void announceRegister(MinecraftClient client) {
        if (ClientProjectCache.selected().isEmpty()) {
            client.player.sendMessage(dev.materialshare.McCompat.literal("§e[Litematica MaterialShare]§r " + Lang.t("storage.noproject")), false);
            return;
        }
        net.minecraft.text.MutableText link = dev.materialshare.McCompat.literal("§b§n" + Lang.t("storage.link"))
                .setStyle(net.minecraft.text.Style.EMPTY.withClickEvent(new net.minecraft.text.ClickEvent(
                        net.minecraft.text.ClickEvent.Action.RUN_COMMAND, "/materialshare storage"))
                        .withHoverEvent(new net.minecraft.text.HoverEvent(net.minecraft.text.HoverEvent.Action.SHOW_TEXT,
                                dev.materialshare.McCompat.literal(Lang.t("storage.link.tip")))));
        client.player.sendMessage(dev.materialshare.McCompat.literal("§a[Litematica MaterialShare]§r " + Lang.t("storage.marked.both") + " ").append(link), false);
    }

    static boolean ready() {
        return a != null && b != null;
    }

    static void clear() {
        a = null;
        b = null;
    }

    /** Takes both corner marks away (for a corner marked by mistake) and says so. */
    public static void clearWithMessage() {
        boolean had = a != null || b != null;
        clear();
        InfoUtils.showGuiOrInGameMessage(Message.MessageType.INFO, Lang.t(had ? "storage.cleared" : "storage.cleared.none"));
    }

    /** The box between the marks, or null while a corner is missing. */
    static Area area(String name) {
        return ready() ? Area.between(name, dim, a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ()) : null;
    }

    /** The first free default name ("Storage 1", ...). */
    static String suggestName(java.util.List<Area> existing) {
        for (int i = 1; i <= 99; i++) {
            String name = Entry.cleanText(Lang.t("storage.defaultname", i));
            if (existing.stream().noneMatch(x -> x.name().equals(name))) {
                return name;
            }
        }
        return Lang.t("storage.defaultname", 100);
    }
}
