package dev.materialshare.common;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import org.junit.jupiter.api.Test;

class ExportTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    /** Returns the key and the arguments, so the tests do not depend on the language files. */
    private static final Translator TR = (key, args) -> key.substring(key.lastIndexOf('.') + 1)
            + (args.length == 0 ? "" : java.util.Arrays.toString(args));

    private static Project sample() {
        Project p = new Project("castle");
        p.merge(Map.of("minecraft:stone", 100, "minecraft:glass", 10));
        p.setClaim(A, "minecraft:stone", true);
        p.setCollected(A, "minecraft:stone", 30);
        p.setClaim(B, "minecraft:stone", true);
        p.setCollected(B, "minecraft:stone", 5);
        return p;
    }

    @Test
    void csvHasOneRowPerItemAndAColumnPerPersonAndZone() {
        Project p = sample();
        String csv = Csv.format(p, id -> id.equals(A) ? "Alice" : "Bob, Jr.", id -> id.substring(10), TR,
                Map.of("minecraft:stone", 64), List.of(Zone.of("Base", 1, 3, Map.of("minecraft:glass", 4))));
        assertTrue(csv.startsWith("﻿"), "a byte order mark for Excel");
        String[] lines = csv.substring(1).split("\r\n");
        assertEquals(3, lines.length);
        // a name with a comma is quoted
        assertTrue(lines[0].contains("\"person[Bob, Jr.]\""), lines[0]);
        assertTrue(lines[0].endsWith("zone[Base]"), lines[0]);
        String stone = lines[1].startsWith("stone") ? lines[1] : lines[2];
        assertEquals("stone,minecraft:stone,100,35,65,0,64,,30,5,", stone);
        String glass = lines[1].startsWith("glass") ? lines[1] : lines[2];
        assertEquals("glass,minecraft:glass,10,0,10,0,0,,,,4", glass);
    }

    @Test
    void csvCellsCannotStartFormulas() {
        assertEquals("'=SUM(A1)", Csv.quote("=SUM(A1)"));
        assertEquals("-5", Csv.quote("-5"));
        assertEquals("\"a \"\"b\"\"\"", Csv.quote("a \"b\""));
    }

    @Test
    void discordPartsKeepEveryLineAndFitTheLimit() {
        Project p = new Project("big");
        Map<String, Integer> many = new java.util.HashMap<>();
        for (int i = 0; i < 300; i++) {
            many.put("minecraft:item_number_" + i, 50 + i);
        }
        p.merge(many);
        List<String> parts = Report.discordParts(p, id -> "x", id -> id, TR);
        assertTrue(parts.size() > 1);
        int lines = 0;
        for (int i = 0; i < parts.size(); i++) {
            String part = parts.get(i);
            assertTrue(part.length() <= 2000, "part " + i + " is " + part.length());
            assertTrue(part.endsWith("(" + (i + 1) + "/" + parts.size() + ")\n"));
            lines += part.split("\n").length - 1;
        }
        // every item line is there (the one-message report cuts them off)
        String all = String.join("", parts);
        for (int i = 0; i < 300; i++) {
            assertTrue(all.contains("minecraft:item_number_" + i + "**"), "item " + i);
        }
        assertTrue(lines >= 300);
        String one = Report.format(p, id -> "x", id -> id, TR, Report.Style.DISCORD, false, LocalDate.now());
        assertEquals(one.split("\n")[0], parts.get(0).split("\n")[0]);
    }

    @Test
    void aShortReportIsOnePartWithoutNumber() {
        List<String> parts = Report.discordParts(sample(), id -> "x", id -> id, TR);
        assertEquals(1, parts.size());
        assertFalse(parts.get(0).contains("(1/1)"));
    }

    @Test
    void foundRoundTrips() {
        Packets.Found found = new Packets.Found("castle", "minecraft:stone",
                List.of(new Packets.Spot("minecraft:overworld", 1, -60, 3, 64), new Packets.Spot("minecraft:the_nether", -5, 70, 9, 2)));
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        Packets.writeFound(buf, found);
        assertEquals(found, Packets.readFound(buf));
        assertEquals(0, buf.readableBytes());
    }
}
