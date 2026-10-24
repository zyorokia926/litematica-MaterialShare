package dev.materialshare.client;

import dev.materialshare.MaterialShare;
import dev.materialshare.client.config.Configs;
import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;

/** Drag the on-screen list (the HUD) to wherever it should sit; the place is kept as a share of the free room. */
public class HudPositionScreen extends GuiBase {
    private HudOverlay.Layout layout;
    private boolean dragging;
    private int grabX;
    private int grabY;

    private HudPositionScreen(Screen parent) {
        this.setParent(parent);
        this.setTitle(Lang.t("hudplace.title"));
    }

    public static void open(Screen parent) {
        GuiBase.openGui(new HudPositionScreen(parent));
    }

    @Override
    public void initGui() {
        super.initGui();
        MinecraftClient client = MinecraftClient.getInstance();
        layout = HudOverlay.sample(client);
        int x = 12;
        String done = Lang.t("hudplace.done");
        int doneW = Lang.buttonWidth(this, done, 60);
        addButton(new ButtonGeneric(x, this.height - 30, doneW, 20, done), (b, mb) -> {
            save();
            openGui(getParent());
        });
        String reset = Lang.t("hudplace.reset");
        int resetW = Lang.buttonWidth(this, reset, 60);
        ButtonGeneric resetButton = new ButtonGeneric(x + doneW + 6, this.height - 30, resetW, 20, reset);
        resetButton.setHoverStrings(UiText.tip(Lang.t("hudplace.reset.tip")));
        addButton(resetButton, (b, mb) -> {
            Configs.Hud.X.setIntegerValue(1000);
            Configs.Hud.Y.setIntegerValue(1000);
            save();
        });
    }

    private static void save() {
        ConfigManager.getInstance().onConfigsChanged(MaterialShare.MOD_ID);
    }

    private int[] boxOrigin() {
        return HudOverlay.origin(MinecraftClient.getInstance(), layout);
    }

    @Override
    public boolean onMouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (super.onMouseClicked(mouseX, mouseY, mouseButton)) {
            return true;
        }
        int[] at = boxOrigin();
        int[] size = HudOverlay.size(layout);
        if (mouseButton == 0 && mouseX >= at[0] && mouseX < at[0] + size[0] && mouseY >= at[1] && mouseY < at[1] + size[1]) {
            dragging = true;
            grabX = mouseX - at[0];
            grabY = mouseY - at[1];
            return true;
        }
        return false;
    }

    @Override
    public boolean onMouseReleased(int mouseX, int mouseY, int mouseButton) {
        if (dragging) {
            dragging = false;
            save();
            return true;
        }
        return super.onMouseReleased(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void drawContents(MatrixStack matrices, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(matrices, mouseX, mouseY, partialTicks);
        MinecraftClient client = MinecraftClient.getInstance();
        if (dragging) {
            int[] size = HudOverlay.size(layout);
            int freeX = Math.max(0, this.width - size[0] - 8);
            int freeY = Math.max(0, this.height - size[1] - 8);
            int x = Math.max(4, Math.min(4 + freeX, mouseX - grabX));
            int y = Math.max(4, Math.min(4 + freeY, mouseY - grabY));
            Configs.Hud.X.setIntegerValue(freeX == 0 ? 0 : (x - 4) * 1000 / freeX);
            Configs.Hud.Y.setIntegerValue(freeY == 0 ? 0 : (y - 4) * 1000 / freeY);
        }
        int[] at = boxOrigin();
        int[] size = HudOverlay.size(layout);
        int border = dragging ? 0xFFFFFF55 : 0xFFAAAAAA;
        DrawableHelper.fill(matrices, at[0] - 2, at[1] - 2, at[0] + size[0] + 2, at[1] - 1, border);
        DrawableHelper.fill(matrices, at[0] - 2, at[1] + size[1] + 1, at[0] + size[0] + 2, at[1] + size[1] + 2, border);
        DrawableHelper.fill(matrices, at[0] - 2, at[1] - 1, at[0] - 1, at[1] + size[1] + 1, border);
        DrawableHelper.fill(matrices, at[0] + size[0] + 1, at[1] - 1, at[0] + size[0] + 2, at[1] + size[1] + 1, border);
        HudOverlay.draw(matrices, client, layout, at[0], at[1]);
        UiText.drawWrapped(matrices, Lang.t("hudplace.help"), 12, 28, Math.min(this.width - 24, 420), 0xFFDDDDDD);
    }
}
