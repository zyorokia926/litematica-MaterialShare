package dev.materialshare.server;

import dev.materialshare.common.Area;
import dev.materialshare.common.History;
import dev.materialshare.common.Project;
import dev.materialshare.common.Storage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Looks at the chests inside the projects' storage boxes: counts what they hold (loaded chunks only; the last count of
 * an unloaded chunk is kept) and notes who put items in or took items out, by comparing a chest's contents from the
 * moment a player opens it to the moment they close it.
 */
public final class StorageTracker {
    private static final int SCAN_INTERVAL_TICKS = 100;
    /** While a chest is open, what changed in it is credited this often, so the screens follow almost at once. */
    private static final int LIVE_INTERVAL_TICKS = 4;
    /** A right-click counts as "opened this chest" if the container screen shows up within this many ticks. */
    private static final int OPEN_WINDOW_TICKS = 40;

    private record Use(String dim, BlockPos pos, long tick) {
    }

    private static final class Session {
        final ScreenHandler handler;
        final String dim;
        final BlockPos pos;
        Map<String, Integer> last;
        /** What changed for each project while the chest was open (for the one history line at the end). */
        final Map<String, java.util.Set<String>> changed = new LinkedHashMap<>();

        Session(ScreenHandler handler, String dim, BlockPos pos, Map<String, Integer> last) {
            this.handler = handler;
            this.dim = dim;
            this.pos = pos;
            this.last = last;
        }
    }

    private static final Map<UUID, Use> LAST_USE = new HashMap<>();
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static int ticks;

