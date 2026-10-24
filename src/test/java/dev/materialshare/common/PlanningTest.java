package dev.materialshare.common;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import org.junit.jupiter.api.Test;

class PlanningTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final Translator TR = (key, args) -> key.replace("litematica_materialshare.", "") + java.util.Arrays.toString(args);

    // ---- item info ----
    @Test
    void textIsCleanedAndCut() {
        assertEquals("chest at spawn", Entry.cleanText("  chest\u0007 at spawn§  "));
        assertEquals(Entry.MAX_TEXT, Entry.cleanText("a".repeat(500)).length());
        assertEquals("", Entry.cleanText(null));
    }

    @Test
    void itemInfoSurvivesTheWire() {
        Project p = new Project("castle");
        p.merge(Map.of("minecraft:stone", 10));
        p.items().get("minecraft:stone").setPriority(true);
        p.items().get("minecraft:stone").setSubstitute("deepslate is fine");
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        Packets.writeProject(buf, p);
        Project back = Packets.readProject(buf);
        assertTrue(back.items().get("minecraft:stone").priority());
        assertEquals("deepslate is fine", back.items().get("minecraft:stone").substitute());

        buf = new PacketByteBuf(Unpooled.buffer());
        Packets.writeItem(buf, "castle", "minecraft:stone", p.items().get("minecraft:stone"));
        Packets.ItemUpdate update = Packets.readItem(buf);
        assertTrue(update.entry().priority());
    }

    // ---- stacks ----
    @Test
    void stacksText() {
        assertEquals("", Stacks.format(10, 64, TR));
        assertEquals("", Stacks.format(500, 1, TR));
        assertEquals("stacks.exact[2]", Stacks.format(128, 64, TR));
        assertEquals("stacks.rest[10, 60]", Stacks.format(700, 64, TR));
        assertEquals("stacks.rest[1, 8]", Stacks.format(24, 16, TR));
    }

    // ---- stats ----
    private static Project project() {
        Project p = new Project("castle");
        p.merge(Map.of("minecraft:stone", 100, "minecraft:glass", 10, "minecraft:dirt", 50));
        p.setClaim(A, "minecraft:stone", true);
        p.setClaim(B, "minecraft:stone", true);
        p.setCollected(A, "minecraft:stone", 60);
        p.setCollected(B, "minecraft:stone", 40);
        p.setClaim(B, "minecraft:glass", true);
        p.setCollected(B, "minecraft:glass", 10);
        return p;
    }

    @Test
    void statsNumbers() {
        List<Stats.PlayerStat> stats = Stats.byPlayer(project());
        assertEquals(A, stats.get(0).player());
        assertEquals(60, stats.get(0).collected());
        assertEquals(1, stats.get(0).items());
        assertEquals(50, stats.get(1).collected());
        assertEquals(2, stats.get(1).items());
        assertEquals(2, stats.get(1).itemsDone());
        assertEquals(54, Stats.share(stats.get(0), stats));
        assertEquals(0, Stats.share(stats.get(0), List.of(new Stats.PlayerStat(A, 0, 1, 0))));
    }

    // ---- report styles ----
    private static String names(UUID id) {
        return id.equals(A) ? "Cara" : "Taro";
    }

    @Test
    void plainReportIsUnchangedWithoutNewFields() {
        Project p = project();
        p.setClaim(A, "minecraft:dirt", true);
        String text = Report.format(p, PlanningTest::names, s -> s, TR);
        assertFalse(text.contains("★"));
        assertTrue(text.contains("report.line"));
    }

    @Test
    void priorityLeads() {
        Project p = new Project("x");
        p.merge(Map.of("minecraft:stone", 100, "minecraft:glass", 5));
        p.items().get("minecraft:glass").setPriority(true);
        String text = Report.format(p, PlanningTest::names, s -> s, TR);
        int glass = text.indexOf("minecraft:glass");
        int stone = text.indexOf("minecraft:stone");
        assertTrue(glass > 0 && glass < stone, "priority item first even though its gap is smaller");
        assertTrue(text.contains("★ "));
    }

    @Test
    void discordReportUsesMarkdownAndABar() {
        Project p = project();
        String text = Report.format(p, PlanningTest::names, s -> s, TR, Report.Style.DISCORD, false, LocalDate.of(2026, 10, 21));
        assertTrue(text.startsWith("**report.header"));
        assertTrue(text.contains("▰"));
        assertTrue(text.contains("▱"));
        assertTrue(text.contains("**minecraft:dirt**"));
    }

    @Test
    void recruitingReportListsOnlyFreeItems() {
        Project p = new Project("x");
        p.merge(Map.of("minecraft:stone", 100, "minecraft:glass", 5));
        p.setClaim(A, "minecraft:stone", true);
        String text = Report.format(p, PlanningTest::names, s -> s, TR, Report.Style.PLAIN, true, LocalDate.of(2026, 10, 21));
        assertTrue(text.contains("report.recruit.header[x, 1]"));
        assertTrue(text.contains("minecraft:glass"));
        assertFalse(text.contains("minecraft:stone"));
    }

    @Test
    void discordReportStaysUnderTheLimit() {
        Project p = new Project("big");
        java.util.Map<String, Integer> req = new java.util.LinkedHashMap<>();
        for (int i = 0; i < 200; i++) {
            req.put("minecraft:item_number_" + i, 100 + i);
        }
        p.merge(req);
        String text = Report.format(p, PlanningTest::names, s -> s, TR, Report.Style.DISCORD, false, LocalDate.of(2026, 10, 21));
        assertTrue(text.length() <= 2000, "length " + text.length());
        assertTrue(text.contains("report.more["));
    }
}
