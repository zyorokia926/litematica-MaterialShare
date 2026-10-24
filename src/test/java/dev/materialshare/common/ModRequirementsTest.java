package dev.materialshare.common;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModRequirementsTest {
    private static final String SYNCMATICA = "1.19.4-0.3.11"; // Syncmatica's versions start with the Minecraft version

    private static List<String> check(String litematica, String malilib) {
        return check(litematica, malilib, SYNCMATICA);
    }

    private static List<String> check(String litematica, String malilib, String syncmatica) {
        Map<String, String> installed = new HashMap<>();
        if (syncmatica != null) {
            installed.put("syncmatica", syncmatica);
        }
        if (litematica != null) {
            installed.put("litematica", litematica);
        }
        if (malilib != null) {
            installed.put("malilib", malilib);
        }
        return ModRequirements.problems(installed::get);
    }

    @Test
    void noProblemsWhenBothAreInRange() {
        assertTrue(check("0.14.7", "0.15.4").isEmpty());
        assertTrue(check("0.14.2", "0.15.2").isEmpty()); // lowest tested versions
        assertTrue(check("0.14.99", "0.15.99").isEmpty());
    }

    @Test
    void reportsEachMissingMod() {
        List<String> problems = check(null, null);
        assertEquals(2, problems.size());
        assertTrue(problems.get(0).contains("Litematica"));
        assertTrue(problems.get(1).contains("malilib"));
        assertEquals(1, check("0.14.7", null).size());
        assertEquals(1, check(null, "0.15.4").size());
    }

    @Test
    void rejectsTooOldAndTooNewVersions() {
        assertEquals(1, check("0.14.1", "0.15.4").size());
        assertEquals(1, check("0.15.0", "0.15.4").size());
        assertEquals(1, check("0.14.7", "0.15.1").size());
        assertEquals(1, check("0.14.7", "0.16.0").size());
    }

    @Test
    void messageNamesTheInstalledVersion() {
        assertTrue(check("0.13.0", "0.15.4").get(0).contains("0.13.0"));
    }

    @Test
    void comparesVersionNumbersNumerically() {
        assertTrue(ModRequirements.compare("0.14.10", "0.14.9") > 0);
        assertTrue(ModRequirements.compare("0.14.2", "0.14.2") == 0);
        assertTrue(ModRequirements.compare("0.14", "0.14.0") == 0);
        assertTrue(ModRequirements.compare("0.14.7-beta", "0.14.7") == 0);
        assertTrue(ModRequirements.compare("1.0", "0.99.99") > 0);
    }

    @Test
    void toleratesOddVersionStrings() {
        assertTrue(check("v0.14.7", "0.15.4").isEmpty());
        assertTrue(check("0.14.7+build.3", "0.15.4-dev").isEmpty());
        assertTrue(check("${version}", "0.15.4").isEmpty()); // unprocessed dev checkout: cannot tell, do not block
        assertEquals(1, check("99999999999999.1", "0.15.4").size()); // huge number: no exception, just "too new"
    }

    @Test
    void syncmaticaIsRequiredButAnyVersionIsAccepted() {
        assertTrue(check("0.14.7", "0.15.4", "1.19.4-0.3.11").isEmpty());
        assertTrue(check("0.14.7", "0.15.4", "0.0.1").isEmpty());
        assertTrue(check("0.14.7", "0.15.4", "99.0").isEmpty());
        List<String> problems = check("0.14.7", "0.15.4", null);
        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("Syncmatica"));
        assertTrue(problems.get(0).contains("is missing"));
        assertEquals(3, check(null, null, null).size());
    }
}
