package dev.materialshare.client.mixin;

import fi.dy.masa.malilib.gui.GuiBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * malilib presses the button under the cursor when the wheel turns over it (up = left click, down = right click). On the
 * screens of this add-on that would pick a player or leave the screen just by scrolling, so the wheel does nothing there.
 */
@Mixin(GuiBase.class)
public abstract class GuiBaseWheelMixin {
    @Inject(method = "onMouseScrolled", at = @At("HEAD"), cancellable = true, remap = false)
    private void materialshare$noWheelClick(int mouseX, int mouseY, double amount, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof dev.materialshare.client.NoWheelClick) {
            cir.setReturnValue(false);
        }
    }
}
