package dev.materialshare.client;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;

/**
 * A screen of equally wide buttons in a grid, optionally under a strip of tabs (like Litematica's own menus). All
 * the buttons of all tabs have the width of the longest label, so nothing grows or runs out of its frame in any
 * language; the grid has as many columns as fit and goes onto pages when the screen is too small for all of it.
 */
abstract class GridScreen extends GuiBase implements NoWheelClick {
    private static final int BUTTON_H = 20;
    private static final int ROW_STEP = 24;

    /** One button of the grid. {@code disabledTip} explains why a disabled button cannot be used. */
    record Cell(String label, String tip, Runnable run, boolean enabled, String disabledTip) {
        static Cell of(String label, String tip, Runnable run) {
            return new Cell(label, tip, run, true, "");
        }

        static Cell when(boolean enabled, String label, String tip, Runnable run, String disabledTip) {
            return new Cell(label, tip, run, enabled, disabledTip);
        }
    }

    record Tab(String label, List<Cell> cells) {
    }

    protected int tab;
    protected int page;
    private int pages = 1;
    private int shownPage;
    private int infoY;
    private int tabBarY = -1;
    private int tabBarX;
    private int tabBarW;
    private int tabBarH;
    private final List<int[]> tabUnderline = new ArrayList<>();

    GridScreen(String title, Screen parent, int tab, int page) {
        this.tab = tab;
        this.page = page;
        this.setParent(parent);
        this.setTitle(title);
    }

    abstract List<Tab> tabs();

    /** Text shown above the grid (a summary of what the buttons act on); may be empty. */
    String info() {
        return "";
    }

    abstract GridScreen reopen(int tab, int page);

    private int navW() {
        return Lang.buttonWidth(this, Lang.t("common.prev"), 60);
    }

    @Override
    public void initGui() {
        super.initGui();
        int x = 12;
        int y = 28;
        tabUnderline.clear();
        int backW = Lang.buttonWidth(this, Lang.t("common.back"), 60);
        addButton(new ButtonGeneric(x, y, backW, BUTTON_H, Lang.t("common.back")), (b, mb) -> openGui(getParent()));
        List<Tab> tabs = tabs();
        int top = y + 26;
        if (tabs.size() > 1) {
            int tx = x + backW + 8;
            int ty = y;
            for (int i = 0; i < tabs.size(); i++) {
                int w = Lang.buttonWidth(this, tabs.get(i).label, 50);
                if (tx + w > this.width - 12 && tx > x + backW + 8) {
                    tx = x;
                    ty += ROW_STEP;
                    top = ty + 26;
                }
                int index = i;
                String label = (i == tab ? "§e" : "") + tabs.get(i).label;
                ButtonGeneric button = new ButtonGeneric(tx, ty, w, BUTTON_H, label);
                addButton(button, (b, mb) -> openGui(reopen(index, 0)));
                if (i == tab) {
                    tabUnderline.add(new int[] {tx, ty + BUTTON_H, w});
                }
                tx += w + 4;
            }
        }
        String info = info();
        infoY = top;
        if (!info.isEmpty()) {
            top += UiText.wrappedHeight(info, this.width - 24) + 6;
        }

        List<Cell> cells = tabs.get(Math.min(tab, tabs.size() - 1)).cells;
        int cellW = 100;
        for (Tab t : tabs) {
            for (Cell c : t.cells) {
                cellW = Math.max(cellW, getStringWidth(c.label) + 16);
            }
        }
        cellW = Math.min(cellW, this.width - 24);
        int columns = Math.max(1, (this.width - 24 + 4) / (cellW + 4));
        int rowsFit = Math.max(1, (this.height - top - 34) / ROW_STEP);
        int perPage = rowsFit * columns;
        pages = Math.max(1, (cells.size() + perPage - 1) / perPage);
        int current = Math.min(page, pages - 1);
        shownPage = current;
        for (int i = current * perPage; i < Math.min(cells.size(), (current + 1) * perPage); i++) {
            int slot = i - current * perPage;
            int cx = x + (slot % columns) * (cellW + 4);
            int cy = top + (slot / columns) * ROW_STEP;
            Cell cell = cells.get(i);
            ButtonGeneric button = new ButtonGeneric(cx, cy, cellW, BUTTON_H, cell.label);
            String tip = cell.enabled ? cell.tip : cell.disabledTip;
            if (tip != null && !tip.isEmpty()) {
                button.setHoverStrings(UiText.tip(tip));
            }
            button.setEnabled(cell.enabled);
            addButton(button, (b, mb) -> {
                if (cell.enabled) {
                    cell.run.run();
                }
            });
        }
        if (pages > 1) {
            int navY = this.height - 28;
            if (current > 0) {
                addButton(new ButtonGeneric(x, navY, navW(), BUTTON_H, Lang.t("common.prev")),
                        (b, mb) -> openGui(reopen(tab, current - 1)));
            }
            if (current < pages - 1) {
                addButton(new ButtonGeneric(x + navW() + 8, navY, navW(), BUTTON_H, Lang.t("common.next")),
                        (b, mb) -> openGui(reopen(tab, current + 1)));
            }
        }
    }

    @Override
    protected void drawContents(MatrixStack matrices, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(matrices, mouseX, mouseY, partialTicks);
        String info = info();
        if (!info.isEmpty()) {
            UiText.drawWrapped(matrices, info, 12, infoY, this.width - 24, 0xFFDDDDDD);
        }
        for (int[] u : tabUnderline) {
            net.minecraft.client.gui.DrawableHelper.fill(matrices, u[0], u[1], u[0] + u[2], u[1] + 2, 0xFFFFD866);
        }
        if (pages > 1) {
            drawStringWithShadow(matrices, Lang.t("common.page", shownPage + 1, pages), 12 + 2 * navW() + 16, this.height - 22,
                    0xFFFFFFFF);
        }
    }
}
