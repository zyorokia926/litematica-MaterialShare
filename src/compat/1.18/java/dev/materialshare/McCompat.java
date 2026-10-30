package dev.materialshare;

import net.minecraft.item.Item;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;

/** The Minecraft 1.18 version of McCompat (see src/main/java/dev/materialshare/McCompat.java). */
public final class McCompat {
    /** The Minecraft version this build is for (shown in messages). */
    public static final String MINECRAFT = "1.18";

    /** The Litematica and malilib versions this build was made and tested for: [min, maxExclusive). */
    public static final String[] LITEMATICA_RANGE = {"0.9.0", "0.10.0"};
    public static final String[] MALILIB_RANGE = {"0.10.0", "0.11.0"};

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
        return new net.minecraft.text.LiteralText(text);
    }

    public static net.minecraft.text.MutableText translatable(String key) {
        return new net.minecraft.text.TranslatableText(key);
    }

    public static net.minecraft.text.MutableText translatable(String key, Object... args) {
        return new net.minecraft.text.TranslatableText(key, args);
    }

    public static <T extends net.minecraft.world.PersistentState> T persistentState(net.minecraft.world.PersistentStateManager manager,
            java.util.function.Function<net.minecraft.nbt.NbtCompound, T> reader, java.util.function.Supplier<T> factory, String key) {
        return manager.getOrCreate(reader, factory, key);
    }

    /** Looks a player up by name at Mojang (through the server's user cache). */
    public static void findProfile(MinecraftServer server, String name,
            java.util.function.Consumer<java.util.Optional<com.mojang.authlib.GameProfile>> then) {
        server.getUserCache().findByNameAsync(name, then);
    }

    /** Passes on the stacks that lie inside a bundle or a shulker box item. */
    public static void forEachInside(net.minecraft.item.ItemStack stack, java.util.function.Consumer<net.minecraft.item.ItemStack> sink) {
        net.minecraft.nbt.NbtCompound tag = stack.getItem() instanceof net.minecraft.item.BundleItem ? stack.getNbt()
                : net.minecraft.block.Block.getBlockFromItem(stack.getItem()) instanceof net.minecraft.block.ShulkerBoxBlock
                        ? stack.getSubNbt("BlockEntityTag") : null;
        if (tag == null) {
            return;
        }
        for (net.minecraft.nbt.NbtElement e : tag.getList("Items", net.minecraft.nbt.NbtElement.COMPOUND_TYPE)) {
            sink.accept(net.minecraft.item.ItemStack.fromNbt((net.minecraft.nbt.NbtCompound) e));
        }
    }
}
