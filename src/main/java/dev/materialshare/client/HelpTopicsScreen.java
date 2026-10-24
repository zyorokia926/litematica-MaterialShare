package dev.materialshare.client;

import java.util.List;
import net.minecraft.client.gui.screen.Screen;

/** The list of all pages of the guide (searchable): pick one to read it. */
class HelpTopicsScreen extends SearchListScreen<HelpScreen.Page> {
    HelpTopicsScreen(Screen parent, int page, String query) {
        super(Lang.t("help.topics.title"), parent, page, query);
    }

    @Override
    List<HelpScreen.Page> candidates() {
        return HelpScreen.PAGES;
    }

    @Override
    String nameOf(HelpScreen.Page p) {
        return HelpScreen.text(p, "title");
    }

    @Override
    String detailOf(HelpScreen.Page p) {
        return "§7" + HelpScreen.text(p, "short");
    }

    @Override
    List<Act<HelpScreen.Page>> actionsFor(HelpScreen.Page p) {
        Screen back = getParent() instanceof HelpScreen h ? h.getParent() : getParent();
        return List.of(new Act<>(Lang.t("help.open"), "", x -> openGui(new HelpScreen(back, HelpScreen.PAGES.indexOf(x)))));
    }

    @Override
    String helpText() {
        return Lang.t("help.topics.help");
    }

    @Override
    String emptyText() {
        return Lang.t("help.topics.empty");
    }

    @Override
    SearchListScreen<HelpScreen.Page> reopen(int page, String query) {
        return new HelpTopicsScreen(getParent(), page, query);
    }
}
