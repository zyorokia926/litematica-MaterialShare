package dev.materialshare.client;

import dev.materialshare.client.config.Configs;
import dev.materialshare.client.config.ConfigScreen;
import dev.materialshare.common.Project;
import fi.dy.masa.malilib.gui.GuiConfirmAction;
import fi.dy.masa.malilib.interfaces.IConfirmationListener;
import fi.dy.masa.litematica.materials.MaterialListBase;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.screen.Screen;

/** Everything that can be done with the selected project, sorted into four tabs. */
class ProjectToolsScreen extends GridScreen {
    private final MaterialListBase materialList;

    ProjectToolsScreen(MaterialListBase materialList, Screen projectScreen, int tab, int page) {
        super(Lang.t("tools.title"), projectScreen, tab, page);
        this.materialList = materialList;
    }

    @Override
    String info() {
        Project p = ClientProjectCache.selected().orElse(null);
        if (p == null) {
            return Lang.t("tools.noproject");
        }
        String text = Lang.t("tools.project", UiText.escapeFormat(p.name()));
        if (p.isComplete()) {
            return text + "  §a" + Lang.t("cmd.progress.finished");
        }
        return text;
    }

    @Override
    List<Tab> tabs() {
        Project p = ClientProjectCache.selected().orElse(null);
        boolean has = p != null;
        String need = Lang.t("tools.need");
        Screen me = this;

        List<Cell> assign = new ArrayList<>();
        assign.add(Cell.when(has, Lang.t("act.bulk"), Lang.t("act.bulk.tip"), () -> openGui(new BulkScreen(materialList, me, null, 0)), need));
        assign.add(Cell.when(has && p.items().values().stream().anyMatch(e -> !e.claims().isEmpty()), Lang.t("act.reset"),
                Lang.t("act.reset.tip"), () -> confirmReset(p), has ? Lang.t("act.reset.none") : need));
        assign.add(Cell.when(has && p.items().values().stream().anyMatch(e -> !e.claims().isEmpty()), Lang.t("act.resetplayer"),
                Lang.t("act.resetplayer.tip"), () -> pickPlayerToReset(p), has ? Lang.t("act.reset.none") : need));
        assign.add(Cell.when(has, Lang.t("act.takecarried"), Lang.t("act.takecarried.tip"), () -> Importer.claimFromInventory(true), need));
        assign.add(Cell.when(has, Lang.t("act.import"), Lang.t("act.import.tip"), () -> Importer.run(materialList, true), need));
        if (Configs.Generic.SHOW_DELIVERY.getBooleanValue()) {
            assign.add(Cell.when(has, Lang.t("act.deliver"), Lang.t("act.deliver.tip"), () -> openGui(new DeliveryScreen(me, 0, "")), need));
        }
        assign.add(Cell.when(has, Lang.t("act.items"), Lang.t("act.items.tip"), () -> openGui(new ItemInfoScreen(me, p.name(), 0, "")), need));

        List<Cell> view = new ArrayList<>();
        view.add(Cell.of(Lang.t("act.help"), Lang.t("act.help.tip"), () -> openGui(new HelpScreen(me, 0))));
        view.add(Cell.of(Lang.t("act.totals"), Lang.t("act.totals.tip"), () -> openGui(new MyTotalsScreen(me, 0, ""))));
        view.add(Cell.when(has, Lang.t("act.missing"), Lang.t("act.missing.tip"), () -> openGui(new MissingScreen(me, p.name(), 0, "")), need));
        view.add(Cell.when(has, Lang.t("act.stats"), Lang.t("act.stats.tip"), () -> openGui(new StatsScreen(me, p.name(), 0, "")), need));
        view.add(Cell.when(has, Lang.t("act.history"), Lang.t("act.history.tip"), () -> openGui(new HistoryScreen(me, p.name(), 0, true)), need));
        view.add(Cell.of(Lang.t("act.feed"), Lang.t("act.feed.tip"), () -> openGui(FeedScreen.open(me))));
        view.add(Cell.when(has, Lang.t("act.surplus", has ? ClientProjectCache.surplus(p.name()).size() : 0),
                Lang.t("act.surplus.tip"), () -> openGui(new SurplusScreen(me, p.name(), 0, "")), need));
        view.add(Cell.when(has, Lang.t("act.storage", has ? ClientProjectCache.storage(p.name()).areas().size() : 0),
                Lang.t("act.storage.tip"), () -> openGui(new StorageScreen(me, p.name(), 0, "")), need));
        view.add(Cell.when(has, Lang.t("act.breakdown"), Lang.t("act.breakdown.tip"), () -> openGui(new BreakdownScreen(me, p.name(), 0, "")), need));
        view.add(Cell.of(Lang.t("overview.mine"), Lang.t("overview.mine.tip"), () -> openGui(new MyAssignmentsScreen(me, null, 0, ""))));

        List<Cell> copy = new ArrayList<>();
        copy.add(Cell.when(has, Lang.t("act.report"), Lang.t("act.report.tip"), () -> ProjectCopy.plain(p), need));
        copy.add(Cell.when(has, Lang.t("act.discord"), Lang.t("act.discord.tip"), () -> ProjectCopy.discord(p), need));
        copy.add(Cell.when(has, Lang.t("act.recruit"), Lang.t("act.recruit.tip"), () -> ProjectCopy.recruit(p), need));

        List<Cell> manage = new ArrayList<>();
        manage.add(Cell.when(has, Lang.t("act.note"), Lang.t("act.note.tip"), () -> ProjectActions.editNote(me, p), need));
        manage.add(Cell.when(has, Lang.t("act.update"), Lang.t("act.update.tip"), () -> ProjectActions.updateFromSchematic(materialList, p, me), need));
        manage.add(Cell.when(has, Lang.t("act.clone"), Lang.t("act.clone.tip"), () -> ProjectActions.clone(materialList, p, me, true), need));
        manage.add(Cell.when(has, Lang.t("act.clone.empty"), Lang.t("act.clone.empty.tip"), () -> ProjectActions.clone(materialList, p, me, false), need));
        manage.add(Cell.when(has && ProjectActions.canRename(materialList, p), Lang.t("act.rename"), Lang.t("act.rename.tip"),
                () -> ProjectActions.rename(materialList, p, me), has ? Lang.t("act.rename.base") : need));
        manage.add(Cell.when(has, Lang.t("act.archivenow"), Lang.t("proj.archive.tip"), () -> {
            ClientNet.sendArchive(p.name(), true);
            openGui(getParent());
        }, need));
        manage.add(Cell.of(Lang.t("act.archive", ArchiveScreen.archivedOf(materialList).size()), Lang.t("act.archive.tip"),
                () -> openGui(new ArchiveScreen(materialList, me, getParentList(), 0, ""))));
        manage.add(Cell.of(Lang.t("act.trash", TrashScreen.trashedOf(materialList).size()), Lang.t("act.trash.tip"),
                () -> openGui(new TrashScreen(materialList, me, 0, ""))));
        manage.add(Cell.of(Lang.t("act.shareas"), Lang.t("act.shareas.tip"), () -> ProjectActions.shareAs(materialList, me)));
        manage.add(Cell.of(Lang.t("proj.settings"), Lang.t("tools.settings.tip"), () -> openGui(new ConfigScreen(me))));

        return List.of(new Tab(Lang.t("tools.tab.assign"), assign), new Tab(Lang.t("tools.tab.view"), view),
                new Tab(Lang.t("tools.tab.copy"), copy), new Tab(Lang.t("tools.tab.manage"), manage));
    }

