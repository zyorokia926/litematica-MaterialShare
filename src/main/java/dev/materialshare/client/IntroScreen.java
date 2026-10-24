package dev.materialshare.client;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;

/** Shown once, the first time a shared material list is opened: what the new column and buttons are for. */
public class IntroScreen extends GuiBase {
    private static final String[] KEYS = {"intro.1", "intro.2", "intro.3", "intro.4"};

    public IntroScreen(Screen parent) {
        this.setParent(parent);
        this.setTitle(Lang.t("intro.title"));
    }

    @Override
    public void initGui() {
        super.initGui();
        int w = Lang.buttonWidth(this, Lang.t("intro.ok"), 80);
        addButton(new ButtonGeneric(this.width / 2 - w / 2, this.height - 40, w, 20, Lang.t("intro.ok")),
                (b, mb) -> openGui(getParent()));
        int hw = Lang.buttonWidth(this, Lang.t("intro.help"), 80);
        addButton(new ButtonGeneric(this.width / 2 - hw / 2, this.height - 64, hw, 20, Lang.t("intro.help")),
                (b, mb) -> openGui(new HelpScreen(this, 0)));
    }

    @Override
    protected void drawContents(MatrixStack matrices, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(matrices, mouseX, mouseY, partialTicks);
        int y = 34;
        int width = Math.min(this.width - 40, 420);
        int x = this.width / 2 - width / 2;
        for (String key : KEYS) {
            y = UiText.drawWrapped(matrices, Lang.t(key), x, y, width, 0xFFFFFFFF) + 8;
        }
    }
}
