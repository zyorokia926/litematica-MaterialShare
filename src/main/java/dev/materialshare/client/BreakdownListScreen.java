package dev.materialshare.client;

import dev.materialshare.common.Breakdown;
import dev.materialshare.common.Entry;
import dev.materialshare.common.Project;
import dev.materialshare.common.Stacks;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.screen.Screen;

/** The raw materials behind a project's finished items, worked out from the game's crafting recipes. */
class BreakdownListScreen extends SearchListScreen<Map.Entry<String, Integer>> {
    private final String project;

    BreakdownListScreen(Screen parent, String project, int page, String query) {
        super(Lang.t("breakdown.title", UiText.escapeFormat(project)), parent, page, query);
        this.project = project;
    }

    private Map<String, Integer> wanted() {
        Map<String, Integer> wanted = new LinkedHashMap<>();
        for (Project p : ClientProjectCache.projects()) {
            if (p.name().equals(project)) {
                for (Map.Entry<String, Entry> item : p.items().entrySet()) {
                    Entry e = item.getValue();
                    int amount = BreakdownScreen.remainingOnly ? e.required() - e.collectedTotal() : e.required();
                    if (amount > 0) {
                        wanted.put(item.getKey(), amount);
                    }
                }
            }
        }
        return wanted;
    }

    @Override
    List<Map.Entry<String, Integer>> candidates() {
        Map<String, Integer> wanted = wanted();
        return Breakdown.sorted(Breakdown.expand(wanted, RecipeAdapter.recipes(wanted.keySet())));
    }

    @Override
    String nameOf(Map.Entry<String, Integer> e) {
        return UiText.itemName(e.getKey());
    }

    @Override
    String detailOf(Map.Entry<String, Integer> e) {
        int stack = RecipeAdapter.stackSize(e.getKey());
        String stacks = Stacks.format(e.getValue(), stack, Lang.TRANSLATOR);
        double boxes = Stacks.shulkers(e.getValue(), stack);
        return String.valueOf(e.getValue()) + (stacks.isEmpty() ? "" : "  (" + stacks + ")")
                + (boxes > 0 ? "  ·  " + Lang.t("row.shulker", boxes) : "");
    }

    @Override
    List<Act<Map.Entry<String, Integer>>> actionsFor(Map.Entry<String, Integer> e) {
        return List.of();
    }

    @Override
    List<Head> headerButtons() {
        return List.of(
                new Head("§e" + (BreakdownScreen.remainingOnly ? Lang.t("breakdown.mode.remaining") : Lang.t("breakdown.mode.all")),
                        Lang.t("breakdown.mode.tip"), () -> {
                            BreakdownScreen.remainingOnly = !BreakdownScreen.remainingOnly;
                            openGui(reopen(0, query));
                        }),
                new Head("§b" + Lang.t("breakdown.list.map"), Lang.t("breakdown.list.map.tip"),
                        () -> openGui(new BreakdownScreen(getParent(), project, 0, ""))));
    }

    @Override
    String helpText() {
        return Lang.t("breakdown.help");
    }

    @Override
    String emptyText() {
        return Lang.t("breakdown.empty");
    }

    @Override
    SearchListScreen<Map.Entry<String, Integer>> reopen(int page, String query) {
        return new BreakdownListScreen(getParent(), project, page, query);
    }
}
