package dev.materialshare.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Bulk assignment of the items nobody has taken yet. */
public final class Assignments {
    public record Assignment(String itemId, UUID player) {
    }

    private Assignments() {
    }

    private static List<Map.Entry<String, Entry>> unclaimed(Project project) {
        List<Map.Entry<String, Entry>> result = new ArrayList<>();
        for (Map.Entry<String, Entry> item : project.items().entrySet()) {
            if (item.getValue().claims().isEmpty()) {
                result.add(item);
            }
        }
        return result;
    }

    public static List<Assignment> allTo(Project project, UUID player) {
        List<Assignment> result = new ArrayList<>();
        unclaimed(project).forEach(item -> result.add(new Assignment(item.getKey(), player)));
        return result;
    }

    /**
     * Balances the amount and the number of kinds together: an item weighs half by its share of all the pieces needed
     * and half by being one of the kinds. Heaviest first, each to whoever has the least so far, so nobody ends up with
     * only the huge stacks of a few items, or with a long list of tiny ones.
     */
    public static List<Assignment> spread(Project project, List<UUID> players) {
        List<Assignment> result = new ArrayList<>();
        if (players.isEmpty()) {
            return result;
        }
        List<Map.Entry<String, Entry>> items = unclaimed(project);
        if (items.isEmpty()) {
            return result;
        }
        double totalRequired = 0;
        for (Map.Entry<String, Entry> item : items) {
            totalRequired += item.getValue().required();
        }
        double perKind = 0.5 / items.size();
        double perPiece = totalRequired > 0 ? 0.5 / totalRequired : 0;
        items.sort(Comparator.<Map.Entry<String, Entry>>comparingInt(e -> -e.getValue().required())
                .thenComparing(Map.Entry::getKey));
        double[] load = new double[players.size()];
        int[] kinds = new int[players.size()];
        for (Map.Entry<String, Entry> item : items) {
            int lightest = 0;
            for (int i = 1; i < load.length; i++) {
                if (load[i] < load[lightest] - 1e-12
                        || (Math.abs(load[i] - load[lightest]) <= 1e-12 && kinds[i] < kinds[lightest])) {
                    lightest = i;
                }
            }
            load[lightest] += perKind + perPiece * item.getValue().required();
            kinds[lightest]++;
            result.add(new Assignment(item.getKey(), players.get(lightest)));
        }
        return result;
    }

    public static List<Assignment> spread(Project project, Collection<UUID> players) {
        return spread(project, new ArrayList<>(players));
    }
}
