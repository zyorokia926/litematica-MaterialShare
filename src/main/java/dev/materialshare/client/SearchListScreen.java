package dev.materialshare.client;

import dev.materialshare.common.Search;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;

/**
 * A page of entries with a search box on top: the archive and the trash. An entry is offered when its name shares even
 * one character with what is typed (best matches first). Each entry shows its name and a detail line, with its own
 * buttons; when the screen is too narrow for them beside the text they go below it, so nothing overlaps or runs out of
 * its frame.
 */
abstract class SearchListScreen<T> extends GuiBase implements NoWheelClick {
    private static final int LINE_H = 11;
    private static final int BUTTON_H = 20;

    record Act<T>(String label, String tip, Consumer<T> run) {
    }

    private record RowText(String name, String detail, int x, int y, int width, java.util.UUID face, Object entry, int h) {
    }

    /** A button of the strip under the search box (not tied to one entry). */
    record Head(String label, String tip, Runnable run) {
    }

    protected final String query;
    protected final int page;
    private final int shownVersion = ClientProjectCache.version();
    private final List<RowText> rows = new ArrayList<>();
    private int pages = 1;
    private int shownPage;
    private int helpY = 53;
    private int emptyY = 78;
    private boolean nothing;

    SearchListScreen(String title, Screen parent, int page, String query) {
        this.page = page;
        this.query = query;
        this.setParent(parent);
        this.setTitle(title);
    }

    /** Every entry of this screen (before searching), in the order to show them. */
    abstract List<T> candidates();

    abstract String nameOf(T entry);

    abstract String detailOf(T entry);

    abstract List<Act<T>> actionsFor(T entry);

    /** Buttons for the whole screen, shown under the search box. */
    List<Head> headerButtons() {
        return List.of();
    }

    /** The player whose face is drawn in front of the entry, or null. */
    java.util.UUID faceOf(T entry) {
        return null;
    }

    abstract String helpText();

    abstract String emptyText();

    abstract SearchListScreen<T> reopen(int page, String query);

    private boolean showHelp() {
        return this.height >= 200;
    }

    private int navW() {
        return Lang.buttonWidth(this, Lang.t("common.prev"), 60);
    }

    @Override
    public void initGui() {
        super.initGui();
        int x = 12;
        int y = 28;
        int backW = Lang.buttonWidth(this, Lang.t("common.back"), 60);
        addButton(new ButtonGeneric(x, y, backW, BUTTON_H, Lang.t("common.back")), (b, mb) -> openGui(getParent()));

        GuiTextFieldGeneric search = new GuiTextFieldGeneric(x + backW + 8, y, Math.max(80, Math.min(260, this.width - backW - 32)),
                BUTTON_H, this.textRenderer);
        search.setMaxLength(64);
        search.setText(query);
        search.setFocused(true);
        search.setCursorToEnd();
        addTextField(search, field -> {
            if (!field.getText().equals(query)) {
                openGui(reopen(0, field.getText()));
            }
            return false;
        });

        helpY = 53;
        int hx = x;
        int hy = 54;
        List<Head> heads = headerButtons();
        for (Head head : heads) {
            int w = Lang.buttonWidth(this, head.label, 40);
            if (hx + w > this.width - 12 && hx > x) {
                hx = x;
                hy += 24;
            }
            ButtonGeneric button = new ButtonGeneric(hx, hy, w, BUTTON_H, head.label);
            if (head.tip != null && !head.tip.isEmpty()) {
                button.setHoverStrings(UiText.tip(head.tip));
            }
            addButton(button, (b, mb) -> head.run.run());
            hx += w + 4;
        }
        if (!heads.isEmpty()) {
            helpY = hy + 26;
        }
        int helpH = showHelp() ? UiText.wrappedHeight(helpText(), this.width - 24) : 0;
        emptyY = helpY + helpH + 8;
        int top = emptyY;

        List<T> shown = Search.filter(query, candidates(), this::nameOf);
        nothing = shown.isEmpty();
        rows.clear();
        if (nothing) {
            top += 14;
        }

        // the buttons of the widest entry decide whether they fit beside the text
        int buttonsW = 0;
        for (T entry : shown) {
            int w = 0;
            for (Act<T> act : actionsFor(entry)) {
                w += Lang.buttonWidth(this, act.label, 40) + 4;
            }
            buttonsW = Math.max(buttonsW, w);
        }
        int detailW = 0;
        for (T entry : shown) {
            detailW = Math.max(detailW, textRenderer.getWidth(detailOf(entry)));
        }
        boolean stacked = this.width - 24 - buttonsW < Math.max(130, Math.min(detailW, 260));
        int rowH = stacked ? LINE_H * 2 + BUTTON_H + 10 : Math.max(LINE_H * 2, BUTTON_H) + 8;

        int perPage = Math.max(1, (this.height - top - 40) / rowH);
        pages = Math.max(1, (shown.size() + perPage - 1) / perPage);
        int current = Math.min(page, pages - 1);
        shownPage = current;
        int rowY = top;
        for (int i = current * perPage; i < Math.min(shown.size(), (current + 1) * perPage); i++) {
            T entry = shown.get(i);
            List<Act<T>> acts = actionsFor(entry);
            int textW = stacked ? this.width - 24 : this.width - 24 - buttonsW - 6;
            java.util.UUID face = faceOf(entry);
            rows.add(new RowText(nameOf(entry), detailOf(entry), x + (face == null ? 0 : 12), rowY,
                    textW - (face == null ? 0 : 12), face, entry, rowH));
            int bx = stacked ? x : this.width - 12 - actionsWidth(acts);
            int by = stacked ? rowY + LINE_H * 2 + 2 : rowY + 1;
            for (Act<T> act : acts) {
                int w = Lang.buttonWidth(this, act.label, 40);
                ButtonGeneric button = new ButtonGeneric(bx, by, w, BUTTON_H, act.label);
                if (act.tip != null && !act.tip.isEmpty()) {
                    button.setHoverStrings(UiText.tip(act.tip));
                }
                addButton(button, (b, mb) -> act.run.accept(entry));
                bx += w + 4;
            }
            rowY += rowH;
        }
        if (pages > 1) {
            int navY = this.height - 28;
            if (current > 0) {
                addButton(new ButtonGeneric(x, navY, navW(), BUTTON_H, Lang.t("common.prev")),
                        (b, mb) -> openGui(reopen(current - 1, query)));
            }
            if (current < pages - 1) {
                addButton(new ButtonGeneric(x + navW() + 8, navY, navW(), BUTTON_H, Lang.t("common.next")),
                        (b, mb) -> openGui(reopen(current + 1, query)));
            }
        }
    }

