package dev.materialshare.common;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The crafting chain of one item as a tree, for drawing it as a map: the item at the root, what it is crafted from
 * as its children, down to the raw materials. Each node stands for one step, so leftovers of a craft are shown on the
 * node ({@link Node#leftover}); the shared total over all items stays with {@link Breakdown#expand}.
 */
public final class BreakdownTree {
    private BreakdownTree() {
    }

    /**
     * @param amount   how many of the item are needed at this point
     * @param crafts   how many times the recipe is run (0 for a raw material)
     * @param leftover items left over from the last craft
     */
    public record Node(String item, int amount, int crafts, int leftover, List<Node> children) {
        public boolean raw() {
            return children.isEmpty();
        }

        /** Number of rows the node takes when drawn: a leaf is one row, a parent as many as its children together. */
        public int rows() {
            int total = 0;
            for (Node child : children) {
                total += child.rows();
            }
            return Math.max(1, total);
        }

        public int depth() {
            int deepest = 0;
            for (Node child : children) {
                deepest = Math.max(deepest, child.depth());
            }
            return children.isEmpty() ? 0 : deepest + 1;
        }
    }

    /** The chain of {@code amount} of {@code item}. With {@code flat}, the steps in between are left out. */
    public static Node build(String item, int amount, Breakdown.Recipes recipes, boolean flat) {
        Node full = node(item, amount, new java.util.LinkedHashSet<>(), recipes, 0);
        if (!flat || full.raw()) {
            return full;
        }
        Map<String, Integer> raw = new LinkedHashMap<>();
        collectRaw(full, raw);
        List<Node> leaves = new ArrayList<>();
        raw.forEach((id, n) -> leaves.add(new Node(id, n, 0, 0, List.of())));
        return new Node(full.item(), full.amount(), full.crafts(), full.leftover(), leaves);
    }

    private static void collectRaw(Node node, Map<String, Integer> into) {
        if (node.raw()) {
            into.merge(node.item(), node.amount(), Integer::sum);
            return;
        }
        for (Node child : node.children()) {
            collectRaw(child, into);
        }
    }

    private static Node node(String item, int amount, java.util.Set<String> path, Breakdown.Recipes recipes, int depth) {
        Breakdown.Recipe chosen = null;
        if (depth < 24) {
            for (Breakdown.Recipe r : recipes.recipesFor(item)) {
                if (Breakdown.usable(item, r, path, recipes)) {
                    chosen = r;
                    break;
                }
            }
        }
        if (chosen == null) {
            return new Node(item, amount, 0, 0, List.of());
        }
        int crafts = (amount + chosen.outputCount() - 1) / chosen.outputCount();
        java.util.Set<String> deeper = new java.util.LinkedHashSet<>(path);
        deeper.add(item);
        List<Node> children = new ArrayList<>();
        for (Map.Entry<String, Integer> ingredient : chosen.ingredients().entrySet()) {
            children.add(node(ingredient.getKey(), ingredient.getValue() * crafts, deeper, recipes, depth + 1));
        }
        return new Node(item, amount, crafts, crafts * chosen.outputCount() - amount, children);
    }
}
