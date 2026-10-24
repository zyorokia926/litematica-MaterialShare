package dev.materialshare;

import dev.materialshare.common.ModRequirements;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/**
 * This mod is an add-on to Litematica. The client refuses to start without Litematica and malilib (in a supported
 * version) and says why, instead of starting a mod that cannot do anything. Servers need neither.
 */
public class MaterialSharePreLaunch implements PreLaunchEntrypoint {
    @Override
    public void onPreLaunch() {
        FabricLoader loader = FabricLoader.getInstance();
        if (loader.getEnvironmentType() != EnvType.CLIENT) {
            return;
        }
        List<String> problems = ModRequirements.problems(id -> loader.getModContainer(id)
                .map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse(null));
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Litematica MaterialShare を起動できません / Litematica MaterialShare cannot start:\n - "
                    + String.join("\n - ", problems));
        }
    }
}
