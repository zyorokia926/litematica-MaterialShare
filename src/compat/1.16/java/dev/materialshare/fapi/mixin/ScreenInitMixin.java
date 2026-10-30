package dev.materialshare.fapi.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.16 and 1.16.1: fires ScreenEvents.AFTER_INIT. */
@Mixin(net.minecraft.client.gui.screen.Screen.class)
public abstract class ScreenInitMixin {
    @Inject(method = "init(Lnet/minecraft/client/MinecraftClient;II)V", at = @At("TAIL"))
    private void materialshare$afterInit(net.minecraft.client.MinecraftClient client, int width, int height, CallbackInfo ci) {
        var screen = (net.minecraft.client.gui.screen.Screen) (Object) this;
        for (var l : dev.materialshare.fapi.ScreenEvents.AFTER_INIT.listeners()) {
            l.afterInit(client, screen, width, height);
        }
    }
}
