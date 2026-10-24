package dev.materialshare.client.mixin;

import dev.materialshare.client.PlacementHolder;
import fi.dy.masa.litematica.materials.MaterialListPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Lets this add-on see which placement (and so which sub-regions) a material list belongs to. */
@Mixin(MaterialListPlacement.class)
public abstract class MaterialListPlacementMixin implements PlacementHolder {
    @Shadow(remap = false)
    @Final
    private SchematicPlacement placement;

    @Override
    public SchematicPlacement materialshare$placement() {
        return placement;
    }
}
