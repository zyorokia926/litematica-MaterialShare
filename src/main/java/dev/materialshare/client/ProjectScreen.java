package dev.materialshare.client;

import dev.materialshare.client.config.ConfigScreen;
import dev.materialshare.common.Project;
import dev.materialshare.common.ProjectRegistry;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiConfirmAction;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.interfaces.IConfirmationListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;

/**
 * The projects of the schematic whose material list is open: pick one, archive it, delete it. Everything that can be
 * done with the selected project is one button away ("Tools"), so this screen stays simple.
 */
public class ProjectScreen extends GuiBase implements NoWheelClick {
    private static final int ROW_H = 22;

    private final MaterialListBase materialList;
    private final int page;
    private int shownVersion = ClientProjectCache.version();
    private int pages = 1;
    private int shownPage;

    private int navW() {
        return Lang.buttonWidth(this, Lang.t("common.prev"), 60);
    }

    private boolean empty;
    private int noteY = 56;
    private int helpY = 53;
    private int emptyY = 78;
    private String note = "";

    private record Bar(int x, int y, int width, double ratio) {
    }

    private final List<Bar> bars = new ArrayList<>();

    /** Parts of this schematic ticked to be shared together (a schematic with sub-regions). */
    private final java.util.Set<String> chosen;

    /** One line of the list: a project, or a part that is not shared yet (region null = the whole schematic). */
    private record Line(Project project, String region, boolean whole) {
    }

    public ProjectScreen(MaterialListBase materialList, Screen parent, int page) {
        this(materialList, parent, page, java.util.Set.of());
    }

    private ProjectScreen(MaterialListBase materialList, Screen parent, int page, java.util.Set<String> chosen) {
        this.chosen = chosen;
        this.materialList = materialList;
        this.page = page;
        this.setParent(parent);
        this.setTitle(Lang.t("proj.title"));
    }

    /** The material list screen this one was opened from. */
    Screen listScreen() {
        return getParent();
    }

    private String helpText() {
        return Parts.regions(materialList).isEmpty() ? Lang.t("proj.help") : Lang.t("parts.help");
    }

    /** The explanation is dropped on a very small screen, so the buttons and the projects keep their room. */
    private int helpH() {
        return this.height < (Parts.regions(materialList).isEmpty() ? 230 : 300) ? 0 : UiText.wrappedHeight(helpText(), this.width - 24);
    }

