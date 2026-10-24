package dev.materialshare.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.materialshare.common.Breakdown;
import dev.materialshare.common.BreakdownTree;
import dev.materialshare.common.Entry;
import dev.materialshare.common.Project;
import dev.materialshare.common.Stacks;
import dev.materialshare.common.Search;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * The raw materials of a project drawn as a map: the project in the middle of the left edge, its items next to it,
 * and from each item the steps of its crafting chain down to the raw materials. Every kind of node has its own colour
 * (and the buttons have a colour of their own, too), so what is what can be seen at a glance. The map moves with the
 * mouse (drag, wheel).
 */
class BreakdownScreen extends GuiBase implements NoWheelClick {
    /** Counted from what is still missing (true), or from everything the project needs. */
    static boolean remainingOnly = true;
    /** Leave out the steps in between (planks, ...): the item goes straight to its raw materials. */
    static boolean flat;

    private static final int ROOT_COLOR = 0xFFE0A030;
    private static final int CRAFT_COLOR = 0xFF4A90E2;
    private static final int MID_COLOR = 0xFF37B7C9;
    private static final int RAW_COLOR = 0xFF4CAF50;
    private static final int MODE_COLOR = 0xFFFFD866;
    private static final int FLAT_COLOR = 0xFFD070E0;
    private static final int VIEW_COLOR = 0xFF55DDFF;
    private static final int RESET_COLOR = 0xFFAAAAAA;
    private static final int NODE_H = 18;
    private static final int ROW_STEP = 22;
    private static final int GAP_X = 18;
    private static final int ICON = 16;
    private static final int PAD = 6;

    private enum Kind { ROOT, CRAFT, MID, RAW }

    private static final class Placed {
        final Kind kind;
        final String itemId;
        final String name;
        /** The needed amount, as it is drawn (right in the box). */
        final String count;
        final int countW;
        final BreakdownTree.Node source;
        final List<Placed> kids = new ArrayList<>();
        int x;
        int y;
        int w;

        Placed(Kind kind, String itemId, String name, String count, BreakdownTree.Node source) {
            this.kind = kind;
            this.itemId = itemId;
            this.name = name;
            this.count = count;
            this.countW = MinecraftClient.getInstance().textRenderer.getWidth(count);
            this.source = source;
        }

        int color() {
            return switch (kind) {
                case ROOT -> ROOT_COLOR;
                case CRAFT -> CRAFT_COLOR;
                case MID -> MID_COLOR;
                case RAW -> RAW_COLOR;
            };
        }
    }

    private final String project;
    private final String query;
    private int dragBar;
    private final int shownVersion = ClientProjectCache.version();
    private static int panX;
    private static int panY;
    private static float zoom = 1.0F;
    private static final float ZOOM_MIN = 0.3F;
    private static final float ZOOM_MAX = 2.5F;
    private Placed root;
    private int contentW;
    private int contentH;
    private int areaX;
    private int areaY;
    private int areaW;
    private int areaH;
    private final List<int[]> underlines = new ArrayList<>();
    private boolean empty;

    BreakdownScreen(Screen parent, String project, int page, String query) {
        this.project = project;
        this.query = query == null ? "" : query;
        this.setParent(parent);
        this.setTitle(Lang.t("breakdown.title", UiText.escapeFormat(project)));
    }

    private Map<String, Integer> wanted() {
        Map<String, Integer> wanted = new LinkedHashMap<>();
        for (Project p : ClientProjectCache.projects()) {
            if (p.name().equals(project)) {
                for (Map.Entry<String, Entry> item : p.items().entrySet()) {
                    Entry e = item.getValue();
                    int amount = remainingOnly ? e.required() - e.collectedTotal() : e.required();
                    if (amount > 0) {
                        wanted.put(item.getKey(), amount);
                    }
                }
            }
        }
        return wanted;
    }

