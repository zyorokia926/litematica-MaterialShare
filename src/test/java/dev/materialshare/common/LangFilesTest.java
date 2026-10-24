package dev.materialshare.common;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Every text the code asks for exists in both languages, with the same number of placeholders. */
class LangFilesTest {
    private static final Path LANG = Path.of("src/main/resources/assets/litematica_materialshare/lang");

    private static JsonObject load(String name) throws IOException {
        return JsonParser.parseString(Files.readString(LANG.resolve(name + ".json"))).getAsJsonObject();
    }

    private static int placeholders(String text) {
        Matcher m = Pattern.compile("%(?!%)[sd]").matcher(text);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    @Test
    void bothLanguagesHaveTheSameKeysAndPlaceholders() throws IOException {
        JsonObject ja = load("ja_jp");
        JsonObject en = load("en_us");
        assertEquals(ja.keySet(), en.keySet());
        for (String key : ja.keySet()) {
            assertEquals(placeholders(ja.get(key).getAsString()), placeholders(en.get(key).getAsString()), key);
            assertFalse(en.get(key).getAsString().isBlank(), key);
        }
    }

    @Test
    void everyKeyUsedInTheCodeExists() throws IOException {
        JsonObject en = load("en_us");
        Set<String> used = new HashSet<>();
        Pattern lang = Pattern.compile("Lang\\.t\\(\\s*\"([a-z0-9_.]+)\"");
        Pattern common = Pattern.compile("\"(litematica_materialshare\\.[a-z0-9_.]+)\"");
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".java"))::iterator) {
                String text = Files.readString(file);
                Matcher m = lang.matcher(text);
                while (m.find()) {
                    used.add("litematica_materialshare." + m.group(1));
                }
                m = common.matcher(text);
                while (m.find()) {
                    used.add(m.group(1));
                }
            }
        }
        assertTrue(used.size() > 50, "found " + used.size());
        for (String key : used) {
            if (key.equals("litematica_materialshare.filter.") || key.equals("litematica_materialshare.")
                    ) {
                continue;
            }
            assertTrue(en.has(key), "missing text: " + key);
        }
    }

    @Test
    void filterNamesExist() throws IOException {
        JsonObject en = load("en_us");
        for (RowFilter f : RowFilter.values()) {
            assertTrue(en.has(f.key()), f.key());
        }
    }
}
