package dev.materialshare.client.config;

import dev.materialshare.MaterialShare;
import dev.materialshare.client.ProjectScreen;
import fi.dy.masa.litematica.gui.GuiMaterialList;
import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.event.InputEventHandler;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.hotkeys.KeyCallbackToggleBoolean;
import fi.dy.masa.malilib.interfaces.IInitializationHandler;
import net.minecraft.client.MinecraftClient;

/** Registers the config file, the hotkeys and their actions once malilib is ready. */
public class InitHandler implements IInitializationHandler {
    @Override
    public void registerModHandlers() {
        ConfigManager.getInstance().registerConfigHandler(MaterialShare.MOD_ID, new ConfigHandler());
        InputEventHandler.getKeybindManager().registerKeybindProvider(KeybindProvider.INSTANCE);

        Configs.Hotkeys.OPEN_CONFIG_GUI.getKeybind().setCallback((action, key) -> {
            GuiBase.openGui(new ConfigScreen(MinecraftClient.getInstance().currentScreen));
            return true;
        });
        Configs.Hotkeys.TOGGLE_OVERLAY.getKeybind()
                .setCallback(new KeyCallbackToggleBoolean(Configs.Generic.SHOW_OVERLAY));
        Configs.Hotkeys.OPEN_BREAKDOWN.getKeybind().setCallback((action, key) -> {
            dev.materialshare.client.ProjectsEntry.openBreakdown();
            return true;
        });
        Configs.Hotkeys.TOGGLE_HUD.getKeybind().setCallback(new KeyCallbackToggleBoolean(Configs.Generic.SHOW_HUD));
        Configs.Hotkeys.CLAIM_HELD.getKeybind().setCallback((action, key) -> {
            dev.materialshare.client.Importer.claimHeld();
            return true;
        });
        Configs.Hotkeys.MARK_STORAGE_A.getKeybind().setCallback((action, key) -> {
            dev.materialshare.client.StorageMarks.mark(true);
            return true;
        });
        Configs.Hotkeys.MARK_STORAGE_B.getKeybind().setCallback((action, key) -> {
            dev.materialshare.client.StorageMarks.mark(false);
            return true;
        });
        Configs.Hotkeys.CLEAR_STORAGE_MARKS.getKeybind().setCallback((action, key) -> {
            dev.materialshare.client.StorageMarks.clearWithMessage();
            return true;
        });
        Configs.Hotkeys.OPEN_HELP.getKeybind().setCallback((action, key) -> {
            GuiBase.openGui(new dev.materialshare.client.HelpScreen(MinecraftClient.getInstance().currentScreen, 0));
            return true;
        });
        Configs.Hotkeys.UNDO.getKeybind().setCallback((action, key) -> {
            if (MinecraftClient.getInstance().currentScreen instanceof GuiMaterialList) {
                dev.materialshare.client.Undo.undo();
                return true;
            }
            return false;
        });
        Configs.Hotkeys.CYCLE_FILTER.getKeybind().setCallback((action, key) -> {
            if (MinecraftClient.getInstance().currentScreen instanceof GuiMaterialList list) {
                dev.materialshare.client.ClientFilter.cycle();
                if (list instanceof dev.materialshare.client.ListRefresh refresh) {
                    refresh.materialshare$refresh();
                }
                return true;
            }
            return false;
        });
        Configs.Hotkeys.OPEN_PROJECT_SCREEN.getKeybind().setCallback((action, key) -> {
            if (MinecraftClient.getInstance().currentScreen instanceof GuiMaterialList list) {
                GuiBase.openGui(new ProjectScreen(list.getMaterialList(), list, 0));
                return true;
            }
            return false;
        });
    }
}
