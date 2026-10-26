package dev.materialshare.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.materialshare.client.config.Configs;
import dev.materialshare.common.Entry;
import dev.materialshare.common.Project;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextInput;
import fi.dy.masa.malilib.gui.Message;
import fi.dy.masa.malilib.util.InfoUtils;
import fi.dy.masa.malilib.util.StringUtils;
import fi.dy.masa.malilib.gui.widgets.WidgetBase;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Extra cells drawn on each Litematica material list row: a claim button and one cell per claimant. */
public final class RowOverlay {
    private enum Kind { STAR, CLAIM, ADD, REGISTER, PLAYER, SUMMARY, MORE }

    /** {@code progress} is 0..1 for a cell that draws a small progress bar under its text, else -1. */
    private record Cell(Kind kind, UUID player, int x, int y, int w, int h, String text, int color,
            List<String> tooltip, double progress) {
        boolean contains(int mx, int my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private static final int WHITE = 0xFFFFFFFF;
    private static final int GREEN = 0xFF55FF55;
    private static final int GRAY = 0xFFAAAAAA;
    private static final int GAP = 4;
    private static final int FACE = 8;
    private static final int FACE_GAP = 2;
    private static final int BUTTON_H = 20;
    /** Skin textures seen this session, so players who went offline keep their face. */
    private static final Map<UUID, Identifier> SEEN_SKINS = new HashMap<>();

    private RowOverlay() {
    }

    private static boolean faces() {
        return Configs.Generic.SHOW_FACES.getBooleanValue();
    }

    private static int faceW() {
        return faces() ? FACE + FACE_GAP : 0;
    }

    private static int shiftStep() {
        return Configs.Generic.WHEEL_STEP_SHIFT.getIntegerValue();
    }

    /** What one wheel notch changes (the step, or the stack step with Shift), 0 when the wheel is not allowed now. */
    static int wheelDelta(double amount) {
        if (amount == 0 || (Configs.Generic.WHEEL_NEEDS_CTRL.getBooleanValue() && !Screen.hasControlDown())) {
            return 0;
        }
        int step = Screen.hasShiftDown() ? shiftStep() : Configs.Generic.WHEEL_STEP.getIntegerValue();
        return amount > 0 ? step : -step;
    }

    public static String itemId(MaterialListEntry entry) {
        return dev.materialshare.McCompat.itemId(entry.getStack().getItem()).toString();
    }

    // ---- header -----------------------------------------------------------------------------

    public static void renderHeader(WidgetBase row, int x0, MatrixStack matrices) {
        if (!Configs.Generic.SHOW_OVERLAY.getBooleanValue() || ClientProjectCache.selected().isEmpty()) {
            return;
        }
        String title = Lang.t("row.header");
        var project = ClientProjectCache.selected().get();
        if (project.totalRequired() > 0) {
            int percent = (int) (100L * project.totalCollected() / project.totalRequired());
            title += "  " + project.totalCollected() + "/" + project.totalRequired() + " (" + percent + "%)";
        }
        int room = row.getX() + row.getWidth() - x0 - 24; // keep clear of Litematica's search icon
        if (row.getStringWidth(title) > room) {
            title = row.getStringWidth(title.substring(0, title.indexOf("  ") < 0 ? title.length() : title.indexOf("  "))) > room
                    ? Lang.t("row.header") : title.substring(0, title.indexOf("  ") < 0 ? title.length() : title.indexOf("  "));
        }
        row.drawString(x0, row.getY() + (row.getHeight() - 8) / 2 + 1, WHITE, title, matrices);
    }

    // ---- rows -------------------------------------------------------------------------------

    public static void render(WidgetBase row, MaterialListEntry entry, int x0, MatrixStack matrices, int mx, int my) {
        Entry progress = ClientProjectCache.selected().map(p -> p.items().get(itemId(entry))).orElse(null);
        if (Configs.Generic.SHOW_OVERLAY.getBooleanValue() && Configs.Generic.HIGHLIGHT_DONE_ROWS.getBooleanValue()
                && progress != null && progress.collectedTotal() >= progress.required()) {
            dev.materialshare.McClientCompat.fill(matrices, row.getX(), row.getY(), row.getX() + row.getWidth(),
                    row.getY() + row.getHeight(), 0x3355FF55);
        }
        for (Cell cell : layout(row, entry, x0)) {
            boolean hover = cell.contains(mx, my);
            switch (cell.kind) {
                case STAR -> row.drawString(cell.x, cell.y + (cell.h - 8) / 2 + 1,
                        cell.color, hover ? "§e" + cell.text.substring(2) : cell.text, matrices);
                case CLAIM, ADD, MORE, REGISTER -> drawVanillaButton(row, matrices, cell, hover);
                case PLAYER -> {
                    if (hover) {
                        dev.materialshare.McClientCompat.fill(matrices, cell.x - 1, cell.y, cell.x + cell.w + 1, cell.y + cell.h,
                                0x66FFFFFF);
                    }
                    if (faces()) {
                        drawFace(matrices, cell.player, cell.x, cell.y + (cell.h - FACE) / 2);
                    }
                    if (isOnline(cell.player)) {
                        // a small green dot on the corner of the face (or before the name when faces are off)
                        int dx = faces() ? cell.x + FACE - 3 : cell.x;
                        int dy = cell.y + (cell.h - FACE) / 2 + (faces() ? FACE - 3 : 2);
                        dev.materialshare.McClientCompat.fill(matrices, dx - 1, dy - 1, dx + 4, dy + 4, 0xFF000000);
                        dev.materialshare.McClientCompat.fill(matrices, dx, dy, dx + 3, dy + 3, 0xFF55FF55);
                    }
                    row.drawString(cell.x + faceW() + (faces() ? 0 : 5), cell.y + (cell.h - 8) / 2 + 1, cell.color, cell.text, matrices);
                }
                case SUMMARY -> {
                    row.drawString(cell.x, cell.y + (cell.h - 8) / 2, cell.color, cell.text, matrices);
                    if (cell.progress >= 0) {
                        drawProgressBar(matrices, cell.x, cell.y + cell.h - 3, Math.max(cell.w, 24), cell.progress,
                                cell.color == GREEN);
                    }
                }
            }
        }
    }

    /** Draws the tooltip of the hovered cell. Returns true if one was drawn (Litematica's own is then skipped). */
    public static boolean tooltip(WidgetBase row, MaterialListEntry entry, int x0, int mx, int my,
            MatrixStack matrices) {
        Screen screen = MinecraftClient.getInstance().currentScreen;
        if (screen == null || !Configs.Generic.SHOW_TOOLTIPS.getBooleanValue()) {
            return false;
        }
        for (Cell cell : layout(row, entry, x0)) {
            if (cell.contains(mx, my) && !cell.tooltip.isEmpty()) {
                List<Text> lines = new ArrayList<>();
                cell.tooltip.forEach(line -> lines.add(dev.materialshare.McCompat.literal(line)));
                dev.materialshare.McClientCompat.tooltip(screen, matrices, UiText.tipLines(lines), mx, my);
                return true;
            }
        }
        return false;
    }

    public static boolean click(WidgetBase row, MaterialListEntry entry, int x0, int mx, int my, int button,
            int multiplier) {
        Optional<Project> selected = ClientProjectCache.selected();
        UUID self = selfId();
        if (selected.isEmpty() || self == null || (button != 0 && button != 1)) {
            return false;
        }
        String project = selected.get().name();
        String itemId = itemId(entry);
        for (Cell cell : layout(row, entry, x0)) {
            if (cell.contains(mx, my) && cell.kind == Kind.STAR) {
                // the star: needed first, or not (the right button does nothing here)
                Entry e = selected.get().items().get(itemId);
                if (button == 0 && e != null) {
                    playClick();
                    ClientNet.sendItemInfo(project, itemId, !e.priority());
                }
                return button == 0;
            }
            if (cell.contains(mx, my) && cell.kind == Kind.SUMMARY && cell.progress >= 0) {
                Entry e = selected.get().items().get(itemId);
                if (e == null) {
                    continue;
                }
                if (button == 0 && Screen.hasControlDown()) {
                    // Ctrl + click on the total: everything about this item on one screen
                    playClick();
                    GuiBase.openGui(new ItemDetailScreen(MinecraftClient.getInstance().currentScreen, project, itemId));
                    return true;
                }
            }
            if (!cell.contains(mx, my) || cell.kind == Kind.SUMMARY) {
                continue;
            }
            Entry e = selected.get().items().get(itemId);
            if (cell.kind == Kind.MORE) {
                if (button != 0) {
                    return false;
                }
                playClick();
                // everybody who takes this item, in a small window: remove people, set how much each one takes
                GuiBase.openGui(new SplitScreen(MinecraftClient.getInstance().currentScreen, project, itemId, 0, ""));
                return true;
            }
            if (cell.kind == Kind.CLAIM) {
                if (button != 0) {
                    return false;
                }
                playClick();
                boolean claim = e == null || !e.claims().containsKey(self);
                if (claim && e != null && e.claims().size() >= dev.materialshare.common.Project.MAX_CLAIMANTS) {
                    tooManyClaimants();
                } else if (claim) {
                    ClientNet.sendClaim(project, itemId, self, true);
                } else {
                        ClientNet.sendClaim(project, itemId, self, false);
                }
            } else if (cell.kind == Kind.REGISTER) {
                if (button != 0) {
                    return false;
                }
                playClick();
                // the server merges: this adds the missing item and leaves everything else as it is
                ClientNet.sendCreate(project, Map.of(itemId, requiredFor(entry, multiplier)));
            } else if (cell.kind == Kind.ADD) {
                if (button != 0) {
                    return false;
                }
                playClick();
                ClientNet.requestPlayers(); // the server adds everyone it has seen before to the known players
                GuiBase.openGui(new PlayerPickScreen(MinecraftClient.getInstance().currentScreen, project, itemId,
                        entry.getStack().getName().getString(), 0, ""));
            } else if (button == 1) {
                playClick();
                ClientNet.sendClaim(project, itemId, cell.player, false);
            } else if (Screen.hasControlDown()) {
                playClick();
                GuiBase.openGui(new SplitScreen(MinecraftClient.getInstance().currentScreen, project, itemId, 0, ""));
            } else if (Screen.hasShiftDown() && cell.player.equals(self)) {
                playClick();
                Importer.runRow(entry);
            } else {
                playClick();
                int current = e == null ? 0 : e.claims().getOrDefault(cell.player, 0);
                UUID target = cell.player;
                GuiBase.openGui(new GuiTextInput(10, Lang.t("row.count.title", ClientProjectCache.nameOf(target)),
                        String.valueOf(current), MinecraftClient.getInstance().currentScreen,
                        (fi.dy.masa.malilib.interfaces.IStringConsumerFeedback) text -> {
                            var count = dev.materialshare.common.Counts.parse(text, entry.getStack().getMaxCount());
                            if (count.isEmpty()) {
                                InfoUtils.showGuiMessage(Message.MessageType.ERROR, Lang.t("row.count.invalid"));
                                return false; // keep the dialog open
                            }
                            ClientNet.sendCollected(project, itemId, target, count.getAsInt());
                            return true;
                        }));
            }
            return true;
        }
        return false;
    }

    static void tooManyClaimants() {
        InfoUtils.showGuiMessage(Message.MessageType.WARNING,
                Lang.t("row.max", dev.materialshare.common.Project.MAX_CLAIMANTS));
    }

    private static int requiredFor(MaterialListEntry entry, int multiplier) {
        return (int) Math.min((long) entry.getCountTotal() * Math.max(1, multiplier), Integer.MAX_VALUE);
    }

    /** Mouse wheel over a claimant: +1 / -1, or +64 / -64 (one stack) with Shift held. */
    public static boolean scroll(WidgetBase row, MaterialListEntry entry, int x0, int mx, int my, double amount) {
        Optional<Project> selected = ClientProjectCache.selected();
        if (selected.isEmpty() || amount == 0) {
            return false;
        }
        if (Configs.Generic.WHEEL_NEEDS_CTRL.getBooleanValue() && !Screen.hasControlDown()) {
            return false; // the list scrolls instead
        }
        String itemId = itemId(entry);
        for (Cell cell : layout(row, entry, x0)) {
            if (cell.kind == Kind.PLAYER && cell.contains(mx, my)) {
                Entry e = selected.get().items().get(itemId);
                int current = e == null ? 0 : e.claims().getOrDefault(cell.player, 0);
                int step = Screen.hasShiftDown() ? shiftStep() : Configs.Generic.WHEEL_STEP.getIntegerValue();
                ClientNet.sendCollected(selected.get().name(), itemId, cell.player,
                        current + (amount > 0 ? step : -step));
                return true;
            }
        }
        return false;
    }

    // ---- layout -----------------------------------------------------------------------------

    static boolean isOnline(UUID id) {
        var handler = MinecraftClient.getInstance().getNetworkHandler();
        return handler != null && handler.getPlayerListEntry(id) != null;
    }

    static UUID selfId() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            return client.player.getUuid();
        }
        return client.getSession() == null ? null : dev.materialshare.McClientCompat.sessionUuid(client);
    }

