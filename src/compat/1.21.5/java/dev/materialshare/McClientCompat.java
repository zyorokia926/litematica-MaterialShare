package dev.materialshare;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;

/** The client-only part of {@link McCompat} (kept apart so a dedicated server never touches client classes). */
public final class McClientCompat {
    private McClientCompat() {
    }

    public static void renderItemIcon(MinecraftClient client, DrawContext matrices, ItemStack stack, int x, int y) {
        matrices.drawItem(stack, x, y);
    }

    /** What the recipe makes: from 1.21.2 on a recipe only tells it through its displays. */
    public static ItemStack recipeOutput(CraftingRecipe recipe, MinecraftClient client) {
        for (net.minecraft.recipe.display.RecipeDisplay display : recipe.getDisplays()) {
            if (display.result() instanceof net.minecraft.recipe.display.SlotDisplay.StackSlotDisplay s) {
                return s.stack().copy();
            }
            if (display.result() instanceof net.minecraft.recipe.display.SlotDisplay.ItemSlotDisplay i) {
                return new ItemStack(i.item());
            }
        }
        return ItemStack.EMPTY;
    }

    /** The ingredients of a crafting recipe (one per filled slot). */
    public static java.util.List<net.minecraft.recipe.Ingredient> ingredients(CraftingRecipe recipe) {
        return recipe.getIngredientPlacement().getIngredients();
    }

    public static boolean isEmpty(net.minecraft.recipe.Ingredient ingredient) {
        return false;
    }

    public static ItemStack[] matchingStacks(net.minecraft.recipe.Ingredient ingredient) {
        return ingredient.getMatchingItems().map(ItemStack::new).toArray(ItemStack[]::new);
    }

    /** From 1.21.5 on every drawing names its own render pipeline: nothing to choose here. */
    public static void useShaderPositionTex() {
    }

    public static void useShaderPositionColor() {
    }

    public static void useShaderLines() {
    }

    /** Litematica gives the schematic file as a Path from 0.22 (1.21.5) on. */
    public static java.io.File file(java.nio.file.Path path) {
        return path == null ? null : path.toFile();
    }

