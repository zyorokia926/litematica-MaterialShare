package dev.materialshare.fapi.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.16 and 1.16.1: fires ServerPlayConnectionEvents.DISCONNECT. */
@Mixin(net.minecraft.server.network.ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {
    @Shadow
    public net.minecraft.server.network.ServerPlayerEntity player;

    @Inject(method = "onDisconnected", at = @At("HEAD"))
    private void materialshare$leave(net.minecraft.text.Text reason, CallbackInfo ci) {
        var handler = (net.minecraft.server.network.ServerPlayNetworkHandler) (Object) this;
        for (var l : dev.materialshare.fapi.ServerPlayConnectionEvents.DISCONNECT.listeners()) {
            l.onPlayDisconnect(handler, player.getServer());
        }
    }
}