    private int actionsWidth(List<Act<T>> acts) {
        int w = 0;
        for (Act<T> act : acts) {
            w += Lang.buttonWidth(this, act.label, 40) + 4;
        }
        return Math.max(0, w - 4);
    }

    /** The mouse wheel over a row (same steps as on the material list); true when the screen used it. */
    boolean rowScrolled(T entry, double amount) {
        return false;
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean onMouseScrolled(int mouseX, int mouseY, double mouseWheelDelta) {
        for (RowText row : rows) {
            if (mouseY >= row.y - 2 && mouseY < row.y + row.h && mouseX >= 12 && mouseX < this.width - 12
                    && row.entry != null && rowScrolled((T) row.entry, mouseWheelDelta)) {
                return true;
            }
        }
        return super.onMouseScrolled(mouseX, mouseY, mouseWheelDelta);
    }

    @Override
    protected void drawContents(MatrixStack matrices, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(matrices, mouseX, mouseY, partialTicks);
        if (showHelp()) {
            UiText.drawWrapped(matrices, helpText(), 12, helpY, this.width - 24, 0xFFAAAAAA);
        }
        if (nothing) {
            drawStringWithShadow(matrices, UiText.fit(query.isBlank() ? emptyText() : Lang.t("list.nomatch"), this.width - 24),
                    12, emptyY, 0xFFFFFFFF);
        }
        for (RowText row : rows) {
            if (row.face != null) {
                RowOverlay.drawFace(matrices, row.face, row.x - 12, row.y + 4);
            }
            drawStringWithShadow(matrices, UiText.fit(row.name, row.width), row.x, row.y + 1, 0xFFFFFFFF);
            drawStringWithShadow(matrices, UiText.fit(row.detail, row.width), row.x, row.y + 1 + LINE_H, 0xFFAAAAAA);
        }
        if (pages > 1) {
            drawStringWithShadow(matrices, Lang.t("common.page", shownPage + 1, pages), 12 + 2 * navW() + 16, this.height - 22,
                    0xFFFFFFFF);
        }
    }

    /** Re-opens itself (keeping the search text) when projects change on the server. */
    @Override
    public void tick() {
        super.tick();
        if (shownVersion != ClientProjectCache.version()) {
            openGui(reopen(page, query));
        }
    }
}