    private StorageTracker() {
    }

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!world.isClient && player instanceof ServerPlayerEntity sp) {
                LAST_USE.put(sp.getUuid(), new Use(dimId(world.getRegistryKey().getValue()), hit.getBlockPos(),
                        world.getServer().getTicks()));
            }
            return ActionResult.PASS;
        });
        ServerTickEvents.END_SERVER_TICK.register(StorageTracker::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            Session s = SESSIONS.remove(player.getUuid());
            if (s != null) {
                finish(server, player, s);
            }
            LAST_USE.remove(player.getUuid());
        });
    }

    private static String dimId(Identifier id) {
        return id.toString();
    }

    /**
     * The blocks that keep items: chests (also trapped), barrels, shulker boxes, hoppers, droppers, dispensers, brewing
     * stands, lecterns (the book) and jukeboxes (the disc). Ender chests keep the items of a player, not of a place, and
     * enchanting tables and cauldrons keep no items, so they are not counted.
     */
    static boolean isStorage(BlockEntity be) {
        return be instanceof LootableContainerBlockEntity || be instanceof net.minecraft.block.entity.BrewingStandBlockEntity
                || be instanceof net.minecraft.block.entity.LecternBlockEntity
                || be instanceof net.minecraft.block.entity.JukeboxBlockEntity;
    }

    /** Adds what a storage block holds (stacks, and what lies inside shulker boxes and bundles). */
    private static void countBlock(BlockEntity be, Map<String, Integer> direct, Map<String, Integer> inner) {
        if (be instanceof Inventory inv) {
            for (int i = 0; i < inv.size(); i++) {
                count(inv.getStack(i), direct, inner, 0);
            }
        } else if (be instanceof net.minecraft.block.entity.LecternBlockEntity lectern) {
            count(lectern.getBook(), direct, inner, 0);
        }
    }

    private static void tick(MinecraftServer server) {
        ticks++;
        MaterialShareState state = null;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            Session s = SESSIONS.get(player.getUuid());
            if (s != null && s.handler != player.currentScreenHandler) {
                SESSIONS.remove(player.getUuid());
                finish(server, player, s);
                s = null;
            }
            if (s != null && ticks % LIVE_INTERVAL_TICKS == 0) {
                apply(server, player, s);
            }
            if (s == null && player.currentScreenHandler != player.playerScreenHandler) {
                Use use = LAST_USE.get(player.getUuid());
                if (use != null && server.getTicks() - use.tick() <= OPEN_WINDOW_TICKS) {
                    if (state == null) {
                        state = MaterialShareState.get(server);
                    }
                    open(server, state, player, use);
                }
            }
        }
        if (ticks % SCAN_INTERVAL_TICKS == 0 && !server.getPlayerManager().getPlayerList().isEmpty()) {
            scanAll(server, MaterialShareState.get(server));
        }
    }

    private static List<Project> projectsAt(MaterialShareState state, String dim, BlockPos pos) {
        List<Project> found = new ArrayList<>();
        for (Project p : state.registry().all()) {
            if (p.storage().contains(dim, pos.getX(), pos.getY(), pos.getZ())) {
                found.add(p);
            }
        }
        return found;
    }

    private static void open(MinecraftServer server, MaterialShareState state, ServerPlayerEntity player, Use use) {
        LAST_USE.remove(player.getUuid());
        ServerWorld world = dev.materialshare.McCompat.world(server, use.dim());
        if (world == null || !isStorage(world.getBlockEntity(use.pos())) || projectsAt(state, use.dim(), use.pos()).isEmpty()) {
            return;
        }
        SESSIONS.put(player.getUuid(), new Session(player.currentScreenHandler, use.dim(), use.pos(),
                contents(player.currentScreenHandler, player)));
    }

    /** What the chest part of an open screen holds, per item (the player's own slots are left out; box contents counted). */
    static Map<String, Integer> contents(ScreenHandler handler, ServerPlayerEntity player) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Slot slot : handler.slots) {
            Inventory inv = slot.inventory;
            if (inv == player.getInventory() || !(inv instanceof BlockEntity || inv.getClass().getSimpleName().equals("DoubleInventory"))) {
                continue;
            }
            count(slot.getStack(), counts, null, 0);
        }
        return counts;
    }

    /**
     * Adds a stack to {@code direct}; what lies inside a shulker box or bundle goes to {@code inner}, or to
     * {@code direct} too when {@code inner} is null.
     */
    static void count(ItemStack stack, Map<String, Integer> direct, Map<String, Integer> inner, int depth) {
        if (stack.isEmpty()) {
            return;
        }
        direct.merge(dev.materialshare.McCompat.itemId(stack.getItem()).toString(), stack.getCount(), Integer::sum);
        if (depth >= 3) {
            return;
        }
        net.minecraft.nbt.NbtCompound tag = stack.getItem() instanceof net.minecraft.item.BundleItem ? stack.getNbt()
                : net.minecraft.block.Block.getBlockFromItem(stack.getItem()) instanceof net.minecraft.block.ShulkerBoxBlock
                        ? stack.getSubNbt("BlockEntityTag") : null;
        if (tag == null) {
            return;
        }
        Map<String, Integer> target = inner != null ? inner : direct;
        for (net.minecraft.nbt.NbtElement e : tag.getList("Items", net.minecraft.nbt.NbtElement.COMPOUND_TYPE)) {
            count(ItemStack.fromNbt((net.minecraft.nbt.NbtCompound) e), target, inner, depth + 1);
        }
    }

    /** Credits what changed since the last look to the player, in every project whose box holds this chest. */
    private static void apply(MinecraftServer server, ServerPlayerEntity player, Session s) {
        Map<String, Integer> after = contents(s.handler, player);
        if (after.equals(s.last)) {
            return;
        }
        Map<String, Integer> delta = new LinkedHashMap<>();
        after.forEach((id, n) -> delta.merge(id, n - s.last.getOrDefault(id, 0), Integer::sum));
        s.last.forEach((id, n) -> delta.putIfAbsent(id, -n));
        delta.values().removeIf(d -> d == 0);
        s.last = after;
        MaterialShareState state = MaterialShareState.get(server);
        List<Project> projects = projectsAt(state, s.dim, s.pos);
        for (Project project : projects) {
            List<String> changed = new ArrayList<>();
            delta.forEach((id, d) -> {
                if (project.deposit(player.getUuid(), id, d)) {
                    changed.add(id);
                }
            });
            if (!changed.isEmpty()) {
                s.changed.computeIfAbsent(project.name(), k -> new java.util.LinkedHashSet<>()).addAll(changed);
                project.touch(player.getUuid(), System.currentTimeMillis());
                ServerNet.broadcastItems(server, project, changed);
                state.markDirty();
            }
        }
        ServerWorld world = dev.materialshare.McCompat.world(server, s.dim);
        if (world != null) {
            scanChunk(server, state, world, s.dim, s.pos.getX() >> 4, s.pos.getZ() >> 4, projects);
        }
    }

    private static void finish(MinecraftServer server, ServerPlayerEntity player, Session s) {
        apply(server, player, s);
        MaterialShareState state = MaterialShareState.get(server);
        s.changed.forEach((name, ids) -> state.registry().get(name).ifPresent(
                project -> ServerNet.recordStorage(server, project, player, new ArrayList<>(ids))));
    }

    private static void scanAll(MinecraftServer server, MaterialShareState state) {
        for (Project project : state.registry().all()) {
            if (!project.storage().isEmpty()) {
                scanProject(server, state, project);
            }
        }
    }

    /** Counts the chests of every loaded chunk the project's boxes reach into. */
    public static void scanProject(MinecraftServer server, MaterialShareState state, Project project) {
        boolean changed = false;
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Area area : project.storage().areas()) {
            ServerWorld world = dev.materialshare.McCompat.world(server, area.dimension());
            if (world == null) {
                continue;
            }
            for (int cx = area.minX() >> 4; cx <= area.maxX() >> 4; cx++) {
                for (int cz = area.minZ() >> 4; cz <= area.maxZ() >> 4; cz++) {
                    if (seen.add(Storage.chunkKey(area.dimension(), cx, cz))) {
                        changed |= countChunk(world, area.dimension(), cx, cz, project);
                    }
                }
            }
        }
        if (changed) {
            state.markDirty();
            ServerNet.broadcastStorage(server, project);
        }
    }

    private static void scanChunk(MinecraftServer server, MaterialShareState state, ServerWorld world, String dim, int cx,
            int cz, List<Project> projects) {
        for (Project project : projects) {
            if (countChunk(world, dim, cx, cz, project)) {
                state.markDirty();
                ServerNet.broadcastStorage(server, project);
            }
        }
    }

    private static boolean countChunk(ServerWorld world, String dim, int cx, int cz, Project project) {
        WorldChunk chunk = world.getChunkManager().getWorldChunk(cx, cz);
        if (chunk == null) {
            return false; // not loaded: keep what was counted before
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, BlockEntity> e : chunk.getBlockEntities().entrySet()) {
            BlockPos pos = e.getKey();
            if (!isStorage(e.getValue()) || !project.storage().contains(dim, pos.getX(), pos.getY(), pos.getZ())) {
                continue;
            }
            Map<String, Integer> direct = new LinkedHashMap<>();
            Map<String, Integer> inner = new LinkedHashMap<>();
            countBlock(e.getValue(), direct, inner);
            direct.forEach((id, n) -> addCapped(counts, id, n));
            inner.forEach((id, n) -> addCapped(counts, Storage.INSIDE + id, n));
        }
        return project.storage().setChunk(Storage.chunkKey(dim, cx, cz), counts);
    }

    private static void addCapped(Map<String, Integer> counts, String id, int n) {
        if (counts.size() < Storage.MAX_KINDS_PER_CHUNK || counts.containsKey(id)) {
            counts.merge(id, n, Integer::sum);
        }
    }
}
