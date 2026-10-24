package dev.materialshare.client.config;

import dev.materialshare.MaterialShare;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import java.util.List;
import net.minecraft.client.gui.screen.Screen;

/** The settings screen (opened from Litematica's menu, Mod Menu, the project screen or a hotkey). */
public class ConfigScreen extends GuiConfigsBase {
    private enum Tab {
        GENERIC("config.tab.generic"), HOTKEYS("config.tab.hotkeys");

        final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    private static Tab tab = Tab.GENERIC;

    public ConfigScreen(Screen parent) {
        super(10, 50, MaterialShare.MOD_ID, parent, dev.materialshare.client.Lang.t("config.title"));
    }

    @Override
    public void initGui() {
        super.initGui();
        clearOptions();
        int x = 10;
        for (Tab t : Tab.values()) {
            ButtonGeneric button = new ButtonGeneric(x, 26, getStringWidth(dev.materialshare.client.Lang.t(t.label)) + 10, 20, dev.materialshare.client.Lang.t(t.label));
            button.setEnabled(tab != t);
            addButton(button, new TabListener(t, this));
            x += button.getWidth() + 2;
        }
        String place = dev.materialshare.client.Lang.t("config.hudplace");
        ButtonGeneric where = new ButtonGeneric(x + 8, 26, getStringWidth(place) + 10, 20, place);
        where.setHoverStrings(dev.materialshare.client.UiText.tip(dev.materialshare.client.Lang.t("config.hudplace.tip")));
        addButton(where, (b, mb) -> dev.materialshare.client.HudPositionScreen.open(this));
    }

    @Override
    public List<ConfigOptionWrapper> getConfigs() {
        List<? extends IConfigBase> configs = tab == Tab.GENERIC ? Configs.Generic.OPTIONS : Configs.Hotkeys.HOTKEY_LIST;
        return ConfigOptionWrapper.createFor(configs);
    }

    private record TabListener(Tab target, ConfigScreen screen) implements IButtonActionListener {
        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
            tab = target;
            screen.reCreateListWidget();
            screen.getListWidget().resetScrollbarPosition();
            screen.initGui();
        }
    }
}
