package dev.materialshare;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
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

    public static ItemStack recipeOutput(CraftingRecipe recipe, MinecraftClient client) {
        return recipe.getResult(client.world.getRegistryManager());
    }

    public static void useShaderPositionTex() {
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
    }

    public static void useShaderPositionColor() {
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
    }

    public static void useShaderLines() {
        RenderSystem.setShader(GameRenderer::getRenderTypeLinesProgram);
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
        g.drawTexture(texture, x, y, w, h, u, v, regionW, regionH, textureW, textureH);
    }

    public static void tooltip(net.minecraft.client.gui.screen.Screen screen, DrawContext g,
                               java.util.List<? extends net.minecraft.text.OrderedText> lines, int x, int y) {
        g.drawOrderedTooltip(MinecraftClient.getInstance().textRenderer, lines, x, y);
    }

    /** The background of a vanilla button of this size. */
    public static void vanillaButton(DrawContext g, int x, int y, int w, int h, boolean hover) {
        g.drawGuiTexture(new net.minecraft.util.Identifier(hover ? "widget/button_highlighted" : "widget/button"), x, y, w, h);
    }

    /** The stack the zoomed map of the breakdown screen is drawn with. */
    public static DrawContext freshStack(DrawContext matrices) {
        return matrices;
    }

    public static void cursorToEnd(net.minecraft.client.gui.widget.TextFieldWidget field) {
        field.setCursorToEnd(false);
    }

    public static java.util.UUID sessionUuid(MinecraftClient client) {
        return client.getSession().getUuidOrNull();
    }

    public static boolean debugHud(MinecraftClient client) {
        return client.getDebugHud().shouldShowDebugHud();
    }

    public static java.util.List<CraftingRecipe> craftingRecipes(net.minecraft.recipe.RecipeManager manager) {
        return manager.listAllOfType(net.minecraft.recipe.RecipeType.CRAFTING).stream()
                .map(net.minecraft.recipe.RecipeEntry::value).toList();
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
        client.getSkinProvider().fetchSkinTextures(profile).thenAccept(textures -> then.accept(textures.texture()));
    }

    /** Moves and scales everything drawn until {@link #endMapTransform()} (the zoomed map of the breakdown screen). */
    public static void beginMapTransform(float x, float y, float zoom) {
        var modelView = RenderSystem.getModelViewStack();
        modelView.push();
        modelView.translate(x, y, 0);
        modelView.scale(zoom, zoom, 1.0F);
        RenderSystem.applyModelViewMatrix();
    }

    public static void endMapTransform() {
        RenderSystem.getModelViewStack().pop();
        RenderSystem.applyModelViewMatrix();
    }

    /** A vanilla button (for adding to a vanilla screen). */
    public static net.minecraft.client.gui.widget.ButtonWidget button(int x, int y, int w, int h, String text, Runnable onPress) {
        return net.minecraft.client.gui.widget.ButtonWidget.builder(dev.materialshare.McCompat.literal(text), b -> onPress.run()).dimensions(x, y, w, h).build();
    }
}
