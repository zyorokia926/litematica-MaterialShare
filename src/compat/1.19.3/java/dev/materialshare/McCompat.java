package dev.materialshare;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

/**
 * The few places where the game's own API differs between Minecraft versions (registries, worlds). This file is for
 * Minecraft 1.19.4 (src/compat/1.19.4); the builds for other versions use the file of the same name under their own
 * directory in src/compat/ (selected with -PmcCompat=<version>, see build.gradle).
 */
public final class McCompat {
    /** The Minecraft version this build is for (shown in messages). */
    public static final String MINECRAFT = "1.19.3";

    /** The Litematica and malilib versions this build was made and tested for: [min, maxExclusive). */
    public static final String[] LITEMATICA_RANGE = {"0.13.0", "0.14.0"};
    public static final String[] MALILIB_RANGE = {"0.14.0", "0.15.0"};

    private McCompat() {
    }

    public static Identifier itemId(Item item) {
        return Registries.ITEM.getId(item);
    }

    public static boolean hasItem(Identifier id) {
        return Registries.ITEM.containsId(id);
    }

    public static Item item(Identifier id) {
        return Registries.ITEM.get(id);
    }

    /** The world with this dimension id (for example "minecraft:overworld"), or null. */
    public static ServerWorld world(MinecraftServer server, String dimension) {
        Identifier id = Identifier.tryParse(dimension);
        return id == null ? null : server.getWorld(RegistryKey.of(RegistryKeys.WORLD, id));
    }

    public static net.minecraft.text.MutableText literal(String text) {
        return net.minecraft.text.Text.literal(text);
    }

    public static net.minecraft.text.MutableText translatable(String key) {
        return net.minecraft.text.Text.translatable(key);
    }
}
