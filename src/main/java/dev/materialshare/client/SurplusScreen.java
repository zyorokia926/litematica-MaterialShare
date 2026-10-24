package dev.materialshare.client;

import dev.materialshare.common.Packets;
import fi.dy.masa.malilib.gui.GuiTextInput;
import fi.dy.masa.malilib.gui.Message;
import fi.dy.masa.malilib.interfaces.IStringConsumerFeedback;
import fi.dy.masa.malilib.util.InfoUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.screen.Screen;

/** The spare items the players of a project offer: take some, or take your own offer back. */
class SurplusScreen extends SearchListScreen<Packets.Offer> {
    private final String project;

    SurplusScreen(Screen parent, String project, int page, String query) {
        super(Lang.t("surplus.title", UiText.escapeFormat(project)), parent, page, query);
        this.project = project;
    }

    @Override
    List<Packets.Offer> candidates() {
        List<Packets.Offer> offers = new ArrayList<>(ClientProjectCache.surplus(project));
        offers.sort(Comparator.comparing((Packets.Offer o) -> UiText.itemName(o.itemId())).thenComparing(Packets.Offer::player));
        return offers;
    }

    @Override
    String nameOf(Packets.Offer o) {
        return UiText.itemName(o.itemId()) + " ×" + o.count();
    }

    @Override
    String detailOf(Packets.Offer o) {
        boolean needed = ClientProjectCache.projects().stream().filter(p -> p.name().equals(project)).findFirst()
                .map(p -> p.items().containsKey(o.itemId())).orElse(false);
        return Lang.t("surplus.detail", UiText.escapeFormat(ClientProjectCache.nameOf(o.player())))
                + (needed ? "  §a" + Lang.t("surplus.needed") : "");
    }

    @Override
    UUID faceOf(Packets.Offer o) {
        return o.player();
    }

    @Override
    List<Act<Packets.Offer>> actionsFor(Packets.Offer o) {
        UUID self = RowOverlay.selfId();
        if (o.player().equals(self)) {
            return List.of(new Act<>(Lang.t("surplus.withdraw"), Lang.t("surplus.withdraw.tip"),
                    x -> ClientNet.sendSurplus(project, x.itemId(), 0)));
        }
        return List.of(new Act<>(Lang.t("surplus.take"), Lang.t("surplus.take.tip"), this::take));
    }

    private void take(Packets.Offer offer) {
        int stack = RecipeAdapter.stackSize(offer.itemId());
        openGui(new GuiTextInput(10, Lang.t("surplus.take.title", UiText.escapeFormat(UiText.itemName(offer.itemId()))),
                String.valueOf(offer.count()), this, (IStringConsumerFeedback) text -> {
                    var count = dev.materialshare.common.Counts.parse(text, stack);
                    if (count.isEmpty() || count.getAsInt() <= 0) {
                        InfoUtils.showGuiMessage(Message.MessageType.ERROR, Lang.t("row.count.invalid"));
                        return false;
                    }
                    ClientNet.sendTakeSurplus(project, offer.itemId(), offer.player(), count.getAsInt());
                    return true;
                }));
    }

    @Override
    List<Head> headerButtons() {
        return List.of(new Head("§d" + Lang.t("surplus.add"), Lang.t("surplus.add.tip"),
                () -> openGui(new SurplusPickScreen(this, project, 0, ""))));
    }

    @Override
    String helpText() {
        return Lang.t("surplus.help");
    }

    @Override
    String emptyText() {
        return Lang.t("surplus.empty");
    }

    @Override
    SearchListScreen<Packets.Offer> reopen(int page, String query) {
        return new SurplusScreen(getParent(), project, page, query);
    }
}
