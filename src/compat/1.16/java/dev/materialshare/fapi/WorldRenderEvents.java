package dev.materialshare.fapi;

/** The world was drawn (fired by WorldRendererMixin). */
public final class WorldRenderEvents {
    public static final Event<Last> LAST = new Event<>();

    private WorldRenderEvents() {
    }

    public interface Last {
        void onLast(WorldRenderContext context);
    }
}
