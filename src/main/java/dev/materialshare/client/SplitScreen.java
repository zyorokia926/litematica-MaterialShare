package dev.materialshare.client;

import dev.materialshare.client.config.Configs;
import dev.materialshare.common.Counts;
import dev.materialshare.common.Entry;
import dev.materialshare.common.Project;
import dev.materialshare.common.Split;
import fi.dy.masa.malilib.gui.GuiTextInput;
import fi.dy.masa.malilib.gui.Message;
import fi.dy.masa.malilib.interfaces.IStringConsumerFeedback;
import fi.dy.masa.malilib.util.InfoUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.gui.screen.Screen;

/** Who collects how much of one item: promised amounts, handed-in amounts, handing a claim over, adding people. */
class SplitScreen extends SearchListScreen<UUID> {
    private final String project;
    private final String itemId;

    SplitScreen(Screen parent, String project, String itemId, int page, String query) {
        super(Lang.t("split.title", UiText.escapeFormat(UiText.itemName(itemId))), parent, page, query);
        this.project = project;
        this.itemId = itemId;
    }

    private Entry entry() {
        return ClientProjectCache.projects().stream().filter(p -> p.name().equals(project)).findFirst()
                .map(p -> p.items().get(itemId)).orElse(null);
    }

    private int stackSize() {
        return dev.materialshare.McCompat.item(net.minecraft.util.Identifier.tryParse(itemId)).getMaxCount();
    }

    @Override
    List<UUID> candidates() {
        Entry e = entry();
        return e == null ? List.of() : new ArrayList<>(e.claims().keySet());
    }

    @Override
    String nameOf(UUID id) {
        return UiText.escapeFormat(ClientProjectCache.nameOf(id));
    }

    @Override
    UUID faceOf(UUID id) {
        return id;
    }

    @Override
    String detailOf(UUID id) {
        Entry e = entry();
        if (e == null) {
            return "";
        }
        int target = e.targetOf(id);
        String text = Lang.t("split.detail", e.claims().getOrDefault(id, 0), target > 0 ? String.valueOf(target) : "-");
        if (Configs.Generic.SHOW_DELIVERY.getBooleanValue()) {
            text += "  ·  " + Lang.t("detail.delivered", e.delivered().getOrDefault(id, 0));
        }
        return text;
    }

    @Override
    List<Act<UUID>> actionsFor(UUID id) {
        List<Act<UUID>> acts = new ArrayList<>();
        acts.add(new Act<>(Lang.t("split.remove"), Lang.t("split.remove.tip"), x -> ClientNet.sendClaim(project, itemId, x, false)));
        acts.add(new Act<>(Lang.t("split.target"), Lang.t("split.target.tip"), x -> editTarget(x)));
        if (Configs.Generic.SHOW_DELIVERY.getBooleanValue()) {
            acts.add(new Act<>(Lang.t("split.delivered"), Lang.t("split.delivered.tip"), x -> editDelivered(x)));
        }
        acts.add(new Act<>(Lang.t("split.handover"), Lang.t("split.handover.tip"), x -> handOver(x)));
        return acts;
    }

    /** Wheel over a person: more / less assigned to them, like the wheel over a name on the material list. */
    @Override
    boolean rowScrolled(UUID id, double amount) {
        Entry e = entry();
        int delta = RowOverlay.wheelDelta(amount);
        if (e == null || delta == 0) {
            return false;
        }
        int next = Math.max(0, Math.min(e.required(), e.targetOf(id) + delta));
        Map<UUID, Integer> targets = new LinkedHashMap<>(e.targets());
        if (next == 0) {
            targets.remove(id);
        } else {
            targets.put(id, next);
        }
        ClientNet.sendTargets(project, itemId, targets);
        return true;
    }

