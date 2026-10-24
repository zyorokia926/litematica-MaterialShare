package dev.materialshare.common;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ProjectRegistryTest {
    @Test
    void isValidName_rejectsBadNames() {
        assertFalse(ProjectRegistry.isValidName(null));
        assertFalse(ProjectRegistry.isValidName(""));
        assertFalse(ProjectRegistry.isValidName("   "));
        assertFalse(ProjectRegistry.isValidName(" lead"));
        assertFalse(ProjectRegistry.isValidName("trail "));
        assertFalse(ProjectRegistry.isValidName("a".repeat(65)));
        assertFalse(ProjectRegistry.isValidName("bad\nname"));
        assertFalse(ProjectRegistry.isValidName("tab\there"));
        assertFalse(ProjectRegistry.isValidName("a§b"));
    }

    @Test
    void isValidName_acceptsSchematicStyleNames() {
        assertTrue(ProjectRegistry.isValidName("城"));
        assertTrue(ProjectRegistry.isValidName("castle_2"));
        assertTrue(ProjectRegistry.isValidName("My Castle"));
        assertTrue(ProjectRegistry.isValidName("城 v2.1"));
        assertTrue(ProjectRegistry.isValidName("[Base] (2)"));
        assertTrue(ProjectRegistry.isValidName("a".repeat(64)));
    }

    @Test
    void suggestName_cleansUpASchematicName() {
        assertEquals("Castle v2", ProjectRegistry.suggestName("  Castle v2 "));
        assertEquals("ab", ProjectRegistry.suggestName("a§b"));
        assertEquals(64, ProjectRegistry.suggestName("x".repeat(100)).length());
        assertEquals("", ProjectRegistry.suggestName(null));
        assertEquals("", ProjectRegistry.suggestName("   "));
        assertTrue(ProjectRegistry.isValidName(ProjectRegistry.suggestName("  weird\tname \n§ ")));
    }

    @Test
    void suggestName_neverCutsInTheMiddleOfAnEmoji() {
        String emoji = new String(Character.toChars(0x1F600)); // 2 chars in UTF-16
        String name = ProjectRegistry.suggestName("a".repeat(63) + emoji + "tail");
        assertTrue(ProjectRegistry.isValidName(name));
        assertTrue(name.length() <= ProjectRegistry.MAX_NAME_LENGTH);
        assertEquals("a".repeat(63), name);
    }

    @Test
    void getOrCreate_returnsSameInstance() {
        ProjectRegistry r = new ProjectRegistry();
        assertSame(r.getOrCreate("castle"), r.getOrCreate("castle"));
        assertEquals(1, r.all().size());
    }

    @Test
    void delete_removesProject() {
        ProjectRegistry r = new ProjectRegistry();
        r.getOrCreate("castle");
        assertTrue(r.delete("castle"));
        assertTrue(r.get("castle").isEmpty());
    }

    @Test
    void delete_unknownReturnsFalse() {
        assertFalse(new ProjectRegistry().delete("nope"));
    }

    @Test
    void canCreate_capsProjectCountButAllowsExisting() {
        ProjectRegistry r = new ProjectRegistry();
        for (int i = 0; i < ProjectRegistry.MAX_PROJECTS; i++) {
            assertTrue(r.canCreate("p" + i));
            r.getOrCreate("p" + i);
        }
        assertFalse(r.canCreate("one_more"));
        assertTrue(r.canCreate("p0"));
    }

    @Test
    void suggestName_dropsTheSchematicFileExtension() {
        // Syncmatica names the copy a player receives "<name>.litematic", the sender's list is just "<name>"
        assertEquals("castle", ProjectRegistry.suggestName("castle.litematic"));
        assertEquals("castle", ProjectRegistry.suggestName("castle.LITEMATIC"));
        assertEquals("castle", ProjectRegistry.suggestName(" castle.litematic "));
        assertEquals("my.castle", ProjectRegistry.suggestName("my.castle.litematic"));
        assertEquals("my.castle", ProjectRegistry.suggestName("my.castle"));
        assertEquals("litematic", ProjectRegistry.suggestName("litematic"));
        assertEquals("", ProjectRegistry.suggestName(".litematic"));
        assertEquals(ProjectRegistry.suggestName("castle"), ProjectRegistry.suggestName("castle.litematic"));
    }

    @Test
    void partProjectsAreNamedAfterTheSchematicAndTheRegion() {
        assertEquals("castle / tower", ProjectRegistry.partName("castle", "tower"));
        assertTrue(ProjectRegistry.isValidName(ProjectRegistry.partName("castle", "tower")));
        // a very long name is cut to the limit, and is still recognised as the same part
        String base = "b".repeat(60);
        String name = ProjectRegistry.partName(base, "wing");
        assertEquals(ProjectRegistry.MAX_NAME_LENGTH, name.length());
    }

    @Test
    void partOfFindsTheRegionAProjectBelongsTo() {
        var regions = java.util.List.of("tower", "wall", "tower 2");
        assertEquals(java.util.Optional.of("tower"), ProjectRegistry.partOf("castle", "castle / tower", regions));
        assertEquals(java.util.Optional.of("tower 2"), ProjectRegistry.partOf("castle", "castle / tower 2", regions));
        assertEquals(java.util.Optional.empty(), ProjectRegistry.partOf("castle", "castle", regions));
        assertEquals(java.util.Optional.empty(), ProjectRegistry.partOf("castle", "other / tower", regions));
        assertEquals(java.util.Optional.empty(), ProjectRegistry.partOf("castle", "castle / gate", regions));
        String base = "b".repeat(60);
        assertEquals(java.util.Optional.of("wing"),
                ProjectRegistry.partOf(base, ProjectRegistry.partName(base, "wing"), java.util.List.of("wing")));
    }

    @Test
    void belongsToListIncludesTheWholeAndItsParts() {
        assertTrue(ProjectRegistry.belongsToList("castle", "castle"));
        assertTrue(ProjectRegistry.belongsToList("castle", "castle / tower"));
        assertFalse(ProjectRegistry.belongsToList("castle", "castle2"));
        assertFalse(ProjectRegistry.belongsToList("castle", "other"));
    }

    @Test
    void onlyTheProjectsOfThisSchematicAreOffered() {
        var all = java.util.List.of("mangrove farm / ALL", "castle / wall", "castle", "castle2", "castle / tower",
                "other");
        assertEquals(java.util.List.of("castle", "castle / wall", "castle / tower"),
                ProjectRegistry.projectsOfList("castle", all));
        assertEquals(java.util.List.of("mangrove farm / ALL"), ProjectRegistry.projectsOfList("mangrove farm", all));
        assertTrue(ProjectRegistry.projectsOfList("nothing", all).isEmpty());
    }
}
