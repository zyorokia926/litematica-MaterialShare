package dev.materialshare.common;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlayerNamesTest {
    @Test
    void isValidName_acceptsAnyNameTheServerAccepts() {
        assertTrue(PlayerNames.isValidName("Steve"));
        assertTrue(PlayerNames.isValidName("a_b1"));
        assertTrue(PlayerNames.isValidName("A".repeat(16)));
        // offline-mode servers and Geyser/Floodgate (Bedrock) players use other printable ASCII characters
        assertTrue(PlayerNames.isValidName(".Steve"));
        assertTrue(PlayerNames.isValidName("*Alex"));
        assertTrue(PlayerNames.isValidName("a-b"));
        assertTrue(PlayerNames.isValidName("[Mod]x"));
    }

    @Test
    void isValidName_rejectsOthers() {
        assertFalse(PlayerNames.isValidName(null));
        assertFalse(PlayerNames.isValidName(""));
        assertFalse(PlayerNames.isValidName("A".repeat(17)));
        assertFalse(PlayerNames.isValidName("a b"));
        assertFalse(PlayerNames.isValidName("スティーブ"));
        assertFalse(PlayerNames.isValidName("ab\u0001c"));
    }

    @Test
    void isValidLookupName_isTheStrictMojangFormat() {
        assertTrue(PlayerNames.isValidLookupName("Steve"));
        assertTrue(PlayerNames.isValidLookupName("a_b"));
        assertFalse(PlayerNames.isValidLookupName(".Steve"));
        assertFalse(PlayerNames.isValidLookupName("ab"));
        assertFalse(PlayerNames.isValidLookupName("A".repeat(17)));
        assertFalse(PlayerNames.isValidLookupName(null));
    }

    @Test
    void put_addsNewAndUpdatesExistingButCapsTotal() {
        Map<UUID, String> names = new HashMap<>();
        for (int i = 0; i < PlayerNames.MAX_KNOWN; i++) {
            assertTrue(PlayerNames.put(names, new UUID(0, i), "p" + i));
        }
        assertFalse(PlayerNames.put(names, new UUID(1, 1), "overflow"));
        assertEquals(PlayerNames.MAX_KNOWN, names.size());
        // a known player can still be renamed
        assertTrue(PlayerNames.put(names, new UUID(0, 0), "renamed"));
        assertEquals("renamed", names.get(new UUID(0, 0)));
    }

    @Test
    void put_returnsFalseWhenNothingChanged() {
        Map<UUID, String> names = new HashMap<>();
        assertTrue(PlayerNames.put(names, new UUID(0, 1), "Steve"));
        assertFalse(PlayerNames.put(names, new UUID(0, 1), "Steve"));
    }

    @Test
    void put_rejectsInvalidNames() {
        Map<UUID, String> names = new HashMap<>();
        assertFalse(PlayerNames.put(names, new UUID(0, 1), "bad name"));
        assertTrue(names.isEmpty());
    }

    @Test
    void rank_emptyQueryMatchesEverything() {
        assertEquals(0, PlayerNames.rank("", "Bob_Builder"));
    }

    @Test
    void rank_prefixBeatsContainsBeatsSubsequenceBeatsTypo() {
        assertEquals(0, PlayerNames.rank("bo", "Bob_Builder"));
        assertEquals(1, PlayerNames.rank("bui", "Bob_Builder"));
        assertEquals(2, PlayerNames.rank("bbld", "Bob_Builder"));
        assertEquals(3, PlayerNames.rank("Bpb", "Bob_Builder"));
        assertEquals(-1, PlayerNames.rank("xyz", "Bob_Builder"));
    }

    @Test
    void rank_ignoresCaseAndUnderscores() {
        assertEquals(0, PlayerNames.rank("BOB", "Bob_Builder"));
        assertEquals(0, PlayerNames.rank("bob_b", "Bob_Builder"));
        assertEquals(0, PlayerNames.rank("bobb", "Bob_Builder"));
    }

    @Test
    void rank_typoNeedsAtLeastThreeCharacters() {
        assertEquals(-1, PlayerNames.rank("xo", "Bob_Builder"));
    }

    @Test
    void filterAndSort_ordersByMatchQualityThenName() {
        Map<UUID, String> names = new java.util.LinkedHashMap<>();
        names.put(new UUID(0, 1), "Charlie_Miner");
        names.put(new UUID(0, 2), "Bob_Builder");
        names.put(new UUID(0, 3), "Abbot");
        names.put(new UUID(0, 4), "Bobby");
        java.util.List<Map.Entry<UUID, String>> result = PlayerNames.filterAndSort(names, "bob");
        assertEquals(java.util.List.of("Bobby", "Bob_Builder", "Abbot"),
                result.stream().map(Map.Entry::getValue).toList());
    }

    @Test
    void forcePut_addsPlayersWhoJoinEvenWhenTheCapIsReached() {
        Map<UUID, String> names = new HashMap<>();
        for (int i = 0; i < PlayerNames.MAX_KNOWN; i++) {
            PlayerNames.put(names, new UUID(0, i), "p" + i);
        }
        UUID joiner = new UUID(9, 9);
        assertFalse(PlayerNames.put(names, joiner, "NewPlayer"));
        assertTrue(PlayerNames.forcePut(names, joiner, "NewPlayer"));
        assertEquals("NewPlayer", names.get(joiner));
        assertFalse(PlayerNames.forcePut(names, new UUID(8, 8), "bad name"));
        assertTrue(PlayerNames.forcePut(names, new UUID(7, 7), ".Bedrock"));
    }
}
