package dev.materialshare.client.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.materialshare.client.config.ConfigScreen;

/** Adds a settings button for this mod to Mod Menu (only used when Mod Menu is installed). */
public class ModMenuImpl implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ConfigScreen::new;
    }
}
