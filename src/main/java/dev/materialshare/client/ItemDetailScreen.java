package dev.materialshare.client;

import dev.materialshare.client.config.Configs;
import dev.materialshare.common.Entry;
import dev.materialshare.common.Project;
import dev.materialshare.common.Stacks;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiConfirmAction;
import fi.dy.masa.malilib.gui.GuiTextInput;
import fi.dy.masa.malilib.interfaces.IConfirmationListener;
import fi.dy.masa.malilib.interfaces.IStringConsumerFeedback;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

/** One item of a project: priority, where it is kept, what may replace it, who collects how much, and removing it. */
class ItemDetailScreen extends GridScreen {
    private final String project;
    private final String itemId;
    private final int shownVersion = ClientProjectCache.version();

    ItemDetailScreen(Screen parent, String project, String itemId) {
        super(UiText.itemName(itemId), parent, 0, 0);
        this.project = project;
        this.itemId = itemId;
    }

    private Entry entry() {
        return ClientProjectCache.projects().stream().filter(p -> p.name().equals(project)).findFirst()
                .map(p -> p.items().get(itemId)).orElse(null);
    }

    @Override
    String info() {
        Entry e = entry();
        if (e == null) {
            return Lang.t("detail.gone");
        }
        StringBuilder sb = new StringBuilder();
        sb.append(e.priority() ? "§6★§r " : "").append("§f").append(UiText.itemName(itemId)).append("§r  ")
                .append(Lang.t("detail.counts", e.collectedTotal(), e.required(), e.targetTotal()));
        if (Configs.Generic.SHOW_DELIVERY.getBooleanValue()) {
            sb.append("  ").append(Lang.t("detail.delivered", e.deliveredTotal()));
        }
        int stack = dev.materialshare.McCompat.item(net.minecraft.util.Identifier.tryParse(itemId)).getMaxCount();
        String stacks = Stacks.format(e.required(), stack, Lang.TRANSLATOR);
        if (!stacks.isEmpty()) {
            sb.append("\n§7").append(Lang.t("row.stacks", stacks));
        }
        if (!e.substitute().isEmpty()) {
            sb.append("\n").append(Lang.t("row.substitute", UiText.escapeFormat(e.substitute())));
        }
        return sb.toString();
    }

    @Override
    List<Tab> tabs() {
        Entry e = entry();
        boolean has = e != null;
        String gone = Lang.t("detail.gone");
        Screen me = this;
        return List.of(new Tab("", List.of(
                Cell.when(has, e != null && e.priority() ? Lang.t("detail.unpriority") : Lang.t("detail.priority"), Lang.t("iteminfo.priority.tip"),
                        () -> ClientNet.sendItemInfo(project, itemId, !entry().priority()), gone),
                Cell.when(has, Lang.t("detail.substitute"), Lang.t("detail.substitute.tip"), () -> editText(me), gone),
                Cell.when(has, Lang.t("detail.split"), Lang.t("detail.split.tip"), () -> openGui(new SplitScreen(me, project, itemId, 0, "")), gone))));
    }

    private void editText(Screen me) {
        Entry e = entry();
        String name = UiText.escapeFormat(UiText.itemName(itemId));
        openGui(new GuiTextInput(Entry.MAX_TEXT, Lang.t("detail.substitute.title", name), e.substitute(), me,
                (IStringConsumerFeedback) text -> {
                    ClientNet.sendItemInfo(project, itemId, e.priority(), text);
                    return true;
                }));
    }

    @Override
    GridScreen reopen(int tab, int page) {
        return new ItemDetailScreen(getParent(), project, itemId);
    }

    @Override
    public void tick() {
        super.tick();
        if (shownVersion != ClientProjectCache.version()) {
            openGui(reopen(0, 0));
        }
    }
}
