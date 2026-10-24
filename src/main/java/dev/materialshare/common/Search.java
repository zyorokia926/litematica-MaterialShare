package dev.materialshare.common;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/** Name search of the archive and the trash: a name is a candidate when it shares even one character with the query. */
public final class Search {
    private Search() {
    }

    private static Set<Integer> chars(String text) {
        String folded = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        Set<Integer> set = new HashSet<>();
        folded.codePoints().filter(c -> !Character.isWhitespace(c)).forEach(set::add);
        return set;
    }

    /** How many different characters of the query appear in the name. */
    public static int score(String query, String name) {
        Set<Integer> q = chars(query);
        q.retainAll(chars(name));
        return q.size();
    }

    public static boolean matches(String query, String name) {
        return chars(query).isEmpty() || score(query, name) > 0;
    }

    /** The candidates, best match first (the given order breaks ties). An empty query keeps everything as it is. */
    public static <T> List<T> filter(String query, List<T> items, Function<T, String> name) {
        if (chars(query).isEmpty()) {
            return new ArrayList<>(items);
        }
        List<T> result = new ArrayList<>();
        for (T item : items) {
            if (score(query, name.apply(item)) > 0) {
                result.add(item);
            }
        }
        result.sort((a, b) -> Integer.compare(score(query, name.apply(b)), score(query, name.apply(a))));
        return result; // List.sort is stable
    }
}
