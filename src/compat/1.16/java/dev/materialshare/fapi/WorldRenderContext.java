package dev.materialshare.fapi;

/** What the world drawing hands on (only the parts this mod uses). */
public final class WorldRenderContext {
    private final net.minecraft.client.util.math.MatrixStack matrixStack;
    private final net.minecraft.client.render.Camera camera;

    public WorldRenderContext(net.minecraft.client.util.math.MatrixStack matrixStack, net.minecraft.client.render.Camera camera) {
        this.matrixStack = matrixStack;
        this.camera = camera;
    }

    public net.minecraft.client.util.math.MatrixStack matrixStack() {
        return matrixStack;
    }

    public net.minecraft.client.render.Camera camera() {
        return camera;
    }
}
