package dev.materialshare.server;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The server's settings, read from {@code config/litematica_materialshare_server.json} (written with the defaults the
 * first time). A missing or broken file means the defaults: everybody may do everything.
 */
public final class ServerConfig {
    private final int maxProjects;

    ServerConfig(int maxProjects) {
        this.maxProjects = maxProjects;
    }

    public static ServerConfig defaults() {
        return new ServerConfig(dev.materialshare.common.ProjectRegistry.MAX_PROJECTS);
    }

    public int maxProjects() {
        return maxProjects;
    }

    /** Reads the settings from JSON text; anything missing or out of range falls back to the default. */
    public static ServerConfig parse(String json) {
        ServerConfig d = defaults();
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            return new ServerConfig(clamp(intOf(o, "maxProjects", d.maxProjects), 1, dev.materialshare.common.ProjectRegistry.MAX_PROJECTS));
        } catch (RuntimeException broken) {
            return d;
        }
    }

    public String toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("_help", "maxProjects: how many schematics can be shared on this server at once (1-"
                + dev.materialshare.common.ProjectRegistry.MAX_PROJECTS + ").");
        o.addProperty("maxProjects", maxProjects);
        return new GsonBuilder().setPrettyPrinting().create().toJson(o);
    }

    private static int intOf(JsonObject o, String key, int fallback) {
        try {
            return o.has(key) ? o.get(key).getAsInt() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static ServerConfig current = defaults();

    public static ServerConfig get() {
        return current;
    }

    /** Loads the file (creating it with the defaults when it is not there). */
    public static void load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("litematica_materialshare_server.json");
        try {
            if (Files.isRegularFile(file)) {
                current = parse(Files.readString(file, StandardCharsets.UTF_8));
            } else {
                current = defaults();
                Files.createDirectories(file.getParent());
                Files.writeString(file, current.toJson(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            current = defaults();
        }
    }
}