    @Override
    public void initGui() {
        super.initGui();
        int x = 12;
        int y = 28;

        // one strip of equal buttons: Back, Tools, Settings, Deselect (they wrap when the screen is narrow)
        String[] labels = {Lang.t("common.back"), Lang.t("proj.tools"), Lang.t("proj.settings"), Lang.t("proj.deselect")};
        int w = 60;
        for (String label : labels) {
            w = Math.max(w, getStringWidth(label) + 16);
        }
        Runnable[] actions = {() -> openGui(getParent()), () -> openGui(new ProjectToolsScreen(materialList, this, 0, 0)),
                () -> openGui(new ConfigScreen(this)), () -> {
                    ProjectNames.deselect(materialList);
                    openGui(getParent());
                }};
        String[] tips = {"", Lang.t("proj.tools.tip"), "", Lang.t("proj.deselect.tip")};
        int cx = x;
        int cy = y;
        for (int i = 0; i < labels.length; i++) {
            if (cx + w > this.width - 12 && cx > x) {
                cx = x;
                cy += 24;
            }
            ButtonGeneric button = new ButtonGeneric(cx, cy, w, 20, labels[i]);
            if (!tips[i].isEmpty()) {
                button.setHoverStrings(UiText.tip(tips[i]));
            }
            Runnable run = actions[i];
            addButton(button, (b, mb) -> run.run());
            cx += w + 4;
        }
        int below = cy + 26;

        // a schematic with sub-regions: share the chosen / all parts that are not shared yet, from here
        String base = ProjectNames.forList(materialList);
        List<String> regions = Parts.regions(materialList);
        List<String> unshared = new ArrayList<>();
        for (String region : regions) {
            if (!ClientProjectCache.exists(ProjectRegistry.partName(base, region))) {
                unshared.add(region);
            }
        }
        if (!unshared.isEmpty()) {
            int pending = (int) chosen.stream().filter(unshared::contains).count();
            String chosenLabel = Lang.t("parts.share.chosen", pending);
            String allLabel = Lang.t("parts.share.all");
            int sw = Math.max(Lang.buttonWidth(this, chosenLabel, 60), Lang.buttonWidth(this, allLabel, 60));
            int sx = x;
            int sy = below - 2;
            ButtonGeneric shareChosen = new ButtonGeneric(sx, sy, sw, 20, chosenLabel);
            shareChosen.setEnabled(pending > 0);
            shareChosen.setHoverStrings(UiText.tip(Lang.t("parts.share.chosen.tip")));
            addButton(shareChosen, (b, mb) -> shareParts(base, unshared.stream().filter(chosen::contains).toList()));
            if (sx + sw * 2 + 4 > this.width - 12) {
                sx = x;
                sy += 24;
            } else {
                sx += sw + 4;
            }
            ButtonGeneric shareAll = new ButtonGeneric(sx, sy, sw, 20, allLabel);
            shareAll.setHoverStrings(UiText.tip(Lang.t("parts.share.all.tip")));
            addButton(shareAll, (b, mb) -> shareParts(base, unshared));
            below = sy + 26;
        }

        Project selected = ClientProjectCache.selected().orElse(null);
        note = selected == null ? "" : selected.note();
        noteY = below;
        if (!note.isEmpty()) {
            below += UiText.wrappedHeight(Lang.t("proj.note", note), this.width - 24) + 4;
        }
        helpY = below;

        List<Project> projects = new ArrayList<>();
        for (String name : ProjectNames.projectsFor(materialList)) {
            ClientProjectCache.projects().stream().filter(p -> p.name().equals(name) && !p.archived()).findFirst()
                    .ifPresent(projects::add);
        }
        List<Line> lines = new ArrayList<>();
        projects.forEach(p -> lines.add(new Line(p, null, false)));
        if (!regions.isEmpty() && !ClientProjectCache.exists(base)) {
            lines.add(new Line(null, null, true));
        }
        unshared.forEach(region -> lines.add(new Line(null, region, false)));
        bars.clear();
        emptyY = helpY + helpH() + 8;
        int rowsTop = emptyY + (lines.isEmpty() ? UiText.wrappedHeight(Lang.t("proj.empty"), this.width - 24) + 8 : 0);
        int perPage = Math.max(1, (this.height - rowsTop - 40) / ROW_H);
        pages = Math.max(1, (lines.size() + perPage - 1) / perPage);
        int current = Math.min(page, pages - 1);
        shownPage = current;
        empty = lines.isEmpty();

        int archW = Lang.buttonWidth(this, Lang.t("proj.archive"), 50);
        int delW = Lang.buttonWidth(this, Lang.t("proj.delete"), 50);
        int rowY = rowsTop;
        for (int i = current * perPage; i < Math.min(lines.size(), (current + 1) * perPage); i++) {
            Line line = lines.get(i);
            if (line.project() == null) {
                int partW = Math.max(100, Math.min(280, this.width - 24 - delW - archW - 16));
                String region = line.region();
                String label = line.whole() ? Lang.t("parts.whole")
                        : (chosen.contains(region) ? "§a☑§r " : "§7☐§r ") + region;
                addButton(new ButtonGeneric(x, rowY, partW, 20, UiText.fit(label, partW - 10)), (b, mb) -> {
                    if (line.whole()) {
                        ProjectNames.startSharingAs(materialList, base, ProjectNames.required(materialList), this);
                        return;
                    }
                    java.util.Set<String> next = new java.util.HashSet<>(chosen);
                    if (!next.remove(region)) {
                        next.add(region);
                    }
                    openGui(new ProjectScreen(materialList, getParent(), current, next));
                });
                rowY += ROW_H;
                continue;
            }
            Project project = line.project();
            boolean isSelected = selected != null && selected.name().equals(project.name());
            String label = (isSelected ? "§a▶ §r" : "") + project.name()
                    + (ProjectNames.belongs(materialList, project.name()) ? " §b" + Lang.t("proj.own") + "§r" : "")
                    + "  §7" + project.totalCollected() + "/" + project.totalRequired() + "§r";
            int nameW = Math.max(100, Math.min(280, this.width - 24 - delW - archW - 16));
            if (project.totalRequired() > 0) {
                bars.add(new Bar(x + 3, rowY + 17, nameW - 6, (double) project.totalCollected() / project.totalRequired()));
            }
            addButton(new ButtonGeneric(x, rowY, nameW, 20, UiText.fit(label, nameW - 10)), (b, mb) -> {
                ProjectNames.choose(project.name());
                openGui(getParent());
            });
            ButtonGeneric archive = new ButtonGeneric(x + nameW + 8, rowY, archW, 20, Lang.t("proj.archive"));
            archive.setHoverStrings(UiText.tip(Lang.t("proj.archive.tip")));
            addButton(archive, (b, mb) -> ClientNet.sendArchive(project.name(), true));
            ButtonGeneric delete = new ButtonGeneric(x + nameW + 8 + archW + 4, rowY, delW, 20, Lang.t("proj.delete"));
            delete.setHoverStrings(UiText.tip(Lang.t("proj.delete.tip")));
            addButton(delete, (b, mb) -> confirmDelete(project.name()));
            rowY += ROW_H;
        }

        if (pages > 1) {
            int navY = this.height - 28;
            if (current > 0) {
                addButton(new ButtonGeneric(x, navY, navW(), 20, Lang.t("common.prev")),
                        (b, mb) -> openGui(new ProjectScreen(materialList, getParent(), current - 1, chosen)));
            }
            if (current < pages - 1) {
                addButton(new ButtonGeneric(x + navW() + 8, navY, navW(), 20, Lang.t("common.next")),
                        (b, mb) -> openGui(new ProjectScreen(materialList, getParent(), current + 1, chosen)));
            }
        }
    }

