package dev.materialshare.client;

import dev.materialshare.common.Assignments;
import dev.materialshare.common.Project;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.Message;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.util.InfoUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;

/** Hands out every item nobody has taken yet: all to yourself, or evenly among the players you tick. */
public class BulkScreen extends GuiBase implements NoWheelClick {
    private static final int ROW_H = 22;

    private final MaterialListBase materialList;
    private final int page;
    private final Set<UUID> chosen;
    private int unclaimed;
    private int pages = 1;
    private int shownPage;
    private int listTop;

    private int navW() {
        return Lang.buttonWidth(this, Lang.t("common.prev"), 60);
    }

    public BulkScreen(MaterialListBase materialList, Screen parent, Set<UUID> chosen, int page) {
        this.materialList = materialList;
        this.page = page;
        this.chosen = chosen != null ? chosen : onlinePlayers();
        this.setParent(parent);
        this.setTitle(Lang.t("bulk.title"));
    }

    private static Set<UUID> onlinePlayers() {
        Set<UUID> online = new HashSet<>();
        var handler = MinecraftClient.getInstance().getNetworkHandler();
        for (UUID id : ClientProjectCache.players().keySet()) {
            if (handler != null && handler.getPlayerListEntry(id) != null) {
                online.add(id);
            }
        }
        return online;
    }

    @Override
    public void initGui() {
        super.initGui();
        int x = 12;
        int y = 28;
        Project project = ClientProjectCache.selected().orElse(null);
        UUID self = RowOverlay.selfId();
        addButton(new ButtonGeneric(x, y, Lang.buttonWidth(this, Lang.t("common.back"), 60), 20, Lang.t("common.back")), (b, mb) -> openGui(getParent()));
        if (project == null || self == null) {
            unclaimed = 0;
            return;
        }
        unclaimed = Assignments.allTo(project, self).size();

        int rowY = 53 + UiText.wrappedHeight(Lang.t("bulk.help"), this.width - 24) + 8;
        ButtonGeneric mine = new ButtonGeneric(x, rowY, Math.min(300, this.width - 24), 20,
                Lang.t("bulk.all", unclaimed));
        mine.setEnabled(unclaimed > 0);
        addButton(mine, (b, mb) -> {
            ClientNet.sendBulk(project.name(), Assignments.allTo(project, self));
            done(Lang.t("bulk.all.done", unclaimed));
        });
        rowY += ROW_H + 4;

        List<UUID> picked = new ArrayList<>(chosen);
        picked.retainAll(ClientProjectCache.players().keySet());
        ButtonGeneric spread = new ButtonGeneric(x, rowY, Math.min(300, this.width - 24), 20,
                Lang.t("bulk.spread", picked.size()));
        spread.setEnabled(unclaimed > 0 && !picked.isEmpty());
        addButton(spread, (b, mb) -> {
            ClientNet.sendBulk(project.name(), Assignments.spread(project, picked));
            done(Lang.t("bulk.spread.done", unclaimed, picked.size()));
        });
        rowY += ROW_H + 18; // room for the caption of the list below
        listTop = rowY;

        var handler = MinecraftClient.getInstance().getNetworkHandler();
        List<Map.Entry<UUID, String>> players = new ArrayList<>(ClientProjectCache.players().entrySet());
        players.sort(Comparator
                .<Map.Entry<UUID, String>>comparingInt(
                        e -> handler != null && handler.getPlayerListEntry(e.getKey()) != null ? 0 : 1)
                .thenComparing(e -> e.getValue().toLowerCase(Locale.ROOT)));
        int perPage = Math.max(1, (this.height - rowY - 40) / ROW_H);
        pages = Math.max(1, (players.size() + perPage - 1) / perPage);
        int current = Math.min(page, pages - 1);
        shownPage = current;
        for (int i = current * perPage; i < Math.min(players.size(), (current + 1) * perPage); i++) {
            UUID id = players.get(i).getKey();
            boolean on = chosen.contains(id);
            boolean online = handler != null && handler.getPlayerListEntry(id) != null;
            String label = (on ? "§a☑§r " : "§7☐§r ") + (online ? "§a●§r " : "§8●§r ") + players.get(i).getValue();
            addButton(new ButtonGeneric(x + 14, rowY, Math.min(240, this.width - 40), 20, label), (b, mb) -> {
                Set<UUID> next = new HashSet<>(chosen);
                if (!next.remove(id)) {
                    next.add(id);
                }
                openGui(new BulkScreen(materialList, getParent(), next, current));
            });
            rowY += ROW_H;
        }
        if (pages > 1) {
            int navY = this.height - 28;
            if (current > 0) {
                addButton(new ButtonGeneric(x, navY, navW(), 20, Lang.t("common.prev")),
                        (b, mb) -> openGui(new BulkScreen(materialList, getParent(), chosen, current - 1)));
            }
            if (current < pages - 1) {
                addButton(new ButtonGeneric(x + navW() + 8, navY, navW(), 20, Lang.t("common.next")),
                        (b, mb) -> openGui(new BulkScreen(materialList, getParent(), chosen, current + 1)));
            }
        }
    }

    private void done(String message) {
        InfoUtils.showGuiMessage(Message.MessageType.SUCCESS, Lang.t("bulk.done", message));
        openGui(getParent());
    }

    @Override
    protected void drawContents(MatrixStack matrices, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(matrices, mouseX, mouseY, partialTicks);
        UiText.drawWrapped(matrices, Lang.t("bulk.help"), 12, 53,
                this.width - 24, 0xFFAAAAAA);
        if (ClientProjectCache.selected().isEmpty()) {
            drawStringWithShadow(matrices, Lang.t("bulk.noproject"), 12,
                    53 + UiText.wrappedHeight(Lang.t("bulk.help"), this.width - 24) + 8, 0xFFFFFFFF);
            return;
        }
        drawStringWithShadow(matrices, Lang.t("bulk.people"), 12, listTop - 14, 0xFFAAAAAA);
        if (pages > 1) {
            drawStringWithShadow(matrices, Lang.t("common.page", shownPage + 1, pages), 12 + 2 * navW() + 16, this.height - 22,
                    0xFFFFFFFF);
        }
    }
}
