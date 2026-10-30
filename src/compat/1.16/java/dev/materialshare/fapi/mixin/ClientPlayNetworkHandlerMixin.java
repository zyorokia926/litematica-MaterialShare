package dev.materialshare.fapi.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.16 and 1.16.1: fires ClientPlayConnectionEvents.DISCONNECT. */
@Mixin(net.minecraft.client.network.ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
    @Inject(method = "onDisconnected", at = @At("HEAD"))
    private void materialshare$leave(net.minecraft.text.Text reason, CallbackInfo ci) {
        var handler = (net.minecraft.client.network.ClientPlayNetworkHandler) (Object) this;
        for (var l : dev.materialshare.fapi.ClientPlayConnectionEvents.DISCONNECT.listeners()) {
            l.onPlayDisconnect(handler, net.minecraft.client.MinecraftClient.getInstance());
        }
    }
}