    /** "×200" and, for what stacks, how many stacks that is. The colour codes keep the amount easy to find. */
    private static String countText(String itemId, int amount) {
        String text = "§e×" + amount;
        String stacks = Stacks.format(amount, RecipeAdapter.stackSize(itemId), Lang.TRANSLATOR);
        return stacks.isEmpty() ? text : text + " §7(" + stacks + ")";
    }

    @Override
    public void initGui() {
        super.initGui();
        underlines.clear();
        int x = 12;
        int y = 28;
        int backW = Lang.buttonWidth(this, Lang.t("common.back"), 60);
        addButton(new ButtonGeneric(x, y, backW, 20, Lang.t("common.back")), (b, mb) -> openGui(getParent()));
        x += backW + 8;
        String[] labels = {
            "§e" + (remainingOnly ? Lang.t("breakdown.mode.remaining") : Lang.t("breakdown.mode.all")),
            "§d" + (flat ? Lang.t("breakdown.map.flat.on") : Lang.t("breakdown.map.flat.off")),
            "§b" + Lang.t("breakdown.map.list"),
            "§7" + Lang.t("breakdown.map.reset"), "§a＋", "§c－"};
        String[] tips = {Lang.t("breakdown.mode.tip"), Lang.t("breakdown.map.flat.tip"), Lang.t("breakdown.map.list.tip"),
            Lang.t("breakdown.map.reset.tip"), Lang.t("breakdown.map.zoomin.tip"), Lang.t("breakdown.map.zoomout.tip")};
        int[] colors = {MODE_COLOR, FLAT_COLOR, VIEW_COLOR, RESET_COLOR, 0xFF55FF55, 0xFFFF5555};
        Runnable[] actions = {
            () -> {
                remainingOnly = !remainingOnly;
                openGui(new BreakdownScreen(getParent(), project, 0, query));
            },
            () -> {
                flat = !flat;
                openGui(new BreakdownScreen(getParent(), project, 0, query));
            },
            () -> openGui(new BreakdownListScreen(getParent(), project, 0, query)),
            () -> {
                panX = 0;
                panY = 0;
                zoom = 1.0F;
            },
            () -> zoomBy(1.25F, areaX + areaW / 2.0, areaY + areaH / 2.0),
            () -> zoomBy(1 / 1.25F, areaX + areaW / 2.0, areaY + areaH / 2.0)};
        int rowY = y;
        for (int i = 0; i < labels.length; i++) {
            int w = i >= 4 ? 24 : Lang.buttonWidth(this, labels[i], 40);
            if (x + w > this.width - 12 && x > 12) {
                x = 12;
                rowY += 26;
            }
            ButtonGeneric button = new ButtonGeneric(x, rowY, w, 20, labels[i]);
            button.setHoverStrings(UiText.tip(tips[i]));
            Runnable run = actions[i];
            addButton(button, (b, mb) -> run.run());
            underlines.add(new int[] {x, rowY + 20, w, colors[i]});
            x += w + 4;
        }
        int searchW = Math.max(90, Math.min(170, this.width - 12 - x));
        if (x + 90 > this.width - 12) {
            x = 12;
            rowY += 26;
            searchW = Math.max(90, Math.min(170, this.width - 24));
        }
        GuiTextFieldGeneric search = new GuiTextFieldGeneric(x, rowY, searchW, 20, this.textRenderer);
        search.setMaxLength(32);
        search.setText(query);
        search.setFocused(!query.isEmpty());
        search.setCursorToEnd();
        addTextField(search, field -> {
            if (!field.getText().equals(query)) {
                openGui(new BreakdownScreen(getParent(), project, 0, field.getText()));
            }
            return false;
        });
        underlines.add(new int[] {x, rowY + 20, searchW, 0xFF8888FF});
        areaX = 8;
        areaY = rowY + 46; // room for the legend
        areaW = this.width - 16;
        areaH = Math.max(40, this.height - areaY - 8);
        build();
        clampPan();
    }

