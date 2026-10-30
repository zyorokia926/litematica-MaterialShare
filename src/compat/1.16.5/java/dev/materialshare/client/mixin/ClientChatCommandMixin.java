package dev.materialshare.client.mixin;

import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.16 only (Fabric API for 1.16 has no client commands): answers /materialshare before the line goes to the server. */
@Mixin(ClientPlayerEntity.class)
public abstract class ClientChatCommandMixin {
    @Inject(method = "sendChatMessage", at = @At("HEAD"), cancellable = true)
    private void materialshare$command(String message, CallbackInfo ci) {
        if (message.startsWith("/") && dev.materialshare.McClientCompat.runCommand(message.substring(1))) {
            ci.cancel();
        }
    }
}