    /** The material list screen (the project screen's own parent). */
    private Screen getParentList() {
        return getParent() instanceof ProjectScreen ps ? ps.listScreen() : getParent();
    }

    @Override
    GridScreen reopen(int tab, int page) {
        return new ProjectToolsScreen(materialList, getParent(), tab, page);
    }

    @Override
    public void tick() {
        super.tick();
        if (shownVersion != ClientProjectCache.version()) {
            openGui(reopen(tab, page));
        }
    }

    private final int shownVersion = ClientProjectCache.version();

    private void pickPlayerToReset(Project p) {
        ClientNet.requestPlayers();
        openGui(new PlayerPickScreen(this, p.name(), "", UiText.escapeFormat(p.name()), 0, "",
                who -> confirmResetPlayer(p, who), "resetplayer.pick"));
    }

    /** Takes one person off every item, after a dialog that says how much would be lost. */
    private void confirmResetPlayer(Project p, java.util.UUID who) {
        int assigned = 0;
        int collected = 0;
        for (var e : p.items().values()) {
            if (e.claims().containsKey(who)) {
                assigned++;
                collected += e.claims().get(who);
            }
        }
        String person = UiText.escapeFormat(ClientProjectCache.nameOf(who));
        if (assigned == 0 && p.items().values().stream().noneMatch(e -> e.targets().containsKey(who) || e.delivered().containsKey(who))) {
            fi.dy.masa.malilib.util.InfoUtils.showGuiMessage(fi.dy.masa.malilib.gui.Message.MessageType.WARNING,
                    Lang.t("resetplayer.none", person));
            openGui(this);
            return;
        }
        String name = p.name();
        openGui(new GuiConfirmAction(320, Lang.t("resetplayer.title"), new IConfirmationListener() {
            @Override
            public boolean onActionConfirmed() {
                ClientNet.sendResetPlayer(name, who);
                return false;
            }

            @Override
            public boolean onActionCancelled() {
                return false;
            }
        }, this, Lang.t("resetplayer.confirm", UiText.escapeFormat(name), person, assigned, collected)));
    }

    /** Takes everybody off every item, after a dialog that says how much would be lost. */
    private void confirmReset(Project p) {
        String name = p.name();
        int assigned = 0;
        int people = 0;
        int collected = 0;
        for (var e : p.items().values()) {
            if (!e.claims().isEmpty()) {
                assigned++;
                people += e.claims().size();
                collected += e.collectedTotal();
            }
        }
        openGui(new GuiConfirmAction(320, Lang.t("reset.title"), new IConfirmationListener() {
            @Override
            public boolean onActionConfirmed() {
                ClientNet.sendResetClaims(name);
                return false;
            }

            @Override
            public boolean onActionCancelled() {
                return false;
            }
        }, this, Lang.t("reset.confirm", UiText.escapeFormat(name), assigned, people, collected)));
    }
}
