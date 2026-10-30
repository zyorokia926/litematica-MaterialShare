package dev.materialshare.common;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * A project as a table for a spreadsheet: one row per item, with what everybody collected and handed in, what lies in
 * the storage and what each zone needs. Comma separated, quoted where needed, with a byte order mark so that Excel
 * reads Japanese names right.
 */
public final class Csv {
    private Csv() {
    }

    public static String format(Project project, Function<UUID, String> playerName, Function<String, String> itemName,
            Translator tr, Map<String, Integer> stock, List<Zone> zones) {
        Set<UUID> people = new LinkedHashSet<>();
        project.items().values().forEach(e -> people.addAll(e.claims().keySet()));
        List<String> head = new ArrayList<>(List.of(tr.tr("litematica_materialshare.csv.item"), tr.tr("litematica_materialshare.csv.id"),
                tr.tr("litematica_materialshare.csv.required"), tr.tr("litematica_materialshare.csv.collected"),
                tr.tr("litematica_materialshare.csv.left"), tr.tr("litematica_materialshare.csv.delivered"),
                tr.tr("litematica_materialshare.csv.stock"), tr.tr("litematica_materialshare.csv.priority")));
        for (UUID who : people) {
            head.add(tr.tr("litematica_materialshare.csv.person", playerName.apply(who)));
        }
        for (Zone z : zones) {
            head.add(tr.tr("litematica_materialshare.csv.zone", z.name()));
        }
        StringBuilder out = new StringBuilder("\uFEFF");
        line(out, head);
        for (Map.Entry<String, Entry> item : project.items().entrySet()) {
            Entry e = item.getValue();
            List<String> row = new ArrayList<>(List.of(itemName.apply(item.getKey()), item.getKey(), String.valueOf(e.required()),
                    String.valueOf(e.collectedTotal()), String.valueOf(Math.max(0, e.required() - e.collectedTotal())),
                    String.valueOf(e.deliveredTotal()), String.valueOf(stock.getOrDefault(item.getKey(), 0)), e.priority() ? "★" : ""));
            for (UUID who : people) {
                Integer n = e.claims().get(who);
                row.add(n == null ? "" : String.valueOf(n));
            }
            for (Zone z : zones) {
                row.add(z.needs(item.getKey()) ? String.valueOf(z.requiredOf(item.getKey())) : "");
            }
            line(out, row);
        }
        return out.toString();
    }

    private static void line(StringBuilder out, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(quote(cells.get(i)));
        }
        out.append("\r\n");
    }

    /** Quotes a cell that holds a comma, a quote or a line break; a leading = + - @ is made harmless for spreadsheets. */
    static String quote(String cell) {
        String c = cell == null ? "" : cell;
        if (!c.isEmpty() && "=+-@".indexOf(c.charAt(0)) >= 0 && !c.matches("-?\\d+")) {
            c = "'" + c;
        }
        if (c.contains(",") || c.contains("\"") || c.contains("\n") || c.contains("\r")) {
            return "\"" + c.replace("\"", "\"\"") + "\"";
        }
        return c;
    }
}
