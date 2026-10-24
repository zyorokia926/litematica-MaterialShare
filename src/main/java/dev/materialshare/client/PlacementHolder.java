package dev.materialshare.client;

import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;

/** Implemented (by a mixin) on Litematica's placement material list: gives access to the placement it was made for. */
public interface PlacementHolder {
    SchematicPlacement materialshare$placement();
}
