package dev.materialshare.common;

import dev.materialshare.MaterialShare;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public final class Packets {
    public static final int MAX_STRING = 256;
    /** How many lines the "recent activity" list has at most. */
    public static final int FEED_MAX = 100;

    public static final Identifier CREATE = id("create");
    public static final Identifier DELETE = id("delete");
    public static final Identifier CLAIM = id("claim");
    public static final Identifier SET_COLLECTED = id("set_collected");
    public static final Identifier SNAPSHOT = id("snapshot");
    public static final Identifier PROJECT_CHANGED = id("project_changed");
    public static final Identifier PROJECT_DELETED = id("project_deleted");
    public static final Identifier PLAYER_NAMES = id("player_names");
    public static final Identifier ITEM_CHANGED = id("item_changed");
    public static final Identifier REQUEST_PLAYERS = id("request_players");
    public static final Identifier LOOKUP_PLAYER = id("lookup_player");
    public static final Identifier LOOKUP_RESULT = id("lookup_result");
    public static final Identifier CREATE_FAILED = id("create_failed");
    public static final Identifier HELLO = id("hello");
    public static final Identifier BULK_CLAIM = id("bulk_claim");
    public static final Identifier SET_NOTE = id("set_note");
    public static final Identifier HISTORY_REQUEST = id("history_request");
    public static final Identifier HISTORY = id("history");
    public static final Identifier BULK_COLLECTED = id("bulk_collected");
    public static final Identifier SET_ARCHIVED = id("set_archived");
    public static final Identifier SET_TARGETS = id("set_targets");
    public static final Identifier TRANSFER = id("transfer");
    public static final Identifier SET_DELIVERED = id("set_delivered");
    public static final Identifier BULK_DELIVERED = id("bulk_delivered");
    public static final Identifier REMOVE_ITEM = id("remove_item");
    /** project + item id (empty = every item of the project). */
    public static final Identifier RESET_CLAIMS = id("reset_claims");
    public static final Identifier RESTORE_ITEM = id("restore_item");
    public static final Identifier REMOVED_ITEMS = id("removed_items");
    public static final Identifier RENAME = id("rename");
    public static final Identifier CLONE = id("clone");
    public static final Identifier UPDATE_REQUIRED = id("update_required");
    public static final Identifier FEED_REQUEST = id("feed_request");
    public static final Identifier FEED = id("feed");
    public static final Identifier ACTION_FAILED = id("action_failed");
    public static final Identifier SET_ITEM_INFO = id("set_item_info");
    public static final Identifier TRASH_LIST = id("trash_list");
    public static final Identifier TRASH_RESTORE = id("trash_restore");
    public static final Identifier TRASH_PURGE = id("trash_purge");
    public static final Identifier TRASH_FAILED = id("trash_failed");
    public static final Identifier SET_SURPLUS = id("set_surplus");
    public static final Identifier TAKE_SURPLUS = id("take_surplus");
    public static final Identifier SURPLUS = id("surplus");
    /** project + one area (the sender is only allowed to add boxes). */
    public static final Identifier STORAGE_ADD = id("storage_add");
    /** project + index of the area. */
    public static final Identifier STORAGE_REMOVE = id("storage_remove");
    public static final Identifier STORAGE = id("storage");
    public static final Identifier ACTIVITY = id("activity");
    public static final Identifier ACTIVITY_REQUEST = id("activity_request");

    /** Entries per BULK_CLAIM / BULK_COLLECTED packet, so one packet stays far below the 32767-byte client limit. */
    public static final int BULK_CHUNK = 200;

    /** Why the server refused a CREATE (sent in CREATE_FAILED). */
    public static final int FAIL_BAD_NAME = 0;
    public static final int FAIL_TOO_MANY = 1;
    /** Why the server refused to delete or restore (sent in TRASH_FAILED). */
    public static final int FAIL_TRASH_FULL = 10;
    public static final int FAIL_NAME_TAKEN = 11;
    public static final int FAIL_NOT_FOUND = 12;
    /** Why the server refused a rename or a copy (sent in ACTION_FAILED). */
    public static final int FAIL_BAD_NAME_PROJECT = 20;

    private Packets() {
    }

    private static Identifier id(String path) {
        return new Identifier(MaterialShare.MOD_ID, path);
    }

    private static void writeInfo(PacketByteBuf buf, Entry entry) {
        buf.writeBoolean(entry.priority());
        buf.writeString(entry.substitute(), MAX_STRING);
        writeShares(buf, entry.targets());
        writeShares(buf, entry.delivered());
    }

    private static void readInfo(PacketByteBuf buf, Entry entry) {
        entry.setPriority(buf.readBoolean());
        entry.setSubstitute(buf.readString(MAX_STRING));
        readShares(buf, entry.targets());
        readShares(buf, entry.delivered());
    }

    private static void writeShares(PacketByteBuf buf, Map<UUID, Integer> shares) {
        buf.writeVarInt(shares.size());
        shares.forEach((id, n) -> {
            buf.writeUuid(id);
            buf.writeVarInt(n);
        });
    }

    private static void readShares(PacketByteBuf buf, Map<UUID, Integer> into) {
        int n = Math.min(buf.readVarInt(), Project.MAX_CLAIMANTS);
        for (int i = 0; i < n; i++) {
            into.put(buf.readUuid(), buf.readVarInt());
        }
    }

    public static void writeProject(PacketByteBuf buf, Project project) {
        buf.writeString(project.name(), MAX_STRING);
        buf.writeString(project.note(), MAX_STRING);
        buf.writeBoolean(project.archived());
        buf.writeVarInt(project.items().size());
        for (Map.Entry<String, Entry> item : project.items().entrySet()) {
            buf.writeString(item.getKey(), MAX_STRING);
            buf.writeVarInt(item.getValue().required());
            writeInfo(buf, item.getValue());
            buf.writeVarInt(item.getValue().claims().size());
            for (Map.Entry<UUID, Integer> claim : item.getValue().claims().entrySet()) {
                buf.writeUuid(claim.getKey());
                buf.writeVarInt(claim.getValue());
            }
        }
    }

    public static Project readProject(PacketByteBuf buf) {
        Project project = new Project(buf.readString(MAX_STRING));
        project.setNote(buf.readString(MAX_STRING));
        project.setArchived(buf.readBoolean());
        int items = buf.readVarInt();
        for (int i = 0; i < items; i++) {
            String itemId = buf.readString(MAX_STRING);
            Entry entry = new Entry(buf.readVarInt());
            readInfo(buf, entry);
            int claims = buf.readVarInt();
            for (int c = 0; c < claims; c++) {
                entry.claims().put(buf.readUuid(), buf.readVarInt());
            }
            project.items().put(itemId, entry);
        }
        return project;
    }

    /** What the trash list shows of a deleted project (not its items). */
    public record TrashEntry(long id, String name, long time, UUID by, int collected, int required) {
    }

    public static void writeTrash(PacketByteBuf buf, java.util.List<ProjectRegistry.Trashed> trash) {
        buf.writeVarInt(trash.size());
        for (ProjectRegistry.Trashed t : trash) {
            buf.writeLong(t.id());
            buf.writeString(t.project().name(), MAX_STRING);
            buf.writeLong(t.time());
            buf.writeUuid(t.by() == null ? NOBODY : t.by());
            buf.writeVarInt(t.project().totalCollected());
            buf.writeVarInt(t.project().totalRequired());
        }
    }

    public static java.util.List<TrashEntry> readTrash(PacketByteBuf buf) {
        int n = buf.readVarInt();
        java.util.List<TrashEntry> list = new java.util.ArrayList<>();
        if (n < 0 || n > ProjectRegistry.MAX_TRASH) {
            return list;
        }
        for (int i = 0; i < n; i++) {
            long id = buf.readLong();
            String name = buf.readString(MAX_STRING);
            long time = buf.readLong();
            UUID by = buf.readUuid();
            list.add(new TrashEntry(id, name, time, NOBODY.equals(by) ? null : by, buf.readVarInt(), buf.readVarInt()));
        }
        return list;
    }

    /** One item of a project: sent instead of the whole project when only a claim or a count changed. */
    public record ItemUpdate(String project, String itemId, Entry entry) {
    }

    public static void writeItem(PacketByteBuf buf, String project, String itemId, Entry entry) {
        buf.writeString(project, MAX_STRING);
        buf.writeString(itemId, MAX_STRING);
        buf.writeVarInt(entry.required());
        writeInfo(buf, entry);
        buf.writeVarInt(entry.claims().size());
        for (Map.Entry<UUID, Integer> claim : entry.claims().entrySet()) {
            buf.writeUuid(claim.getKey());
            buf.writeVarInt(claim.getValue());
        }
    }

    public static ItemUpdate readItem(PacketByteBuf buf) {
        String project = buf.readString(MAX_STRING);
        String itemId = buf.readString(MAX_STRING);
        Entry entry = new Entry(buf.readVarInt());
            readInfo(buf, entry);
        int claims = buf.readVarInt();
        for (int i = 0; i < claims; i++) {
            entry.claims().put(buf.readUuid(), buf.readVarInt());
        }
        return new ItemUpdate(project, itemId, entry);
    }

    public static void writeNames(PacketByteBuf buf, Map<UUID, String> names) {
        buf.writeVarInt(names.size());
        names.forEach((uuid, name) -> {
            buf.writeUuid(uuid);
            buf.writeString(name, MAX_STRING);
        });
    }

    public static Map<UUID, String> readNames(PacketByteBuf buf) {
        Map<UUID, String> names = new LinkedHashMap<>();
        int n = buf.readVarInt();
        for (int i = 0; i < n; i++) {
            names.put(buf.readUuid(), buf.readString(MAX_STRING));
        }
        return names;
    }

    private static final UUID NOBODY = new UUID(0L, 0L);

    public record HistoryReply(String project, java.util.List<History.Event> events) {
    }

    public static void writeHistory(PacketByteBuf buf, String project, java.util.List<History.Event> events) {
        buf.writeString(project, MAX_STRING);
        buf.writeVarInt(events.size());
        for (History.Event e : events) {
            buf.writeLong(e.time());
            buf.writeUuid(e.actor() == null ? NOBODY : e.actor());
            buf.writeVarInt(e.kind().ordinal());
            buf.writeString(e.itemId() == null ? "" : e.itemId(), MAX_STRING);
            buf.writeUuid(e.target() == null ? NOBODY : e.target());
            buf.writeVarInt(e.value());
        }
    }

    public static HistoryReply readHistory(PacketByteBuf buf) {
        String project = buf.readString(MAX_STRING);
        int n = Math.min(buf.readVarInt(), History.MAX);
        java.util.List<History.Event> events = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            long time = buf.readLong();
            UUID actor = buf.readUuid();
            History.Kind kind = History.Kind.of(buf.readVarInt());
            String itemId = buf.readString(MAX_STRING);
            UUID target = buf.readUuid();
            int value = buf.readVarInt();
            events.add(new History.Event(time, NOBODY.equals(actor) ? null : actor, kind, itemId,
                    NOBODY.equals(target) ? null : target, value));
        }
        return new HistoryReply(project, events);
    }

    public record Bulk(String project, java.util.List<Assignments.Assignment> assignments) {
    }

    public static void writeBulk(PacketByteBuf buf, String project, java.util.List<Assignments.Assignment> list) {
        buf.writeString(project, MAX_STRING);
        buf.writeVarInt(list.size());
        for (Assignments.Assignment a : list) {
            buf.writeString(a.itemId(), MAX_STRING);
            buf.writeUuid(a.player());
        }
    }

    /** A length above the item limit is refused outright, so a hostile packet cannot make the server read on and on. */
    public static Bulk readBulk(PacketByteBuf buf) {
        String project = buf.readString(MAX_STRING);
        int n = buf.readVarInt();
        java.util.List<Assignments.Assignment> list = new java.util.ArrayList<>();
        if (n >= 0 && n <= Project.MAX_ITEMS) {
            for (int i = 0; i < n; i++) {
                list.add(new Assignments.Assignment(buf.readString(MAX_STRING), buf.readUuid()));
            }
        }
        return new Bulk(project, list);
    }

    public record Import(String project, Map<String, Integer> counts) {
    }

    public static void writeImport(PacketByteBuf buf, String project, Map<String, Integer> counts) {
        buf.writeString(project, MAX_STRING);
        buf.writeVarInt(counts.size());
        counts.forEach((itemId, n) -> {
            buf.writeString(itemId, MAX_STRING);
            buf.writeVarInt(n);
        });
    }

    public static Import readImport(PacketByteBuf buf) {
        String project = buf.readString(MAX_STRING);
        int n = buf.readVarInt();
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (n >= 0 && n <= Project.MAX_ITEMS) {
            for (int i = 0; i < n; i++) {
                counts.put(buf.readString(MAX_STRING), buf.readVarInt());
            }
        }
        return new Import(project, counts);
    }

    /** One line of the "recent activity" list: an event and the project it happened in. */
    public record FeedEntry(String project, History.Event event) {
    }

    public static void writeFeed(PacketByteBuf buf, java.util.List<FeedEntry> feed) {
        buf.writeVarInt(feed.size());
        for (FeedEntry f : feed) {
            buf.writeString(f.project(), MAX_STRING);
            History.Event e = f.event();
            buf.writeLong(e.time());
            buf.writeUuid(e.actor() == null ? NOBODY : e.actor());
            buf.writeVarInt(e.kind().ordinal());
            buf.writeString(e.itemId() == null ? "" : e.itemId(), MAX_STRING);
            buf.writeUuid(e.target() == null ? NOBODY : e.target());
            buf.writeVarInt(e.value());
        }
    }

    public static java.util.List<FeedEntry> readFeed(PacketByteBuf buf) {
        int n = buf.readVarInt();
        java.util.List<FeedEntry> feed = new java.util.ArrayList<>();
        if (n < 0 || n > FEED_MAX) {
            return feed;
        }
        for (int i = 0; i < n; i++) {
            String project = buf.readString(MAX_STRING);
            long time = buf.readLong();
            UUID actor = buf.readUuid();
            History.Kind kind = History.Kind.of(buf.readVarInt());
            String itemId = buf.readString(MAX_STRING);
            UUID target = buf.readUuid();
            int value = buf.readVarInt();
            feed.add(new FeedEntry(project, new History.Event(time, NOBODY.equals(actor) ? null : actor, kind, itemId,
                    NOBODY.equals(target) ? null : target, value)));
        }
        return feed;
    }

    public record RemovedList(String project, java.util.List<String> itemIds) {
    }

    public static void writeRemoved(PacketByteBuf buf, Project project) {
        buf.writeString(project.name(), MAX_STRING);
        buf.writeVarInt(project.removed().size());
        project.removed().keySet().forEach(id -> buf.writeString(id, MAX_STRING));
    }

    public static RemovedList readRemoved(PacketByteBuf buf) {
        String name = buf.readString(MAX_STRING);
        int n = Math.min(buf.readVarInt(), Project.MAX_REMOVED);
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            ids.add(buf.readString(MAX_STRING));
        }
        return new RemovedList(name, ids);
    }

    /** One offer of spare items. */
    public record Offer(String itemId, UUID player, int count) {
    }

    public record SurplusList(String project, java.util.List<Offer> offers) {
    }

    public static void writeSurplus(PacketByteBuf buf, Project project) {
        buf.writeString(project.name(), MAX_STRING);
        buf.writeVarInt(project.offerCount());
        project.surplus().forEach((id, m) -> m.forEach((who, n) -> {
            buf.writeString(id, MAX_STRING);
            buf.writeUuid(who);
            buf.writeVarInt(n);
        }));
    }

    public static SurplusList readSurplus(PacketByteBuf buf) {
        String name = buf.readString(MAX_STRING);
        int n = Math.min(buf.readVarInt(), Project.MAX_OFFERS);
        java.util.List<Offer> offers = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            offers.add(new Offer(buf.readString(MAX_STRING), buf.readUuid(), buf.readVarInt()));
        }
        return new SurplusList(name, offers);
    }

    /** Targets set for one item at once (an even split among the claimants). */
    public record Targets(String project, String itemId, Map<UUID, Integer> targets) {
    }

    public static void writeTargets(PacketByteBuf buf, String project, String itemId, Map<UUID, Integer> targets) {
        buf.writeString(project, MAX_STRING);
        buf.writeString(itemId, MAX_STRING);
        writeShares(buf, targets);
    }

    public static Targets readTargets(PacketByteBuf buf) {
        String project = buf.readString(MAX_STRING);
        String itemId = buf.readString(MAX_STRING);
        Map<UUID, Integer> targets = new LinkedHashMap<>();
        readShares(buf, targets);
        return new Targets(project, itemId, targets);
    }

    public static void writeArea(PacketByteBuf buf, Area a) {
        buf.writeString(a.name(), MAX_STRING);
        buf.writeString(a.dimension(), MAX_STRING);
        buf.writeInt(a.minX());
        buf.writeInt(a.minY());
        buf.writeInt(a.minZ());
        buf.writeInt(a.maxX());
        buf.writeInt(a.maxY());
        buf.writeInt(a.maxZ());
    }

    public static Area readArea(PacketByteBuf buf) {
        String name = buf.readString(MAX_STRING);
        String dim = buf.readString(MAX_STRING);
        return Area.between(name, dim, buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readInt());
    }

    public record AreaAdd(String project, Area area) {
    }

    public static void writeAreaAdd(PacketByteBuf buf, String project, Area area) {
        buf.writeString(project, MAX_STRING);
        writeArea(buf, area);
    }

    public static AreaAdd readAreaAdd(PacketByteBuf buf) {
        String project = buf.readString(MAX_STRING);
        return new AreaAdd(project, readArea(buf));
    }

    /** The boxes of a project and what the chests in them hold, per item. */
    public record StorageData(String project, java.util.List<Area> areas, Map<String, Integer> stock, Map<String, Integer> inside) {
    }

    public static void writeStorage(PacketByteBuf buf, Project project) {
        buf.writeString(project.name(), MAX_STRING);
        buf.writeVarInt(project.storage().areas().size());
        project.storage().areas().forEach(a -> writeArea(buf, a));
        for (Map<String, Integer> totals : java.util.List.of(project.storage().totals(), project.storage().insideTotals())) {
            buf.writeVarInt(totals.size());
            totals.forEach((id, n) -> {
                buf.writeString(id, MAX_STRING);
                buf.writeVarInt(n);
            });
        }
    }

    public static StorageData readStorage(PacketByteBuf buf) {
        String name = buf.readString(MAX_STRING);
        java.util.List<Area> areas = new java.util.ArrayList<>();
        int n = Math.min(buf.readVarInt(), Storage.MAX_AREAS);
        for (int i = 0; i < n; i++) {
            areas.add(readArea(buf));
        }
        Map<String, Integer> stock = new LinkedHashMap<>();
        Map<String, Integer> inside = new LinkedHashMap<>();
        for (Map<String, Integer> into : java.util.List.of(stock, inside)) {
            int m = Math.min(buf.readVarInt(), Project.MAX_ITEMS * 2);
            for (int i = 0; i < m; i++) {
                into.put(buf.readString(MAX_STRING), buf.readVarInt());
            }
        }
        return new StorageData(name, areas, stock, inside);
    }

    public record ActivityData(String project, Map<UUID, Long> last) {
    }

    public static void writeActivity(PacketByteBuf buf, Project project) {
        buf.writeString(project.name(), MAX_STRING);
        buf.writeVarInt(project.activity().size());
        project.activity().forEach((id, t) -> {
            buf.writeUuid(id);
            buf.writeLong(t);
        });
    }

    public static ActivityData readActivity(PacketByteBuf buf) {
        String name = buf.readString(MAX_STRING);
        Map<UUID, Long> last = new LinkedHashMap<>();
        int n = Math.min(buf.readVarInt(), 500);
        for (int i = 0; i < n; i++) {
            last.put(buf.readUuid(), buf.readLong());
        }
        return new ActivityData(name, last);
    }
}
