package dev.materialshare.client.mixin;

import dev.materialshare.client.ClientFilter;
import fi.dy.masa.litematica.gui.GuiMaterialList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import fi.dy.masa.litematica.gui.widgets.WidgetListMaterialList;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import java.util.Collection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies the "表示" filter (mine / unclaimed / unfinished) to the rows of the material list. */
@Mixin(WidgetListMaterialList.class)
public abstract class WidgetListMaterialListMixin {
    @Shadow(remap = false)
    @Final
    private GuiMaterialList gui;

    @Inject(method = "getAllEntries", at = @At("RETURN"), cancellable = true, remap = false)
    private void materialshare$filter(CallbackInfoReturnable<Collection<MaterialListEntry>> cir) {
        cir.setReturnValue(ClientFilter.apply(cir.getReturnValue(), gui.getMaterialList()));
    }
}
