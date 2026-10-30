package dev.materialshare.server;

import dev.materialshare.common.Entry;
import dev.materialshare.common.History;
import dev.materialshare.common.Project;
import dev.materialshare.common.ProjectRegistry;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

public class MaterialShareState extends PersistentState {
    private static final String KEY = "litematica_materialshare";

    private final ProjectRegistry registry = new ProjectRegistry();
    private final Map<UUID, String> names = new LinkedHashMap<>();

    public ProjectRegistry registry() {
        return registry;
    }

    /** Last seen name of every player that appears as a claimant (or was online). */
    public Map<UUID, String> names() {
        return names;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList projects = new NbtList();
        for (Project project : registry.all()) {
            projects.add(projectToNbt(project));
        }
        nbt.put("projects", projects);
        NbtList trash = new NbtList();
        for (ProjectRegistry.Trashed t : registry.trashed()) {
            NbtCompound c = new NbtCompound();
            c.putLong("id", t.id());
            c.putLong("time", t.time());
            if (t.by() != null) {
                c.putUuid("by", t.by());
            }
            c.put("project", projectToNbt(t.project()));
            trash.add(c);
        }
        nbt.put("trash", trash);

        NbtList nameList = new NbtList();
        names.forEach((uuid, name) -> {
            NbtCompound n = new NbtCompound();
            n.putUuid("player", uuid);
            n.putString("name", name);
            nameList.add(n);
        });
        nbt.put("names", nameList);
        return nbt;
    }

    private static NbtList sharesToNbt(Map<UUID, Integer> shares) {
        NbtList list = new NbtList();
        shares.forEach((id, n) -> {
            NbtCompound c = new NbtCompound();
            c.putUuid("player", id);
            c.putInt("n", n);
            list.add(c);
        });
        return list;
    }

    private static void sharesFromNbt(NbtList list, Map<UUID, Integer> into) {
        for (NbtElement e : list) {
            NbtCompound c = (NbtCompound) e;
            into.put(c.getUuid("player"), c.getInt("n"));
        }
    }

    private static NbtCompound projectToNbt(Project project) {
        NbtCompound p = new NbtCompound();
        p.putString("name", project.name());
        p.putString("note", project.note());
        p.putBoolean("archived", project.archived());
        NbtList history = new NbtList();
        for (History.Event e : project.history().events()) {
            NbtCompound h = new NbtCompound();
            h.putLong("time", e.time());
            if (e.actor() != null) {
                h.putUuid("actor", e.actor());
            }
            h.putInt("kind", e.kind().ordinal());
            h.putString("item", e.itemId() == null ? "" : e.itemId());
            if (e.target() != null) {
                h.putUuid("target", e.target());
            }
            h.putInt("value", e.value());
            history.add(h);
        }
        p.put("history", history);
        NbtList surplus = new NbtList();
        project.surplus().forEach((id, m) -> m.forEach((who, n) -> {
            NbtCompound c = new NbtCompound();
            c.putString("item", id);
            c.putUuid("player", who);
            c.putInt("n", n);
            surplus.add(c);
        }));
        p.put("surplus", surplus);
        NbtList areas = new NbtList();
        for (dev.materialshare.common.Area a : project.storage().areas()) {
            NbtCompound c = new NbtCompound();
            c.putString("name", a.name());
            c.putString("dim", a.dimension());
            c.putIntArray("box", new int[] {a.minX(), a.minY(), a.minZ(), a.maxX(), a.maxY(), a.maxZ()});
            areas.add(c);
        }
        p.put("areas", areas);
        NbtList stock = new NbtList();
        project.storage().chunks().forEach((key, counts) -> {
            NbtCompound c = new NbtCompound();
            c.putString("chunk", key);
            NbtList items = new NbtList();
            counts.forEach((id, n) -> {
                NbtCompound i = new NbtCompound();
                i.putString("id", id);
                i.putInt("n", n);
                items.add(i);
            });
            c.put("items", items);
            stock.add(c);
        });
        p.put("stock", stock);
        NbtList activity = new NbtList();
        project.activity().forEach((who, time) -> {
            NbtCompound c = new NbtCompound();
            c.putUuid("player", who);
            c.putLong("t", time);
            activity.add(c);
        });
        p.put("activity", activity);
        NbtList zones = new NbtList();
        for (dev.materialshare.common.Zone z : project.zones()) {
            NbtCompound c = new NbtCompound();
            c.putString("name", z.name());
            c.putInt("from", z.fromLayer());
            c.putInt("to", z.toLayer());
            NbtList items = new NbtList();
            z.required().forEach((id, n) -> {
                NbtCompound i = new NbtCompound();
                i.putString("id", id);
                i.putInt("n", n);
                items.add(i);
            });
            c.put("items", items);
            zones.add(c);
        }
        p.put("zones", zones);
        p.put("items", itemsToNbt(project.items()));
        p.put("removed", itemsToNbt(project.removed()));
        return p;
    }

