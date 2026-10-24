package dev.materialshare.client;

import dev.materialshare.common.ProjectRegistry;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import fi.dy.masa.litematica.materials.MaterialListUtils;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A schematic file can hold several sub-regions. Each one ("part") can be its own project, so that who takes what is
 * kept separately per part. Only material lists made for a placement have parts.
 */
public final class Parts {
    private static final Map<String, Map<String, Integer>> CACHE = new HashMap<>();

    private Parts() {
    }

    private static Optional<SchematicPlacement> placementOf(MaterialListBase list) {
        return list instanceof PlacementHolder holder ? Optional.ofNullable(holder.materialshare$placement())
                : Optional.empty();
    }

    /** Names of the sub-regions, or an empty list when there is only one (nothing to choose from). */
    public static List<String> regions(MaterialListBase list) {
        List<String> names = new ArrayList<>();
        placementOf(list).ifPresent(placement -> {
            if (placement.getSubRegionCount() > 1) {
                for (SubRegionPlacement region : placement.getAllSubRegionsPlacements()) {
                    names.add(region.getName());
                }
            }
        });
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    /** What one sub-region needs, per item (the list shows count * multiplier). */
    public static Map<String, Integer> required(MaterialListBase list, String region) {
        Optional<SchematicPlacement> placement = placementOf(list);
        if (placement.isEmpty() || placement.get().getSchematic() == null) {
            return Map.of();
        }
        String key = System.identityHashCode(placement.get()) + "\n" + System.identityHashCode(placement.get().getSchematic()) + "\n" + region;
        Map<String, Integer> base = CACHE.computeIfAbsent(key, k -> {
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (MaterialListEntry entry : MaterialListUtils.createMaterialListFor(placement.get().getSchematic(),
                    List.of(region))) {
                counts.merge(RowOverlay.itemId(entry), entry.getCountTotal(), Integer::sum);
            }
            return counts;
        });
        int multiplier = Math.max(1, list.getMultiplier());
        Map<String, Integer> result = new LinkedHashMap<>();
        base.forEach((id, n) -> result.put(id, (int) Math.min((long) n * multiplier, Integer.MAX_VALUE)));
        return result;
    }

    /** The region the selected project is about, if it is one of this list's part projects. */
    public static Optional<String> scopeOf(MaterialListBase list) {
        var selected = ClientProjectCache.selected();
        if (selected.isEmpty()) {
            return Optional.empty();
        }
        return ProjectRegistry.partOf(ProjectNames.forList(list), selected.get().name(), regions(list));
    }
}
