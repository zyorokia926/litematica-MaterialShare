package dev.materialshare.client;

import net.minecraft.client.resource.language.I18n;

/** Texts of this mod in the language the player has chosen in Minecraft (assets/litematica_materialshare/lang). */
public final class Lang {
    private Lang() {
    }

    public static String t(String key, Object... args) {
        return I18n.translate("litematica_materialshare." + key, args);
    }

    /** For the pure code in the common package. */
    public static final dev.materialshare.common.Translator TRANSLATOR = (key, args) -> I18n.translate(key, args);

    /** A button wide enough for its text in any language. */
    public static int buttonWidth(fi.dy.masa.malilib.gui.GuiBase gui, String text, int min) {
        return Math.max(min, gui.getStringWidth(text) + 16);
    }
}