    private void build() {
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        Map<String, Integer> wanted = wanted();
        Breakdown.Recipes recipes = RecipeAdapter.recipes(wanted.keySet());
        root = new Placed(Kind.ROOT, null, project, "§e" + wanted.size() + Lang.t("breakdown.node.kinds"), null);
        List<BreakdownTree.Node> trees = new ArrayList<>();
        for (Map.Entry<String, Integer> item : wanted.entrySet()) {
            BreakdownTree.Node tree = BreakdownTree.build(item.getKey(), item.getValue(), recipes, flat);
            if (matches(tree)) {
                trees.add(tree);
            }
        }
        // with a search word, the best matches come first
        if (!query.isBlank()) {
            trees.sort((a, b) -> Integer.compare(bestScore(b), bestScore(a)));
        }
        for (BreakdownTree.Node tree : trees) {
            root.kids.add(place(tree, 1, true));
        }
        noMatch = root.kids.isEmpty() && !wanted.isEmpty() && !query.isBlank();
        empty = root.kids.isEmpty();
        int maxDepth = 0;
        for (Placed p : root.kids) {
            maxDepth = Math.max(maxDepth, p.source.depth() + 1);
        }
        // widths per column, then x positions
        int[] colW = new int[maxDepth + 2];
        colW[0] = 18 + font.getWidth(UiText.fit(project, 110)) + root.countW;
        measure(root, 0, colW, font);
        int[] colX = new int[colW.length];
        colX[0] = PAD;
        for (int i = 1; i < colW.length; i++) {
            colX[i] = colX[i - 1] + colW[i - 1] + GAP_X;
        }
        int[] row = {0};
        layout(root, 0, colX, colW, row);
        contentW = colX[colW.length - 1] + colW[colW.length - 1] + PAD;
        contentH = Math.max(1, row[0]) * ROW_STEP + PAD;
    }

    private boolean noMatch;

    private int bestScore(BreakdownTree.Node node) {
        int best = Search.score(query, UiText.itemName(node.item()));
        for (BreakdownTree.Node child : node.children()) {
            best = Math.max(best, bestScore(child));
        }
        return best;
    }

    /** The item or anything it is made of shares a character with the search word (an empty word keeps everything). */
    private boolean matches(BreakdownTree.Node node) {
        return query.isBlank() || bestScore(node) > 0;
    }

    private Placed place(BreakdownTree.Node node, int depth, boolean top) {
        Kind kind = top ? (node.raw() ? Kind.RAW : Kind.CRAFT) : (node.raw() ? Kind.RAW : Kind.MID);
        Placed placed = new Placed(kind, node.item(), UiText.itemName(node.item()), countText(node.item(), node.amount()), node);
        for (BreakdownTree.Node child : node.children()) {
            placed.kids.add(place(child, depth + 1, false));
        }
        return placed;
    }

    private void measure(Placed p, int depth, int[] colW, TextRenderer font) {
        if (p.kind != Kind.ROOT) {
            p.w = ICON + 18 + Math.min(font.getWidth(p.name), 140) + p.countW;
            colW[depth] = Math.max(colW[depth], p.w);
        }
        for (Placed kid : p.kids) {
            measure(kid, depth + 1, colW, font);
        }
    }

    private void layout(Placed p, int depth, int[] colX, int[] colW, int[] row) {
        p.x = colX[depth];
        if (p.kind == Kind.ROOT) {
            p.w = colW[0];
        }
        if (p.kids.isEmpty()) {
            p.y = PAD + row[0] * ROW_STEP;
            row[0]++;
            return;
        }
        for (Placed kid : p.kids) {
            layout(kid, depth + 1, colX, colW, row);
        }
        p.y = (p.kids.get(0).y + p.kids.get(p.kids.size() - 1).y) / 2;
    }

    private int scaledW() {
        return Math.round(contentW * zoom);
    }

    private int scaledH() {
        return Math.round(contentH * zoom);
    }

    private void clampPan() {
        panX = Math.max(Math.min(0, areaW - scaledW()), Math.min(0, panX));
        panY = Math.max(Math.min(0, areaH - scaledH()), Math.min(0, panY));
    }

