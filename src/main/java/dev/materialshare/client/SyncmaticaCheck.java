package dev.materialshare.client;

import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import java.util.Optional;
import java.util.UUID;

/**
 * A project can only be started for a schematic that is shared with Syncmatica right now, so that everyone works on
 * the same file. Two things must hold: Syncmatica has given the placement a server id, and the Syncmatica server
 * really lists a placement with that id (an id kept in the saved placements from an earlier share does not count).
 * If this cannot be checked, the schematic counts as not shared.
 */
public final class SyncmaticaCheck {
    private static final String CONTAINER = "ch.endte.syncmatica.litematica.IIDContainer";
    private static final String MANAGER = "ch.endte.syncmatica.litematica.LitematicManager";
    private static boolean warned;

    private SyncmaticaCheck() {
    }

    public static boolean isShared(MaterialListBase list) {
        Optional<SchematicPlacement> placement = list instanceof PlacementHolder holder
                ? Optional.ofNullable(holder.materialshare$placement()) : Optional.empty();
        return placement.isPresent() && isShared(placement.get());
    }

    /** Syncmatica for 1.20.5 and later calls it syncmatica$getServerId (a prefixed interface method); older ones getServerId. */
    private static java.lang.reflect.Method serverIdMethod(Class<?> container) throws NoSuchMethodException {
        try {
            return container.getMethod("getServerId");
        } catch (NoSuchMethodException old) {
            return container.getMethod("syncmatica$getServerId");
        }
    }

    static boolean isShared(SchematicPlacement placement) {
        try {
            Class<?> container = Class.forName(CONTAINER);
            if (!container.isInstance(placement)) {
                return false;
            }
            Object id = serverIdMethod(container).invoke(placement);
            if (!(id instanceof UUID serverId)) {
                return false;
            }
            Class<?> manager = Class.forName(MANAGER);
            Object context = manager.getMethod("getActiveContext").invoke(manager.getMethod("getInstance").invoke(null));
            if (context == null) {
                return false; // not connected to a Syncmatica server
            }
            Object syncmatics = context.getClass().getMethod("getSyncmaticManager").invoke(context);
            return syncmatics.getClass().getMethod("getPlacement", UUID.class).invoke(syncmatics, serverId) != null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            if (!warned) {
                warned = true;
                System.err.println("[litematica_materialshare] Cannot tell whether the schematic is shared via Syncmatica: " + e);
            }
            return false;
        }
    }
}
