package dev.materialshare.common;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The client-side mods this add-on needs. Litematica and malilib are client-only mods, so they cannot be declared in
 * fabric.mod.json "depends" (a dedicated server without them would then refuse to start); the client checks them
 * itself, as early as possible. Syncmatica (which shares the schematics this add-on tracks) is required the same way,
 * but any version is accepted: its version strings start with a Minecraft version, so a range cannot be compared.
 */
public final class ModRequirements {
    /** The range that was actually tested, as [min, maxExclusive); both null means "any version". */
    public record Requirement(String modId, String displayName, String min, String maxExclusive) {
    }

    public static final List<Requirement> CLIENT = List.of(
            new Requirement("litematica", "Litematica", dev.materialshare.McCompat.LITEMATICA_RANGE[0], dev.materialshare.McCompat.LITEMATICA_RANGE[1]),
            new Requirement("malilib", "malilib", dev.materialshare.McCompat.MALILIB_RANGE[0], dev.materialshare.McCompat.MALILIB_RANGE[1]),
            new Requirement("syncmatica", "Syncmatica", null, null));

    private ModRequirements() {
    }

    /** One readable message per missing or out-of-range mod; empty if everything is fine. */
    public static List<String> problems(Function<String, String> installedVersion) {
        List<String> problems = new ArrayList<>();
        for (Requirement r : CLIENT) {
            boolean anyVersion = r.min == null || r.maxExclusive == null;
            String range = anyVersion ? "どの版でも / any version"
                    : r.min + " 以上 " + r.maxExclusive + " 未満 / " + r.min + " or newer, older than " + r.maxExclusive;
            String version = installedVersion.apply(r.modId);
            if (version == null) {
                problems.add(r.displayName + " が見つかりません / " + r.displayName + " is missing"
                        + " (必要 / required: " + range + ", Minecraft " + dev.materialshare.McCompat.MINECRAFT + ")");
            } else if (!anyVersion && hasNumbers(version)
                    && (compare(version, r.min) < 0 || compare(version, r.maxExclusive) >= 0)) {
                problems.add(r.displayName + " " + version + " には対応していません / " + r.displayName + " " + version
                        + " is not supported (必要 / required: " + range + ", Minecraft " + dev.materialshare.McCompat.MINECRAFT + ")");
            }
        }
        return problems;
    }

    /** Compares dotted version numbers numerically; text around them ("v", "-beta", "+build.3") is ignored. */
    public static int compare(String a, String b) {
        int[] x = parse(a);
        int[] y = parse(b);
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int xi = i < x.length ? x[i] : 0;
            int yi = i < y.length ? y[i] : 0;
            if (xi != yi) {
                return Integer.compare(xi, yi);
            }
        }
        return 0;
    }

    private static final java.util.regex.Pattern NUMBERS = java.util.regex.Pattern.compile("\\d+(?:\\.\\d+)*");

    /** The first run of dotted numbers in the text; a number too big for an int counts as the biggest int. */
    private static int[] parse(String version) {
        java.util.regex.Matcher m = NUMBERS.matcher(version);
        if (!m.find()) {
            return new int[0];
        }
        String[] parts = m.group().split("\\.");
        int[] numbers = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            numbers[i] = parts[i].length() > 9 ? Integer.MAX_VALUE : Integer.parseInt(parts[i]);
        }
        return numbers;
    }

    private static boolean hasNumbers(String version) {
        return NUMBERS.matcher(version).find();
    }
}
