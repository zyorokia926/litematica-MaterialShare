package dev.materialshare.client;

import dev.materialshare.common.Project;
import dev.materialshare.common.Totals;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.screen.Screen;

/** What you still have to collect, per material, over all your schematics: one trip to the mine for all of them. */
class MyTotalsScreen extends SearchListScreen<Totals.Item> {
    MyTotalsScreen(Screen parent, int page, String query) {
        super(Lang.t("totals.title"), parent, page, query);
    }

    @Override
    List<Totals.Item> candidates() {
        UUID self = RowOverlay.selfId();
        if (self == null) {
            return List.of();
        }
        List<Project> projects = new ArrayList<>();
        for (Project p : ClientProjectCache.projects()) {
            if (!p.archived() && ProjectOpener.canOpen(p.name())) {
                projects.add(p);
            }
        }
        return Totals.of(projects, self);
    }

    @Override
    String nameOf(Totals.Item item) {
        return UiText.escapeFormat(UiText.itemName(item.itemId()));
    }

    @Override
    String detailOf(Totals.Item item) {
        return Lang.t("totals.detail", item.left(), item.projects());
    }

    @Override
    List<Act<Totals.Item>> actionsFor(Totals.Item item) {
        return List.of();
    }

    @Override
    String helpText() {
        return Lang.t("totals.help");
    }

    @Override
    String emptyText() {
        return Lang.t("totals.empty");
    }

    @Override
    SearchListScreen<Totals.Item> reopen(int page, String query) {
        return new MyTotalsScreen(getParent(), page, query);
    }
}
