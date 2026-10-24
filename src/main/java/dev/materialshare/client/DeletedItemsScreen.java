package dev.materialshare.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.screen.Screen;

/** Items taken out of a project, newest first, each with a button that puts it back as it was. */
class DeletedItemsScreen extends SearchListScreen<String> {
    private final String project;

    DeletedItemsScreen(Screen parent, String project, int page, String query) {
        super(Lang.t("deleted.title"), parent, page, query);
        this.project = project;
    }

    @Override
    List<String> candidates() {
        List<String> ids = new ArrayList<>(ClientProjectCache.removed(project));
        java.util.Collections.reverse(ids);
        return ids;
    }

    @Override
    String nameOf(String itemId) {
        return UiText.itemName(itemId);
    }

    @Override
    String detailOf(String itemId) {
        return itemId;
    }

    @Override
    List<Act<String>> actionsFor(String itemId) {
        return List.of(new Act<>("§a" + Lang.t("deleted.restore"), Lang.t("deleted.restore.tip"),
                id -> ClientNet.sendRestoreItem(project, id)));
    }

    @Override
    String helpText() {
        return Lang.t("deleted.help");
    }

    @Override
    String emptyText() {
        return Lang.t("deleted.empty");
    }

    @Override
    SearchListScreen<String> reopen(int page, String query) {
        return new DeletedItemsScreen(getParent(), project, page, query);
    }
}
