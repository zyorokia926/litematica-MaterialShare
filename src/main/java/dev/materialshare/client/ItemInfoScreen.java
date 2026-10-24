package dev.materialshare.client;

import dev.materialshare.common.Entry;
import dev.materialshare.common.Project;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.screen.Screen;

/** Every item of a project with a star to mark it as needed first, and a way into the item's details. */
class ItemInfoScreen extends SearchListScreen<ItemInfoScreen.Row> {
    record Row(String itemId, Entry entry) {
    }

    private final String project;

    ItemInfoScreen(Screen parent, String project, int page, String query) {
        super(Lang.t("iteminfo.title"), parent, page, query);
        this.project = project;
    }

    @Override
    List<Row> candidates() {
        List<Row> rows = new ArrayList<>();
        for (Project p : ClientProjectCache.projects()) {
            if (p.name().equals(project)) {
                for (Map.Entry<String, Entry> item : p.items().entrySet()) {
                    rows.add(new Row(item.getKey(), item.getValue()));
                }
            }
        }
        rows.sort(Comparator.<Row>comparingInt(r -> r.entry().priority() ? 0 : 1)
                .thenComparingInt(r -> -r.entry().required()));
        return rows;
    }

    @Override
    String nameOf(Row r) {
        return (r.entry().priority() ? "§6★§r " : "§8☆§r ") + UiText.itemName(r.itemId());
    }

    @Override
    String detailOf(Row r) {
        Entry e = r.entry();
        String text = Lang.t("iteminfo.detail", e.collectedTotal(), e.required());
        if (!e.substitute().isEmpty()) {
            text += "  ·  " + Lang.t("iteminfo.sub", UiText.escapeFormat(e.substitute()));
        }
        return text;
    }

    @Override
    List<Act<Row>> actionsFor(Row r) {
        return List.of(
                new Act<>(r.entry().priority() ? "§6★" : "☆", Lang.t("iteminfo.priority.tip"),
                        x -> ClientNet.sendItemInfo(project, x.itemId(), !x.entry().priority())),
                new Act<>(Lang.t("iteminfo.detail.button"), Lang.t("iteminfo.detail.tip"),
                        x -> openGui(new ItemDetailScreen(this, project, x.itemId()))),
                new Act<>("§c" + Lang.t("iteminfo.delete"), Lang.t("iteminfo.delete.tip"),
                        x -> ClientNet.sendRemoveItem(project, x.itemId())));
    }

    @Override
    List<Head> headerButtons() {
        int n = ClientProjectCache.removed(project).size();
        return List.of(new Head(Lang.t("iteminfo.deleted", n), Lang.t("iteminfo.deleted.tip"),
                () -> openGui(new DeletedItemsScreen(this, project, 0, ""))));
    }

    @Override
    String helpText() {
        return Lang.t("iteminfo.help");
    }

    @Override
    String emptyText() {
        return Lang.t("iteminfo.empty");
    }

    @Override
    SearchListScreen<Row> reopen(int page, String query) {
        return new ItemInfoScreen(getParent(), project, page, query);
    }
}
