package dev.materialshare.fapi;

/** The buttons of a screen; adding one puts it on the screen (ScreenMixin opens addButton). */
public final class Screens {
    private Screens() {
    }

    public static java.util.List<net.minecraft.client.gui.widget.AbstractButtonWidget> getButtons(net.minecraft.client.gui.screen.Screen screen) {
        dev.materialshare.fapi.mixin.ScreenMixin open = (dev.materialshare.fapi.mixin.ScreenMixin) screen;
        return new java.util.AbstractList<>() {
            @Override
            public net.minecraft.client.gui.widget.AbstractButtonWidget get(int index) {
                return open.materialshare$buttons().get(index);
            }

            @Override
            public int size() {
                return open.materialshare$buttons().size();
            }

            @Override
            public boolean add(net.minecraft.client.gui.widget.AbstractButtonWidget button) {
                open.materialshare$addButton(button);
                return true;
            }
        };
    }
}
