package dev.materialshare.fapi.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 1.16 and 1.16.1: opens the buttons of a screen (for Screens.getButtons). The init event is in ScreenInitMixin. */
@Mixin(net.minecraft.client.gui.screen.Screen.class)
public interface ScreenMixin {
    @Accessor("buttons")
    java.util.List<net.minecraft.client.gui.widget.AbstractButtonWidget> materialshare$buttons();

    @Invoker("addButton")
    <T extends net.minecraft.client.gui.widget.AbstractButtonWidget> T materialshare$addButton(T button);
}
