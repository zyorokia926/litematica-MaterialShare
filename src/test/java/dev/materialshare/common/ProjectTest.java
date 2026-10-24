package dev.materialshare.common;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProjectTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private static Project stone64() {
        Project p = new Project("castle");
        p.merge(Map.of("stone", 64));
        return p;
    }

    @Test
    void merge_keepsExistingRequired() {
        Project p = stone64();
        assertTrue(p.merge(Map.of("stone", 128, "dirt", 8)));
        assertEquals(64, p.items().get("stone").required());
        assertEquals(8, p.items().get("dirt").required());
    }

    @Test
    void merge_ignoresNonPositive() {
        Project p = new Project("castle");
        assertFalse(p.merge(Map.of("dirt", 0, "sand", -5)));
        assertTrue(p.items().isEmpty());
    }

    @Test
    void setCollected_clampsToRange() {
        Project p = stone64();
        p.setClaim(A, "stone", true);
        assertTrue(p.setCollected(A, "stone", -3));
        assertEquals(0, p.totalCollected("stone"));
        assertTrue(p.setCollected(A, "stone", 999));
        assertEquals(64, p.totalCollected("stone"));
    }

    @Test
    void setCollected_rejectsNonClaimant() {
        Project p = stone64();
        assertFalse(p.setCollected(A, "stone", 10));
        assertEquals(0, p.totalCollected("stone"));
    }

    @Test
    void setClaim_offRemovesCollected() {
        Project p = stone64();
        p.setClaim(A, "stone", true);
        p.setCollected(A, "stone", 10);
        assertTrue(p.setClaim(A, "stone", false));
        assertEquals(0, p.totalCollected("stone"));
        assertFalse(p.items().get("stone").claims().containsKey(A));
    }

    @Test
    void multipleClaimants_sumTotals() {
        Project p = stone64();
        p.setClaim(A, "stone", true);
        p.setClaim(B, "stone", true);
        p.setCollected(A, "stone", 10);
        p.setCollected(B, "stone", 10);
        assertEquals(20, p.totalCollected("stone"));
        assertEquals(20, p.totalCollected());
        assertEquals(64, p.totalRequired());
    }

    @Test
    void anyoneCanEditOthers() {
        Project p = stone64();
        p.setClaim(A, "stone", true);
        assertTrue(p.setCollected(A, "stone", 5));
        assertEquals(5, p.items().get("stone").claims().get(A));
    }

    @Test
    void unknownItem_isIgnored() {
        Project p = stone64();
        assertFalse(p.setClaim(A, "gold", true));
        assertFalse(p.setCollected(A, "gold", 1));
    }

    @Test
    void entryCollectedTotal_sumsAllClaimants() {
        Project p = stone64();
        p.setClaim(A, "stone", true);
        p.setClaim(B, "stone", true);
        p.setCollected(A, "stone", 40);
        p.setCollected(B, "stone", 40);
        assertEquals(80, p.items().get("stone").collectedTotal());
    }

    @Test
    void merge_rejectsInvalidItemIds() {
        Project p = new Project("castle");
        p.merge(Map.of("Bad Id", 1, "", 1, "a:b:c", 1, "x".repeat(200), 1));
        assertTrue(p.items().isEmpty());
        assertTrue(p.merge(Map.of("minecraft:stone", 1, "oak_log", 1)));
        assertEquals(2, p.items().size());
    }

    @Test
    void merge_capsItemsPerProject() {
        Project p = new Project("castle");
        Map<String, Integer> many = new java.util.HashMap<>();
        for (int i = 0; i < Project.MAX_ITEMS + 100; i++) {
            many.put("minecraft:item_" + i, 1);
        }
        p.merge(many);
        assertEquals(Project.MAX_ITEMS, p.items().size());
    }

    @Test
    void totalCollected_capsEachItemAtRequired() {
        Project p = new Project("castle");
        p.merge(Map.of("stone", 64, "dirt", 64));
        p.setClaim(A, "stone", true);
        p.setClaim(B, "stone", true);
        p.setCollected(A, "stone", 64);
        p.setCollected(B, "stone", 64);
        // stone is over-collected (128/64) but must count as 64, leaving dirt incomplete
        assertEquals(64, p.totalCollected());
        assertEquals(128, p.totalRequired());
    }

    @Test
    void setClaim_limitsClaimantsPerItem() {
        Project p = stone64();
        for (int i = 0; i < Project.MAX_CLAIMANTS; i++) {
            assertTrue(p.setClaim(new UUID(0, 100 + i), "stone", true));
        }
        assertFalse(p.setClaim(new UUID(0, 999), "stone", true));
        assertEquals(Project.MAX_CLAIMANTS, p.items().get("stone").claims().size());
        // freeing a slot makes room again; an existing claimant is unaffected
        assertTrue(p.setClaim(new UUID(0, 100), "stone", false));
        assertTrue(p.setClaim(new UUID(0, 999), "stone", true));
    }

    @Test
    void surplus_offersAreSetTakenAndLimited() {
        Project p = new Project("castle");
        assertTrue(p.setSurplus(A, "minecraft:oak_planks", 40));
        assertFalse(p.setSurplus(A, "minecraft:oak_planks", 40)); // nothing changed
        assertTrue(p.setSurplus(B, "minecraft:oak_planks", 10));
        assertFalse(p.setSurplus(A, "bad id!", 5));
        assertEquals(15, p.takeSurplus(A, "minecraft:oak_planks", 15));
        assertEquals(25, p.surplus().get("minecraft:oak_planks").get(A));
        assertEquals(10, p.takeSurplus(B, "minecraft:oak_planks", 99)); // never more than offered
        assertFalse(p.surplus().get("minecraft:oak_planks").containsKey(B));
        assertEquals(0, p.takeSurplus(B, "minecraft:oak_planks", 1));
        assertTrue(p.setSurplus(A, "minecraft:oak_planks", 0));
        assertTrue(p.surplus().isEmpty());
        for (int i = 0; i < Project.MAX_OFFERS; i++) {
            assertTrue(p.setSurplus(new UUID(0, i), "minecraft:stone", 1));
        }
        assertFalse(p.setSurplus(A, "minecraft:dirt", 1)); // too many offers
        assertEquals(1, p.copyAs("copy", true).surplus().size());
    }
}
