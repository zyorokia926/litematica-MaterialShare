package dev.materialshare.client;

import dev.materialshare.common.Entry;
import dev.materialshare.common.Project;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.gui.screen.Screen;

/**
 * The unfinished assignments of a player (you, or the one picked on the stats screen) from every project, so nothing
 * is forgotten when several builds run at once.
 */
class MyAssignmentsScreen extends SearchListScreen<MyAssignmentsScreen.Row> {
    record Row(String project, String itemId, Entry entry, int mine) {
    }

    /** {@code who} is null for yourself. */
    private final UUID who;

    MyAssignmentsScreen(Screen parent, UUID who, int page, String query) {
        super(who == null ? Lang.t("mine.title") : Lang.t("mine.title.of", UiText.escapeFormat(ClientProjectCache.nameOf(who))),
                parent, page, query);
        this.who = who;
    }

    @Override
    List<Row> candidates() {
        UUID player = who != null ? who : RowOverlay.selfId();
        List<Row> rows = new ArrayList<>();
        if (player == null) {
            return rows;
        }
        for (Project p : ClientProjectCache.projects()) {
            // a schematic that is not loaded in Litematica is not shown (its assignments are tied to it)
            if (p.archived() || !ProjectOpener.canOpen(p.name())) {
                continue;
            }
            for (Map.Entry<String, Entry> item : p.items().entrySet()) {
                Entry e = item.getValue();
                Integer mine = e.claims().get(player);
                if (mine != null && e.collectedTotal() < e.required()) {
                    rows.add(new Row(p.name(), item.getKey(), e, mine));
                }
            }
        }
        rows.sort(Comparator.<Row>comparingInt(r -> r.entry().priority() ? 0 : 1)
                .thenComparing(Row::project)
                .thenComparingInt(r -> -(r.entry().required() - r.entry().collectedTotal())));
        return rows;
    }

    @Override
    String nameOf(Row r) {
        return (r.entry().priority() ? "§6★§r " : "") + UiText.itemName(r.itemId()) + "  §7(" + r.project() + ")";
    }

    @Override
    String detailOf(Row r) {
        Entry e = r.entry();
        return Lang.t("mine.detail", r.mine(), e.collectedTotal(), e.required(), e.required() - e.collectedTotal());
    }

    @Override
    List<Act<Row>> actionsFor(Row r) {
        return List.of(new Act<>(Lang.t("mine.open"), Lang.t("overview.open.tip"), x -> ProjectOpener.open(x.project())));
    }

    @Override
    String helpText() {
        return who == null ? Lang.t("mine.help") : Lang.t("mine.help.of");
    }

    @Override
    String emptyText() {
        return Lang.t("mine.empty");
    }

    @Override
    SearchListScreen<Row> reopen(int page, String query) {
        return new MyAssignmentsScreen(getParent(), who, page, query);
    }
}