    /** Zooms by a factor, keeping the point (fx, fy) of the screen where it is. */
    private void zoomBy(float factor, double fx, double fy) {
        float next = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, zoom * factor));
        if (next == zoom) {
            return;
        }
        double cx = (fx - areaX - panX) / zoom; // the map point under (fx, fy)
        double cy = (fy - areaY - panY) / zoom;
        zoom = next;
        panX = (int) Math.round(fx - areaX - cx * zoom);
        panY = (int) Math.round(fy - areaY - cy * zoom);
        clampPan();
    }

    private boolean inArea(double mx, double my) {
        return mx >= areaX && mx < areaX + areaW && my >= areaY && my < areaY + areaH;
    }

    @Override
    public boolean onMouseScrolled(int mouseX, int mouseY, double amount) {
        if (inArea(mouseX, mouseY)) {
            // the usual way: wheel = up/down, Shift + wheel = sideways, Ctrl + wheel = zoom
            if (hasControlDown()) {
                zoomBy(amount > 0 ? 1.15F : 1 / 1.15F, mouseX, mouseY);
            } else if (hasShiftDown()) {
                panX += (int) Math.signum(amount) * 40;
                clampPan();
            } else {
                panY += (int) Math.signum(amount) * 30;
                clampPan();
            }
            return true;
        }
        return super.onMouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0 && dragBar != 0) {
            dragTo((int) mouseX, (int) mouseY);
            return true;
        }
        if (button == 0 && inArea(mouseX, mouseY)) {
            panX += (int) Math.round(deltaX);
            panY += (int) Math.round(deltaY);
            clampPan();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public void tick() {
        super.tick();
        if (shownVersion != ClientProjectCache.version()) {
            openGui(new BreakdownScreen(getParent(), project, 0, query));
        }
    }

    @Override
    protected void drawContents(MatrixStack matrices, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(matrices, mouseX, mouseY, partialTicks);
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer font = client.textRenderer;
        for (int[] u : underlines) {
            DrawableHelper.fill(matrices, u[0], u[1], u[0] + u[2], u[1] + 2, u[3]);
        }
        drawLegend(matrices, font);
        DrawableHelper.fill(matrices, areaX, areaY, areaX + areaW, areaY + areaH, 0x66000000);
        if (empty) {
            drawStringWithShadow(matrices, UiText.fit(noMatch ? Lang.t("list.nomatch") : Lang.t("breakdown.empty"), areaW - 16), areaX + 8, areaY + 8, 0xFFFFFFFF);
            return;
        }
        double scale = client.getWindow().getScaleFactor();
        RenderSystem.enableScissor((int) (areaX * scale), (int) (client.getWindow().getFramebufferHeight() - (areaY + areaH) * scale),
                (int) (areaW * scale), (int) (areaH * scale));
        // The map is drawn in its own units, scaled through the model view matrix: boxes, text and item icons all go
        // through it, so one transform moves and scales everything (the matrix stack given here is left alone).
        var modelView = RenderSystem.getModelViewStack();
        modelView.push();
        modelView.translate(areaX + panX, areaY + panY, 0);
        modelView.scale(zoom, zoom, 1.0F);
        RenderSystem.applyModelViewMatrix();
        MatrixStack mapStack = new MatrixStack();
        hoverOn = inArea(mouseX, mouseY);
        hoverX = (mouseX - areaX - panX) / zoom;
        hoverY = (mouseY - areaY - panY) / zoom;
        drawEdges(mapStack, root, 0, 0);
        Placed[] hovered = {null};
        drawNodes(mapStack, font, root, 0, 0, hovered);
        modelView.pop();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.disableScissor();
        drawScrollbars(matrices);
        if (hovered[0] != null && inArea(mouseX, mouseY)) {
            tooltip(matrices, hovered[0], mouseX, mouseY);
        }
    }

    private static final int BAR = 6;

    private boolean tallerThanArea() {
        return scaledH() > areaH;
    }

    private boolean widerThanArea() {
        return scaledW() > areaW;
    }

    private int thumbSize(int view, int content) {
        return Math.max(14, view * view / Math.max(1, content));
    }

    private void drawScrollbars(MatrixStack matrices) {
        if (tallerThanArea()) {
            int x = areaX + areaW - BAR;
            DrawableHelper.fill(matrices, x, areaY, x + BAR, areaY + areaH, 0x66000000);
            int thumb = thumbSize(areaH, scaledH());
            int y = areaY + (int) ((long) -panY * (areaH - thumb) / Math.max(1, scaledH() - areaH));
            DrawableHelper.fill(matrices, x + 1, y, x + BAR - 1, y + thumb, dragBar == 1 ? 0xFFFFFFFF : 0xFFAAAAAA);
        }
        if (widerThanArea()) {
            int y = areaY + areaH - BAR;
            DrawableHelper.fill(matrices, areaX, y, areaX + areaW, y + BAR, 0x66000000);
            int thumb = thumbSize(areaW, scaledW());
            int x = areaX + (int) ((long) -panX * (areaW - thumb) / Math.max(1, scaledW() - areaW));
            DrawableHelper.fill(matrices, x, y + 1, x + thumb, y + BAR - 1, dragBar == 2 ? 0xFFFFFFFF : 0xFFAAAAAA);
        }
    }

    /** Puts the map where the dragged scroll bar thumb is. */
    private void dragTo(int mx, int my) {
        if (dragBar == 1) {
            int thumb = thumbSize(areaH, scaledH());
            double ratio = (my - areaY - thumb / 2.0) / Math.max(1, areaH - thumb);
            panY = -(int) Math.round(Math.max(0, Math.min(1, ratio)) * (scaledH() - areaH));
        } else if (dragBar == 2) {
            int thumb = thumbSize(areaW, scaledW());
            double ratio = (mx - areaX - thumb / 2.0) / Math.max(1, areaW - thumb);
            panX = -(int) Math.round(Math.max(0, Math.min(1, ratio)) * (scaledW() - areaW));
        }
        clampPan();
    }

    @Override
    public boolean onMouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0 && inArea(mouseX, mouseY)) {
            if (tallerThanArea() && mouseX >= areaX + areaW - BAR - 2) {
                dragBar = 1;
                dragTo(mouseX, mouseY);
                return true;
            }
            if (widerThanArea() && mouseY >= areaY + areaH - BAR - 2) {
                dragBar = 2;
                dragTo(mouseX, mouseY);
                return true;
            }
        }
        return super.onMouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public boolean onMouseReleased(int mouseX, int mouseY, int mouseButton) {
        dragBar = 0;
        return super.onMouseReleased(mouseX, mouseY, mouseButton);
    }

    private void drawLegend(MatrixStack matrices, TextRenderer font) {
        int y = areaY - 14;
        int x = 12;
        Object[][] items = {{ROOT_COLOR, Lang.t("breakdown.legend.project")}, {CRAFT_COLOR, Lang.t("breakdown.legend.craft")},
            {MID_COLOR, Lang.t("breakdown.legend.mid")}, {RAW_COLOR, Lang.t("breakdown.legend.raw")}};
        for (Object[] item : items) {
            String text = (String) item[1];
            int w = 10 + font.getWidth(text) + 10;
            if (x + w > this.width - 8) {
                break;
            }
            DrawableHelper.fill(matrices, x, y + 1, x + 8, y + 9, (int) item[0]);
            font.drawWithShadow(matrices, text, x + 11, y, 0xFFDDDDDD);
            x += w;
        }
    }

    private void drawEdges(MatrixStack matrices, Placed p, int ox, int oy) {
        for (Placed kid : p.kids) {
            int x1 = ox + p.x + p.w;
            int y1 = oy + p.y + NODE_H / 2;
            int x2 = ox + kid.x;
            int y2 = oy + kid.y + NODE_H / 2;
            int mid = x1 + (x2 - x1) / 2;
            int color = kid.color() & 0xCCFFFFFF;
            DrawableHelper.fill(matrices, x1, y1, mid + 1, y1 + 1, color);
            DrawableHelper.fill(matrices, mid, Math.min(y1, y2), mid + 1, Math.max(y1, y2) + 1, color);
            DrawableHelper.fill(matrices, mid, y2, x2, y2 + 1, color);
            drawEdges(matrices, kid, ox, oy);
        }
    }

    private boolean hoverOn;
    private double hoverX;
    private double hoverY;

    private void drawNodes(MatrixStack matrices, TextRenderer font, Placed p, int ox, int oy, Placed[] hovered) {
        int x = ox + p.x;
        int y = oy + p.y;
        boolean over = hoverOn && hoverX >= x && hoverX < x + p.w && hoverY >= y && hoverY < y + NODE_H;
        if (over) {
            hovered[0] = p;
        }
        int color = p.color();
        DrawableHelper.fill(matrices, x, y, x + p.w, y + NODE_H, color);
        DrawableHelper.fill(matrices, x + 1, y + 1, x + p.w - 1, y + NODE_H - 1, over ? 0xFF2A3345 : 0xF0151A22);
        DrawableHelper.fill(matrices, x + 1, y + 1, x + 4, y + NODE_H - 1, color);
        int textX = x + 6;
        if (p.kind == Kind.ROOT) {
            font.drawWithShadow(matrices, UiText.fit(p.name, p.w - p.countW - 18), textX, y + 5, 0xFFFFE9B0);
            font.drawWithShadow(matrices, p.count, x + p.w - p.countW - 6, y + 5, 0xFFFFFFFF);
        } else {
            Identifier id = Identifier.tryParse(p.itemId);
            if (id != null && Registries.ITEM.containsId(id)) {
                // the icon is drawn smaller than the box is high, so it stays inside the frame
                client().getItemRenderer().renderGuiItemIcon(matrices, new ItemStack(Registries.ITEM.get(id)), textX, y + 1);
            }
            font.drawWithShadow(matrices, UiText.fit(p.name, p.w - p.countW - ICON - 18), textX + ICON + 2, y + 5, 0xFFFFFFFF);
            font.drawWithShadow(matrices, p.count, x + p.w - p.countW - 6, y + 5, 0xFFFFFFFF);
        }
        for (Placed kid : p.kids) {
            drawNodes(matrices, font, kid, ox, oy, hovered);
        }
    }

    private static MinecraftClient client() {
        return MinecraftClient.getInstance();
    }

    private void tooltip(MatrixStack matrices, Placed p, int mx, int my) {
        List<Text> lines = new ArrayList<>();
        if (p.kind == Kind.ROOT) {
            lines.add(Text.literal("§6" + p.name));
            lines.add(Text.literal(Lang.t("breakdown.node.items", p.kids.size())));
        } else {
            BreakdownTree.Node n = p.source;
            lines.add(Text.literal("§f" + UiText.itemName(p.itemId)));
            lines.add(Text.literal(Lang.t("breakdown.node.need", n.amount())));
            int stack = RecipeAdapter.stackSize(p.itemId);
            String stacks = Stacks.format(n.amount(), stack, Lang.TRANSLATOR);
            if (!stacks.isEmpty()) {
                lines.add(Text.literal("§7" + stacks));
            }
            if (n.raw()) {
                lines.add(Text.literal("§a" + Lang.t("breakdown.node.raw")));
            } else {
                lines.add(Text.literal("§b" + Lang.t("breakdown.node.crafts", n.crafts())));
                if (n.leftover() > 0) {
                    lines.add(Text.literal("§7" + Lang.t("breakdown.node.leftover", n.leftover())));
                }
            }
        }
        if (p.kind != Kind.ROOT) {
            for (dev.materialshare.common.Packets.Offer offer : ClientProjectCache.surplusOf(this.project, p.itemId)) {
                lines.add(Text.literal("§d" + Lang.t("row.surplus", ClientProjectCache.nameOf(offer.player()), offer.count())));
            }
        }
        renderOrderedTooltip(matrices, UiText.tipLines(lines), mx, my);
    }
}