    private static List<Cell> layout(WidgetBase row, MaterialListEntry entry, int x0) {
        List<Cell> cells = new ArrayList<>();
        Optional<Project> selected = ClientProjectCache.selected();
        UUID self = selfId();
        if (entry == null || selected.isEmpty() || self == null || !Configs.Generic.SHOW_OVERLAY.getBooleanValue()) {
            return cells;
        }
        Entry e = selected.get().items().get(itemId(entry));
        int h = BUTTON_H;
        int y = row.getY() + (row.getHeight() - h) / 2;
        // keep clear of Litematica's right-aligned "Ignore" button
        int limit = row.getX() + row.getWidth()
                - (row.getStringWidth(StringUtils.translate("litematica.gui.button.material_list.ignore")) + 10 + 6);
        int x = x0;

        if (e == null) {
            String register = Lang.t("row.register");
            int registerW = row.getStringWidth(register) + 12;
            if (x + registerW > limit) {
                return cells;
            }
            cells.add(new Cell(Kind.REGISTER, null, x, y, registerW, h, register, WHITE,
                    List.of(Lang.t("row.register.tip1"), Lang.t("row.register.tip2")), -1));
            x += registerW + GAP;
            String note = UiText.fit(Lang.t("row.unregistered"), limit - x - 2);
            if (limit - x - 2 > row.getStringWidth("…") * 2) {
                cells.add(new Cell(Kind.SUMMARY, null, x, y, 0, h, note, GRAY, List.of(), -1));
            }
            return cells;
        }
        // the star: click to mark the item as needed first
        String star = e.priority() ? "§6★" : "§8☆";
        int starW = row.getStringWidth("★") + 3;
        if (x + starW < limit) {
            cells.add(new Cell(Kind.STAR, null, x, y, starW, h, star, WHITE,
                    List.of(e.priority() ? Lang.t("row.star.on") : Lang.t("row.star.off")), -1));
            x += starW + 1;
        }
        boolean mine = e.claims().containsKey(self);
        String claimLabel = mine ? Lang.t("row.release") : Lang.t("row.claim");
        int claimW = row.getStringWidth(claimLabel) + 12;
        if (x + claimW > limit) {
            return cells;
        }
        cells.add(new Cell(Kind.CLAIM, null, x, y, claimW, h, claimLabel, WHITE,
                List.of(mine ? Lang.t("row.release.tip") : Lang.t("row.claim.tip")), -1));
        x += claimW + 2;

        // "+" assigns somebody else to this item
        int addW = BUTTON_H;
        if (x + addW <= limit) {
            cells.add(new Cell(Kind.ADD, null, x, y, addW, h, "+", WHITE,
                    List.of(Lang.t("row.add.tip1"), Lang.t("row.add.tip2")), -1));
            x += addW + (e.claims().isEmpty() ? GAP : 2);
        } else {
            x += GAP - 2;
        }
        // "≡" opens everybody who takes this item (remove people, set how much each one takes)
        if (!e.claims().isEmpty() && x + addW <= limit) {
            cells.add(new Cell(Kind.MORE, null, x, y, addW, h, "≡", WHITE, List.of(Lang.t("row.more.tip1"), Lang.t("row.more.tip2")), -1));
            x += addW + GAP;
        }

        int total = e.collectedTotal();
        int stored = ClientProjectCache.storage(selected.get().name()).stock().getOrDefault(itemId(entry), 0);
        String summary = total + "/" + e.required() + (stored > 0 ? " §b" + Lang.t("row.stock.short", stored) : "");
        int summaryW = row.getStringWidth(summary);
        // the total always comes first; claimants follow as far as there is room
        if (x + summaryW > limit) {
            return cells;
        }
        String remaining = total >= e.required() ? Lang.t("row.done") : Lang.t("row.left", e.required() - total);
        List<String> summaryTip = new ArrayList<>();
        if (e.priority()) {
            summaryTip.add(Lang.t("row.priority"));
        }
        summaryTip.add(Lang.t("row.total", total, e.required(), remaining));
        if (e.targetTotal() > 0) {
            summaryTip.add(Lang.t("row.targets", e.targetTotal(), e.required(), Math.max(0, e.required() - e.targetTotal())));
        }
        if (stored > 0) {
            var data = ClientProjectCache.storage(selected.get().name());
            int inside = data.inside().getOrDefault(itemId(entry), 0);
            String stacks = dev.materialshare.common.Stacks.format(stored, entry.getStack().getMaxCount(), Lang.TRANSLATOR);
            summaryTip.add("§b" + Lang.t("row.stock", stored) + (stacks.isEmpty() ? "" : " (" + stacks + ")")
                    + (inside > 0 ? "  §7" + Lang.t("row.stock.inside", inside) : ""));
        }
        if (Configs.Generic.SHOW_DELIVERY.getBooleanValue() && e.deliveredTotal() > 0) {
            summaryTip.add(Lang.t("row.delivered", e.deliveredTotal(), total));
        }
        if (Configs.Generic.SHOW_STACKS.getBooleanValue()) {
            int stack = entry.getStack().getMaxCount();
            String stacks = dev.materialshare.common.Stacks.format(e.required(), stack, Lang.TRANSLATOR);
            if (!stacks.isEmpty()) {
                summaryTip.add(Lang.t("row.stacks", stacks));
            }
            double boxes = dev.materialshare.common.Stacks.shulkers(e.required(), stack);
            if (boxes > 0) {
                summaryTip.add(Lang.t("row.shulker.tip", boxes));
            }
        }
        // Litematica's own count of what is still missing in the world, when it differs from the total
        if (entry.getCountMissing() < entry.getCountTotal()) {
            summaryTip.add(Lang.t("row.built", entry.getCountTotal() - entry.getCountMissing(), entry.getCountTotal()));
        }
        if (!e.substitute().isEmpty()) {
            summaryTip.add(Lang.t("row.substitute", UiText.escapeFormat(e.substitute())));
        }
        for (dev.materialshare.common.Packets.Offer offer : ClientProjectCache.surplusOf(selected.get().name(), itemId(entry))) {
            summaryTip.add("§d" + Lang.t("row.surplus", ClientProjectCache.nameOf(offer.player()), offer.count()));
        }
        if (e.claims().isEmpty()) {
            summaryTip.add(Lang.t("row.nobody"));
        }
        e.claims().forEach((id, n) -> summaryTip.add("  " + ClientProjectCache.nameOf(id) + ": " + n
                + (e.targetOf(id) > 0 ? " / " + e.targetOf(id) : "") + (isOnline(id) ? " §a●" : "")));
        summaryTip.add(Lang.t("row.info.ctrl"));
        cells.add(new Cell(Kind.SUMMARY, null, x, y, summaryW, h, summary, total >= e.required() ? GREEN : WHITE,
                summaryTip, Math.min(1.0, (double) total / e.required())));
        x += summaryW + GAP * 2;

        List<Map.Entry<UUID, Integer>> claims = new ArrayList<>(e.claims().entrySet());
        boolean compact = !fitsAll(row, e, claims, false, x, limit);
        // how many claimants are shown: as many as fit, leaving room for "+N" when some have to stay out
        int shown = 0;
        int end = x;
        for (int i = 0; i < claims.size(); i++) {
            int w = faceW() + row.getStringWidth(label(e, claims.get(i), compact));
            if (end + w > limit) {
                break;
            }
            end += w + GAP * 2;
            shown++;
        }
        while (shown < claims.size() && shown > 0) {
            int moreW = row.getStringWidth("+" + (claims.size() - shown));
            if (end + moreW <= limit) {
                break;
            }
            // not even "+N" fits: the last one shown gives way to it
            shown--;
            end -= faceW() + row.getStringWidth(label(e, claims.get(shown), compact)) + GAP * 2;
        }
        for (int i = 0; i < shown; i++) {
            Map.Entry<UUID, Integer> claim = claims.get(i);
            String text = label(e, claim, compact);
            int w = faceW() + row.getStringWidth(text);
            List<String> tip = new ArrayList<>();
            tip.add(ClientProjectCache.nameOf(claim.getKey()));
            tip.add(Lang.t("row.p.count", claim.getValue(), e.required()));
            if (e.targetOf(claim.getKey()) > 0) {
                tip.add(Lang.t("row.p.target", e.targetOf(claim.getKey())));
            }
            if (Configs.Generic.SHOW_DELIVERY.getBooleanValue() && e.delivered().getOrDefault(claim.getKey(), 0) > 0) {
                tip.add(Lang.t("row.p.delivered", e.delivered().get(claim.getKey())));
            }
            tip.add(isOnline(claim.getKey()) ? Lang.t("row.p.online") : Lang.t("row.p.offline"));
            tip.add(Lang.t("row.p.left"));
            if (claim.getKey().equals(self)) {
                tip.add(Lang.t("row.p.import"));
            }
            tip.add(Lang.t("row.p.right"));
            tip.add(Configs.Generic.WHEEL_NEEDS_CTRL.getBooleanValue()
                    ? Lang.t("row.p.wheel.ctrl", Configs.Generic.WHEEL_STEP.getIntegerValue(), shiftStep())
                    : Lang.t("row.p.wheel", Configs.Generic.WHEEL_STEP.getIntegerValue(), shiftStep()));
            tip.add(Lang.t("row.p.ctrl"));
            cells.add(new Cell(Kind.PLAYER, claim.getKey(), x, y, w, h, text, WHITE, tip, -1));
            x += w + GAP * 2;
        }
        int hidden = claims.size() - shown;
        if (hidden > 0 && x + row.getStringWidth("+" + hidden) <= limit) {
            List<String> tip = new ArrayList<>();
            tip.add(Lang.t("row.others"));
            for (int i = shown; i < claims.size(); i++) {
                tip.add(ClientProjectCache.nameOf(claims.get(i).getKey()) + ": " + claims.get(i).getValue());
            }
            cells.add(new Cell(Kind.SUMMARY, null, x, y, row.getStringWidth("+" + hidden), h, "+" + hidden, GRAY, tip, -1));
        }
        return cells;
    }

