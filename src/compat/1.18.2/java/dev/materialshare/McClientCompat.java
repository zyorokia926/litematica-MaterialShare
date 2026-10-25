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
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
    }

    public static void useShaderPositionColor() {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
    }

    public static void useShaderLines() {
        RenderSystem.setShader(GameRenderer::getRenderTypeLinesShader);
    }

    /** Registers /materialshare (the client command API of Fabric API for 1.18 is the "v1" one). */
    public static void registerCommands() {
        net.fabricmc.fabric.api.client.command.v1.ClientCommandManager.DISPATCHER.register(dev.materialshare.client.ClientCommands.tree(
                net.fabricmc.fabric.api.client.command.v1.ClientCommandManager::literal,
                net.fabricmc.fabric.api.client.command.v1.ClientCommandManager::argument));
    }

    /** The stream of a file in the resource packs, or null when there is none. */
    public static java.io.InputStream openResource(net.minecraft.util.Identifier id) throws java.io.IOException {
        var manager = MinecraftClient.getInstance().getResourceManager();
        return manager.containsResource(id) ? manager.getResource(id).getInputStream() : null;
    }
}
