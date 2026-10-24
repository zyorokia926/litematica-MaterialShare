package dev.materialshare.common;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Turns a list of finished items into the raw materials they are crafted from. */
public final class Breakdown {
    private Breakdown() {
    }

    /** A crafting recipe: {@code outputCount} items from the ingredients (item id to count). */
    public record Recipe(int outputCount, Map<String, Integer> ingredients) {
    }

    public interface Recipes {
        /** Every crafting recipe that makes this item (empty for raw materials). */
        List<Recipe> recipesFor(String itemId);
    }

    /**
     * Raw materials needed for {@code wanted}. Several players each need whole crafts, so leftovers of one craft (for
     * example 3 of 4 planks) are reused before crafting more. Pairs like "9 ingots <-> 1 block" are never followed back
     * to the smaller side, and nuggets are never used, so ingots and gems stay as they are.
     */
    public static Map<String, Integer> expand(Map<String, Integer> wanted, Recipes recipes) {
        Map<String, Integer> raw = new LinkedHashMap<>();
        Map<String, Integer> leftover = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : wanted.entrySet()) {
            if (e.getValue() > 0) {
                need(e.getKey(), e.getValue(), new LinkedHashSet<>(), recipes, raw, leftover, 0);
            }
        }
        return raw;
    }

    private static boolean reverseExists(String output, String ingredient, Recipes recipes) {
        for (Recipe r : recipes.recipesFor(ingredient)) {
            if (r.ingredients().containsKey(output)) {
                return true;
            }
        }
        return false;
    }

    static boolean usable(String item, Recipe r, Set<String> path, Recipes recipes) {
        if (r.outputCount() <= 0 || r.ingredients().isEmpty()) {
            return false;
        }
        for (String ingredient : r.ingredients().keySet()) {
            if (path.contains(ingredient) || ingredient.equals(item) || ingredient.endsWith("_nugget")) {
                return false;
            }
        }
        // one item turned into several of the smaller kind (block -> 9 ingots) while the way back exists
        if (r.ingredients().size() == 1 && r.outputCount() > 1) {
            String only = r.ingredients().keySet().iterator().next();
            if (r.ingredients().get(only) == 1 && reverseExists(item, only, recipes)) {
                return false;
            }
        }
        return true;
    }

    private static void need(String item, int amount, Set<String> path, Recipes recipes, Map<String, Integer> raw,
            Map<String, Integer> leftover, int depth) {
        int spare = leftover.getOrDefault(item, 0);
        if (spare > 0) {
            int use = Math.min(spare, amount);
            leftover.put(item, spare - use);
            amount -= use;
        }
        if (amount <= 0) {
            return;
        }
        Recipe chosen = null;
        if (depth < 24) {
            for (Recipe r : recipes.recipesFor(item)) {
                if (usable(item, r, path, recipes)) {
                    chosen = r;
                    break;
                }
            }
        }
        if (chosen == null) {
            raw.merge(item, amount, Integer::sum);
            return;
        }
        int crafts = (amount + chosen.outputCount() - 1) / chosen.outputCount();
        leftover.merge(item, crafts * chosen.outputCount() - amount, Integer::sum);
        Set<String> deeper = new LinkedHashSet<>(path);
        deeper.add(item);
        for (Map.Entry<String, Integer> ingredient : chosen.ingredients().entrySet()) {
            need(ingredient.getKey(), ingredient.getValue() * crafts, deeper, recipes, raw, leftover, depth + 1);
        }
    }

    /** Sorted by amount, biggest first. */
    public static List<Map.Entry<String, Integer>> sorted(Map<String, Integer> raw) {
        List<Map.Entry<String, Integer>> list = new ArrayList<>(raw.entrySet());
        list.sort((a, b) -> b.getValue() != a.getValue().intValue() ? Integer.compare(b.getValue(), a.getValue())
                : a.getKey().compareTo(b.getKey()));
        return list;
    }
}
