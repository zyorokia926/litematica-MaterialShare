package dev.materialshare.client.mixin;

import dev.materialshare.client.RowOverlay;
import dev.materialshare.client.RowScroll;
import fi.dy.masa.litematica.gui.widgets.WidgetMaterialListEntry;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import fi.dy.masa.malilib.gui.widgets.WidgetBase;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WidgetMaterialListEntry.class)
public abstract class WidgetMaterialListEntryMixin implements RowScroll {
    @Shadow(remap = false)
    @Final
    private MaterialListEntry entry;

    @Shadow(remap = false)
    @Final
    private MaterialListBase materialList;

    @Shadow(remap = false)
    protected abstract int getColumnPosX(int column);

    /** Column 4 is the end of the last Litematica column; our cells start right after it. */
    private int materialshare$startX() {
        return getColumnPosX(4) + 6;
    }

    @Inject(method = "render", at = @At("TAIL"), remap = false)
    private void materialshare$render(int mouseX, int mouseY, boolean selected, MatrixStack matrices,
            CallbackInfo ci) {
        WidgetBase self = (WidgetBase) (Object) this;
        if (entry != null) {
            RowOverlay.render(self, entry, materialshare$startX(), matrices, mouseX, mouseY);
        } else {
            RowOverlay.renderHeader(self, materialshare$startX(), matrices);
        }
    }

    /** Our tooltip replaces Litematica's while the mouse is over one of our cells. */
    @Inject(method = "postRenderHovered", at = @At("HEAD"), cancellable = true, remap = false)
    private void materialshare$tooltip(int mouseX, int mouseY, boolean selected, MatrixStack matrices,
            CallbackInfo ci) {
        if (entry != null
                && RowOverlay.tooltip((WidgetBase) (Object) this, entry, materialshare$startX(), mouseX, mouseY,
                        matrices)) {
            ci.cancel();
        }
    }

    @Inject(method = "onMouseClickedImpl", at = @At("HEAD"), cancellable = true, remap = false)
    private void materialshare$click(int mouseX, int mouseY, int mouseButton, CallbackInfoReturnable<Boolean> cir) {
        if (entry != null
                && RowOverlay.click((WidgetBase) (Object) this, entry, materialshare$startX(), mouseX, mouseY,
                        mouseButton, materialList.getMultiplier())) {
            cir.setReturnValue(true);
        }
    }

    @Override
    public boolean materialshare$scroll(int mouseX, int mouseY, double amount) {
        return entry != null
                && RowOverlay.scroll((WidgetBase) (Object) this, entry, materialshare$startX(), mouseX, mouseY,
                        amount);
    }
}
