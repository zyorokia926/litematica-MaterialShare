package dev.materialshare.client.mixin;

import dev.materialshare.client.ClientFilter;
import dev.materialshare.client.Lang;
import dev.materialshare.client.UiText;
import dev.materialshare.common.ButtonLayout;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import java.util.ArrayList;
import java.util.List;
import dev.materialshare.client.TrashScreen;
import dev.materialshare.client.SyncmaticaCheck;
import dev.materialshare.client.Parts;
import dev.materialshare.common.ProjectRegistry;
import dev.materialshare.client.ListRefresh;
import fi.dy.masa.litematica.gui.widgets.WidgetListMaterialList;
import fi.dy.masa.litematica.gui.widgets.WidgetMaterialListEntry;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import fi.dy.masa.malilib.gui.GuiListBase;
import dev.materialshare.client.ClientProjectCache;
import dev.materialshare.client.Undo;
import dev.materialshare.client.ClientNet;
import dev.materialshare.client.ProjectNames;
import dev.materialshare.client.ProjectScreen;
import fi.dy.masa.litematica.gui.GuiMaterialList;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiMaterialList.class)
public abstract class GuiMaterialListMixin
        extends GuiListBase<MaterialListEntry, WidgetMaterialListEntry, WidgetListMaterialList>
        implements ListRefresh {
    protected GuiMaterialListMixin(int listX, int listY) {
        super(listX, listY); // never called: Mixin only needs this class to look like a subclass of the target's super
    }

    private static final int MAX_LABEL_NAME = 24;
    /** Space kept free at the top right for Litematica's info icon. */
    private static final int TOP_RIGHT_RESERVED = 34;
    private static final int TITLE_GAP = 8;
    private static final int MIN_TITLE_WIDTH = 120;
    private static final int TITLE_SHARE_PERCENT = 40;
    private static final int BUTTON_GAP = 4;
    /**
     * The top strip is only 22 pixels high: Litematica's second row (Clear cache, Write to file, the Multiplier box)
     * starts at y = 22, so a button in the strip must end above that, whatever the GUI scale is.
     */
    private static final int TOP_Y = 2;
    private static final int TOP_H = 18;
    private static final int SECOND_ROW_Y = 22;
    /** Space kept free at the right of the second row for Litematica's multiplier box. */
    private static final int SECOND_ROW_RIGHT_RESERVED = 110;

    @Shadow(remap = false)
    public abstract MaterialListBase getMaterialList();

    @Override
    public void materialshare$refresh() {
        getListWidget().refreshEntries();
    }

    /**
     * One button, top right: "共有を開始: <schematic>" until the list is shared, then "プロジェクト: <name>" which opens
     * the project list. No name has to be typed: the project is named after the schematic.
     */
    @Inject(method = "initGui", at = @At("TAIL"), remap = false)
    private void materialshare$addProjectButton(CallbackInfo ci) {
        GuiBase gui = (GuiBase) (Object) this;
        MaterialListBase list = getMaterialList();
        ProjectNames.autoSelect(list);
        String name = ProjectNames.forList(list);
        // a schematic with several sub-regions: one project per part, picked on the part screen
        boolean parts = !Parts.regions(list).isEmpty();
        boolean shared = ClientProjectCache.exists(name)
                || ClientProjectCache.selected().map(p -> ProjectNames.belongs(list, p.name())).orElse(false);

        if (shared && dev.materialshare.client.config.Configs.Generic.SHOW_INTRO.getBooleanValue()) {
            // the first time: a short how-to (once; the setting can be switched on again to see it again)
            dev.materialshare.client.config.Configs.Generic.SHOW_INTRO.setBooleanValue(false);
            net.minecraft.client.MinecraftClient.getInstance().execute(() -> GuiBase.openGui(new dev.materialshare.client.IntroScreen(gui)));
        }
        if (!shared && !parts && !ProjectNames.isUnnamed(list) && ProjectNames.autoStart(list, gui)) {
            return; // the schematic is being set up; this screen is opened again when the server has answered
        }

        // The three buttons share the strip at the top between the title and Litematica's info icon. They are
        // fitted into the free width (shorter labels first, then the least important button is left out), so they
        // never overlap the title, each other or the icon.
        String shortName = clip(name, MAX_LABEL_NAME);
        String[] mainLabels;
        if (shared) {
            String selectedName = ClientProjectCache.selected().map(p -> p.name()).orElse(Lang.t("list.none"));
            mainLabels = new String[] {Lang.t("list.project", clip(selectedName, MAX_LABEL_NAME)),
                    Lang.t("list.project", clip(selectedName, 12)), Lang.t("list.project", clip(selectedName, 6)),
                    Lang.t("list.project.short")};
        } else {
            mainLabels = new String[] {Lang.t("list.share", shortName), Lang.t("list.share", clip(name, 12)),
                    Lang.t("list.share", clip(name, 6)), Lang.t("list.share.short")};
        }
        boolean withTools = shared && ClientProjectCache.selected().isPresent();
        String[] filterLabels = {ClientFilter.fullLabelWidest(), ClientFilter.shortLabelWidest()};
        String undoLabel = Lang.t("list.undo");
        String trashLabel = Lang.t("act.trash", TrashScreen.trashedOf(list).size());

        List<int[]> options = new ArrayList<>();
        options.add(widths(gui, mainLabels));
        if (withTools) {
            options.add(widths(gui, filterLabels));
            options.add(widths(gui, new String[] {undoLabel}));
        } else if (!shared) {
            // the trash is reachable without the schematic being shared: deleted ones can be set up again from there
            options.add(widths(gui, new String[] {trashLabel}));
        }
        int right = gui.width - TOP_RIGHT_RESERVED;
        // a long title gives way to the buttons: it takes at most this share of the width and ends with "…" (see drawTitle)
        int titleCap = Math.max(MIN_TITLE_WIDTH, gui.width * TITLE_SHARE_PERCENT / 100);
        int left = 20 + Math.min(gui.getStringWidth(materialshare$title()), titleCap) + TITLE_GAP;
        List<Integer> chosen = ButtonLayout.fit(right - left, BUTTON_GAP, options);
        int mainX;
        int mainY = TOP_Y;
        int mainW;
        int mainLevel;
        if (!chosen.isEmpty()) {
            mainW = chosen.get(0);
            mainLevel = levelOf(options.get(0), mainW);
            mainX = right - mainW;
        } else {
            // not even the shortest label fits beside the title: use the free gap in the second row instead,
            // between Litematica's own buttons on the left and its multiplier box on the right
            int[] w = options.get(0);
            int rowEnd = 10;
            for (ButtonBase other : ((GuiBaseAccessor) gui).materialshare$buttons()) {
                if (other.getY() >= 22 && other.getY() <= 48) {
                    rowEnd = Math.max(rowEnd, other.getX() + other.getWidth());
                }
            }
            int gapLeft = rowEnd + 6;
            int gapWidth = gui.width - SECOND_ROW_RIGHT_RESERVED - gapLeft;
            mainLevel = w.length - 1;
            for (int i = 0; i < w.length; i++) {
                if (w[i] <= gapWidth) {
                    mainLevel = i;
                    break;
                }
            }
            mainW = w[mainLevel];
            boolean fitsSecondRow = mainW <= gapWidth;
            mainX = fitsSecondRow ? gapLeft : right - mainW;
            mainY = fitsSecondRow ? SECOND_ROW_Y : TOP_Y;
            chosen = List.of(mainW);
        }
        // the title stops before the leftmost button of the top strip (or before the info icon when there is none)
        int stopAt = gui.width - TOP_RIGHT_RESERVED;
        for (int i = 0; i < chosen.size() && mainY == TOP_Y; i++) {
            stopAt = Math.min(stopAt, mainX - sumBefore(chosen, i));
        }
        materialshare$titleRight = stopAt - TITLE_GAP;
        String mainLabel = mainLabels[Math.min(mainLevel, mainLabels.length - 1)];
        ButtonGeneric button = new ButtonGeneric(mainX, mainY, mainW, mainY == TOP_Y ? TOP_H : 20, mainLabel);
        if (!shared && !SyncmaticaCheck.isShared(list)) {
            // only a schematic that has been shared with Syncmatica can be shared here
            button.setEnabled(false);
            button.setHoverStrings(UiText.tip(Lang.t("share.needsync")));
        }
        gui.addButton(button, (b, mouseButton) -> {
            if (shared || parts) {
                GuiBase.openGui(new ProjectScreen(list, gui, 0));
            } else if (ProjectNames.isUnnamed(list)) {
                GuiBase.openGui(ProjectNames.nameDialog(list, gui));
            } else {
                ProjectNames.startSharing(list, gui);
            }
        });

        int x = button.getX();
        if (!withTools && !shared && chosen.size() >= 2) {
            int trashW = chosen.get(1);
            ButtonGeneric trash = new ButtonGeneric(x - BUTTON_GAP - trashW, TOP_Y, trashW, TOP_H, trashLabel);
            trash.setHoverStrings(UiText.tip(Lang.t("act.trash.tip")));
            gui.addButton(trash, (b, mouseButton) -> GuiBase.openGui(new TrashScreen(list, gui, 0, "")));
        }
        if (withTools && chosen.size() >= 2) {
            int filterW = chosen.get(1);
            boolean compact = levelOf(options.get(1), filterW) > 0;
            x -= BUTTON_GAP + filterW;
            ButtonGeneric filter = new ButtonGeneric(x, TOP_Y, filterW, TOP_H, ClientFilter.label(compact));
            ClientFilter.bind(filter, compact);
            gui.addButton(filter, (b, mouseButton) -> {
                ClientFilter.cycle();
                materialshare$refresh();
                if (ClientFilter.current() == dev.materialshare.common.RowFilter.PERSON) {
                    // the "person" filter: pick who (the list shows only the items that person takes)
                    ClientNet.requestPlayers();
                    GuiBase.openGui(new dev.materialshare.client.PlayerPickScreen(gui, "", "", "", 0, "", who -> {
                        ClientFilter.setPerson(who);
                        materialshare$refresh();
                        GuiBase.openGui(gui);
                    }, "filter.person.title"));
                }
            });
        }
        if (withTools && chosen.size() >= 3) {
            int undoW = chosen.get(2);
            x -= BUTTON_GAP + undoW;
            ButtonGeneric undo = new ButtonGeneric(x, TOP_Y, undoW, TOP_H, undoLabel);
            undo.setHoverStrings(UiText.tip(Lang.t("list.undo.tip")));
            gui.addButton(undo, (b, mouseButton) -> Undo.undo());
        }
    }

    private static int sumBefore(List<Integer> widths, int index) {
        int sum = 0;
        for (int i = 1; i <= index; i++) {
            sum += BUTTON_GAP + widths.get(i);
        }
        return sum;
    }

    /** Litematica's title, with the part's name behind it when a part of a schematic with sub-regions is selected. */
    @Unique
    private String materialshare$title() {
        String title = getTitleString();
        return Parts.scopeOf(getMaterialList()).map(region -> title + " / " + region).orElse(title);
    }

    @Unique
    private int materialshare$titleRight = Integer.MAX_VALUE;

    /** The title as Litematica draws it, but cut with "…" before it would run into the buttons or the info icon. */
    @Override
    protected void drawTitle(MatrixStack matrices, int mouseX, int mouseY, float partialTicks) {
        String title = materialshare$title();
        int room = materialshare$titleRight - 20;
        if (room > 0 && getStringWidth(title) > room) {
            title = UiText.fit(title, room);
        }
        this.drawString(matrices, title, 20, 10, -1);
    }

    /** Button widths for each label option (text plus the button's padding). */
    private static int[] widths(GuiBase gui, String[] labels) {
        int[] w = new int[labels.length];
        for (int i = 0; i < labels.length; i++) {
            w[i] = gui.getStringWidth(labels[i]) + 12;
        }
        return w;
    }

    private static int levelOf(int[] options, int width) {
        for (int i = 0; i < options.length; i++) {
            if (options[i] == width) {
                return i;
            }
        }
        return options.length - 1;
    }

    private static String clip(String name, int max) {
        return name.length() <= max ? name : name.substring(0, max) + "…";
    }
}