    private static NbtList itemsToNbt(Map<String, Entry> map) {
        NbtList items = new NbtList();
        for (Map.Entry<String, Entry> item : map.entrySet()) {
            NbtCompound i = new NbtCompound();
            i.putString("id", item.getKey());
            i.putInt("required", item.getValue().required());
            i.putBoolean("priority", item.getValue().priority());
            i.putString("substitute", item.getValue().substitute());
            i.put("targets", sharesToNbt(item.getValue().targets()));
            i.put("delivered", sharesToNbt(item.getValue().delivered()));
            NbtList claims = new NbtList();
            for (Map.Entry<UUID, Integer> claim : item.getValue().claims().entrySet()) {
                NbtCompound c = new NbtCompound();
                c.putUuid("player", claim.getKey());
                c.putInt("collected", claim.getValue());
                claims.add(c);
            }
            i.put("claims", claims);
            items.add(i);
        }
        return items;
    }


    private static void fillProject(Project project, NbtCompound p) {
        project.setNote(p.getString("note"));
        project.setArchived(p.getBoolean("archived"));
        for (NbtElement he : p.getList("history", NbtElement.COMPOUND_TYPE)) {
            NbtCompound h = (NbtCompound) he;
            project.history().add(new History.Event(h.getLong("time"),
                    h.containsUuid("actor") ? h.getUuid("actor") : null, History.Kind.of(h.getInt("kind")),
                    h.getString("item"), h.containsUuid("target") ? h.getUuid("target") : null,
                    h.getInt("value")));
        }
        for (NbtElement oe : p.getList("surplus", NbtElement.COMPOUND_TYPE)) {
            NbtCompound oc = (NbtCompound) oe;
            project.setSurplus(oc.getUuid("player"), oc.getString("item"), oc.getInt("n"));
        }
        for (NbtElement ae : p.getList("areas", NbtElement.COMPOUND_TYPE)) {
            NbtCompound ac = (NbtCompound) ae;
            int[] b = ac.getIntArray("box");
            if (b.length == 6) {
                project.storage().add(dev.materialshare.common.Area.between(ac.getString("name"), ac.getString("dim"),
                        b[0], b[1], b[2], b[3], b[4], b[5]));
            }
        }
        for (NbtElement ce : p.getList("stock", NbtElement.COMPOUND_TYPE)) {
            NbtCompound cc = (NbtCompound) ce;
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (NbtElement ie : cc.getList("items", NbtElement.COMPOUND_TYPE)) {
                NbtCompound ic = (NbtCompound) ie;
                counts.put(ic.getString("id"), ic.getInt("n"));
            }
            project.storage().setChunk(cc.getString("chunk"), counts);
        }
        for (NbtElement ae : p.getList("activity", NbtElement.COMPOUND_TYPE)) {
            NbtCompound ac = (NbtCompound) ae;
            project.activity().put(ac.getUuid("player"), ac.getLong("t"));
        }
        for (NbtElement ze : p.getList("zones", NbtElement.COMPOUND_TYPE)) {
            NbtCompound zc = (NbtCompound) ze;
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (NbtElement ie : zc.getList("items", NbtElement.COMPOUND_TYPE)) {
                NbtCompound ic = (NbtCompound) ie;
                counts.put(ic.getString("id"), ic.getInt("n"));
            }
            project.putZone(dev.materialshare.common.Zone.of(zc.getString("name"), zc.getInt("from"), zc.getInt("to"), counts));
        }
        itemsFromNbt(p.getList("items", NbtElement.COMPOUND_TYPE), project.items());
        itemsFromNbt(p.getList("removed", NbtElement.COMPOUND_TYPE), project.removed());
    }

