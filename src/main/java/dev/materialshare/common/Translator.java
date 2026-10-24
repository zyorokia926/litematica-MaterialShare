package dev.materialshare.common;

/** Looks up a text of the player's language by key (the client uses Minecraft's language files). */
@FunctionalInterface
public interface Translator {
    String tr(String key, Object... args);
}
