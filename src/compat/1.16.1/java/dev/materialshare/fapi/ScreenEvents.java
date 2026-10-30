package dev.materialshare.fapi;

/** A screen was set up (fired by ScreenInitMixin). */
public final class ScreenEvents {
    public static final Event<AfterInit> AFTER_INIT = new Event<>();

    private ScreenEvents() {
    }

    public interface AfterInit {
        void afterInit(net.minecraft.client.MinecraftClient client, net.minecraft.client.gui.screen.Screen screen, int width, int height);
    }
}
