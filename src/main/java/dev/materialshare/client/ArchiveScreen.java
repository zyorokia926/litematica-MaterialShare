package dev.materialshare.client;

import dev.materialshare.common.Project;
import fi.dy.masa.litematica.materials.MaterialListBase;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.screen.Screen;

/** The archived projects of one schematic: open one again, or move it back to the project list. */
class ArchiveScreen extends SearchListScreen<Project> {
    private final MaterialListBase materialList;
    private final Screen listScreen;

    ArchiveScreen(MaterialListBase materialList, Screen projectScreen, Screen listScreen, int page, String query) {
        super(Lang.t("archive.title"), projectScreen, page, query);
        this.materialList = materialList;
        this.listScreen = listScreen;
    }

    /** The archived projects of one schematic, or of all of them when {@code list} is null. */
    static List<Project> archivedOf(MaterialListBase list) {
        List<Project> result = new ArrayList<>();
        if (list == null) {
            ClientProjectCache.projects().stream().filter(Project::archived).forEach(result::add);
            return result;
        }
        for (String name : ProjectNames.projectsFor(list)) {
            ClientProjectCache.projects().stream().filter(p -> p.name().equals(name) && p.archived()).findFirst()
                    .ifPresent(result::add);
        }
        return result;
    }

    @Override
    List<Project> candidates() {
        return archivedOf(materialList);
    }

    @Override
    String nameOf(Project p) {
        return p.name();
    }

    @Override
    String detailOf(Project p) {
        return p.totalCollected() + "/" + p.totalRequired() + (p.note().isEmpty() ? "" : "  ·  " + p.note());
    }

    @Override
    List<Act<Project>> actionsFor(Project p) {
        return List.of(
                new Act<>(Lang.t("archive.open"), Lang.t("archive.open.tip"), x -> {
                    if (listScreen == null) {
                        ProjectOpener.open(x.name());
                        return;
                    }
                    ProjectNames.choose(x.name());
                    openGui(listScreen);
                }),
                new Act<>(Lang.t("archive.restore"), Lang.t("archive.restore.tip"),
                        x -> ClientNet.sendArchive(x.name(), false)));
    }

    @Override
    String helpText() {
        return Lang.t("archive.help");
    }

    @Override
    String emptyText() {
        return Lang.t("archive.empty");
    }

    @Override
    SearchListScreen<Project> reopen(int page, String query) {
        return new ArchiveScreen(materialList, getParent(), listScreen, page, query);
    }
}
