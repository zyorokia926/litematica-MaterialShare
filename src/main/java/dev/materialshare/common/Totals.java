package dev.materialshare.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** What one player still has to collect, added up per material over several projects. */
public final class Totals {
    private Totals() {
    }

    /** {@code left} is what the items still miss in total; {@code projects} how many projects they are in. */
    public record Item(String itemId, int left, int projects) {
    }

    public static List<Item> of(Collection<Project> projects, UUID player) {
        Map<String, int[]> acc = new HashMap<>();
        for (Project p : projects) {
            for (Map.Entry<String, Entry> item : p.items().entrySet()) {
                Entry e = item.getValue();
                if (!e.claims().containsKey(player) || e.collectedTotal() >= e.required()) {
                    continue;
                }
                int[] a = acc.computeIfAbsent(item.getKey(), k -> new int[2]);
                a[0] += e.required() - e.collectedTotal();
                a[1]++;
            }
        }
        List<Item> result = new ArrayList<>();
        acc.forEach((id, a) -> result.add(new Item(id, a[0], a[1])));
        result.sort(Comparator.comparingInt(Item::left).reversed().thenComparing(Item::itemId));
        return result;
    }
}
