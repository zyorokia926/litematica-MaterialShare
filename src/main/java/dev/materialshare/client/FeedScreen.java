package dev.materialshare.client;

import dev.materialshare.common.HistoryText;
import dev.materialshare.common.Packets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import net.minecraft.client.gui.screen.Screen;

/** The latest changes of every project, newest first: who did what, where. */
class FeedScreen extends SearchListScreen<Packets.FeedEntry> {
    private final int shownFeed = ClientProjectCache.feedVersion();

    private FeedScreen(Screen parent, int page, String query) {
        super(Lang.t("feed.title"), parent, page, query);
    }

    /** Opens the screen and asks the server for the latest changes. */
    static FeedScreen open(Screen parent) {
        ClientNet.requestFeed();
        return new FeedScreen(parent, 0, "");
    }

    @Override
    List<Packets.FeedEntry> candidates() {
        return ClientProjectCache.feed();
    }

    @Override
    String nameOf(Packets.FeedEntry f) {
        return UiText.escapeFormat(HistoryText.describe(f.event(), ClientProjectCache::nameOf, UiText::itemName, Lang.TRANSLATOR));
    }

    @Override
    String detailOf(Packets.FeedEntry f) {
        return new SimpleDateFormat("MM/dd HH:mm").format(new Date(f.event().time())) + "  ·  " + f.project();
    }

    @Override
    List<Act<Packets.FeedEntry>> actionsFor(Packets.FeedEntry f) {
        return List.of(new Act<>(Lang.t("overview.open"), Lang.t("overview.open.tip"), x -> ProjectOpener.open(x.project())));
    }

    @Override
    String helpText() {
        return Lang.t("feed.help");
    }

    @Override
    String emptyText() {
        return Lang.t("feed.empty");
    }

    @Override
    SearchListScreen<Packets.FeedEntry> reopen(int page, String query) {
        return new FeedScreen(getParent(), page, query);
    }

    @Override
    public void tick() {
        super.tick();
        if (shownFeed != ClientProjectCache.feedVersion()) {
            openGui(reopen(page, query));
        }
    }
}
