package dev.materialshare;

import net.minecraft.item.Item;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;

/** The Minecraft 1.19.1 version of McCompat (see src/main/java/dev/materialshare/McCompat.java). */
public final class McCompat {
    /** The Minecraft version this build is for (shown in messages). */
    public static final String MINECRAFT = "1.19.1";

    /** The Litematica and malilib versions this build was made and tested for: [min, maxExclusive). */
    public static final String[] LITEMATICA_RANGE = {"0.12.0", "0.13.0"};
    public static final String[] MALILIB_RANGE = {"0.13.0", "0.14.0"};

    private McCompat() {
    }

    public static Identifier itemId(Item item) {
        return Registry.ITEM.getId(item);
    }

    public static boolean hasItem(Identifier id) {
        return Registry.ITEM.containsId(id);
    }

    public static Item item(Identifier id) {
        return Registry.ITEM.get(id);
    }

    public static ServerWorld world(MinecraftServer server, String dimension) {
        Identifier id = Identifier.tryParse(dimension);
        return id == null ? null : server.getWorld(RegistryKey.of(Registry.WORLD_KEY, id));
    }

    public static net.minecraft.text.MutableText literal(String text) {
        return net.minecraft.text.Text.literal(text);
    }

    public static net.minecraft.text.MutableText translatable(String key) {
        return net.minecraft.text.Text.translatable(key);
    }
}
