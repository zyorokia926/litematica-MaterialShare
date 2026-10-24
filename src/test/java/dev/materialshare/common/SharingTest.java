package dev.materialshare.common;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import org.junit.jupiter.api.Test;

class SharingTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final String STONE = "minecraft:stone";

    private static Project project() {
        Project p = new Project("castle");
        p.merge(Map.of(STONE, 100, "minecraft:glass", 10));
        p.setClaim(A, STONE, true);
        p.setClaim(B, STONE, true);
        return p;
    }

    // ---- targets ----
    @Test
    void targetNeedsAClaimantAndStaysWithinRequired() {
        Project p = project();
        assertTrue(p.setTarget(A, STONE, 60));
        assertEquals(60, p.items().get(STONE).targetOf(A));
        assertTrue(p.setTarget(A, STONE, 5000));
        assertEquals(100, p.items().get(STONE).targetOf(A));
        assertFalse(p.setTarget(C, STONE, 10), "C has not taken the item");
        assertFalse(p.setTarget(A, "minecraft:dirt", 10));
    }

    @Test
    void zeroTargetClearsThePromise() {
        Project p = project();
        p.setTarget(A, STONE, 40);
        assertTrue(p.setTarget(A, STONE, 0));
        assertTrue(p.items().get(STONE).targets().isEmpty());
        assertFalse(p.setTarget(A, STONE, 0), "nothing changed");
    }

    @Test
    void droppingAnItemDropsTheTargetAndTheDelivery() {
        Project p = project();
        p.setCollected(A, STONE, 30);
        p.setTarget(A, STONE, 40);
        p.setDelivered(A, STONE, 20);
        p.setClaim(A, STONE, false);
        assertFalse(p.items().get(STONE).targets().containsKey(A));
        assertFalse(p.items().get(STONE).delivered().containsKey(A));
    }

    // ---- delivery ----
    @Test
    void deliveredIsNeverMoreThanCollected() {
        Project p = project();
        p.setCollected(A, STONE, 30);
        assertTrue(p.setDelivered(A, STONE, 500));
        assertEquals(30, p.items().get(STONE).delivered().get(A));
        p.setCollected(A, STONE, 10);
        assertEquals(10, p.items().get(STONE).delivered().get(A), "lowering the count lowers the hand-in too");
        assertFalse(p.setDelivered(C, STONE, 1));
    }

    // ---- hand over ----
    @Test
    void transferMovesEverythingToTheOtherPlayer() {
        Project p = project();
        p.setCollected(A, STONE, 30);
        p.setTarget(A, STONE, 50);
        p.setDelivered(A, STONE, 10);
        assertTrue(p.transfer(STONE, A, C));
        Entry e = p.items().get(STONE);
        assertFalse(e.claims().containsKey(A));
        assertEquals(30, e.claims().get(C));
        assertEquals(50, e.targetOf(C));
        assertEquals(10, e.delivered().get(C));
        assertTrue(e.targets().keySet().stream().noneMatch(A::equals));
    }

    @Test
    void transferToSomebodyWhoHasItAddsUp() {
        Project p = project();
        p.setCollected(A, STONE, 30);
        p.setCollected(B, STONE, 25);
        assertTrue(p.transfer(STONE, A, B));
        assertEquals(55, p.items().get(STONE).claims().get(B));
        assertFalse(p.items().get(STONE).claims().containsKey(A));
    }

    @Test
    void transferIsRefusedForNonsense() {
        Project p = project();
        assertFalse(p.transfer(STONE, A, A));
        assertFalse(p.transfer(STONE, C, B), "C has nothing to hand over");
        assertFalse(p.transfer("minecraft:dirt", A, B));
    }

    // ---- items ----
    @Test
    void itemsCanBeRemoved() {
        Project p = project();
        assertTrue(p.removeItem("minecraft:glass"));
        assertFalse(p.items().containsKey("minecraft:glass"));
        assertFalse(p.removeItem("minecraft:glass"));
    }

    @Test
    void updateAddsAndChangesButKeepsUnneededItems() {
        Project p = project();
        p.setCollected(A, STONE, 80);
        Project.Update u = p.updateRequired(Map.of(STONE, 60, "minecraft:dirt", 5));
        assertEquals(1, u.added());
        assertEquals(1, u.changed());
        assertEquals(60, p.items().get(STONE).required());
        assertEquals(60, p.items().get(STONE).claims().get(A), "a count above the new requirement is cut down");
        assertTrue(p.items().containsKey("minecraft:glass"), "not in the update, still kept");
        assertEquals(5, p.items().get("minecraft:dirt").required());
    }

    @Test
    void updateWithTheSameAmountsChangesNothing() {
        Project p = project();
        Project.Update u = p.updateRequired(Map.of(STONE, 100, "minecraft:glass", 10));
        assertEquals(0, u.added() + u.changed());
    }

    // ---- rename / copy ----
    @Test
    void renameKeepsTheDataAndTheOrder() {
        ProjectRegistry r = new ProjectRegistry();
        r.getOrCreate("a");
        Project b = r.getOrCreate("b / x");
        b.merge(Map.of(STONE, 10));
        b.setClaim(A, STONE, true);
        b.setNote("note");
        r.getOrCreate("c");
        assertEquals(ProjectRegistry.Change.OK, r.rename("b / x", "b / y"));
        assertTrue(r.get("b / x").isEmpty());
        Project renamed = r.get("b / y").get();
        assertTrue(renamed.items().get(STONE).claims().containsKey(A));
        assertEquals("note", renamed.note());
        assertEquals(List.of("a", "b / y", "c"), r.all().stream().map(Project::name).toList());
    }

    @Test
    void renameChecksTheNewName() {
        ProjectRegistry r = new ProjectRegistry();
        r.getOrCreate("a");
        r.getOrCreate("b");
        assertEquals(ProjectRegistry.Change.NAME_TAKEN, r.rename("a", "b"));
        assertEquals(ProjectRegistry.Change.BAD_NAME, r.rename("a", " bad "));
        assertEquals(ProjectRegistry.Change.NOT_FOUND, r.rename("zzz", "q"));
    }

    @Test
    void copyKeepsPeopleButNotCounts() {
        ProjectRegistry r = new ProjectRegistry();
        Project p = r.getOrCreate("a");
        p.merge(Map.of(STONE, 10));
        p.setClaim(A, STONE, true);
        p.setCollected(A, STONE, 7);
        p.setTarget(A, STONE, 8);
        p.setNote("n");
        assertEquals(ProjectRegistry.Change.OK, r.copy("a", "a / 2", true));
        Project c = r.get("a / 2").get();
        assertEquals(0, c.items().get(STONE).claims().get(A));
        assertEquals(8, c.items().get(STONE).targetOf(A));
        assertEquals("", c.note());
        assertEquals(7, p.items().get(STONE).claims().get(A), "the original is untouched");
        assertEquals(ProjectRegistry.Change.OK, r.copy("a", "a / 3", false));
        assertTrue(r.get("a / 3").get().items().get(STONE).claims().isEmpty());
    }

    @Test
    void copyIsRefusedWhenTheNameIsTakenOrTheLimitReached() {
        ProjectRegistry r = new ProjectRegistry();
        r.getOrCreate("a");
        assertEquals(ProjectRegistry.Change.NAME_TAKEN, r.copy("a", "a", true));
        for (int i = 0; i < ProjectRegistry.MAX_PROJECTS; i++) {
            r.getOrCreate("p" + i);
        }
        assertEquals(ProjectRegistry.Change.TOO_MANY, r.copy("a", "new", true));
    }

    // ---- split ----
    @Test
    void evenSplitAddsUpExactly() {
        Map<UUID, Integer> shares = Split.even(100, List.of(A, B, C));
        assertEquals(100, shares.values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(34, shares.get(A));
        assertEquals(33, shares.get(B));
        assertEquals(33, shares.get(C));
        assertTrue(Split.even(100, List.of()).isEmpty());
        assertEquals(1, Split.even(1, List.of(A, B)).size(), "nobody gets an empty share");
    }

    @Test
    void unassignedIsWhatIsNotPromised() {
        Project p = project();
        p.setTarget(A, STONE, 30);
        assertEquals(70, Split.unassigned(p.items().get(STONE)));
        p.setTarget(B, STONE, 90);
        assertEquals(0, Split.unassigned(p.items().get(STONE)));
    }

    // ---- wire ----
    @Test
    void targetsAndDeliveryCrossTheWire() {
        Project p = project();
        p.setCollected(A, STONE, 30);
        p.setTarget(A, STONE, 40);
        p.setDelivered(A, STONE, 10);
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        Packets.writeItem(buf, "castle", STONE, p.items().get(STONE));
        Entry back = Packets.readItem(buf).entry();
        assertEquals(40, back.targetOf(A));
        assertEquals(10, back.delivered().get(A));
    }

    @Test
    void targetsPacketRoundTrips() {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        Packets.writeTargets(buf, "castle", STONE, Map.of(A, 10));
        Packets.Targets t = Packets.readTargets(buf);
        assertEquals("castle", t.project());
        assertEquals(10, t.targets().get(A));
    }

    @Test
    void feedRoundTripsAndIsCapped() {
        History.Event e = new History.Event(5L, A, History.Kind.CLAIM, STONE, A, 0);
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        Packets.writeFeed(buf, List.of(new Packets.FeedEntry("castle", e)));
        List<Packets.FeedEntry> back = Packets.readFeed(buf);
        assertEquals("castle", back.get(0).project());
        assertEquals(History.Kind.CLAIM, back.get(0).event().kind());
        buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeVarInt(Integer.MAX_VALUE);
        assertTrue(Packets.readFeed(buf).isEmpty());
    }

    // ---- counts with stacks ----
    @Test
    void stacksCanBeTyped() {
        assertEquals(OptionalInt.of(192), Counts.parse("3st", 64));
        assertEquals(OptionalInt.of(48), Counts.parse("3ST", 16));
        assertEquals(OptionalInt.of(202), Counts.parse("3st+10", 64));
        assertEquals(OptionalInt.of(192), Counts.parse("3s", 64));
        assertEquals(OptionalInt.of(48), Counts.parse("3s", 16));
        assertEquals(OptionalInt.of(138), Counts.parse("2s+10", 64));
        assertEquals(OptionalInt.of(27 * 64), Counts.parse("1sb", 64));
        assertEquals(OptionalInt.of(5), Counts.parse("5", 64));
        assertEquals(OptionalInt.of(3), Counts.parse("3S", 1));
        assertTrue(Counts.parse("s", 64).isEmpty());
        assertTrue(Counts.parse("3x", 64).isEmpty());
        assertTrue(Counts.parse("2s+", 64).isEmpty());
        assertEquals(OptionalInt.of(0), Counts.parse("-5", 64));
    }

    @Test
    void shulkersAreCountedFromHalfABox() {
        assertEquals(0.0, Stacks.shulkers(100, 64));
        assertEquals(1.0, Stacks.shulkers(27 * 64, 64));
        assertEquals(0.5, Stacks.shulkers(27 * 32, 64));
        assertEquals(0.0, Stacks.shulkers(10, 0));
    }

    // ---- filters ----
    @Test
    void newFilters() {
        Project p = project();
        p.items().get(STONE).setPriority(true);
        Entry stone = p.items().get(STONE);
        Entry glass = p.items().get("minecraft:glass");
        assertTrue(RowFilter.PRIORITY.matches(stone, A));
        assertFalse(RowFilter.PRIORITY.matches(glass, A));
        assertTrue(RowFilter.OTHERS.matches(stone, C), "A and B have it, C does not");
        assertFalse(RowFilter.OTHERS.matches(stone, A));
        assertFalse(RowFilter.OTHERS.matches(glass, C), "nobody has glass");
        assertTrue(RowFilter.SHORT.matches(glass, A), "nobody has it");
        assertFalse(RowFilter.SHORT.matches(stone, A), "somebody has it and there are no targets");
        p.setTarget(A, STONE, 30);
        assertTrue(RowFilter.SHORT.matches(stone, A), "only 30 of 100 are promised");
        p.setTarget(B, STONE, 70);
        assertFalse(RowFilter.SHORT.matches(stone, A));
    }

    // ---- breakdown ----
    private static Breakdown.Recipes recipes(Map<String, List<Breakdown.Recipe>> map) {
        return id -> map.getOrDefault(id, List.of());
    }

    @Test
    void breakdownFollowsRecipesDown() {
        var map = Map.of(
                "stick", List.of(new Breakdown.Recipe(4, Map.of("planks", 2))),
                "planks", List.of(new Breakdown.Recipe(4, Map.of("log", 1))));
        Map<String, Integer> raw = Breakdown.expand(Map.of("stick", 8), recipes(map));
        // 8 sticks = 2 crafts = 4 planks = 1 craft = 1 log
        assertEquals(Map.of("log", 1), raw);
    }

    @Test
    void breakdownTreeShowsEachStep() {
        var map = Map.of(
                "stick", List.of(new Breakdown.Recipe(4, Map.of("planks", 2))),
                "planks", List.of(new Breakdown.Recipe(4, Map.of("log", 1))));
        BreakdownTree.Node tree = BreakdownTree.build("stick", 6, recipes(map), false);
        assertEquals(2, tree.crafts());
        assertEquals(2, tree.leftover());
        assertEquals(2, tree.depth());
        BreakdownTree.Node planks = tree.children().get(0);
        assertEquals(4, planks.amount());
        assertEquals(1, planks.crafts());
        BreakdownTree.Node log = planks.children().get(0);
        assertTrue(log.raw());
        assertEquals(1, log.amount());
        assertEquals(1, tree.rows());
        BreakdownTree.Node flat = BreakdownTree.build("stick", 6, recipes(map), true);
        assertEquals(1, flat.depth());
        assertEquals("log", flat.children().get(0).item());
        assertTrue(BreakdownTree.build("log", 5, recipes(map), false).raw());
    }

    @Test
    void breakdownReusesLeftovers() {
        var map = Map.of(
                "planks", List.of(new Breakdown.Recipe(4, Map.of("log", 1))),
                "chest", List.of(new Breakdown.Recipe(1, Map.of("planks", 8))));
        Map<String, Integer> raw = Breakdown.expand(Map.of("chest", 1, "planks", 3), recipes(map));
        // 8 planks for the chest = 2 logs; 3 more planks: 0 left over from an exact 2 crafts, so 1 more log
        assertEquals(Map.of("log", 3), raw);
    }

    @Test
    void breakdownKeepsIngotsAndNeverUsesNuggets() {
        var map = Map.of(
                "iron_block", List.of(new Breakdown.Recipe(1, Map.of("iron_ingot", 9))),
                "iron_ingot", List.of(new Breakdown.Recipe(9, Map.of("iron_block", 1)),
                        new Breakdown.Recipe(1, Map.of("iron_nugget", 9))),
                "iron_nugget", List.of(new Breakdown.Recipe(9, Map.of("iron_ingot", 1))));
        assertEquals(Map.of("iron_ingot", 18), Breakdown.expand(Map.of("iron_block", 2), recipes(map)));
        assertEquals(Map.of("iron_ingot", 4), Breakdown.expand(Map.of("iron_ingot", 4), recipes(map)));
    }

    @Test
    void breakdownSurvivesCycles() {
        var map = Map.of("a", List.of(new Breakdown.Recipe(1, Map.of("b", 1))), "b", List.of(new Breakdown.Recipe(1, Map.of("a", 1))));
        Map<String, Integer> raw = Breakdown.expand(Map.of("a", 3), recipes(map));
        assertEquals(1, raw.size());
        assertEquals(3, raw.values().iterator().next());
    }

    @Test
    void breakdownOfRawThingsIsThemselves() {
        assertEquals(Map.of("sand", 5), Breakdown.expand(Map.of("sand", 5), recipes(Map.of())));
        assertTrue(Breakdown.expand(Map.of("sand", 0), recipes(Map.of())).isEmpty());
    }

    @Test
    void historySearchFindsItemActionAndPlayer() {
        Translator tr = (key, args) -> key.replace("litematica_materialshare.", "") + java.util.Arrays.toString(args);
        java.util.function.Function<UUID, String> names = id -> id.equals(A) ? "Alice" : "Bob";
        java.util.function.Function<String, String> items = id -> id.equals(STONE) ? "Stone" : id;
        History.Event e = new History.Event(1L, A, History.Kind.CLAIM, STONE, B, 0);
        assertTrue(HistoryText.matches(e, "", names, items, tr));
        assertTrue(HistoryText.matches(e, "stone", names, items, tr)); // the item
        assertTrue(HistoryText.matches(e, "ALICE", names, items, tr)); // the player who did it
        assertTrue(HistoryText.matches(e, "bob", names, items, tr)); // the player it is about
        assertTrue(HistoryText.matches(e, "claim", names, items, tr)); // the kind of action (its sentence)
        assertTrue(HistoryText.matches(e, "alice stone", names, items, tr)); // all words, any order
        assertFalse(HistoryText.matches(e, "alice dirt", names, items, tr));
        assertFalse(HistoryText.matches(e, "carol", names, items, tr));
    }
}
