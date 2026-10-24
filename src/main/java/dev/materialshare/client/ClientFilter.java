package dev.materialshare.client;

import dev.materialshare.common.Entry;
import dev.materialshare.common.RowFilter;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

/** Which rows the material list shows (all / mine / unclaimed / unfinished), applied by a mixin on the list. */
public final class ClientFilter {
    private static RowFilter current = RowFilter.ALL;
    private static ButtonGeneric button;

    private ClientFilter() {
    }

    public static RowFilter current() {
        return current;
    }

    private static boolean compact;

    private static UUID person;

    /** The player the "person" filter shows the items of (null until one is chosen). */
    public static void setPerson(UUID who) {
        person = who;
        if (button != null) {
            button.setDisplayString(label(compact));
        }
    }

    private static String name(RowFilter f) {
        if (f == RowFilter.PERSON && person != null) {
            String who = ClientProjectCache.nameOf(person);
            return "§e" + (who.length() > 8 ? who.substring(0, 7) + "…" : who) + "§r";
        }
        return Lang.t(f.key().substring("litematica_materialshare.".length()));
    }

    /** The button text: "Filter: Mine", or just "Mine" when the strip is too narrow for the prefix. */
    public static String label(boolean compactForm) {
        return compactForm ? name(current) : Lang.t("list.filter", name(current));
    }

    /** The widest text the button can show in each form (so it never needs to grow when it changes). */
    public static String fullLabelWidest() {
        return widest(true);
    }

    public static String shortLabelWidest() {
        return widest(false);
    }

    private static String widest(boolean withPrefix) {
        var font = net.minecraft.client.MinecraftClient.getInstance().textRenderer;
        String best = "";
        for (RowFilter f : RowFilter.values()) {
            // a chosen name is at most 8 letters wide: the widest letters stand for it
            String shown = f == RowFilter.PERSON ? "§eWWWWWWWW§r" : name(f);
            String text = withPrefix ? Lang.t("list.filter", shown) : shown;
            if (font.getWidth(text) > font.getWidth(best)) {
                best = text;
            }
        }
        return best;
    }

    public static void bind(ButtonGeneric b, boolean compactForm) {
        button = b;
        compact = compactForm;
    }

    /** Next filter; the caller refreshes the list. */
    public static void cycle() {
        current = current.next();
        if (button != null) {
            button.setDisplayString(label(compact));
        }
    }

    /**
     * The rows to show: when the selected project is one part (sub-region) of this list's schematic, only the
     * items of that part; then the chosen filter on top.
     */
    public static Collection<MaterialListEntry> apply(Collection<MaterialListEntry> entries,
            fi.dy.masa.litematica.materials.MaterialListBase list) {
        UUID self = RowOverlay.selfId();
        var project = ClientProjectCache.selected();
        if (project.isEmpty() || self == null) {
            return entries;
        }
        boolean partOnly = list != null && Parts.scopeOf(list).isPresent();
        if (current == RowFilter.ALL && !partOnly) {
            return entries;
        }
        Collection<MaterialListEntry> result = new ArrayList<>();
        for (MaterialListEntry entry : entries) {
            Entry e = project.get().items().get(RowOverlay.itemId(entry));
            if (partOnly && e == null) {
                continue; // belongs to another part of the schematic
            }
            if (current.matches(e, self, person)) {
                result.add(entry);
            }
        }
        return result;
    }
}
