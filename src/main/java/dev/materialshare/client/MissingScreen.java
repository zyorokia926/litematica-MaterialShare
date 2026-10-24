package dev.materialshare.client;

import dev.materialshare.common.Missing;
import dev.materialshare.common.Project;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.screen.Screen;

/** What is still missing: the items nobody takes first, then the biggest gaps; take one or open its details. */
class MissingScreen extends SearchListScreen<Missing.Item> {
    private final String project;

    MissingScreen(Screen parent, String project, int page, String query) {
        super(Lang.t("missing.title", UiText.escapeFormat(project)), parent, page, query);
        this.project = project;
        ClientProjectCache.requestActivity(project);
    }

    private Project current() {
        return ClientProjectCache.projects().stream().filter(p -> p.name().equals(project)).findFirst().orElse(null);
    }

    @Override
    List<Missing.Item> candidates() {
        Project p = current();
        return p == null ? List.of() : Missing.of(p);
    }

    @Override
    String nameOf(Missing.Item item) {
        return UiText.escapeFormat(UiText.itemName(item.itemId()));
    }

    @Override
    String detailOf(Missing.Item item) {
        String who = item.nobody() ? "§c" + Lang.t("missing.nobody") + "§r"
                : item.people().stream().map(id -> ClientProjectCache.nameOf(id) + (ClientProjectCache.idle(project, id) ? "§c!§r" : "")).limit(3).collect(java.util.stream.Collectors.joining(", "))
                        + (item.people().size() > 3 ? " +" + (item.people().size() - 3) : "");
        return Lang.t("missing.detail", item.left(), item.required(), UiText.escapeFormat(who));
    }

    @Override
    List<Act<Missing.Item>> actionsFor(Missing.Item item) {
        UUID self = RowOverlay.selfId();
        List<Act<Missing.Item>> acts = new java.util.ArrayList<>();
        if (self != null && !item.people().contains(self)) {
            acts.add(new Act<>(Lang.t("missing.take"), Lang.t("missing.take.tip"),
                    x -> ClientNet.sendClaim(project, x.itemId(), self, true)));
        }
        acts.add(new Act<>(Lang.t("missing.details"), Lang.t("missing.details.tip"),
                x -> openGui(new ItemDetailScreen(this, project, x.itemId()))));
        return acts;
    }

    @Override
    String helpText() {
        Project p = current();
        List<Missing.Item> items = p == null ? List.of() : Missing.of(p);
        return Lang.t("missing.help", items.size(), Missing.unassigned(items));
    }

    @Override
    String emptyText() {
        return Lang.t("missing.empty");
    }

    @Override
    SearchListScreen<Missing.Item> reopen(int page, String query) {
        return new MissingScreen(getParent(), project, page, query);
    }
}
