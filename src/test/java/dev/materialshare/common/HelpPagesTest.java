package dev.materialshare.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The how-to screen (HelpScreen) must stay complete: every page has its texts in both languages and its pictures in
 * the jar, and the pages were looked at again for the current protocol version (so a new feature cannot be forgotten).
 */
class HelpPagesTest {
    private static final Path ROOT = Path.of("src/main/resources/assets/litematica_materialshare");
    private static final Path SOURCE = Path.of("src/main/java/dev/materialshare/client/HelpScreen.java");

    private record Page(String id, List<String> images) {
    }

    private static List<Page> pages() throws IOException {
        String text = Files.readString(SOURCE);
        List<Page> pages = new ArrayList<>();
        Matcher m = Pattern.compile("new Page\\(\"([a-z]+)\", List\\.of\\(([^)]*)\\)\\)").matcher(text);
        while (m.find()) {
            List<String> images = new ArrayList<>();
            Matcher im = Pattern.compile("\"([a-z0-9_]+)\"").matcher(m.group(2));
            while (im.find()) {
                images.add(im.group(1));
            }
            pages.add(new Page(m.group(1), images));
        }
        return pages;
    }

    @Test
    void everyPageHasTextsInBothLanguages() throws IOException {
        List<Page> pages = pages();
        assertTrue(pages.size() >= 15, "pages found: " + pages.size());
        for (String lang : new String[] {"ja_jp", "en_us"}) {
            JsonObject texts = JsonParser.parseString(Files.readString(ROOT.resolve("lang/" + lang + ".json"))).getAsJsonObject();
            for (Page p : pages) {
                for (String part : new String[] {"title", "short", "body"}) {
                    String key = "litematica_materialshare.help." + p.id() + "." + part;
                    assertTrue(texts.has(key), lang + " lacks " + key);
                    assertFalse(texts.get(key).getAsString().isBlank(), key);
                }
            }
        }
    }

    @Test
    void everyPictureIsInTheJarForBothLanguages() throws IOException {
        for (Page p : pages()) {
            for (String image : p.images()) {
                for (String lang : new String[] {"ja", "en"}) {
                    assertTrue(Files.isRegularFile(ROOT.resolve("textures/gui/help/" + lang + "/" + image + ".png")),
                            "missing picture " + lang + "/" + image + ".png (page " + p.id() + ")");
                }
            }
        }
    }

    @Test
    void pagesWereReviewedForTheCurrentProtocol() throws IOException {
        Matcher m = Pattern.compile("REVIEWED_PROTOCOL = (\\d+)").matcher(Files.readString(SOURCE));
        assertTrue(m.find());
        assertEquals(ProtocolCheck.PROTOCOL_VERSION, Integer.parseInt(m.group(1)),
                "The protocol changed: review the how-to pages (HelpScreen.PAGES, help.* texts, pictures) and raise REVIEWED_PROTOCOL");
    }
}
