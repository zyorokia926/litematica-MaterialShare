package dev.materialshare.client.mixin;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read access to the buttons a malilib screen already has, so new buttons can be placed where nothing else is. */
@Mixin(GuiBase.class)
public interface GuiBaseAccessor {
    @Accessor(value = "buttons", remap = false)
    List<ButtonBase> materialshare$buttons();
}