    private static String label(Entry e, Map.Entry<UUID, Integer> claim, boolean compact) {
        int target = e.targetOf(claim.getKey());
        String count = claim.getValue() + (target > 0 ? "/" + target : "");
        return compact ? count : ClientProjectCache.nameOf(claim.getKey()) + ":" + count;
    }

    private static boolean fitsAll(WidgetBase row, Entry e, List<Map.Entry<UUID, Integer>> claims, boolean compact, int x,
            int limit) {
        int total = x;
        for (Map.Entry<UUID, Integer> claim : claims) {
            total += faceW() + row.getStringWidth(label(e, claim, compact)) + GAP * 2;
        }
        return total - GAP * 2 <= limit;
    }

    // ---- drawing ----------------------------------------------------------------------------

    private static void playClick() {
        if (!Configs.Generic.CLICK_SOUNDS.getBooleanValue()) {
            return;
        }
        MinecraftClient.getInstance().getSoundManager()
                .play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    /** A button drawn from the vanilla widgets texture, with the vanilla hover colour. */
    private static void drawVanillaButton(WidgetBase row, MatrixStack matrices, Cell cell, boolean hover) {
        int v = 46 + (hover ? 2 : 1) * 20;
        int half = cell.w / 2;
        dev.materialshare.McClientCompat.vanillaButton(matrices, cell.x, cell.y, cell.w, cell.h, hover);
        int color = hover ? 0xFFFFFFA0 : 0xFFE0E0E0;
        row.drawCenteredStringWithShadow(cell.x + cell.w / 2, cell.y + (cell.h - 8) / 2, color, cell.text, matrices);
    }

    /** A thin bar under the total: grey track, blue while collecting, green when done. */
    private static void drawProgressBar(MatrixStack matrices, int x, int y, int width, double ratio, boolean done) {
        dev.materialshare.McClientCompat.fill(matrices, x, y, x + width, y + 2, 0xFF3A3A3A);
        int filled = (int) Math.round(width * Math.max(0.0, Math.min(1.0, ratio)));
        if (filled > 0) {
            dev.materialshare.McClientCompat.fill(matrices, x, y, x + filled, y + 2, done ? 0xFF55FF55 : 0xFF55AAFF);
        }
    }

    private static Identifier skinOf(UUID id) {
        MinecraftClient client = MinecraftClient.getInstance();
        PlayerListEntry entry = client.getNetworkHandler() == null ? null
                : client.getNetworkHandler().getPlayerListEntry(id);
        if (entry != null) {
            Identifier texture = dev.materialshare.McClientCompat.entrySkin(entry);
            SEEN_SKINS.put(id, texture);
            return texture;
        }
        MojangProfiles.request(id);
        Identifier fetched = MojangProfiles.skin(id);
        if (fetched != null) {
            return fetched;
        }
        return SEEN_SKINS.getOrDefault(id, dev.materialshare.McClientCompat.defaultSkin(id));
    }

    /** The 8x8 face from the skin, plus the hat layer on top. */
    static void drawFace(MatrixStack matrices, UUID player, int x, int y) {
        dev.materialshare.McClientCompat.useShaderPositionTex();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        Identifier skin = skinOf(player);
        dev.materialshare.McClientCompat.texture(matrices, skin, x, y, FACE, FACE, 8.0F, 8.0F, 8, 8, 64, 64);
        dev.materialshare.McClientCompat.texture(matrices, skin, x, y, FACE, FACE, 40.0F, 8.0F, 8, 8, 64, 64);
        RenderSystem.disableBlend();
    }
}
