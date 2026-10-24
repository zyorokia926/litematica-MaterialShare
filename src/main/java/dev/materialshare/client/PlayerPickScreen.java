package dev.materialshare.client;

import dev.materialshare.common.PlayerNames;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;

/**
 * Lets anyone assign any player the server knows (online or not) to an item of the selected project. The search box
 * filters the list; a name that is not in the list can be looked up on the server by exact name.
 */
public class PlayerPickScreen extends GuiBase implements NoWheelClick {
    private static final int ROW_H = 22;
    private static final int FACE_COLUMN = 14;

    private final String project;
    private final String itemId;
    private final String itemName;
    private final int page;
    private final String query;
    private final java.util.function.Consumer<UUID> onPick;
    private final String titleKey;
    private final List<UUID> shown = new ArrayList<>();
    private int shownVersion = ClientProjectCache.namesVersion();
    private int faceRowY;
    private int backW;

    private int navW() {
        return Lang.buttonWidth(this, Lang.t("common.prev"), 60);
    }
    private int pages = 1;
    private int shownPage;
    private boolean nothingToShow;
    private boolean canLookup;

    public PlayerPickScreen(Screen parent, String project, String itemId, String itemName, int page, String query) {
        this(parent, project, itemId, itemName, page, query, null, "pick.title");
    }

    /** {@code onPick} replaces "assign this player": it gets the chosen player (hand over a claim, for example). */
    public PlayerPickScreen(Screen parent, String project, String itemId, String itemName, int page, String query,
            java.util.function.Consumer<UUID> onPick, String titleKey) {
        this.onPick = onPick;
        this.titleKey = titleKey;
        this.project = project;
        this.itemId = itemId;
        this.itemName = itemName;
        this.page = page;
        this.query = query;
        this.setParent(parent);
        this.setTitle(Lang.t(titleKey, itemName));
    }