    @Override
    protected void drawContents(MatrixStack matrices, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(matrices, mouseX, mouseY, partialTicks);
        if (!note.isEmpty()) {
            UiText.drawWrapped(matrices, Lang.t("proj.note", note), 12, noteY, this.width - 24, 0xFFFFFFFF);
        }
        if (helpH() > 0) {
            UiText.drawWrapped(matrices, helpText(), 12, helpY, this.width - 24, 0xFFAAAAAA);
        }
        if (empty) {
            UiText.drawWrapped(matrices, Lang.t("proj.empty"), 12, emptyY, this.width - 24, 0xFFFFFFFF);
        }
        if (pages > 1) {
            drawStringWithShadow(matrices, Lang.t("common.page", shownPage + 1, pages), 12 + 2 * navW() + 16, this.height - 22,
                    0xFFFFFFFF);
        }
    }

    /** Draws each project's progress along the bottom edge of its button (on top of the buttons). */
    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float partialTicks) {
        super.render(matrices, mouseX, mouseY, partialTicks);
        for (Bar bar : bars) {
            DrawableHelper.fill(matrices, bar.x, bar.y, bar.x + bar.width, bar.y + 2, 0xFF1E1E1E);
            int filled = (int) Math.round(bar.width * Math.max(0.0, Math.min(1.0, bar.ratio)));
            if (filled > 0) {
                DrawableHelper.fill(matrices, bar.x, bar.y, bar.x + filled, bar.y + 2,
                        bar.ratio >= 1.0 ? 0xFF55FF55 : 0xFF55AAFF);
            }
        }
    }

    /** Re-opens itself when projects change on the server (create/delete/edits by other players). */
    @Override
    public void tick() {
        super.tick();
        if (shownVersion != ClientProjectCache.version()) {
            openGui(new ProjectScreen(materialList, getParent(), page, chosen));
        }
    }

    private void shareParts(String base, List<String> parts) {
        for (String region : parts) {
            Map<String, Integer> required = Parts.required(materialList, region);
            if (!required.isEmpty()) {
                ClientNet.sendCreate(ProjectRegistry.partName(base, region), required);
            }
        }
        // the parts show as shared as soon as the server has answered (this screen reopens itself)
        openGui(new ProjectScreen(materialList, getParent(), page));
    }

    private void confirmDelete(String name) {
        openGui(new GuiConfirmAction(320, Lang.t("delete.title"), new IConfirmationListener() {
            @Override
            public boolean onActionConfirmed() {
                ClientNet.sendDelete(name);
                return false;
            }

            @Override
            public boolean onActionCancelled() {
                return false;
            }
        }, this, Lang.t("delete.confirm", UiText.escapeFormat(name))));
    }
}
