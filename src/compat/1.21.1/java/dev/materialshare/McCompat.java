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
 * Minecraft 1.21.1 (src/compat/1.21.1); the builds for other versions use the file of the same name under their own
 * directory in src/compat/ (selected with -PmcCompat=<version>, see build.gradle).
 */
public final class McCompat {
    /** The Minecraft version this build is for (shown in messages). */
    public static final String MINECRAFT = "1.21.1";

    /** The Litematica and malilib versions this build was made and tested for: [min, maxExclusive). */
    public static final String[] LITEMATICA_RANGE = {"0.19.0", "0.20.0"};
    public static final String[] MALILIB_RANGE = {"0.21.0", "0.22.0"};

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

    public static net.minecraft.text.MutableText translatable(String key, Object... args) {
        return net.minecraft.text.Text.translatable(key, args);
    }

    public static <T extends net.minecraft.world.PersistentState> T persistentState(net.minecraft.world.PersistentStateManager manager,
            java.util.function.Function<net.minecraft.nbt.NbtCompound, T> reader, java.util.function.Supplier<T> factory, String key) {
        return manager.getOrCreate(new net.minecraft.world.PersistentState.Type<T>(factory, (nbt, lookup) -> reader.apply(nbt), null), key);
    }

    /** Looks a player up by name at Mojang (through the server's user cache). */
    public static void findProfile(MinecraftServer server, String name,
            java.util.function.Consumer<java.util.Optional<com.mojang.authlib.GameProfile>> then) {
        server.getUserCache().findByNameAsync(name).thenAccept(then);
    }

    /** Passes on the stacks that lie inside a bundle or a shulker box item. */
    public static void forEachInside(net.minecraft.item.ItemStack stack, java.util.function.Consumer<net.minecraft.item.ItemStack> sink) {
        if (stack.getItem() instanceof net.minecraft.item.BundleItem) {
            var contents = stack.get(net.minecraft.component.DataComponentTypes.BUNDLE_CONTENTS);
            if (contents != null) {
                contents.iterate().forEach(sink);
            }
        } else if (net.minecraft.block.Block.getBlockFromItem(stack.getItem()) instanceof net.minecraft.block.ShulkerBoxBlock) {
            var contents = stack.get(net.minecraft.component.DataComponentTypes.CONTAINER);
            if (contents != null) {
                contents.iterateNonEmpty().forEach(sink);
            }
        }
    }
}