    @Override
    public void initGui() {
        super.initGui();
        int x = 12;
        int y = 28;
        backW = Lang.buttonWidth(this, Lang.t("common.back"), 60);
        shown.clear();

        addButton(new ButtonGeneric(x, y, backW, 20, Lang.t("common.back")), (b, mb) -> openGui(getParent()));

        GuiTextFieldGeneric search = new GuiTextFieldGeneric(x + backW + 8, y, Math.min(220, this.width - backW - 40), 20,
                this.textRenderer);
        search.setMaxLength(16);
        search.setText(query);
        search.setFocused(true);
        search.setCursorToEnd();
        addTextField(search, field -> {
            if (!field.getText().equals(query)) {
                openGui(new PlayerPickScreen(getParent(), project, itemId, itemName, 0, field.getText(), onPick, titleKey));
            }
            return false;
        });

        // typing re-opens this screen, so the candidates update on every keystroke (best matches first)
        List<Map.Entry<UUID, String>> players = PlayerNames.filterAndSort(ClientProjectCache.players(), query);
        boolean exact = false;
        for (String known : ClientProjectCache.players().values()) {
            exact |= known.equalsIgnoreCase(query);
        }
        if (query.isEmpty()) {
            // with nothing typed, the people who are here right now come first
            players = new java.util.ArrayList<>(players);
            players.sort(Comparator.<Map.Entry<UUID, String>>comparingInt(e -> isOnline(e.getKey()) ? 0 : 1)
                    .thenComparing(e -> e.getValue().toLowerCase(Locale.ROOT)));
        }
        nothingToShow = players.isEmpty();
        canLookup = PlayerNames.isValidLookupName(query) && !exact;

        int rowY = 53 + UiText.wrappedHeight(Lang.t("pick.help"), this.width - 24) + 8; // below the help text
        if (canLookup) {
            int w = Math.max(160, Math.min(320, this.width - 24));
            addButton(new ButtonGeneric(x, rowY, w, 20, Lang.t("pick.lookup", query)),
                    (b, mb) -> ClientNet.sendLookup(query));
            rowY += ROW_H;
        }

        int perPage = Math.max(1, (this.height - rowY - 40) / ROW_H);
        pages = Math.max(1, (players.size() + perPage - 1) / perPage);
        int current = Math.min(page, pages - 1);
        shownPage = current;

        Set<UUID> already = ClientProjectCache.selected()
                .map(p -> p.items().get(itemId)).map(e -> e.claims().keySet()).orElse(Set.of());
        faceRowY = rowY;
        for (int i = current * perPage; i < Math.min(players.size(), (current + 1) * perPage); i++) {
            UUID uuid = players.get(i).getKey();
            String name = players.get(i).getValue();
            shown.add(uuid);
            boolean claimed = already.contains(uuid);
            boolean full = !claimed && already.size() >= dev.materialshare.common.Project.MAX_CLAIMANTS;
            int w = Math.max(120, Math.min(240, this.width - 24 - FACE_COLUMN));
            String label = (isOnline(uuid) ? "§a●§r " : "§8●§r ") + (claimed ? "§7" + name + Lang.t("pick.assigned") : name);
            addButton(new ButtonGeneric(x + FACE_COLUMN, rowY, w, 20, label), (b, mb) -> {
                if (onPick != null) {
                    onPick.accept(uuid);
                    return;
                }
                if (full) {
                    RowOverlay.tooManyClaimants();
                    return;
                }
                if (!claimed) {
                    ClientNet.sendClaim(project, itemId, uuid, true);
                }
                openGui(getParent());
            });
            rowY += ROW_H;
        }
        if (pages > 1) {
            int navY = this.height - 28;
            if (current > 0) {
                addButton(new ButtonGeneric(x, navY, navW(), 20, Lang.t("common.prev")), (b, mb) -> openGui(
                        new PlayerPickScreen(getParent(), project, itemId, itemName, current - 1, query, onPick, titleKey)));
            }
            if (current < pages - 1) {
                addButton(new ButtonGeneric(x + navW() + 8, navY, navW(), 20, Lang.t("common.next")), (b, mb) -> openGui(
                        new PlayerPickScreen(getParent(), project, itemId, itemName, current + 1, query, onPick, titleKey)));
            }
        }
    }

    private static boolean isOnline(UUID id) {
        var handler = MinecraftClient.getInstance().getNetworkHandler();
        return handler != null && handler.getPlayerListEntry(id) != null;
    }

    /** Re-opens itself (keeping the search text) when the known players change on the server. */
    @Override
    public void tick() {
        super.tick();
        if (shownVersion != ClientProjectCache.namesVersion()) {
            openGui(new PlayerPickScreen(getParent(), project, itemId, itemName, page, query, onPick, titleKey));
        }
    }

    @Override
    protected void drawContents(MatrixStack matrices, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(matrices, mouseX, mouseY, partialTicks);
        UiText.drawWrapped(matrices, Lang.t("pick.help"),
                12, 53, this.width - 24, 0xFFAAAAAA);
        int legendX = 12 + backW + 8 + Math.min(220, this.width - backW - 40) + 10;
        String legend = Lang.t("pick.legend");
        if (legendX + getStringWidth(legend) + 6 < this.width) {
            drawStringWithShadow(matrices, legend, legendX, 34, 0xFFAAAAAA);
        }
        int y = faceRowY;
        for (UUID uuid : shown) {
            RowOverlay.drawFace(matrices, uuid, 12, y + 6);
            y += ROW_H;
        }
        if (nothingToShow && !canLookup) {
            drawStringWithShadow(matrices, query.isEmpty() ? Lang.t("pick.empty") : Lang.t("pick.nomatch"), 12,
                    faceRowY + 4, 0xFFFFFFFF);
        }
        if (pages > 1) {
            drawStringWithShadow(matrices, Lang.t("common.page", shownPage + 1, pages), 12 + 2 * navW() + 16, this.height - 22,
                    0xFFFFFFFF);
        }
    }
}
