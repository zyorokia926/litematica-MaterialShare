package dev.materialshare.common;

import java.util.UUID;

/** Which rows of the material list are shown. Unregistered items count as unclaimed and unfinished. */
public enum RowFilter {
    ALL, MINE, UNCLAIMED, INCOMPLETE, PRIORITY, OTHERS, SHORT, PERSON;

    /** The language key of the name shown for this filter. */
    public String key() {
        return "litematica_materialshare.filter." + name().toLowerCase(java.util.Locale.ROOT);
    }

    public RowFilter next() {
        RowFilter[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public boolean matches(Entry entry, UUID self) {
        return matches(entry, self, null);
    }

    /** {@code person} is the player chosen for {@link #PERSON} (nobody chosen yet shows everything). */
    public boolean matches(Entry entry, UUID self, UUID person) {
        return switch (this) {
            case PERSON -> person == null || (entry != null && entry.claims().containsKey(person));
            case ALL -> true;
            case MINE -> entry != null && entry.claims().containsKey(self);
            case UNCLAIMED -> entry == null || entry.claims().isEmpty();
            case INCOMPLETE -> entry == null || entry.collectedTotal() < entry.required();
            case PRIORITY -> entry != null && entry.priority();
            // somebody has it, but not me
            case OTHERS -> entry != null && !entry.claims().isEmpty() && !entry.claims().containsKey(self);
            // not finished, and not everything is promised to somebody
            case SHORT -> entry == null || (entry.collectedTotal() < entry.required()
                    && (entry.claims().isEmpty() || (!entry.targets().isEmpty() && entry.targetTotal() < entry.required())));
        };
    }
}
