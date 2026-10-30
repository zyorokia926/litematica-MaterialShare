package dev.materialshare.client;

import dev.materialshare.common.Project;
import dev.materialshare.common.Zone;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.malilib.gui.GuiTextInput;
import fi.dy.masa.malilib.gui.Message;
import fi.dy.masa.malilib.interfaces.IStringConsumerFeedback;
import fi.dy.masa.malilib.util.InfoUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.screen.Screen;

/**
 * The zones of a project (layers of the schematic): add one from the schematic, choose the one you work on (the list
 * and the HUD then show only what it needs), remove one. The zones are shared; the choice is yours alone.
 */
class ZoneScreen extends SearchListScreen<Zone> {
    private final MaterialListBase materialList;
    private final String project;

    ZoneScreen(MaterialListBase materialList, Screen parent, String project, int page, String query) {
        super(Lang.t("zone.title", UiText.escapeFormat(project)), parent, page, query);
        this.materialList = materialList;
        this.project = project;
    }

    private boolean chosen(Zone z) {
        return ClientProjectCache.chosenZone(project).map(c -> c.name().equals(z.name())).orElse(false);
    }

    @Override
    List<Zone> candidates() {
        return new ArrayList<>(ClientProjectCache.zones(project));
    }

    @Override
    String nameOf(Zone z) {
        return (chosen(z) ? "§a▶ " : "") + UiText.escapeFormat(z.name());
    }

    @Override
    String detailOf(Zone z) {
        Project p = ClientProjectCache.get(project).orElse(null);
        int percent = p == null ? 0 : z.percent(p);
        return Lang.t("zone.detail", z.fromLayer(), z.toLayer(), z.required().size(), percent);
    }

    @Override
    List<Act<Zone>> actionsFor(Zone z) {
        List<Act<Zone>> acts = new ArrayList<>();
        if (chosen(z)) {
            acts.add(new Act<>(Lang.t("zone.unchoose"), Lang.t("zone.unchoose.tip"), x -> choose(null)));
        } else {
            acts.add(new Act<>("§a" + Lang.t("zone.choose"), Lang.t("zone.choose.tip"), x -> choose(x.name())));
        }
        acts.add(new Act<>(Lang.t("zone.remove"), Lang.t("zone.remove.tip"), x -> ClientNet.sendZoneRemove(project, x.name())));
        return acts;
    }

    private void choose(String zone) {
        ClientProjectCache.chooseZone(project, zone);
        openGui(reopen(page, query));
    }

    @Override
    List<Head> headerButtons() {
        List<Head> heads = new ArrayList<>();
        heads.add(new Head("§a" + Lang.t("zone.add"), Lang.t("zone.add.tip"), this::add));
        if (ClientProjectCache.chosenZone(project).isPresent()) {
            heads.add(new Head(Lang.t("zone.unchoose"), Lang.t("zone.unchoose.tip"), () -> choose(null)));
        }
        return heads;
    }

    private void add() {
        int height = Zones.height(materialList);
        if (height <= 0) {
            InfoUtils.showGuiMessage(Message.MessageType.WARNING, Lang.t("zone.noplacement"));
            return;
        }
        if (ClientProjectCache.zones(project).size() >= Zone.MAX_ZONES) {
            InfoUtils.showGuiMessage(Message.MessageType.ERROR, Lang.t("zone.full", Zone.MAX_ZONES));
            return;
        }
        openGui(new GuiTextInput(dev.materialshare.common.Entry.MAX_TEXT, Lang.t("zone.input.title", height),
                suggestion(height), this, (IStringConsumerFeedback) text -> {
                    Zone.Parsed parsed = Zone.parse(text);
                    if (parsed == null || parsed.from() < 1 || parsed.to() > height) {
                        InfoUtils.showGuiMessage(Message.MessageType.ERROR, Lang.t("zone.input.invalid", height));
                        return false;
                    }
                    Project p = ClientProjectCache.get(project).orElse(null);
                    Map<String, Integer> needed = Zones.required(materialList, parsed.from(), parsed.to());
                    if (p != null) {
                        needed.keySet().retainAll(p.items().keySet());
                    }
                    if (needed.isEmpty()) {
                        InfoUtils.showGuiMessage(Message.MessageType.WARNING, Lang.t("zone.input.empty"));
                        return false;
                    }
                    String name = parsed.name().isBlank() ? Lang.t("zone.defaultname", parsed.from(), parsed.to()) : parsed.name();
                    ClientNet.sendZoneAdd(project, Zone.of(name, parsed.from(), parsed.to(), needed));
                    InfoUtils.showGuiMessage(Message.MessageType.SUCCESS, Lang.t("zone.added", name, needed.size()));
                    return true;
                }));
    }

    /** The layers right above the last zone (or the bottom ten). */
    private String suggestion(int height) {
        int from = 1;
        for (Zone z : ClientProjectCache.zones(project)) {
            from = Math.max(from, z.toLayer() + 1);
        }
        from = Math.min(from, height);
        return from + "-" + Math.min(height, from + 9) + " ";
    }

    @Override
    String helpText() {
        return Lang.t("zone.help", Zones.height(materialList));
    }

    @Override
    String emptyText() {
        return Lang.t("zone.empty");
    }

    @Override
    SearchListScreen<Zone> reopen(int page, String query) {
        return new ZoneScreen(materialList, getParent(), project, page, query);
    }
}
