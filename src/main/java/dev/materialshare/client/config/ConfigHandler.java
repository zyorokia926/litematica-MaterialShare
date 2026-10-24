package dev.materialshare.client.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.materialshare.MaterialShare;
import fi.dy.masa.malilib.config.ConfigUtils;
import fi.dy.masa.malilib.config.IConfigHandler;
import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.JsonUtils;
import java.io.File;

/** Reads and writes config/litematica_materialshare.json. */
public class ConfigHandler implements IConfigHandler {
    private static final String OLD_CONFIG_FILE_NAME = "matlisttracker.json";
    private static final String CONFIG_FILE_NAME = MaterialShare.MOD_ID + ".json";

    @Override
    public void load() {
        File file = new File(FileUtils.getConfigDirectory(), CONFIG_FILE_NAME);
        boolean old = false;
        if (!file.isFile() || !file.canRead()) {
            // the settings of the old name (the mod was called Material List Tracker) are taken over once
            file = new File(FileUtils.getConfigDirectory(), OLD_CONFIG_FILE_NAME);
            old = true;
            if (!file.isFile() || !file.canRead()) {
                return;
            }
        }
        JsonElement element = JsonUtils.parseJsonFile(file);
        if (element != null && element.isJsonObject()) {
            JsonObject root = element.getAsJsonObject();
            if (old) {
                root = renameKeys(root);
            }
            ConfigUtils.readConfigBase(root, "Generic", Configs.Generic.OPTIONS);
            ConfigUtils.readConfigBase(root, "Hud", Configs.Hud.OPTIONS);
            ConfigUtils.readHotkeys(root, "Hotkeys", Configs.Hotkeys.HOTKEY_LIST);
        }
    }

    /** Setting names used to start with "matlist"; now they start with "materialshare". */
    private static JsonObject renameKeys(JsonObject root) {
        JsonObject result = new JsonObject();
        for (var section : root.entrySet()) {
            if (section.getValue().isJsonObject()) {
                JsonObject renamed = new JsonObject();
                for (var option : section.getValue().getAsJsonObject().entrySet()) {
                    String key = option.getKey();
                    renamed.add(key.startsWith("matlist") ? "materialshare" + key.substring("matlist".length()) : key,
                            option.getValue());
                }
                result.add(section.getKey(), renamed);
            } else {
                result.add(section.getKey(), section.getValue());
            }
        }
        return result;
    }

    @Override
    public void save() {
        File dir = FileUtils.getConfigDirectory();
        if (!dir.exists() && !dir.mkdirs()) {
            return;
        }
        JsonObject root = new JsonObject();
        ConfigUtils.writeConfigBase(root, "Generic", Configs.Generic.OPTIONS);
        ConfigUtils.writeConfigBase(root, "Hud", Configs.Hud.OPTIONS);
        ConfigUtils.writeHotkeys(root, "Hotkeys", Configs.Hotkeys.HOTKEY_LIST);
        JsonUtils.writeJsonToFile(root, new File(dir, CONFIG_FILE_NAME));
    }
}
