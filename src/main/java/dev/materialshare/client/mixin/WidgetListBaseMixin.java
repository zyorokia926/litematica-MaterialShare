package dev.materialshare.client.mixin;

import dev.materialshare.client.RowScroll;
import fi.dy.masa.litematica.gui.widgets.WidgetListMaterialList;
import fi.dy.masa.malilib.gui.widgets.WidgetListBase;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * malilib lists do not pass the mouse wheel to their rows, so the material list hands it to the row under the
 * cursor itself (used to change a claimant's collected count). Other malilib lists are left alone.
 */
@Mixin(WidgetListBase.class)
public abstract class WidgetListBaseMixin {
    @Shadow(remap = false)
    protected List<?> listWidgets;

    @Inject(method = "onMouseScrolled", at = @At("HEAD"), cancellable = true, remap = false)
    private void materialshare$wheel(int mouseX, int mouseY, double amount, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof WidgetListMaterialList)) {
            return;
        }
        for (Object row : listWidgets) {
            if (row instanceof RowScroll scroll && scroll.materialshare$scroll(mouseX, mouseY, amount)) {
                cir.setReturnValue(true);
                return;
            }
        }
    }
}
