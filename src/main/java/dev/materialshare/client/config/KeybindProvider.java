package dev.materialshare.client.config;

import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.hotkeys.IKeybindManager;
import fi.dy.masa.malilib.hotkeys.IKeybindProvider;

public class KeybindProvider implements IKeybindProvider {
    public static final KeybindProvider INSTANCE = new KeybindProvider();

    @Override
    public void addKeysToMap(IKeybindManager manager) {
        for (IHotkey hotkey : Configs.Hotkeys.HOTKEY_LIST) {
            manager.addKeybindToMap(hotkey.getKeybind());
        }
    }

    @Override
    public void addHotkeys(IKeybindManager manager) {
        manager.addHotkeysForCategory("Litematica MaterialShare", dev.materialshare.client.Lang.t("config.tab.hotkeys"), Configs.Hotkeys.HOTKEY_LIST);
    }
}
