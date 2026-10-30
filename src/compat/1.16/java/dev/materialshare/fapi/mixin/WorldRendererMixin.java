package dev.materialshare.fapi.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.16 and 1.16.1: fires WorldRenderEvents.LAST after the world is drawn. */
@Mixin(net.minecraft.client.render.WorldRenderer.class)
public abstract class WorldRendererMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void materialshare$last(net.minecraft.client.util.math.MatrixStack matrices, float tickDelta, long limitTime, boolean renderBlockOutline,
                                    net.minecraft.client.render.Camera camera, net.minecraft.client.render.GameRenderer gameRenderer,
                                    net.minecraft.client.render.LightmapTextureManager lightmap, net.minecraft.util.math.Matrix4f projection,
                                    CallbackInfo ci) {
        var context = new dev.materialshare.fapi.WorldRenderContext(matrices, camera);
        for (var l : dev.materialshare.fapi.WorldRenderEvents.LAST.listeners()) {
            l.onLast(context);
        }
    }
}
