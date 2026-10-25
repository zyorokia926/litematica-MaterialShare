package dev.materialshare.client;

import dev.materialshare.common.Counts;
import dev.materialshare.common.Entry;
import fi.dy.masa.malilib.gui.GuiTextInput;
import fi.dy.masa.malilib.gui.Message;
import fi.dy.masa.malilib.interfaces.IStringConsumerFeedback;
import fi.dy.masa.malilib.util.InfoUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.gui.screen.Screen;

/** Your items with how much of it you have handed in to the shared storage. */
class DeliveryScreen extends SearchListScreen<DeliveryScreen.Row> {
    record Row(String itemId, Entry entry) {
    }

    DeliveryScreen(Screen parent, int page, String query) {
        super(Lang.t("deliver.title"), parent, page, query);
    }

    private String project() {
        return ClientProjectCache.selected().map(dev.materialshare.common.Project::name).orElse("");
    }

    @Override
    List<Row> candidates() {
        UUID self = RowOverlay.selfId();
        List<Row> rows = new ArrayList<>();
        ClientProjectCache.selected().ifPresent(p -> {
            for (Map.Entry<String, Entry> item : p.items().entrySet()) {
                if (self != null && item.getValue().claims().containsKey(self)) {
                    rows.add(new Row(item.getKey(), item.getValue()));
                }
            }
        });
        rows.sort(Comparator.<Row>comparingInt(r -> {
            Integer c = r.entry().claims().get(self);
            return (c == null ? 0 : c) - r.entry().delivered().getOrDefault(self, 0) > 0 ? 0 : 1;
        }).thenComparing(r -> UiText.itemName(r.itemId())));
        return rows;
    }

    @Override
    String nameOf(Row r) {
        return UiText.itemName(r.itemId());
    }

    @Override
    String detailOf(Row r) {
        UUID self = RowOverlay.selfId();
        int collected = r.entry().claims().getOrDefault(self, 0);
        int delivered = r.entry().delivered().getOrDefault(self, 0);
        return Lang.t("deliver.detail", collected, delivered, Math.max(0, collected - delivered));
    }

    @Override
    List<Act<Row>> actionsFor(Row r) {
        UUID self = RowOverlay.selfId();
        return List.of(
                new Act<>(Lang.t("deliver.all"), Lang.t("deliver.all.tip"),
                        x -> ClientNet.sendDelivered(project(), x.itemId(), self, x.entry().claims().getOrDefault(self, 0))),
                new Act<>(Lang.t("deliver.enter"), Lang.t("deliver.enter.tip"), x -> enter(x)));
    }

    private void enter(Row r) {
        UUID self = RowOverlay.selfId();
        int stack = dev.materialshare.McCompat.item(net.minecraft.util.Identifier.tryParse(r.itemId())).getMaxCount();
        openGui(new GuiTextInput(12, Lang.t("deliver.enter.title", UiText.escapeFormat(UiText.itemName(r.itemId()))),
                String.valueOf(r.entry().delivered().getOrDefault(self, 0)), this, (IStringConsumerFeedback) text -> {
                    var count = Counts.parse(text, stack);
                    if (count.isEmpty()) {
                        InfoUtils.showGuiMessage(Message.MessageType.ERROR, Lang.t("row.count.invalid"));
                        return false;
                    }
                    ClientNet.sendDelivered(project(), r.itemId(), self, count.getAsInt());
                    return true;
                }));
    }

    @Override
    List<Head> headerButtons() {
        return List.of(new Head(Lang.t("deliver.everything"), Lang.t("deliver.everything.tip"), () -> Importer.deliverAll(true)));
    }

    @Override
    String helpText() {
        return Lang.t("deliver.help");
    }

    @Override
    String emptyText() {
        return Lang.t("deliver.empty");
    }

    @Override
    SearchListScreen<Row> reopen(int page, String query) {
        return new DeliveryScreen(getParent(), page, query);
    }
}