    private static void itemsFromNbt(NbtList list, Map<String, Entry> into) {
        for (NbtElement ie : list) {
            NbtCompound i = (NbtCompound) ie;
            Entry entry = new Entry(i.getInt("required"));
            entry.setPriority(i.getBoolean("priority"));
            entry.setSubstitute(i.getString("substitute"));
            sharesFromNbt(i.getList("targets", NbtElement.COMPOUND_TYPE), entry.targets());
            sharesFromNbt(i.getList("delivered", NbtElement.COMPOUND_TYPE), entry.delivered());
            for (NbtElement ce : i.getList("claims", NbtElement.COMPOUND_TYPE)) {
                NbtCompound c = (NbtCompound) ce;
                entry.claims().put(c.getUuid("player"), c.getInt("collected"));
            }
            into.put(i.getString("id"), entry);
        }
    }

    public static MaterialShareState fromNbt(NbtCompound nbt) {
        MaterialShareState state = new MaterialShareState();
        for (NbtElement pe : nbt.getList("projects", NbtElement.COMPOUND_TYPE)) {
            NbtCompound p = (NbtCompound) pe;
            fillProject(state.registry.getOrCreate(p.getString("name")), p);
        }
        for (NbtElement te : nbt.getList("trash", NbtElement.COMPOUND_TYPE)) {
            NbtCompound c = (NbtCompound) te;
            NbtCompound p = c.getCompound("project");
            Project project = new Project(p.getString("name"));
            fillProject(project, p);
            state.registry.addTrashed(new ProjectRegistry.Trashed(c.getLong("id"), c.getLong("time"),
                    c.containsUuid("by") ? c.getUuid("by") : null, project));
        }
        for (NbtElement ne : nbt.getList("names", NbtElement.COMPOUND_TYPE)) {
            NbtCompound n = (NbtCompound) ne;
            state.names.put(n.getUuid("player"), n.getString("name"));
        }
        return state;
    }

    /** The data file of the versions before the mod was renamed (the mod was called Material List Tracker). */
    private static final String OLD_KEY = "matlisttracker";
    private static MinecraftServer migratedFor;

    /** Copies the saved data of the old name to the new one, once per server and only if there is nothing new yet. */
    private static synchronized void migrateOldData(MinecraftServer server) {
        if (migratedFor == server) {
            return;
        }
        migratedFor = server;
        try {
            java.nio.file.Path data = server.getSavePath(net.minecraft.util.WorldSavePath.ROOT).resolve("data");
            java.nio.file.Path old = data.resolve(OLD_KEY + ".dat");
            java.nio.file.Path now = data.resolve(KEY + ".dat");
            if (java.nio.file.Files.isRegularFile(old) && !java.nio.file.Files.exists(now)) {
                java.nio.file.Files.copy(old, now);
                System.out.println("[Litematica MaterialShare] Took over the saved projects of the old name (" + OLD_KEY + ".dat)");
            }
        } catch (java.io.IOException | RuntimeException e) {
            System.err.println("[Litematica MaterialShare] Could not take over the old data: " + e);
        }
    }

    public static MaterialShareState get(MinecraftServer server) {
        migrateOldData(server);
        return dev.materialshare.McCompat.persistentState(server.getOverworld().getPersistentStateManager(),
                MaterialShareState::fromNbt, MaterialShareState::new, KEY);
    }
}
