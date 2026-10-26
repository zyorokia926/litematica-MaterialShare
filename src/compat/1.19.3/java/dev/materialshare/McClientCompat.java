package dev.materialshare;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;

/** The client-only part of {@link McCompat} (kept apart so a dedicated server never touches client classes). */
public final class McClientCompat {
    private McClientCompat() {
    }

    public static void renderItemIcon(MinecraftClient client, MatrixStack matrices, ItemStack stack, int x, int y) {
        client.getItemRenderer().renderGuiItemIcon(stack, x, y);
    }

    public static ItemStack recipeOutput(CraftingRecipe recipe, MinecraftClient client) {
        return recipe.getOutput();
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

    // ---- 2D drawing (Minecraft before 1.20 draws through a MatrixStack; from 1.20 on through a DrawContext) ----

    public static void fill(MatrixStack matrices, int x1, int y1, int x2, int y2, int color) {
        net.minecraft.client.gui.DrawableHelper.fill(matrices, x1, y1, x2, y2, color);
    }

    public static void text(net.minecraft.client.font.TextRenderer font, MatrixStack matrices, String text, int x, int y, int color) {
        font.drawWithShadow(matrices, text, x, y, color);
    }

    public static void texture(MatrixStack matrices, net.minecraft.util.Identifier texture, int x, int y, int w, int h, float u, float v,
                               int regionW, int regionH, int textureW, int textureH) {
        RenderSystem.setShaderTexture(0, texture);
        useShaderPositionTex();
        net.minecraft.client.gui.DrawableHelper.drawTexture(matrices, x, y, w, h, u, v, regionW, regionH, textureW, textureH);
    }

    public static void tooltip(net.minecraft.client.gui.screen.Screen screen, MatrixStack matrices,
                               java.util.List<? extends net.minecraft.text.OrderedText> lines, int x, int y) {
        screen.renderOrderedTooltip(matrices, lines, x, y);
    }

    /** The background of a vanilla button of this size. */
    public static void vanillaButton(MatrixStack matrices, int x, int y, int w, int h, boolean hover) {
        int v = 46 + (hover ? 2 : 1) * 20;
        int half = w / 2;
        useShaderPositionTex();
        RenderSystem.setShaderTexture(0, net.minecraft.client.gui.widget.ClickableWidget.WIDGETS_TEXTURE);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        net.minecraft.client.gui.DrawableHelper.drawTexture(matrices, x, y, half, h, 0.0F, (float) v, half, h, 256, 256);
        net.minecraft.client.gui.DrawableHelper.drawTexture(matrices, x + half, y, w - half, h, (float) (200 - (w - half)), (float) v, w - half, h, 256, 256);
        RenderSystem.disableBlend();
    }

    /** The stack the zoomed map of the breakdown screen is drawn with. */
    public static MatrixStack freshStack(MatrixStack matrices) {
        return new MatrixStack();
    }

    public static void cursorToEnd(net.minecraft.client.gui.widget.TextFieldWidget field) {
        field.setCursorToEnd();
    }

    public static java.util.UUID sessionUuid(MinecraftClient client) {
        return client.getSession().getProfile().getId();
    }

    public static boolean debugHud(MinecraftClient client) {
        return client.options.debugEnabled;
    }

    public static java.util.List<CraftingRecipe> craftingRecipes(net.minecraft.recipe.RecipeManager manager) {
        return manager.listAllOfType(net.minecraft.recipe.RecipeType.CRAFTING);
    }

    public static net.minecraft.util.Identifier entrySkin(net.minecraft.client.network.PlayerListEntry entry) {
        return entry.getSkinTexture();
    }

    public static net.minecraft.util.Identifier defaultSkin(java.util.UUID id) {
        return net.minecraft.client.util.DefaultSkinHelper.getTexture(id);
    }

    /** Fetches the skin of this profile; the callback comes when the picture is downloaded and registered. */
    public static void loadSkin(MinecraftClient client, com.mojang.authlib.GameProfile profile,
                                java.util.function.Consumer<net.minecraft.util.Identifier> then) {
        client.getSkinProvider().loadSkin(profile, (type, identifier, texture) -> {
            if (type == com.mojang.authlib.minecraft.MinecraftProfileTexture.Type.SKIN) {
                then.accept(identifier);
            }
        }, false);
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
}
