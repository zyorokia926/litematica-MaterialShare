package dev.materialshare.client;

import dev.materialshare.common.Project;
import dev.materialshare.common.Stats;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.screen.Screen;

/** Who collected how much in one project, best first, with their face. */
class StatsScreen extends SearchListScreen<Stats.PlayerStat> {
    private final String project;

    StatsScreen(Screen parent, String project, int page, String query) {
        super(Lang.t("stats.title", UiText.escapeFormat(project)), parent, page, query);
        this.project = project;
        ClientProjectCache.requestActivity(project);
    }

    private List<Stats.PlayerStat> all() {
        return ClientProjectCache.projects().stream().filter(p -> p.name().equals(project)).findFirst()
                .map(Stats::byPlayer).orElse(List.of());
    }

    @Override
    List<Stats.PlayerStat> candidates() {
        return all();
    }

    @Override
    String nameOf(Stats.PlayerStat s) {
        return Lang.t("stats.name", all().indexOf(s) + 1, UiText.escapeFormat(ClientProjectCache.nameOf(s.player())));
    }

    @Override
    String detailOf(Stats.PlayerStat s) {
        String idle = ClientProjectCache.idle(project, s.player())
                ? "  §c" + Lang.t("idle.days", ClientProjectCache.idleDays(project, s.player())) : "";
        return Lang.t("stats.detail", s.collected(), s.items(), s.itemsDone(), Stats.share(s, all())) + idle;
    }

    @Override
    UUID faceOf(Stats.PlayerStat s) {
        return s.player();
    }

    @Override
    List<Act<Stats.PlayerStat>> actionsFor(Stats.PlayerStat s) {
        return List.of(new Act<>(Lang.t("stats.items"), Lang.t("stats.items.tip"),
                x -> openGui(new MyAssignmentsScreen(this, x.player(), 0, ""))));
    }

    @Override
    String helpText() {
        return Lang.t("stats.help");
    }

    @Override
    String emptyText() {
        return Lang.t("stats.empty");
    }

    @Override
    SearchListScreen<Stats.PlayerStat> reopen(int page, String query) {
        return new StatsScreen(getParent(), project, page, query);
    }
}
