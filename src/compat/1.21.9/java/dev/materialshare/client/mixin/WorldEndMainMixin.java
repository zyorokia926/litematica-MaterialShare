package dev.materialshare.client.mixin;

import dev.materialshare.client.StorageRender;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.9 only: Fabric API for this version has no world render event, so the storage boxes are drawn from here, at the
 * end of the main world pass (the same place as Fabric API's END_MAIN from 1.21.10 on).
 */
@Mixin(WorldRenderer.class)
public abstract class WorldEndMainMixin {
    @Inject(method = "method_62214", at = @At(value = "INVOKE:LAST", target = "Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;draw()V"))
    private void materialshare$endMain(CallbackInfo ci) {
        StorageRender.render(new MatrixStack());
    }
}
