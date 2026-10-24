package dev.materialshare.client;

import dev.materialshare.common.Entry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.screen.Screen;

/** What the chests inside the storage boxes hold, per item, next to what the project needs. */
class StockScreen extends SearchListScreen<Map.Entry<String, Integer>> {
    private final String project;

    StockScreen(Screen parent, String project, int page, String query) {
        super(Lang.t("stock.title", UiText.escapeFormat(project)), parent, page, query);
        this.project = project;
    }

    /** The items the chests hold; a shulker box is left out unless the project needs shulker boxes (its contents are listed). */
    static List<Map.Entry<String, Integer>> stock(String project) {
        List<Map.Entry<String, Integer>> list = new ArrayList<>();
        var needed = ClientProjectCache.projects().stream().filter(p -> p.name().equals(project)).findFirst();
        for (Map.Entry<String, Integer> e : ClientProjectCache.storage(project).stock().entrySet()) {
            boolean box = e.getKey().endsWith("shulker_box");
            if (!box || needed.map(p -> p.items().containsKey(e.getKey())).orElse(false)) {
                list.add(e);
            }
        }
        return list;
    }

    @Override
    List<Map.Entry<String, Integer>> candidates() {
        List<Map.Entry<String, Integer>> list = stock(project);
        list.sort(Comparator.<Map.Entry<String, Integer>>comparingInt(e -> neededOf(e.getKey()) > 0 ? 0 : 1)
                .thenComparing(e -> UiText.itemName(e.getKey())));
        return list;
    }

    private int neededOf(String itemId) {
        return ClientProjectCache.projects().stream().filter(p -> p.name().equals(project)).findFirst()
                .map(p -> {
                    Entry e = p.items().get(itemId);
                    return e == null ? 0 : e.required();
                }).orElse(0);
    }

    @Override
    String nameOf(Map.Entry<String, Integer> e) {
        String stacks = dev.materialshare.common.Stacks.format(e.getValue(), RecipeAdapter.stackSize(e.getKey()), Lang.TRANSLATOR);
        return UiText.escapeFormat(UiText.itemName(e.getKey())) + " ×" + e.getValue() + (stacks.isEmpty() ? "" : " (" + stacks + ")");
    }

    @Override
    String detailOf(Map.Entry<String, Integer> e) {
        int need = neededOf(e.getKey());
        int inside = ClientProjectCache.storage(project).inside().getOrDefault(e.getKey(), 0);
        String box = inside > 0 ? "§b" + Lang.t("stock.inside", inside) + "§r  " : "";
        return box + (need > 0 ? (e.getValue() >= need ? "§a" : "§e") + Lang.t("stock.detail.needed", need)
                : "§7" + Lang.t("stock.detail.other"));
    }

    @Override
    List<Act<Map.Entry<String, Integer>>> actionsFor(Map.Entry<String, Integer> e) {
        return List.of();
    }

    @Override
    String helpText() {
        return Lang.t("stock.help");
    }

    @Override
    String emptyText() {
        return Lang.t("stock.empty");
    }

    @Override
    SearchListScreen<Map.Entry<String, Integer>> reopen(int page, String query) {
        return new StockScreen(getParent(), project, page, query);
    }
}
