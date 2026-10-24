package dev.materialshare.common;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** What is still missing in a project: unfinished items, those nobody takes first, then by how much is left. */
public final class Missing {
    private Missing() {
    }

    public record Item(String itemId, int required, int collected, List<UUID> people) {
        public int left() {
            return Math.max(0, required - collected);
        }

        public boolean nobody() {
            return people.isEmpty();
        }
    }

    public static List<Item> of(Project project) {
        List<Item> result = new ArrayList<>();
        for (Map.Entry<String, Entry> e : project.items().entrySet()) {
            Entry entry = e.getValue();
            if (entry.collectedTotal() >= entry.required()) {
                continue;
            }
            result.add(new Item(e.getKey(), entry.required(), entry.collectedTotal(),
                    new ArrayList<>(entry.claims().keySet())));
        }
        result.sort(Comparator.comparing(Item::nobody).reversed()
                .thenComparing(Comparator.comparingInt(Item::left).reversed())
                .thenComparing(Item::itemId));
        return result;
    }

    /** How many of the items nobody takes yet. */
    public static int unassigned(List<Item> items) {
        return (int) items.stream().filter(Item::nobody).count();
    }
}