    /** Registers /materialshare (the client command API of Fabric API from 1.19 on is the "v2" one). */
    public static void registerCommands() {
        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess) -> dispatcher.register(dev.materialshare.client.ClientCommands.tree(
                        net.fabricmc.fabric.api.client.command.v2.ClientCommandManager::literal,
                        net.fabricmc.fabric.api.client.command.v2.ClientCommandManager::argument)));
    }

    /** The stream of a file in the resource packs, or null when there is none. */
    public static java.io.InputStream openResource(net.minecraft.util.Identifier id) throws java.io.IOException {
        var resource = MinecraftClient.getInstance().getResourceManager().getResource(id).orElse(null);
        return resource == null ? null : resource.getInputStream();
    }

    // ---- 2D drawing (from 1.20 on the screens draw through a DrawContext) ----

    public static void fill(DrawContext g, int x1, int y1, int x2, int y2, int color) {
        g.fill(x1, y1, x2, y2, color);
    }

    public static void text(net.minecraft.client.font.TextRenderer font, DrawContext g, String text, int x, int y, int color) {
        g.drawTextWithShadow(font, text, x, y, color);
    }

    public static void texture(DrawContext g, net.minecraft.util.Identifier texture, int x, int y, int w, int h, float u, float v,
                               int regionW, int regionH, int textureW, int textureH) {
        g.drawTexture(RenderLayer::getGuiTextured, texture, x, y, u, v, w, h, regionW, regionH, textureW, textureH);
    }

    public static void tooltip(net.minecraft.client.gui.screen.Screen screen, DrawContext g,
                               java.util.List<? extends net.minecraft.text.OrderedText> lines, int x, int y) {
        g.drawOrderedTooltip(MinecraftClient.getInstance().textRenderer, lines, x, y);
    }

    /** The background of a vanilla button of this size. */
    public static void vanillaButton(DrawContext g, int x, int y, int w, int h, boolean hover) {
        g.drawGuiTexture(RenderLayer::getGuiTextured, net.minecraft.util.Identifier.of(hover ? "widget/button_highlighted" : "widget/button"), x, y, w, h);
    }

    /**
     * The stack the zoomed map of the breakdown screen is drawn with. From 1.21.2 on the screen's drawing is collected and
     * drawn later, so the move and the zoom go onto the DrawContext's own matrices (not the model view matrix).
     */
    public static DrawContext freshStack(DrawContext matrices) {
        mapContext = matrices;
        matrices.getMatrices().push();
        matrices.getMatrices().translate(mapX, mapY, 0.0F);
        matrices.getMatrices().scale(mapZoom, mapZoom, 1.0F);
        return matrices;
    }

    private static DrawContext mapContext;
    private static float mapX;
    private static float mapY;
    private static float mapZoom = 1.0F;

    public static void cursorToEnd(net.minecraft.client.gui.widget.TextFieldWidget field) {
        field.setCursorToEnd(false);
    }

    public static java.util.UUID sessionUuid(MinecraftClient client) {
        return client.getSession().getUuidOrNull();
    }

    public static boolean debugHud(MinecraftClient client) {
        return client.getDebugHud().shouldShowDebugHud();
    }

    /**
     * The crafting recipes. From 1.21.2 on the server no longer sends the recipes to the players, so they come from the
     * game's own server (single player and the player who opened the world to LAN); elsewhere the list is empty.
     */
    public static java.util.List<CraftingRecipe> craftingRecipes(net.minecraft.recipe.RecipeManager manager) {
        net.minecraft.recipe.RecipeManager all = manager;
        var server = MinecraftClient.getInstance().getServer();
        if (!(all instanceof net.minecraft.recipe.ServerRecipeManager) && server != null) {
            all = server.getRecipeManager();
        }
        if (!(all instanceof net.minecraft.recipe.ServerRecipeManager serverRecipes)) {
            return java.util.List.of();
        }
        return serverRecipes.values().stream().map(net.minecraft.recipe.RecipeEntry::value)
                .filter(r -> r instanceof CraftingRecipe).map(r -> (CraftingRecipe) r).toList();
    }

    public static net.minecraft.util.Identifier entrySkin(net.minecraft.client.network.PlayerListEntry entry) {
        return entry.getSkinTextures().texture();
    }

    public static net.minecraft.util.Identifier defaultSkin(java.util.UUID id) {
        return net.minecraft.client.util.DefaultSkinHelper.getSkinTextures(id).texture();
    }

    /** Fetches the skin of this profile; the callback comes when the picture is downloaded and registered. */
    public static void loadSkin(MinecraftClient client, com.mojang.authlib.GameProfile profile,
                                java.util.function.Consumer<net.minecraft.util.Identifier> then) {
        client.getSkinProvider().fetchSkinTextures(profile).thenAccept(found -> found.ifPresent(textures -> then.accept(textures.texture())));
    }

    /** Moves and scales everything drawn until {@link #endMapTransform()} (the zoomed map; applied in {@link #freshStack}). */
    public static void beginMapTransform(float x, float y, float zoom) {
        mapX = x;
        mapY = y;
        mapZoom = zoom;
    }

    public static void endMapTransform() {
        if (mapContext != null) {
            mapContext.draw();
            mapContext.getMatrices().pop();
            mapContext = null;
        }
    }

    /** A vanilla button (for adding to a vanilla screen). */
    public static net.minecraft.client.gui.widget.ButtonWidget button(int x, int y, int w, int h, String text, Runnable onPress) {
        return net.minecraft.client.gui.widget.ButtonWidget.builder(dev.materialshare.McCompat.literal(text), b -> onPress.run()).dimensions(x, y, w, h).build();
    }
}
