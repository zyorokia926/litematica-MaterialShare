package dev.materialshare.common;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/** A plain-text progress report of a project, for pasting into chat (or Discord). */
public final class Report {
    /** Discord refuses messages longer than 2000 characters; stay below with room for the "more" line. */
    public static final int DISCORD_LIMIT = 1900;

    public enum Style { PLAIN, DISCORD }

    private Report() {
    }

    public static String format(Project project, Function<UUID, String> playerName,
            Function<String, String> itemName, Translator tr) {
        return format(project, playerName, itemName, tr, Style.PLAIN, false, LocalDate.now());
    }

    /**
     * @param unclaimedOnly the "recruiting" report: only the open items nobody has taken
     */
    public static String format(Project project, Function<UUID, String> playerName, Function<String, String> itemName,
            Translator tr, Style style, boolean unclaimedOnly, LocalDate today) {
        List<String> lines = lines(project, playerName, itemName, tr, style, unclaimedOnly);
        if (style == Style.DISCORD) {
            return limit(lines, tr);
        }
        return String.join("\n", lines) + "\n";
    }

    /**
     * The whole Discord report, cut into messages that each fit Discord's limit (whole lines only), each ending with
     * "(1/3)" and so on. Nothing is left out, however long the list is.
     */
    public static List<String> discordParts(Project project, Function<UUID, String> playerName,
            Function<String, String> itemName, Translator tr) {
        List<String> lines = lines(project, playerName, itemName, tr, Style.DISCORD, false);
        List<StringBuilder> parts = new ArrayList<>();
        StringBuilder part = new StringBuilder();
        // room for the "(12/12)" line at the end of each part
        int room = DISCORD_LIMIT - 12;
        for (String line : lines) {
            String l = line.length() > room ? line.substring(0, room) : line;
            if (part.length() > 0 && part.length() + l.length() + 1 > room) {
                parts.add(part);
                part = new StringBuilder();
            }
            part.append(l).append('\n');
        }
        parts.add(part);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            out.add(parts.size() == 1 ? parts.get(i).toString() : parts.get(i) + "(" + (i + 1) + "/" + parts.size() + ")\n");
        }
        return out;
    }

    private static List<String> lines(Project project, Function<UUID, String> playerName, Function<String, String> itemName,
            Translator tr, Style style, boolean unclaimedOnly) {
        boolean discord = style == Style.DISCORD;
        List<String> lines = new ArrayList<>();
        int collected = project.totalCollected();
        int required = project.totalRequired();
        int percent = required == 0 ? 0 : (int) (100L * collected / required);

        List<Map.Entry<String, Entry>> open = new ArrayList<>();
        int done = 0;
        for (Map.Entry<String, Entry> item : project.items().entrySet()) {
            if (item.getValue().collectedTotal() >= item.getValue().required()) {
                done++;
            } else {
                open.add(item);
            }
        }
        // the items marked "first" lead, then the biggest gaps
        open.sort(Comparator.<Map.Entry<String, Entry>>comparingInt(e -> e.getValue().priority() ? 0 : 1)
                .thenComparingInt(e -> -(e.getValue().required() - e.getValue().collectedTotal())));

        String header = unclaimedOnly
                ? tr.tr("litematica_materialshare.report.recruit.header", project.name(),
                        open.stream().filter(e -> e.getValue().claims().isEmpty()).count())
                : tr.tr("litematica_materialshare.report.header", project.name(), collected, required, percent);
        lines.add(discord ? "**" + header + "**" : header);
        if (discord && !unclaimedOnly) {
            int cells = 20;
            int filled = required == 0 ? 0 : (int) (cells * (long) Math.min(collected, required) / required);
            lines.add("▰".repeat(filled) + "▱".repeat(cells - filled) + " " + percent + "%");
        }
        if (!project.note().isEmpty()) {
            String note = tr.tr("litematica_materialshare.report.note", project.note());
            lines.add(discord ? "> " + note : note);
        }
        if (!unclaimedOnly) {
            lines.add(tr.tr("litematica_materialshare.report.summary", done, open.size()));
        }
        for (Map.Entry<String, Entry> item : open) {
            Entry e = item.getValue();
            if (unclaimedOnly && !e.claims().isEmpty()) {
                continue;
            }
            String who;
            if (e.claims().isEmpty()) {
                who = tr.tr("litematica_materialshare.report.unclaimed");
            } else {
                List<String> names = new ArrayList<>();
                e.claims().forEach((id, n) -> names.add(playerName.apply(id) + ":" + n));
                who = String.join(", ", names);
            }
            String name = itemName.apply(item.getKey());
            String line = tr.tr("litematica_materialshare.report.line", discord ? "**" + name + "**" : name, e.collectedTotal(),
                    e.required(), e.required() - e.collectedTotal(), who);
            if (e.priority()) {
                line = line.startsWith("- ") ? "- ★ " + line.substring(2) : "★ " + line;
            }
            lines.add(line);
        }
        return lines;
    }

    /** Keeps whole lines within Discord's limit and says how many were left out. */
    private static String limit(List<String> lines, Translator tr) {
        StringBuilder out = new StringBuilder();
        int used = 0;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (used + line.length() + 1 > DISCORD_LIMIT) {
                out.append(tr.tr("litematica_materialshare.report.more", lines.size() - i));
                return out.toString();
            }
            out.append(line).append('\n');
            used += line.length() + 1;
        }
        return out.toString();
    }
}
