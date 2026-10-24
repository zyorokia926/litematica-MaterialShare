package dev.materialshare.client;

import dev.materialshare.common.Packets;
import fi.dy.masa.malilib.gui.GuiTextInput;
import fi.dy.masa.malilib.gui.Message;
import fi.dy.masa.malilib.interfaces.IStringConsumerFeedback;
import fi.dy.masa.malilib.util.InfoUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

/** What the player carries, to pick the items to offer as spare. */
class SurplusPickScreen extends SearchListScreen<Map.Entry<String, Integer>> {
    private final String project;

    SurplusPickScreen(Screen parent, String project, int page, String query) {
        super(Lang.t("surplus.pick.title"), parent, page, query);
        this.project = project;
    }

    @Override
    List<Map.Entry<String, Integer>> candidates() {
        var player = MinecraftClient.getInstance().player;
        if (player == null) {
            return List.of();
        }
        List<Map.Entry<String, Integer>> rows = new ArrayList<>(Inventory.counts(player).entrySet());
        rows.sort(Comparator.comparing((Map.Entry<String, Integer> e) -> UiText.itemName(e.getKey())));
        return rows;
    }

    @Override
    String nameOf(Map.Entry<String, Integer> e) {
        return UiText.itemName(e.getKey());
    }

    @Override
    String detailOf(Map.Entry<String, Integer> e) {
        UUID self = RowOverlay.selfId();
        int offered = ClientProjectCache.surplusOf(project, e.getKey()).stream()
                .filter(o -> o.player().equals(self)).mapToInt(Packets.Offer::count).sum();
        return Lang.t("surplus.pick.detail", e.getValue()) + (offered > 0 ? "  §d" + Lang.t("surplus.pick.offered", offered) : "");
    }

    @Override
    List<Act<Map.Entry<String, Integer>>> actionsFor(Map.Entry<String, Integer> e) {
        return List.of(new Act<>(Lang.t("surplus.offer"), Lang.t("surplus.offer.tip"), this::offer));
    }

    private void offer(Map.Entry<String, Integer> row) {
        int stack = RecipeAdapter.stackSize(row.getKey());
        openGui(new GuiTextInput(10, Lang.t("surplus.offer.title", UiText.escapeFormat(UiText.itemName(row.getKey()))),
                String.valueOf(row.getValue()), this, (IStringConsumerFeedback) text -> {
                    var count = dev.materialshare.common.Counts.parse(text, stack);
                    if (count.isEmpty()) {
                        InfoUtils.showGuiMessage(Message.MessageType.ERROR, Lang.t("row.count.invalid"));
                        return false;
                    }
                    ClientNet.sendSurplus(project, row.getKey(), count.getAsInt());
                    return true;
                }));
    }

    @Override
    String helpText() {
        return Lang.t("surplus.pick.help");
    }

    @Override
    String emptyText() {
        return Lang.t("surplus.pick.empty");
    }

    @Override
    SearchListScreen<Map.Entry<String, Integer>> reopen(int page, String query) {
        return new SurplusPickScreen(getParent(), project, page, query);
    }
}
