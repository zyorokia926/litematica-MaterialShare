package dev.materialshare.client.mixin;

import dev.materialshare.client.Lang;
import dev.materialshare.client.UiText;
import dev.materialshare.client.config.ConfigScreen;
import fi.dy.masa.litematica.gui.GuiMainMenu;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import dev.materialshare.client.MenuIcon;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Puts this add-on's buttons next to Litematica's own menu. Other add-ons (Syncmatica...) put buttons in the same
 * corner, so the place is searched: the first free spot from the top right, never on top of another button.
 */
@Mixin(GuiMainMenu.class)
public abstract class GuiMainMenuMixin {
    private static final int HEIGHT = 20;
    private static final int MARGIN = 1;
    private static final int FIRST_ROW_Y = 30;
    private static final int ROW_STEP = 22;

    @Inject(method = "initGui", at = @At("TAIL"), remap = false)
    private void materialshare$addButtons(CallbackInfo ci) {
        GuiBase gui = (GuiBase) (Object) this;
        ButtonGeneric config = materialshare$place(gui, Lang.t("menu.settings"), Lang.t("menu.settings.tip"), MenuIcon.SETTINGS);
        gui.addButton(config, (b, mouseButton) -> GuiBase.openGui(new ConfigScreen(gui)));
        ButtonGeneric help = materialshare$place(gui, Lang.t("menu.help"), Lang.t("menu.help.tip"), MenuIcon.HELP);
        gui.addButton(help, (b, mouseButton) -> GuiBase.openGui(new dev.materialshare.client.HelpScreen(gui, 0)));
    }

    /**
     * Same size and the same grid as Litematica's own buttons: the width most of them have, rows 22 apart from the
     * first one; the first free place that does not touch any other button, column by column from the left.
     */
    private static ButtonGeneric materialshare$place(GuiBase gui, String label, String tip, MenuIcon icon) {
        List<ButtonBase> others = ((GuiBaseAccessor) gui).materialshare$buttons();
        int width = materialshare$commonWidth(others);
        width = Math.max(width, gui.getStringWidth(label) + 16 + 16);
        TreeSet<Integer> columns = new TreeSet<>();
        for (ButtonBase other : others) {
            if (other.getWidth() == width || other.getWidth() >= width - 24) {
                columns.add(other.getX());
            }
        }
        if (columns.isEmpty()) {
            columns.add(12);
        }
        int x = columns.first();
        int y = FIRST_ROW_Y;
        search:
        for (int row = 0; FIRST_ROW_Y + row * ROW_STEP <= gui.height - HEIGHT - 4; row++) {
            int candidate = FIRST_ROW_Y + row * ROW_STEP;
            for (int column : columns) {
                if (column + width > gui.width - 4) {
                    continue;
                }
                boolean free = true;
                for (ButtonBase other : others) {
                    boolean ox = column < other.getX() + other.getWidth() + MARGIN && other.getX() < column + width + MARGIN;
                    boolean oy = candidate < other.getY() + other.getHeight() + MARGIN && other.getY() < candidate + HEIGHT + MARGIN;
                    if (ox && oy) {
                        free = false;
                        break;
                    }
                }
                if (free) {
                    x = column;
                    y = candidate;
                    break search;
                }
            }
        }
        ButtonGeneric button = new ButtonGeneric(x, y, width, HEIGHT, label, icon, UiText.tip(tip));
        return button;
    }

    /** The width most buttons of the menu have (Litematica gives its own buttons all the same width). */
    private static int materialshare$commonWidth(List<ButtonBase> buttons) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (ButtonBase button : buttons) {
            if (button.getHeight() == HEIGHT && button.getWidth() >= 80) {
                counts.merge(button.getWidth(), 1, Integer::sum);
            }
        }
        int best = 150;
        int bestCount = 0;
        for (Map.Entry<Integer, Integer> e : counts.entrySet()) {
            if (e.getValue() > bestCount) {
                best = e.getKey();
                bestCount = e.getValue();
            }
        }
        return best;
    }
}
