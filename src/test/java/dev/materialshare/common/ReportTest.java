package dev.materialshare.common;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReportTest {
    private static final Translator STUB = (key, args) -> key + java.util.Arrays.toString(args);

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    @Test
    void listsUnfinishedItemsWithRemainingAndWhoHasThem() {
        Project p = new Project("castle");
        p.merge(Map.of("minecraft:stone", 64, "minecraft:dirt", 10, "minecraft:glass", 5));
        p.setClaim(A, "minecraft:stone", true);
        p.setCollected(A, "minecraft:stone", 30);
        p.setClaim(A, "minecraft:dirt", true);
        p.setCollected(A, "minecraft:dirt", 10);
        p.setNote("材料はチェスト");
        String text = Report.format(p, id -> "Alice", id -> id.substring(id.indexOf(':') + 1), STUB);
        assertTrue(text.contains("report.header[castle, 40, 79, 50]"));
        assertTrue(text.contains("report.note[材料はチェスト]"));
        assertTrue(text.contains("[stone, 30, 64, 34, Alice:30]"));
        assertTrue(text.contains("[glass, 0, 5, 5, report.unclaimed[]]") || text.contains("glass, 0, 5, 5, litematica_materialshare.report.unclaimed[]"));
        assertFalse(text.contains("[dirt, 10, 10"), "finished items are only counted");
        assertTrue(text.contains("report.summary[1, 2]"));
    }

    @Test
    void unfinishedItemsComeLargestRemainingFirst() {
        Project p = new Project("p");
        p.merge(Map.of("minecraft:a", 5, "minecraft:b", 500));
        String text = Report.format(p, id -> "x", id -> id, STUB);
        assertTrue(text.indexOf("minecraft:b") < text.indexOf("minecraft:a"));
    }
}
