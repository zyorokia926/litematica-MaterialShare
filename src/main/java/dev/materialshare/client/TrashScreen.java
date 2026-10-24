package dev.materialshare.client;

import dev.materialshare.common.Packets;
import dev.materialshare.common.ProjectRegistry;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.malilib.gui.GuiConfirmAction;
import fi.dy.masa.malilib.gui.Message;
import fi.dy.masa.malilib.interfaces.IConfirmationListener;
import fi.dy.masa.malilib.util.InfoUtils;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import net.minecraft.client.gui.screen.Screen;

/** Deleted projects of one schematic: restore one, or delete it for good (the only way a project is ever lost). */
public class TrashScreen extends SearchListScreen<Packets.TrashEntry> {
    private final MaterialListBase materialList;

    public TrashScreen(MaterialListBase materialList, Screen projectScreen, int page, String query) {
        super(Lang.t("trash.title"), projectScreen, page, query);
        this.materialList = materialList;
    }

    public static List<Packets.TrashEntry> trashedOf(MaterialListBase list) {
        String base = list == null ? null : ProjectNames.forList(list);
        List<Packets.TrashEntry> result = new ArrayList<>();
        for (Packets.TrashEntry e : ClientProjectCache.trash()) {
            if (base == null || ProjectRegistry.belongsToList(base, e.name())) {
                result.add(e);
            }
        }
        Collections.reverse(result); // newest first
        return result;
    }

    @Override
    List<Packets.TrashEntry> candidates() {
        return trashedOf(materialList);
    }

    @Override
    String nameOf(Packets.TrashEntry e) {
        return e.name();
    }

    @Override
    String detailOf(Packets.TrashEntry e) {
        String who = e.by() == null ? Lang.t("history.unknown") : ClientProjectCache.nameOf(e.by());
        return Lang.t("trash.detail", e.collected(), e.required(), UiText.escapeFormat(who),
                new SimpleDateFormat("MM/dd HH:mm").format(new Date(e.time())));
    }

    @Override
    List<Act<Packets.TrashEntry>> actionsFor(Packets.TrashEntry e) {
        return List.of(
                new Act<>(Lang.t("trash.restore"), Lang.t("trash.restore.tip"), this::restore),
                new Act<>(Lang.t("trash.purge"), Lang.t("trash.purge.tip"), this::confirmPurge));
    }

    /** Only a schematic that is shared with Syncmatica can be set up again; the restored one is then shared at once. */
    private void restore(Packets.TrashEntry entry) {
        boolean shared = ProjectOpener.placementOf(entry.name()).map(SyncmaticaCheck::isShared).orElse(false);
        if (!shared) {
            InfoUtils.showGuiMessage(Message.MessageType.ERROR, Lang.t("trash.restore.notshared", UiText.escapeFormat(entry.name())));
            return;
        }
        ClientProjectCache.selectWhenArrives(entry.name());
        ClientNet.sendTrashRestore(entry.id());
    }

    private void confirmPurge(Packets.TrashEntry entry) {
        TrashScreen self = this;
        openGui(new GuiConfirmAction(320, Lang.t("trash.purge.title"), new IConfirmationListener() {
            @Override
            public boolean onActionConfirmed() {
                ClientNet.sendTrashPurge(entry.id());
                return false;
            }

            @Override
            public boolean onActionCancelled() {
                return false;
            }
        }, self, Lang.t("trash.purge.confirm", UiText.escapeFormat(entry.name()))));
    }

    @Override
    String helpText() {
        return Lang.t("trash.help");
    }

    @Override
    String emptyText() {
        return Lang.t("trash.empty");
    }

    @Override
    SearchListScreen<Packets.TrashEntry> reopen(int page, String query) {
        return new TrashScreen(materialList, getParent(), page, query);
    }
}