    private void editTarget(UUID id) {
        Entry e = entry();
        if (e == null) {
            return;
        }
        openGui(new GuiTextInput(12, Lang.t("split.target.title", UiText.escapeFormat(ClientProjectCache.nameOf(id))),
                e.targetOf(id) > 0 ? String.valueOf(e.targetOf(id)) : "", this, (IStringConsumerFeedback) text -> {
                    var count = text.isBlank() ? java.util.OptionalInt.of(0) : Counts.parse(text, stackSize());
                    if (count.isEmpty()) {
                        InfoUtils.showGuiMessage(Message.MessageType.ERROR, Lang.t("row.count.invalid"));
                        return false;
                    }
                    Map<UUID, Integer> targets = new LinkedHashMap<>(e.targets());
                    if (count.getAsInt() == 0) {
                        targets.remove(id);
                    } else {
                        targets.put(id, Math.min(count.getAsInt(), e.required()));
                    }
                    ClientNet.sendTargets(project, itemId, targets);
                    return true;
                }));
    }

    private void editDelivered(UUID id) {
        Entry e = entry();
        if (e == null) {
            return;
        }
        openGui(new GuiTextInput(12, Lang.t("split.delivered.title", UiText.escapeFormat(ClientProjectCache.nameOf(id))),
                String.valueOf(e.delivered().getOrDefault(id, 0)), this, (IStringConsumerFeedback) text -> {
                    var count = Counts.parse(text, stackSize());
                    if (count.isEmpty()) {
                        InfoUtils.showGuiMessage(Message.MessageType.ERROR, Lang.t("row.count.invalid"));
                        return false;
                    }
                    ClientNet.sendDelivered(project, itemId, id, count.getAsInt());
                    return true;
                }));
    }

    private void handOver(UUID from) {
        Screen me = this;
        openGui(new PlayerPickScreen(me, project, itemId, UiText.itemName(itemId), 0, "", to -> {
            if (!to.equals(from)) {
                ClientNet.sendTransfer(project, itemId, from, to);
            }
            openGui(me);
        }, "split.handover.title"));
    }

    @Override
    List<Head> headerButtons() {
        List<Head> heads = new ArrayList<>();
        heads.add(new Head(Lang.t("split.even"), Lang.t("split.even.tip"), () -> {
            Entry e = entry();
            if (e != null && !e.claims().isEmpty()) {
                ClientNet.sendTargets(project, itemId, Split.even(e.required(), new ArrayList<>(e.claims().keySet())));
            }
        }));
        heads.add(new Head(Lang.t("split.add"), Lang.t("split.add.tip"), () -> {
            ClientNet.requestPlayers();
            openGui(new PlayerPickScreen(this, project, itemId, UiText.itemName(itemId), 0, ""));
        }));
        heads.add(new Head(Lang.t("split.me"), Lang.t("split.me.tip"), () -> {
            Entry e = entry();
            UUID self = RowOverlay.selfId();
            if (e == null || self == null) {
                return;
            }
            if (!e.claims().containsKey(self)) {
                ClientNet.sendClaim(project, itemId, self, true);
            }
            Map<UUID, Integer> targets = new LinkedHashMap<>(e.targets());
            targets.put(self, Math.min(e.required(), e.targetOf(self) + Math.max(1, Split.unassigned(e))));
            ClientNet.sendTargets(project, itemId, targets);
        }));
        heads.add(new Head("§c" + Lang.t("split.reset"), Lang.t("split.reset.tip"), () -> {
            Entry e = entry();
            if (e != null && !e.claims().isEmpty()) {
                ClientNet.sendResetClaims(project, itemId);
            }
        }));
        return heads;
    }

    @Override
    String helpText() {
        Entry e = entry();
        if (e == null) {
            return Lang.t("detail.gone");
        }
        return Lang.t("split.help", e.required(), e.collectedTotal(), e.targetTotal(), Split.unassigned(e)) + " " + Lang.t("split.wheel");
    }

    @Override
    String emptyText() {
        return Lang.t("split.empty");
    }

    @Override
    SearchListScreen<UUID> reopen(int page, String query) {
        return new SplitScreen(getParent(), project, itemId, page, query);
    }
}
