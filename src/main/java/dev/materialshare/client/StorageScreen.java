package dev.materialshare.client;

import dev.materialshare.common.Area;
import fi.dy.masa.malilib.gui.GuiTextInput;
import fi.dy.masa.malilib.gui.Message;
import fi.dy.masa.malilib.interfaces.IStringConsumerFeedback;
import fi.dy.masa.malilib.util.InfoUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.screen.Screen;

/** The storage boxes of a project: register the marked corners as a box, look at what is inside, remove a box. */
class StorageScreen extends SearchListScreen<StorageScreen.Row> {
    record Row(int index, Area area) {
    }

    private final String project;

    StorageScreen(Screen parent, String project, int page, String query) {
        super(Lang.t("storage.title", UiText.escapeFormat(project)), parent, page, query);
        this.project = project;
    }

    private List<Area> areas() {
        return ClientProjectCache.storage(project).areas();
    }

    @Override
    List<Row> candidates() {
        List<Row> rows = new ArrayList<>();
        List<Area> areas = areas();
        for (int i = 0; i < areas.size(); i++) {
            rows.add(new Row(i, areas.get(i)));
        }
        return rows;
    }

    @Override
    String nameOf(Row r) {
        return UiText.escapeFormat(r.area().name());
    }

    @Override
    String detailOf(Row r) {
        Area a = r.area();
        return Lang.t("storage.detail", a.dimension(), a.minX(), a.minY(), a.minZ(), a.maxX(), a.maxY(), a.maxZ(),
                a.sizeX(), a.sizeY(), a.sizeZ());
    }

    @Override
    List<Act<Row>> actionsFor(Row r) {
        return List.of(new Act<>(Lang.t("storage.remove"), Lang.t("storage.remove.tip"),
                x -> ClientNet.sendStorageRemove(project, x.index())));
    }

    @Override
    List<Head> headerButtons() {
        List<Head> heads = new ArrayList<>();
        heads.add(new Head("§a" + Lang.t("storage.register"), Lang.t("storage.register.tip"), this::register));
        heads.add(new Head(Lang.t("storage.clear"), Lang.t("storage.clear.tip"), () -> {
            StorageMarks.clearWithMessage();
            openGui(reopen(page, query));
        }));
        heads.add(new Head(Lang.t("storage.contents", StockScreen.stock(project).size()),
                Lang.t("storage.contents.tip"), () -> openGui(new StockScreen(this, project, 0, ""))));
        return heads;
    }

    private void register() {
        if (!StorageMarks.ready()) {
            InfoUtils.showGuiMessage(Message.MessageType.WARNING, Lang.t("storage.nomarks"));
            return;
        }
        Area probe = StorageMarks.area("x");
        if (!probe.valid()) {
            InfoUtils.showGuiMessage(Message.MessageType.ERROR, Lang.t("storage.toobig", Area.MAX_SIDE));
            return;
        }
        openGui(new GuiTextInput(dev.materialshare.common.Entry.MAX_TEXT, Lang.t("storage.name.title"),
                StorageMarks.suggestName(areas()), this, (IStringConsumerFeedback) text -> {
                    Area area = StorageMarks.area(text.strip());
                    if (area == null || !area.valid()) {
                        InfoUtils.showGuiMessage(Message.MessageType.ERROR, Lang.t("storage.name.invalid"));
                        return false;
                    }
                    ClientNet.sendStorageAdd(project, area);
                    StorageMarks.clear();
                    return true;
                }));
    }

    @Override
    String helpText() {
        return Lang.t("storage.help", StorageMarks.a() == null ? "-" : pos(StorageMarks.a()),
                StorageMarks.b() == null ? "-" : pos(StorageMarks.b()));
    }

    private static String pos(net.minecraft.util.math.BlockPos p) {
        return p.getX() + ", " + p.getY() + ", " + p.getZ();
    }

    @Override
    String emptyText() {
        return Lang.t("storage.empty");
    }

    @Override
    SearchListScreen<Row> reopen(int page, String query) {
        return new StorageScreen(getParent(), project, page, query);
    }
}
