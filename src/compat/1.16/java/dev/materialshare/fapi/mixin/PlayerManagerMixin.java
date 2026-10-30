package dev.materialshare.fapi.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.16 and 1.16.1: fires ServerPlayConnectionEvents.JOIN once the player is in the world. */
@Mixin(net.minecraft.server.PlayerManager.class)
public abstract class PlayerManagerMixin {
    @Inject(method = "onPlayerConnect", at = @At("TAIL"))
    private void materialshare$join(net.minecraft.network.ClientConnection connection, net.minecraft.server.network.ServerPlayerEntity player,
                                    CallbackInfo ci) {
        var handler = player.networkHandler;
        dev.materialshare.fapi.PacketSender sender = (channel, buf) -> dev.materialshare.McNet.send(player, channel, buf);
        for (var l : dev.materialshare.fapi.ServerPlayConnectionEvents.JOIN.listeners()) {
            l.onPlayReady(handler, sender, player.getServer());
        }
    }
}
