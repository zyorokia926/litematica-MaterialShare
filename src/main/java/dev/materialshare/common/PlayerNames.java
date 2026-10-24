package dev.materialshare.common;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/** Rules for the player names the server remembers so anyone can be assigned to an item. */
public final class PlayerNames {
    /** Upper bound on remembered players, so lookups cannot grow the saved data without limit. */
    public static final int MAX_KNOWN = 5000;

    // Same rule as the game's own check for user names: up to 16 printable ASCII characters. Servers in offline mode
    // and Geyser/Floodgate (Bedrock players, e.g. ".Steve") use more than letters, digits and underscores.
    private static final Pattern VALID = Pattern.compile("[\\x21-\\x7E]{1,16}");
    // Mojang account names, the only ones the profile lookup can find.
    private static final Pattern LOOKUP = Pattern.compile("[A-Za-z0-9_]{3,16}");

    private PlayerNames() {
    }

    public static boolean isValidName(String name) {
        return name != null && VALID.matcher(name).matches();
    }

    public static boolean isValidLookupName(String name) {
        return name != null && LOOKUP.matcher(name).matches();
    }

    /** Remembers or renames a player. Returns true only if the map changed. */
    public static boolean put(Map<UUID, String> names, UUID id, String name) {
        if (!isValidName(name)) {
            return false;
        }
        String old = names.get(id);
        if (old == null && names.size() >= MAX_KNOWN) {
            return false;
        }
        if (name.equals(old)) {
            return false;
        }
        names.put(id, name);
        return true;
    }

    /**
     * Like {@link #put} but ignores the cap: a player who actually joins the server must always be assignable, even
     * if lookups have filled the list of remembered names.
     */
    public static boolean forcePut(Map<UUID, String> names, UUID id, String name) {
        if (!isValidName(name) || name.equals(names.get(id))) {
            return false;
        }
        names.put(id, name);
        return true;
    }

    // ---- search ---------------------------------------------------------------------------------

    /** Lower-case, without underscores, so "bobb" finds "Bob_Builder". */
    private static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT).replace("_", "");
    }

    /**
     * How well a typed query matches a name: 0 = starts with it, 1 = contains it, 2 = its letters appear in order
     * (e.g. "bbld" for "Bob_Builder"), 3 = a one-letter typo of a part of the name (queries of 3+ letters).
     * Returns -1 for no match. An empty query matches everything with 0.
     */
    public static int rank(String query, String name) {
        String q = normalize(query);
        String n = normalize(name);
        if (q.isEmpty() || n.startsWith(q)) {
            return 0;
        }
        if (n.contains(q)) {
            return 1;
        }
        if (isSubsequence(q, n)) {
            return 2;
        }
        if (q.length() >= 3 && closeToPartOf(q, n, 1)) {
            return 3;
        }
        return -1;
    }

    /** The players that match the query, best match first (then shorter names, then alphabetical). */
    public static List<Map.Entry<UUID, String>> filterAndSort(Map<UUID, String> names, String query) {
        record Hit(Map.Entry<UUID, String> entry, int rank) { }
        List<Hit> hits = new ArrayList<>();
        for (Map.Entry<UUID, String> e : names.entrySet()) {
            int rank = rank(query, e.getValue());
            if (rank >= 0) {
                hits.add(new Hit(e, rank));
            }
        }
        hits.sort(Comparator.<Hit>comparingInt(Hit::rank)
                .thenComparingInt(h -> h.entry().getValue().length())
                .thenComparing(h -> h.entry().getValue().toLowerCase(Locale.ROOT)));
        List<Map.Entry<UUID, String>> result = new ArrayList<>();
        hits.forEach(h -> result.add(h.entry()));
        return result;
    }

    private static boolean isSubsequence(String needle, String haystack) {
        int i = 0;
        for (int j = 0; j < haystack.length() && i < needle.length(); j++) {
            if (needle.charAt(i) == haystack.charAt(j)) {
                i++;
            }
        }
        return i == needle.length();
    }

    /** Approximate substring match: some part of the text is within maxDistance edits of the pattern. */
    private static boolean closeToPartOf(String pattern, String text, int maxDistance) {
        int m = pattern.length();
        int[] column = new int[m + 1];
        for (int i = 0; i <= m; i++) {
            column[i] = i;
        }
        if (column[m] <= maxDistance) {
            return true;
        }
        for (int j = 1; j <= text.length(); j++) {
            int diagonal = column[0];
            column[0] = 0; // a match may start anywhere in the text
            for (int i = 1; i <= m; i++) {
                int above = column[i];
                int cost = pattern.charAt(i - 1) == text.charAt(j - 1) ? 0 : 1;
                column[i] = Math.min(Math.min(column[i - 1] + 1, above + 1), diagonal + cost);
                diagonal = above;
            }
            if (column[m] <= maxDistance) {
                return true;
            }
        }
        return false;
    }
}
