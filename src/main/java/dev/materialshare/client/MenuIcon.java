package dev.materialshare.client;

import dev.materialshare.MaterialShare;
import fi.dy.masa.malilib.gui.interfaces.IGuiIcon;
import fi.dy.masa.malilib.render.RenderUtils;
import net.minecraft.util.Identifier;

/** Small icons for this add-on's buttons in Litematica's menu, drawn like Litematica's own (three shades: off, normal, hovered). */
public enum MenuIcon implements IGuiIcon {
    PROJECTS(0, 0),
    SETTINGS(0, 16),
    HELP(0, 32);

    private static final Identifier TEXTURE = new Identifier(MaterialShare.MOD_ID, "textures/gui/menu_icons.png");
    private final int u;
    private final int v;

    MenuIcon(int u, int v) {
        this.u = u;
        this.v = v;
    }

    @Override
    public int getWidth() {
        return 16;
    }

    @Override
    public int getHeight() {
        return 16;
    }

    @Override
    public int getU() {
        return this.u;
    }

    @Override
    public int getV() {
        return this.v;
    }

    @Override
    public void renderAt(int x, int y, float zLevel, boolean enabled, boolean selected) {
        int shade = enabled ? (selected ? 2 : 1) : 0;
        RenderUtils.color(1f, 1f, 1f, 1f);
        RenderUtils.bindTexture(TEXTURE);
        RenderUtils.drawTexturedRect(x, y, this.u + shade * 16, this.v, 16, 16);
    }

    @Override
    public Identifier getTexture() {
        return TEXTURE;
    }
}
