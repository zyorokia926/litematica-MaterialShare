package dev.materialshare;

import dev.materialshare.server.ServerNet;
import net.fabricmc.api.ModInitializer;

public class MaterialShare implements ModInitializer {
    public static final String MOD_ID = "litematica_materialshare";

    @Override
    public void onInitialize() {
        ServerNet.register();
    }
}
