package dev.materialshare.client;

import dev.materialshare.common.History;
import dev.materialshare.common.HistoryText;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;

/** Who changed what in the project, newest first (the server keeps the last 100 edits). */
public class HistoryScreen extends GuiBase implements NoWheelClick {
    private static final int LINE_H = 12;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM/dd HH:mm").withZone(ZoneId.systemDefault());

    private final String project;
    private final int page;
    private final String query;
    private List<History.Event> shown = List.of();
    private int listTop = 58;
    private int searchBottom;
    private int shownVersion = ClientProjectCache.historyVersion();
    private int perPage = 10;
    private int pages = 1;
    private int shownPage;

    private int navW() {
        return Lang.buttonWidth(this, Lang.t("common.prev"), 60);
    }

    public HistoryScreen(Screen parent, String project, int page, boolean request) {
        this(parent, project, page, request, "");
    }

    public HistoryScreen(Screen parent, String project, int page, boolean request, String query) {
        this.project = project;
        this.page = page;
        this.query = query == null ? "" : query;
        this.setParent(parent);
        this.setTitle(Lang.t("hist.title", project));
        if (request) {
            ClientNet.requestHistory(project);
        }
    }

    /** The saved edits that fit the search words (all of them when the search box is empty). */
    private List<History.Event> filtered() {
        List<History.Event> all = ClientProjectCache.historyOf(project);
        if (query.isBlank()) {
            return all;
        }
        List<History.Event> result = new java.util.ArrayList<>();
        for (History.Event e : all) {
            if (HistoryText.matches(e, query, ClientProjectCache::nameOf, UiText::itemName, Lang.TRANSLATOR)) {
                result.add(e);
            }
        }
        return result;
    }

    @Override
    public void initGui() {
        super.initGui();
        int backW = Lang.buttonWidth(this, Lang.t("common.back"), 60);
        addButton(new ButtonGeneric(12, 28, backW, 20, Lang.t("common.back")), (b, mb) -> openGui(getParent()));
        GuiTextFieldGeneric search = new GuiTextFieldGeneric(12 + backW + 8, 28, Math.max(80, Math.min(260, this.width - backW - 32)), 20,
                this.textRenderer);
        search.setMaxLength(48);
        search.setText(query);
        search.setFocused(true);
        search.setCursorToEnd();
        addTextField(search, field -> {
            if (!field.getText().equals(query)) {
                openGui(new HistoryScreen(getParent(), project, 0, false, field.getText()));
            }
            return false;
        });
        shown = filtered();
        // the explanation is dropped on a very small screen, so the lines keep their room
        listTop = this.height >= 200 ? 53 + UiText.wrappedHeight(Lang.t("hist.search.help"), this.width - 24) + 6 : 58;
        perPage = Math.max(1, (this.height - listTop - 32) / LINE_H);
        pages = Math.max(1, (shown.size() + perPage - 1) / perPage);
        int current = Math.min(page, pages - 1);
        shownPage = current;
        if (pages > 1) {
            int navY = this.height - 28;
            if (current > 0) {
                addButton(new ButtonGeneric(12, navY, navW(), 20, Lang.t("common.prev")),
                        (b, mb) -> openGui(new HistoryScreen(getParent(), project, current - 1, false, query)));
            }
            if (current < pages - 1) {
                addButton(new ButtonGeneric(12 + navW() + 8, navY, navW(), 20, Lang.t("common.next")),
                        (b, mb) -> openGui(new HistoryScreen(getParent(), project, current + 1, false, query)));
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (shownVersion != ClientProjectCache.historyVersion()) {
            openGui(new HistoryScreen(getParent(), project, page, false, query));
        }
    }

    @Override
    protected void drawContents(MatrixStack matrices, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(matrices, mouseX, mouseY, partialTicks);
        if (this.height >= 200) {
            UiText.drawWrapped(matrices, Lang.t("hist.search.help"), 12, 53, this.width - 24, 0xFFAAAAAA);
        }
        if (shown.isEmpty()) {
            boolean searching = !query.isBlank() && !ClientProjectCache.historyOf(project).isEmpty();
            drawStringWithShadow(matrices, UiText.fit(searching ? Lang.t("hist.nomatch") : Lang.t("hist.empty"), this.width - 24), 12,
                    listTop, searching ? 0xFFFFFFFF : 0xFFAAAAAA);
            return;
        }
        int y = listTop;
        for (int i = shownPage * perPage; i < Math.min(shown.size(), (shownPage + 1) * perPage); i++) {
            History.Event e = shown.get(i);
            String line = "§7" + TIME.format(Instant.ofEpochMilli(e.time())) + "§r  "
                    + HistoryText.describe(e, ClientProjectCache::nameOf, UiText::itemName, Lang.TRANSLATOR);
            drawStringWithShadow(matrices, UiText.fit(line, this.width - 24), 12, y, 0xFFFFFFFF);
            y += LINE_H;
        }
        if (pages > 1) {
            drawStringWithShadow(matrices, Lang.t("common.page", shownPage + 1, pages), 12 + 2 * navW() + 16, this.height - 22,
                    0xFFFFFFFF);
        }
    }
}
