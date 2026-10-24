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
}
