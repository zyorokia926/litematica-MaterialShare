package dev.materialshare.client;

import dev.materialshare.client.config.Configs;
import dev.materialshare.common.Entry;
import dev.materialshare.common.Project;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/** A small list in a corner of the game screen: what you still have to collect in the selected project. */
public final class HudOverlay {
    private static final int ICON = 16;
    private static final int PAD = 3;

    private HudOverlay() {
    }

    private record Line(String itemId, String text, boolean priority) {
    }

    static List<Line> lines(Project project, UUID self, int max) {
        List<Map.Entry<String, Entry>> mine = new ArrayList<>();
        for (Map.Entry<String, Entry> item : project.items().entrySet()) {
            Entry e = item.getValue();
            Integer collected = e.claims().get(self);
            int goal = e.targetOf(self) > 0 ? e.targetOf(self) : e.required();
            // your own share is done once you reach your promised amount, even if the item as a whole is not
            if (collected != null && collected < goal && e.collectedTotal() < e.required()) {
                mine.add(item);
            }
        }
        mine.sort(Comparator.<Map.Entry<String, Entry>>comparingInt(e -> e.getValue().priority() ? 0 : 1)
                .thenComparingInt(e -> -(e.getValue().required() - e.getValue().claims().get(self))));
        List<Line> lines = new ArrayList<>();
        for (int i = 0; i < Math.min(max, mine.size()); i++) {
            Map.Entry<String, Entry> item = mine.get(i);
            Entry e = item.getValue();
            int mineCount = e.claims().get(self);
            int target = e.targetOf(self) > 0 ? e.targetOf(self) : e.required();
            int handed = e.delivered().getOrDefault(self, 0);
            int stored = ClientProjectCache.storage(project.name()).stock().getOrDefault(item.getKey(), 0);
            lines.add(new Line(item.getKey(), UiText.itemName(item.getKey()) + "  " + mineCount + "/" + target
                    + (handed > 0 ? "  §a" + Lang.t("hud.handed", handed) : "")
                    + (stored > 0 ? "  §b" + Lang.t("hud.stored", stored) : "")
                    + "  §7" + Lang.t("hud.left", Math.max(0, target - mineCount)), e.priority()));
        }
        return lines;
    }

    /** What the box shows and how big it is. */
    record Layout(String title, List<Line> lines, int width, int height) {
    }

    private static Layout layoutOf(MinecraftClient client, String title, List<Line> lines) {
        TextRenderer font = client.textRenderer;
        int width = font.getWidth(title);
        for (Line line : lines) {
            width = Math.max(width, ICON + 2 + font.getWidth((line.priority ? "§6★§r " : "") + line.text));
        }
        if (lines.isEmpty()) {
            width = Math.max(width, font.getWidth(Lang.t("hud.nothing")));
        }
        int height = 10 + (lines.isEmpty() ? 10 : lines.size() * ICON) + 2;
        return new Layout(title, lines, width, height);
    }

    /** The box for the selected project, or null when there is nothing to show. */
    private static Layout layout(MinecraftClient client) {
        var project = ClientProjectCache.selected();
        UUID self = RowOverlay.selfId();
        if (project.isEmpty() || self == null) {
            return null;
        }
        List<Line> lines = lines(project.get(), self, Configs.Generic.HUD_LINES.getIntegerValue());
        int percent = project.get().totalRequired() == 0 ? 0
                : (int) (100L * project.get().totalCollected() / project.get().totalRequired());
        String title = project.get().name() + "  §7" + percent + "%";
        return layoutOf(client, title, lines);
    }

    /** A made-up box, for the screen where the player drags it into place. */
    static Layout sample(MinecraftClient client) {
        return layoutOf(client, Lang.t("hud.sample.title") + "  §742%", List.of(
                new Line("minecraft:stone", UiText.itemName("minecraft:stone") + "  21/64  §b" + Lang.t("hud.stored", 30) + "  §7" + Lang.t("hud.left", 43), true),
                new Line("minecraft:oak_planks", UiText.itemName("minecraft:oak_planks") + "  0/16  §7" + Lang.t("hud.left", 16), false)));
    }

    /** The top left corner of the box: the saved place is a share (0 to 1000) of the free room on each side. */
    static int[] origin(MinecraftClient client, Layout l) {
        int freeX = Math.max(0, client.getWindow().getScaledWidth() - l.width - PAD * 2 - 8);
        int freeY = Math.max(0, client.getWindow().getScaledHeight() - l.height - PAD * 2 - 8);
        return new int[] {4 + freeX * Configs.Hud.X.getIntegerValue() / 1000, 4 + freeY * Configs.Hud.Y.getIntegerValue() / 1000};
    }

    static int[] size(Layout l) {
        return new int[] {l.width + PAD * 2, l.height + PAD * 2};
    }

    public static void render(MatrixStack matrices, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!Configs.Generic.SHOW_HUD.getBooleanValue() || client.player == null || client.options.hudHidden
                || client.currentScreen != null || client.options.debugEnabled) {
            return;
        }
        Layout l = layout(client);
        if (l != null) {
            int[] at = origin(client, l);
            draw(matrices, client, l, at[0], at[1]);
        }
    }

    static void draw(MatrixStack matrices, MinecraftClient client, Layout l, int x, int y) {
        TextRenderer font = client.textRenderer;
        DrawableHelper.fill(matrices, x, y, x + l.width + PAD * 2, y + l.height + PAD * 2, 0x88000000);
        font.drawWithShadow(matrices, l.title, x + PAD, y + PAD, 0xFFFFFFFF);
        int ly = y + PAD + 10;
        if (l.lines.isEmpty()) {
            font.drawWithShadow(matrices, Lang.t("hud.nothing"), x + PAD, ly, 0xFF88FF88);
            return;
        }
        for (Line line : l.lines) {
            Identifier id = Identifier.tryParse(line.itemId);
            if (id != null && Registries.ITEM.containsId(id)) {
                client.getItemRenderer().renderGuiItemIcon(matrices, new ItemStack(Registries.ITEM.get(id)), x + PAD, ly);
            }
            font.drawWithShadow(matrices, (line.priority ? "§6★§r " : "") + line.text, x + PAD + ICON + 2, ly + 4, 0xFFFFFFFF);
            ly += ICON;
        }
    }
}
