package dev.materialshare.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

/**
 * The current user name and skin of players who are not on this server, looked up at Mojang's session server by UUID.
 * Lookups run one at a time on a background thread (the server limits how often a profile may be asked for); an answer
 * is kept for the session and a failed lookup is tried again only after a while.
 */
final class MojangProfiles {
    private static final String URL = "https://sessionserver.mojang.com/session/minecraft/profile/";
    private static final long RETRY_MS = 5 * 60 * 1000L;
    private static final long SPACING_MS = 300;

    private static final Map<UUID, String> NAMES = new ConcurrentHashMap<>();
    private static final Map<UUID, Identifier> SKINS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> ASKED = new ConcurrentHashMap<>();
    private static final Set<UUID> IN_FLIGHT = ConcurrentHashMap.newKeySet();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "materialshare-profiles");
        t.setDaemon(true);
        return t;
    });

    private MojangProfiles() {
    }

    static String name(UUID id) {
        return NAMES.get(id);
    }

    static Identifier skin(UUID id) {
        return SKINS.get(id);
    }

    /** Asks for the profile unless it is known, being asked, tried lately, or the player is on this server. */
    static void request(UUID id) {
        if (id.version() != 4 || NAMES.containsKey(id) && SKINS.containsKey(id)) {
            return; // offline-mode players (name based UUIDs) have no Mojang profile
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() != null && client.getNetworkHandler().getPlayerListEntry(id) != null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = ASKED.get(id);
        if (last != null && now - last < RETRY_MS || !IN_FLIGHT.add(id)) {
            return;
        }
        ASKED.put(id, now);
        WORKER.execute(() -> {
            try {
                fetch(id);
                Thread.sleep(SPACING_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                // no network, or Mojang said no: the stored name and the default skin stay
            } finally {
                IN_FLIGHT.remove(id);
            }
        });
    }

    private static void fetch(UUID id) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(URL + id.toString().replace("-", "")))
                .timeout(Duration.ofSeconds(8)).GET().build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            return;
        }
        JsonObject profile = JsonParser.parseString(response.body()).getAsJsonObject();
        if (profile.has("name")) {
            NAMES.put(id, profile.get("name").getAsString());
        }
        String skinUrl = null;
        String rawTextures = null;
        if (profile.has("properties")) {
            for (var property : profile.getAsJsonArray("properties")) {
                JsonObject p = property.getAsJsonObject();
                if (!"textures".equals(p.get("name").getAsString())) {
                    continue;
                }
                rawTextures = p.get("value").getAsString();
                String json = new String(Base64.getDecoder().decode(p.get("value").getAsString()), StandardCharsets.UTF_8);
                JsonObject textures = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("textures");
                if (textures != null && textures.has("SKIN")) {
                    JsonObject skin = textures.getAsJsonObject("SKIN");
                    skinUrl = skin.get("url").getAsString();
                }
            }
        }
        final String url = skinUrl;
        final String raw = rawTextures;
        final String shownName = NAMES.getOrDefault(id, id.toString().substring(0, 8));
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            ClientProjectCache.touch(); // the name arrived: screens redraw with it
            if (url != null && raw != null && url.startsWith("https://textures.minecraft.net/")) {
                GameProfile gp = new GameProfile(id, shownName);
                gp.getProperties().put("textures", new Property("textures", raw));
                // the callback comes when the picture has been downloaded and registered
                dev.materialshare.McClientCompat.loadSkin(client, gp, identifier -> SKINS.put(id, identifier));
            }
        });
    }
}
