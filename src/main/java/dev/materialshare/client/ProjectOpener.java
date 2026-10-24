package dev.materialshare.client;

import dev.materialshare.common.ProjectRegistry;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.gui.GuiMaterialList;
import fi.dy.masa.litematica.materials.MaterialListPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.Message;
import fi.dy.masa.malilib.util.InfoUtils;
import java.util.Optional;

/** Opens the material list that belongs to a project, if its schematic is loaded in Litematica. */
final class ProjectOpener {
    private ProjectOpener() {
    }

    /** The loaded placement whose schematic file gives this project (or one of its parts) its name. */
    static Optional<SchematicPlacement> placementOf(String project) {
        try {
            for (SchematicPlacement placement : DataManager.getSchematicPlacementManager().getAllSchematicsPlacements()) {
                java.io.File file = placement.getSchematicFile();
                if (file == null) {
                    continue;
                }
                String base = ProjectRegistry.suggestName(file.getName());
                if (!base.isEmpty() && ProjectRegistry.belongsToList(base, project)) {
                    return Optional.of(placement);
                }
            }
        } catch (RuntimeException e) {
            // Litematica is not ready (no world): nothing to open
        }
        return Optional.empty();
    }

    static boolean canOpen(String project) {
        return placementOf(project).isPresent();
    }

    static void open(String project) {
        Optional<SchematicPlacement> placement = placementOf(project);
        if (placement.isEmpty()) {
            InfoUtils.showGuiMessage(Message.MessageType.WARNING, Lang.t("open.notloaded", UiText.escapeFormat(project)));
            return;
        }
        MaterialListPlacement list = new MaterialListPlacement(placement.get(), true);
        DataManager.setMaterialList(list);
        ProjectNames.choose(project);
        GuiBase.openGui(new GuiMaterialList(list));
    }
}
