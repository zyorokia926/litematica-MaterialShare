package dev.materialshare.client;

import fi.dy.masa.litematica.materials.MaterialCache;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

/**
 * Counts what some layers of a schematic need, read from the schematic file itself (so chunks that are not loaded count
 * too). Layer 1 is the bottom of the schematic; moving the placement does not change the numbers.
 */
final class Zones {
    private Zones() {
    }

    private static Optional<SchematicPlacement> placementOf(MaterialListBase list) {
        return list instanceof PlacementHolder holder && holder.materialshare$placement() != null
                && holder.materialshare$placement().getSchematic() != null
                ? Optional.of(holder.materialshare$placement()) : Optional.empty();
    }

    /** The sub-regions counted: the enabled ones, or only the part the selected project is about. */
    private static List<String> regions(MaterialListBase list, SchematicPlacement placement) {
        Optional<String> part = Parts.scopeOf(list);
        List<String> names = new ArrayList<>();
        for (SubRegionPlacement region : placement.getAllSubRegionsPlacements()) {
            if (part.isPresent() ? region.getName().equals(part.get()) : region.isEnabled()) {
                names.add(region.getName());
            }
        }
        return names;
    }

    /** The lowest block of a sub-region (a negative size reaches downwards from its position). */
    private static int minY(LitematicaSchematic schematic, String region) {
        BlockPos pos = schematic.getSubRegionPosition(region);
        BlockPos size = schematic.getAreaSize(region);
        return size.getY() < 0 ? pos.getY() + size.getY() + 1 : pos.getY();
    }

    private static int bottom(LitematicaSchematic schematic) {
        int bottom = Integer.MAX_VALUE;
        for (String region : schematic.getAreaSizes().keySet()) {
            bottom = Math.min(bottom, minY(schematic, region));
        }
        return bottom == Integer.MAX_VALUE ? 0 : bottom;
    }

    /** How many layers the schematic has, or 0 when this list was not made for a placement. */
    static int height(MaterialListBase list) {
        Optional<SchematicPlacement> placement = placementOf(list);
        if (placement.isEmpty()) {
            return 0;
        }
        LitematicaSchematic schematic = placement.get().getSchematic();
        int bottom = bottom(schematic);
        int top = 0;
        for (String region : schematic.getAreaSizes().keySet()) {
            top = Math.max(top, minY(schematic, region) + Math.abs(schematic.getAreaSize(region).getY()) - bottom);
        }
        return top;
    }

    /** What layers {@code from} to {@code to} need per item (times the list's multiplier); empty without a placement. */
    static Map<String, Integer> required(MaterialListBase list, int from, int to) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Optional<SchematicPlacement> placement = placementOf(list);
        if (placement.isEmpty()) {
            return counts;
        }
        LitematicaSchematic schematic = placement.get().getSchematic();
        int bottom = bottom(schematic);
        MaterialCache cache = MaterialCache.getInstance();
        for (String region : regions(list, placement.get())) {
            var container = schematic.getSubRegionContainer(region);
            if (container == null) {
                continue;
            }
            var size = container.getSize();
            int base = minY(schematic, region) - bottom + 1;
            int y0 = Math.max(0, from - base);
            int y1 = Math.min(size.getY() - 1, to - base);
            for (int y = y0; y <= y1; y++) {
                for (int z = 0; z < size.getZ(); z++) {
                    for (int x = 0; x < size.getX(); x++) {
                        BlockState state = container.get(x, y, z);
                        if (state.isAir()) {
                            continue;
                        }
                        for (ItemStack stack : cache.getItems(state)) {
                            if (!stack.isEmpty()) {
                                counts.merge(Inventory.idOf(stack), stack.getCount(), Integer::sum);
                            }
                        }
                    }
                }
            }
        }
        int multiplier = Math.max(1, list.getMultiplier());
        if (multiplier > 1) {
            counts.replaceAll((id, n) -> (int) Math.min((long) n * multiplier, Integer.MAX_VALUE));
        }
        return counts;
    }
}
