package dev.materialshare.client;

import dev.materialshare.common.Project;
import dev.materialshare.common.Report;
import fi.dy.masa.malilib.gui.Message;
import fi.dy.masa.malilib.util.InfoUtils;
import java.time.LocalDate;
import net.minecraft.client.MinecraftClient;

/** Puts a project's report on the clipboard, in the style chosen. */
final class ProjectCopy {
    private ProjectCopy() {
    }

    private static void copy(Project project, Report.Style style, boolean recruit, String messageKey) {
        String report = Report.format(project, ClientProjectCache::nameOf, UiText::itemName, Lang.TRANSLATOR, style, recruit,
                LocalDate.now());
        MinecraftClient.getInstance().keyboard.setClipboard(report);
        InfoUtils.showGuiMessage(Message.MessageType.SUCCESS, Lang.t(messageKey));
    }

    static void plain(Project project) {
        copy(project, Report.Style.PLAIN, false, "report.copied");
    }

    static void discord(Project project) {
        copy(project, Report.Style.DISCORD, false, "report.copied.discord");
    }

    static void recruit(Project project) {
        copy(project, Report.Style.DISCORD, true, "report.copied.recruit");
    }

    private static String partsProject;
    private static java.util.List<String> parts = java.util.List.of();
    private static int nextPart;

    /**
     * The whole report for Discord, however long, in messages that each fit: every press copies the next part (the
     * first press, or a press for another project, starts again from part 1).
     */
    static void discordParts(Project project) {
        if (!project.name().equals(partsProject) || nextPart >= parts.size()) {
            parts = Report.discordParts(project, ClientProjectCache::nameOf, UiText::itemName, Lang.TRANSLATOR);
            partsProject = project.name();
            nextPart = 0;
        }
        MinecraftClient.getInstance().keyboard.setClipboard(parts.get(nextPart));
        nextPart++;
        InfoUtils.showGuiMessage(Message.MessageType.SUCCESS, nextPart < parts.size()
                ? Lang.t("report.part.more", nextPart, parts.size()) : Lang.t("report.part.last", nextPart, parts.size()));
    }

    /** The folder inside the game folder where exports and backups go. */
    private static java.nio.file.Path folder(String name) throws java.io.IOException {
        java.nio.file.Path dir = MinecraftClient.getInstance().runDirectory.toPath().resolve("materialshare").resolve(name);
        java.nio.file.Files.createDirectories(dir);
        return dir;
    }

    /** A file name from a project or schematic name and the time (characters Windows refuses are replaced). */
    static String fileName(String name, String extension) {
        String safe = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").strip();
        if (safe.isEmpty()) {
            safe = "project";
        }
        return safe + "_" + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                + extension;
    }

    /** Saves the project as a CSV table (for Excel and other spreadsheets) and says where it went. */
    static void csv(Project project) {
        String text = dev.materialshare.common.Csv.format(project, ClientProjectCache::nameOf, UiText::itemName, Lang.TRANSLATOR,
                ClientProjectCache.storage(project.name()).stock(), ClientProjectCache.zones(project.name()));
        try {
            java.nio.file.Path file = folder("exports").resolve(fileName(project.name(), ".csv"));
            java.nio.file.Files.writeString(file, text, java.nio.charset.StandardCharsets.UTF_8);
            saved(file, "export.saved");
        } catch (java.io.IOException | RuntimeException e) {
            InfoUtils.showGuiMessage(Message.MessageType.ERROR, Lang.t("export.failed", e.getMessage()));
        }
    }

    /** Copies the schematic file of the list's placement to the backups folder, with the date in its name. */
    static void backup(fi.dy.masa.litematica.materials.MaterialListBase list) {
        java.io.File source = list instanceof PlacementHolder holder && holder.materialshare$placement() != null
                ? holder.materialshare$placement().getSchematicFile() : null;
        if (source == null || !source.isFile()) {
            InfoUtils.showGuiMessage(Message.MessageType.WARNING, Lang.t("backup.noplacement"));
            return;
        }
        String base = source.getName().replaceFirst("\\.litematic$", "");
        try {
            java.nio.file.Path file = folder("backups").resolve(fileName(base, ".litematic"));
            java.nio.file.Files.copy(source.toPath(), file);
            saved(file, "backup.saved");
        } catch (java.io.IOException | RuntimeException e) {
            InfoUtils.showGuiMessage(Message.MessageType.ERROR, Lang.t("export.failed", e.getMessage()));
        }
    }

    /** Tells where the file went, in the chat as well (the path can be copied from there). */
    private static void saved(java.nio.file.Path file, String key) {
        String where = MinecraftClient.getInstance().runDirectory.toPath().relativize(file).toString();
        InfoUtils.showGuiMessage(Message.MessageType.SUCCESS, Lang.t(key, where));
        var player = MinecraftClient.getInstance().player;
        if (player != null) {
            player.sendMessage(dev.materialshare.McCompat.literal("§a" + Lang.t(key, where)), false);
        }
    }
}
