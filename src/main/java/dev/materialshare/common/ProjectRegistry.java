package dev.materialshare.common;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public final class ProjectRegistry {
    public static final int MAX_PROJECTS = 64;

    public static final int MAX_NAME_LENGTH = 64;

    // Anything printable, so schematic names like "My Castle (v2.1)" work. No control characters (they would break
    // chat/screens) and no '§' (it would colour the labels), no leading/trailing spaces.
    private static final Pattern VALID = Pattern.compile("[^\\p{Cntrl}§]{1," + MAX_NAME_LENGTH + "}");

    /** Syncmatica calls the copy a player downloads "<name>.litematic"; the player who shared it sees "<name>". */
    private static final String PART_SEPARATOR = " / ";
    private static final String SCHEMATIC_EXTENSION = ".litematic";

    private final Map<String, Project> projects = new LinkedHashMap<>();
    private final java.util.List<Trashed> trash = new java.util.ArrayList<>();

    /** Deleted projects wait here until somebody deletes them for good. */
    public static final int MAX_TRASH = 500;

    public enum TrashResult { OK, NOT_FOUND, FULL, NAME_TAKEN, TOO_MANY }

    /** {@code id} is unique among the trashed projects (it is the deletion time, moved up when two share a millisecond). */
    public record Trashed(long id, long time, java.util.UUID by, Project project) {
    }

    public TrashResult moveToTrash(String name, java.util.UUID by, long now) {
        Project project = projects.get(name);
        if (project == null) {
            return TrashResult.NOT_FOUND;
        }
        if (trash.size() >= MAX_TRASH) {
            return TrashResult.FULL; // never forget a project silently: the player has to empty the trash
        }
        long id = now;
        while (hasTrashId(id)) {
            id++;
        }
        projects.remove(name);
        trash.add(new Trashed(id, now, by, project));
        return TrashResult.OK;
    }

    private boolean hasTrashId(long id) {
        return trash.stream().anyMatch(t -> t.id() == id);
    }

    public TrashResult restore(long id) {
        for (Trashed t : trash) {
            if (t.id() == id) {
                String name = t.project().name();
                if (projects.containsKey(name)) {
                    return TrashResult.NAME_TAKEN;
                }
                if (projects.size() >= MAX_PROJECTS) {
                    return TrashResult.TOO_MANY;
                }
                trash.remove(t);
                projects.put(name, t.project());
                return TrashResult.OK;
            }
        }
        return TrashResult.NOT_FOUND;
    }

    public boolean purge(long id) {
        return trash.removeIf(t -> t.id() == id);
    }

    public java.util.List<Trashed> trashed() {
        return java.util.Collections.unmodifiableList(trash);
    }

    /** For loading saved data. */
    public void addTrashed(Trashed entry) {
        trash.add(entry);
    }

    public static boolean isValidName(String name) {
        return name != null && VALID.matcher(name).matches() && name.equals(name.strip());
    }

    /** Turns e.g. a schematic name into a valid project name ("" if nothing usable is left). */
    public static String suggestName(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.replace("§", "").replaceAll("\\p{Cntrl}", " ").strip();
        if (cleaned.toLowerCase(java.util.Locale.ROOT).endsWith(SCHEMATIC_EXTENSION)) {
            cleaned = cleaned.substring(0, cleaned.length() - SCHEMATIC_EXTENSION.length()).strip();
        }
        if (cleaned.length() > MAX_NAME_LENGTH) {
            int end = MAX_NAME_LENGTH;
            if (Character.isHighSurrogate(cleaned.charAt(end - 1))) {
                end--; // do not cut an emoji / rare character in half (it would turn into "?" on the wire)
            }
            cleaned = cleaned.substring(0, end).strip();
        }
        return isValidName(cleaned) ? cleaned : "";
    }

    /** The project of one sub-region ("part") of a schematic that has several: "<schematic> / <region>". */
    public static String partName(String schematic, String region) {
        return suggestName(schematic + PART_SEPARATOR + region);
    }

    /** Which region of the schematic a project is about, if it is one of its part projects. */
    public static Optional<String> partOf(String schematic, String project, java.util.Collection<String> regions) {
        for (String region : regions) {
            if (partName(schematic, region).equals(project)) {
                return Optional.of(region);
            }
        }
        return Optional.empty();
    }

    /** True for the project of the whole schematic and for any of its parts (they are listed together). */
    public static boolean belongsToList(String schematic, String project) {
        return project.equals(schematic) || project.startsWith(schematic + PART_SEPARATOR);
    }

    /**
     * The projects that belong to one schematic: its whole-schematic project first, then its parts, in the order given.
     * Projects of other schematics are never offered, so a list can only ever show its own projects.
     */
    public static java.util.List<String> projectsOfList(String schematic, Collection<String> projectNames) {
        java.util.List<String> result = new java.util.ArrayList<>();
        for (String name : projectNames) {
            if (name.equals(schematic)) {
                result.add(0, name);
            } else if (belongsToList(schematic, name)) {
                result.add(name);
            }
        }
        return result;
    }

    public Optional<Project> get(String name) {
        return Optional.ofNullable(projects.get(name));
    }

    /** True if the project exists already or the project limit has not been reached. */
    public boolean canCreate(String name) {
        return canCreate(name, MAX_PROJECTS);
    }

    /** The same with the server's own (lower) limit. */
    public boolean canCreate(String name, int limit) {
        return projects.containsKey(name) || projects.size() < Math.min(limit, MAX_PROJECTS);
    }

    public Project getOrCreate(String name) {
        return projects.computeIfAbsent(name, Project::new);
    }

    public enum Change { OK, NOT_FOUND, NAME_TAKEN, BAD_NAME, TOO_MANY }

    /** Renames a project (its data stays). */
    public Change rename(String from, String to) {
        Project project = projects.get(from);
        if (project == null) {
            return Change.NOT_FOUND;
        }
        if (!isValidName(to)) {
            return Change.BAD_NAME;
        }
        if (projects.containsKey(to)) {
            return Change.NAME_TAKEN;
        }
        Map<String, Project> rebuilt = new LinkedHashMap<>();
        for (Map.Entry<String, Project> e : projects.entrySet()) {
            if (e.getKey().equals(from)) {
                Project renamed = project.copyAs(to, true, true);
                rebuilt.put(to, renamed);
            } else {
                rebuilt.put(e.getKey(), e.getValue());
            }
        }
        projects.clear();
        projects.putAll(rebuilt);
        return Change.OK;
    }

    /** A new project with the same items (and, if asked, the same claimants with nothing collected yet). */
    public Change copy(String from, String to, boolean keepClaims) {
        Project project = projects.get(from);
        if (project == null) {
            return Change.NOT_FOUND;
        }
        if (!isValidName(to)) {
            return Change.BAD_NAME;
        }
        if (projects.containsKey(to)) {
            return Change.NAME_TAKEN;
        }
        if (projects.size() >= MAX_PROJECTS) {
            return Change.TOO_MANY;
        }
        projects.put(to, project.copyAs(to, false, keepClaims));
        return Change.OK;
    }

    public boolean delete(String name) {
        return projects.remove(name) != null;
    }

    public Collection<Project> all() {
        return projects.values();
    }
}
