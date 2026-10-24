package dev.materialshare.common;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Who collected how much in a project. */
public final class Stats {
    private Stats() {
    }

    /** {@code collected} counts only up to each item's required amount, split fairly if several players share it. */
    public record PlayerStat(UUID player, int collected, int items, int itemsDone) {
    }

    public static List<PlayerStat> byPlayer(Project project) {
        Map<UUID, int[]> acc = new HashMap<>(); // collected, items, itemsDone
        for (Entry e : project.items().values()) {
            int total = e.collectedTotal();
            for (Map.Entry<UUID, Integer> c : e.claims().entrySet()) {
                int[] a = acc.computeIfAbsent(c.getKey(), k -> new int[3]);
                a[0] += c.getValue();
                a[1]++;
                if (total >= e.required() && c.getValue() > 0) {
                    a[2]++;
                }
            }
        }
        List<PlayerStat> result = new ArrayList<>();
        acc.forEach((id, a) -> result.add(new PlayerStat(id, a[0], a[1], a[2])));
        result.sort(Comparator.comparingInt(PlayerStat::collected).reversed()
                .thenComparing(Comparator.comparingInt(PlayerStat::items).reversed())
                .thenComparing(PlayerStat::player));
        return result;
    }

    /** Percent of everything collected in the project that this player collected (0 when nothing was collected). */
    public static int share(PlayerStat stat, List<PlayerStat> all) {
        long sum = all.stream().mapToLong(PlayerStat::collected).sum();
        return sum == 0 ? 0 : (int) (100L * stat.collected() / sum);
    }
}
