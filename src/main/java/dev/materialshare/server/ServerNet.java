package dev.materialshare.server;

import dev.materialshare.common.Assignments;
import dev.materialshare.common.History;
import dev.materialshare.common.Packets;
import dev.materialshare.common.PlayerNames;
import dev.materialshare.common.ProtocolCheck;
import dev.materialshare.common.Project;
import dev.materialshare.common.ProjectRegistry;
import io.netty.buffer.Unpooled;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class ServerNet {
    private ServerNet() {
    }

    /** Tells the player (above the hotbar) why nothing happened. */
    private static void tell(ServerPlayerEntity player, String key) {
        player.sendMessage(dev.materialshare.McCompat.translatable(key), true);
    }

    public static void register() {
        dev.materialshare.McNet.init();
        ServerConfig.load();
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTING.register(server -> ServerConfig.load());
        registerLoginCheck();
        registerPlayerSources();
        StorageTracker.register();
        EnderTracker.register();
        registerStorage();

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            PENDING_SYNC.remove(handler.getPlayer());
            LAST_LOOKUP.remove(handler.getPlayer().getUuid());
            LAST_PLAYERS_REQUEST.remove(handler.getPlayer().getUuid());
            LAST_FEED.remove(handler.getPlayer().getUuid());
            LAST_FIND.remove(handler.getPlayer().getUuid());
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            MaterialShareState state = MaterialShareState.get(server);
            ServerPlayerEntity player = handler.getPlayer();
            PlayerNames.forcePut(state.names(), player.getUuid(), player.getName().getString());
            state.markDirty();
            sendSnapshot(player, state);
            sendTrash(player, state);
            // the joining player may not be in the player list yet, so send to them directly as well
            sendNames(player, state);
            broadcastNames(server, state);
            if (!dev.materialshare.McNet.canSend(player, Packets.SNAPSHOT)) {
                PENDING_SYNC.add(player);
            }
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!PENDING_SYNC.isEmpty() && server.getTicks() % 5 == 0) {
                syncPending(server);
            }
        });

        dev.materialshare.McNet.receive(Packets.CREATE, (server, player, handler, buf, responseSender) -> {
            String name = buf.readString(Packets.MAX_STRING);
            int n = Math.min(buf.readVarInt(), Project.MAX_ITEMS);
            Map<String, Integer> required = new HashMap<>();
            for (int i = 0; i < n; i++) {
                required.put(buf.readString(Packets.MAX_STRING), buf.readVarInt());
            }
            server.execute(() -> {
                if (!ProjectRegistry.isValidName(name)) {
                    sendCreateFailed(player, name, Packets.FAIL_BAD_NAME);
                    return;
                }
                MaterialShareState state = MaterialShareState.get(server);
                if (!state.registry().canCreate(name, ServerConfig.get().maxProjects())) {
                    sendCreateFailed(player, name, Packets.FAIL_TOO_MANY);
                    return;
                }
                boolean isNew = state.registry().get(name).isEmpty();
                Project project = state.registry().getOrCreate(name);
                project.merge(required);
                if (isNew) {
                    record(project, player.getUuid(), History.Kind.CREATED, "", null, project.items().size());
                }
                state.markDirty();
                broadcastProject(server, project);
            });
        });

        dev.materialshare.McNet.receive(Packets.DELETE, (server, player, handler, buf, responseSender) -> {
            String name = buf.readString(Packets.MAX_STRING);
            server.execute(() -> {
                MaterialShareState state = MaterialShareState.get(server);
                ProjectRegistry.TrashResult result = state.registry().moveToTrash(name, player.getUuid(),
                        System.currentTimeMillis());
                if (result == ProjectRegistry.TrashResult.OK) {
                    state.markDirty();
                    broadcast(server, Packets.PROJECT_DELETED, out -> out.writeString(name, Packets.MAX_STRING));
                    broadcastTrash(server, state);
                } else {
                    sendTrashFailed(player, name, failReason(result));
                }
            });
        });

        dev.materialshare.McNet.receive(Packets.TRASH_RESTORE, (server, player, handler, buf, responseSender) -> {
            long id = buf.readLong();
            server.execute(() -> {
                MaterialShareState state = MaterialShareState.get(server);
                String name = state.registry().trashed().stream().filter(t -> t.id() == id)
                        .map(t -> t.project().name()).findFirst().orElse("");
                ProjectRegistry.TrashResult result = state.registry().restore(id);
                if (result == ProjectRegistry.TrashResult.OK) {
                    state.registry().get(name).ifPresent(project -> {
                        record(project, player.getUuid(), History.Kind.RESTORED, "", null, 0);
                        broadcastProject(server, project);
                    });
                    state.markDirty();
                    broadcastTrash(server, state);
                } else {
                    sendTrashFailed(player, name, failReason(result));
                }
            });
        });

        dev.materialshare.McNet.receive(Packets.TRASH_PURGE, (server, player, handler, buf, responseSender) -> {
            long id = buf.readLong();
            server.execute(() -> {
                MaterialShareState state = MaterialShareState.get(server);
                if (state.registry().purge(id)) {
                    state.markDirty();
                    broadcastTrash(server, state);
                }
            });
        });

        dev.materialshare.McNet.receive(Packets.SET_ITEM_INFO, (server, player, handler, buf, responseSender) -> {
            String name = buf.readString(Packets.MAX_STRING);
            String itemId = buf.readString(Packets.MAX_STRING);
            boolean priority = buf.readBoolean();
            String substitute = buf.readString(Packets.MAX_STRING);
            server.execute(() -> {
                MaterialShareState state = MaterialShareState.get(server);
                state.registry().get(name).ifPresent(project -> {
                    dev.materialshare.common.Entry entry = project.items().get(itemId);
                    String cleanSubstitute = dev.materialshare.common.Entry.cleanText(substitute);
                    if (entry != null && (entry.priority() != priority
                            || !entry.substitute().equals(cleanSubstitute))) {
                        entry.setPriority(priority);
                        entry.setSubstitute(cleanSubstitute);
                        record(project, player.getUuid(), History.Kind.ITEM_INFO, itemId, null, priority ? 1 : 0);
                        state.markDirty();
                        broadcast(server, Packets.ITEM_CHANGED,
                                out -> Packets.writeItem(out, project.name(), itemId, entry));
                    }
                });
            });
        });

        registerShares();

        dev.materialshare.McNet.receive(Packets.SET_ARCHIVED, (server, player, handler, buf, responseSender) -> {
            String name = buf.readString(Packets.MAX_STRING);
            boolean archived = buf.readBoolean();
            server.execute(() -> {
                MaterialShareState state = MaterialShareState.get(server);
                state.registry().get(name).ifPresent(project -> {
                    if (project.archived() != archived) {
                        project.setArchived(archived);
                        record(project, player.getUuid(), History.Kind.ARCHIVE, "", null, archived ? 1 : 0);
                        state.markDirty();
                        broadcastProject(server, project);
                    }
                });
            });
        });

        dev.materialshare.McNet.receive(Packets.CLAIM, (server, player, handler, buf, responseSender) -> {
            String name = buf.readString(Packets.MAX_STRING);
            String itemId = buf.readString(Packets.MAX_STRING);
            UUID target = buf.readUuid();
            boolean on = buf.readBoolean();
            server.execute(() -> edit(server, player, name, target, itemId,
                    project -> project.setClaim(target, itemId, on),
                    on ? History.Kind.CLAIM : History.Kind.UNCLAIM, 0));
        });

        dev.materialshare.McNet.receive(Packets.SET_COLLECTED, (server, player, handler, buf, responseSender) -> {
            String name = buf.readString(Packets.MAX_STRING);
            String itemId = buf.readString(Packets.MAX_STRING);
            UUID target = buf.readUuid();
            int n = buf.readVarInt();
            server.execute(() -> edit(server, player, name, target, itemId,
                    project -> project.setCollected(target, itemId, n), History.Kind.COLLECTED, n));
        });

        dev.materialshare.McNet.receive(Packets.BULK_CLAIM, (server, player, handler, buf, sender) -> {
            Packets.Bulk bulk = Packets.readBulk(buf);
            server.execute(() -> bulkClaim(server, player, bulk));
        });

        dev.materialshare.McNet.receive(Packets.BULK_COLLECTED, (server, player, handler, buf, sender) -> {
            Packets.Import imported = Packets.readImport(buf);
            server.execute(() -> bulkCollected(server, player, imported));
        });

        dev.materialshare.McNet.receive(Packets.SET_NOTE, (server, player, handler, buf, sender) -> {
            String name = buf.readString(Packets.MAX_STRING);
            String note = buf.readString(Packets.MAX_STRING);
            server.execute(() -> {
                MaterialShareState state = MaterialShareState.get(server);
                state.registry().get(name).ifPresent(project -> {
                    String clean = Project.cleanNote(note);
                    if (!clean.equals(project.note())) {
                        project.setNote(clean);
                        record(project, player.getUuid(), History.Kind.NOTE, "", null, clean.length());
                        state.markDirty();
                        broadcastProject(server, project);
                    }
                });
            });
        });

        dev.materialshare.McNet.receive(Packets.SET_SURPLUS, (server, player, handler, buf, sender) -> {
            String name = buf.readString(Packets.MAX_STRING);
            String itemId = buf.readString(Packets.MAX_STRING);
            int n = buf.readVarInt();
            server.execute(() -> {
                MaterialShareState state = MaterialShareState.get(server);
                state.registry().get(name).ifPresent(project -> {
                    if (project.setSurplus(player.getUuid(), itemId, n)) {
                        int shown = project.surplus().getOrDefault(itemId, Map.of()).getOrDefault(player.getUuid(), 0);
                        record(project, player.getUuid(), History.Kind.SURPLUS, itemId, null, shown);
                        state.markDirty();
                        broadcastSurplus(server, project);
                    }
                });
            });
        });

        dev.materialshare.McNet.receive(Packets.TAKE_SURPLUS, (server, player, handler, buf, sender) -> {
            String name = buf.readString(Packets.MAX_STRING);
            String itemId = buf.readString(Packets.MAX_STRING);
            UUID from = buf.readUuid();
            int n = buf.readVarInt();
            server.execute(() -> {
                MaterialShareState state = MaterialShareState.get(server);
                state.registry().get(name).ifPresent(project -> {
                    int taken = project.takeSurplus(from, itemId, n);
                    if (taken > 0) {
                        record(project, player.getUuid(), History.Kind.SURPLUS_TAKEN, itemId, from, taken);
                        state.markDirty();
                        broadcastSurplus(server, project);
                    }
                });
            });
        });

        dev.materialshare.McNet.receive(Packets.HISTORY_REQUEST, (server, player, handler, buf, sender) -> {
            String name = buf.readString(Packets.MAX_STRING);
            server.execute(() -> MaterialShareState.get(server).registry().get(name).ifPresent(project -> {
                PacketByteBuf out = new PacketByteBuf(Unpooled.buffer());
                Packets.writeHistory(out, project.name(), project.history().latestFirst());
                send(player, Packets.HISTORY, out);
            }));
        });
    }

    private static final long LOOKUP_COOLDOWN_MS = 3000;
    private static final long PLAYERS_COOLDOWN_MS = 5000;
    private static final Map<UUID, Long> LAST_LOOKUP = new HashMap<>();
    private static final Map<UUID, Long> LAST_PLAYERS_REQUEST = new HashMap<>();

    private static void registerPlayerSources() {
        // The player picker asks for everyone the server has seen before (its usercache).
        dev.materialshare.McNet.receive(Packets.REQUEST_PLAYERS,
                (server, player, handler, buf, responseSender) -> server.execute(() -> {
                    long now = System.currentTimeMillis();
                    Long last = LAST_PLAYERS_REQUEST.get(player.getUuid());
                    if (last != null && now - last < PLAYERS_COOLDOWN_MS) {
                        return; // reading usercache.json on every request would let one client lag the server
                    }
                    LAST_PLAYERS_REQUEST.put(player.getUuid(), now);
                    MaterialShareState state = MaterialShareState.get(server);
                    boolean changed = false;
                    for (Map.Entry<UUID, String> known : readUserCache(server).entrySet()) {
                        changed |= PlayerNames.put(state.names(), known.getKey(), known.getValue());
                    }
                    if (changed) {
                        state.markDirty();
                        broadcastNames(server, state);
                    }
                }));

        // A player who is not known yet can be found by exact name (asks the server's profile lookup).
        dev.materialshare.McNet.receive(Packets.LOOKUP_PLAYER,
                (server, player, handler, buf, responseSender) -> {
                    String name = buf.readString(Packets.MAX_STRING);
                    server.execute(() -> lookup(server, player, name));
                });
    }

    /** Everyone the server has seen before, from its usercache.json (an array of {name, uuid, expiresOn}). */
    private static Map<UUID, String> readUserCache(MinecraftServer server) {
        Map<UUID, String> result = new HashMap<>();
        java.io.File file = server.getFile("usercache.json");
        if (!file.isFile()) {
            return result;
        }
        try (java.io.Reader reader = java.nio.file.Files.newBufferedReader(file.toPath())) {
            for (com.google.gson.JsonElement element : com.google.gson.JsonParser.parseReader(reader).getAsJsonArray()) {
                com.google.gson.JsonObject entry = element.getAsJsonObject();
                try {
                    result.put(UUID.fromString(entry.get("uuid").getAsString()), entry.get("name").getAsString());
                } catch (RuntimeException skipEntry) {
                    // ignore a malformed entry, keep the others
                }
            }
        } catch (java.io.IOException | RuntimeException e) {
            // unreadable cache: the picker simply offers fewer names
        }
        return result;
    }

    private static void lookup(MinecraftServer server, ServerPlayerEntity asker, String name) {
        if (!PlayerNames.isValidLookupName(name)) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = LAST_LOOKUP.get(asker.getUuid());
        if (last != null && now - last < LOOKUP_COOLDOWN_MS) {
            return; // protects the profile service from being spammed
        }
        LAST_LOOKUP.put(asker.getUuid(), now);
        MaterialShareState state = MaterialShareState.get(server);
        for (String known : state.names().values()) {
            if (known.equalsIgnoreCase(name)) {
                sendLookupResult(asker, name, true);
                return;
            }
        }
        dev.materialshare.McCompat.findProfile(server, name, result -> server.execute(() -> {
            boolean found = false;
            if (result.isPresent() && result.get().getId() != null) {
                found = PlayerNames.put(state.names(), result.get().getId(), result.get().getName());
                if (found) {
                    state.markDirty();
                    broadcastNames(server, state);
                }
            }
            sendLookupResult(asker, name, found);
        }));
    }

    private static void sendCreateFailed(ServerPlayerEntity player, String name, int reason) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeString(name.length() > Packets.MAX_STRING ? name.substring(0, Packets.MAX_STRING) : name,
                Packets.MAX_STRING);
        buf.writeVarInt(reason);
        send(player, Packets.CREATE_FAILED, buf);
    }

    private static void sendLookupResult(ServerPlayerEntity player, String name, boolean found) {
        if (player.networkHandler == null) {
            return;
        }
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeString(name, Packets.MAX_STRING);
        buf.writeBoolean(found);
        send(player, Packets.LOOKUP_RESULT, buf);
    }

    private static void registerLoginCheck() {
        ServerLoginConnectionEvents.QUERY_START.register((handler, server, sender, synchronizer) ->
                sender.sendPacket(Packets.HELLO, helloPacket()));

        ServerLoginNetworking.registerGlobalReceiver(Packets.HELLO,
                (server, handler, understood, buf, synchronizer, responseSender) -> {
                    if (!understood) {
                        // a player without this mod may join: they just do not see anything of it
                        return;
                    }
                    // an old client answers with an empty packet, a current one with its protocol version
                    int client = buf.readableBytes() > 0 ? buf.readVarInt() : ProtocolCheck.UNKNOWN;
                    ProtocolCheck.problem(ProtocolCheck.PROTOCOL_VERSION, client)
                            .ifPresent(message -> handler.disconnect(dev.materialshare.McCompat.literal(message)));
                });
    }

    /**
     * Players whose client had not yet reported its channels when they joined (Fabric API 0.91.1 on Minecraft 1.20.3
     * does that late): what the join sent was dropped, so it is sent again as soon as the channels are known.
     */
    private static final java.util.Set<ServerPlayerEntity> PENDING_SYNC = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static void syncPending(MinecraftServer server) {
        PENDING_SYNC.removeIf(player -> {
            if (player.isDisconnected()) {
                return true;
            }
            if (!dev.materialshare.McNet.canSend(player, Packets.SNAPSHOT)) {
                return false;
            }
            MaterialShareState state = MaterialShareState.get(server);
            sendSnapshot(player, state);
            sendTrash(player, state);
            sendNames(player, state);
            return true;
        });
    }

    /** Sends only to players whose client has this mod (a client without it is not sent packets it would not know). */
    private static void send(ServerPlayerEntity player, net.minecraft.util.Identifier channel, PacketByteBuf buf) {
        if (dev.materialshare.McNet.canSend(player, channel)) {
            dev.materialshare.McNet.send(player, channel, buf);
        }
    }

    private static PacketByteBuf helloPacket() {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(ProtocolCheck.PROTOCOL_VERSION);
        return buf;
    }

    private static void record(Project project, UUID actor, History.Kind kind, String itemId, UUID target, int value) {
        long now = System.currentTimeMillis();
        project.history().add(new History.Event(now, actor, kind, itemId, target, value));
        project.touch(actor, now);
    }

    /** Several claims at once ("give me everything nobody has"): the history gets one line, not one per item. */
    private static void bulkClaim(MinecraftServer server, ServerPlayerEntity actor, Packets.Bulk bulk) {
        MaterialShareState state = MaterialShareState.get(server);
        state.registry().get(bulk.project()).ifPresent(project -> {
            java.util.List<String> changed = new java.util.ArrayList<>();
            for (Assignments.Assignment a : bulk.assignments()) {
                dev.materialshare.common.Entry item = project.items().get(a.itemId());
                // bulk handing-out only covers items nobody has taken: a screen opened a while ago may be out of date
                if (item != null && item.claims().isEmpty() && state.names().containsKey(a.player())
                        && project.setClaim(a.player(), a.itemId(), true)) {
                    changed.add(a.itemId());
                }
            }
            if (changed.isEmpty()) {
                return;
            }
            record(project, actor.getUuid(), History.Kind.BULK, "", null, changed.size());
            state.markDirty();
            if (changed.size() > 30) {
                broadcastProject(server, project);
            } else {
                for (String itemId : changed) {
                    broadcast(server, Packets.ITEM_CHANGED,
                            out -> Packets.writeItem(out, project.name(), itemId, project.items().get(itemId)));
                }
            }
        });
    }

    /** A player's own counts from their inventory: one history line for the whole batch. */
    private static void bulkCollected(MinecraftServer server, ServerPlayerEntity actor, Packets.Import imported) {
        MaterialShareState state = MaterialShareState.get(server);
        state.registry().get(imported.project()).ifPresent(project -> {
            java.util.List<String> changed = new java.util.ArrayList<>();
            imported.counts().forEach((itemId, n) -> {
                if (project.setCollected(actor.getUuid(), itemId, n)) {
                    changed.add(itemId);
                }
            });
            if (changed.isEmpty()) {
                return;
            }
            record(project, actor.getUuid(), History.Kind.IMPORT, "", null, changed.size());
            state.markDirty();
            if (changed.size() > 30) {
                broadcastProject(server, project);
            } else {
                for (String itemId : changed) {
                    broadcast(server, Packets.ITEM_CHANGED,
                            out -> Packets.writeItem(out, project.name(), itemId, project.items().get(itemId)));
                }
            }
        });
    }

    private interface Edit {
        boolean apply(Project project);
    }

    /** Applies an edit to a known project for a known player and, if it changed anything, broadcasts the item. */
    private static void edit(MinecraftServer server, ServerPlayerEntity actor, String projectName, UUID target,
            String itemId, Edit edit, History.Kind kind, int value) {
        MaterialShareState state = MaterialShareState.get(server);
        if (!state.names().containsKey(target)) {
            tell(actor, "litematica_materialshare.unknown.player");
            return;
        }
        state.registry().get(projectName).ifPresent(project -> {
            if (edit.apply(project)) {
                int shown = kind == History.Kind.COLLECTED
                        ? project.items().get(itemId).claims().getOrDefault(target, value) : value; // as stored
                record(project, actor.getUuid(), kind, itemId, target, shown);
                state.markDirty();
                // only the changed item is sent, not the whole project (it can have 1500 items)
                broadcast(server, Packets.ITEM_CHANGED,
                        out -> Packets.writeItem(out, project.name(), itemId, project.items().get(itemId)));
            }
        });
    }

    private static void sendSnapshot(ServerPlayerEntity player, MaterialShareState state) {
        // An empty snapshot resets the client; projects follow one per packet to stay under the payload limit.
        PacketByteBuf reset = new PacketByteBuf(Unpooled.buffer());
        reset.writeVarInt(0);
        send(player, Packets.SNAPSHOT, reset);
        for (Project project : state.registry().all()) {
            PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            Packets.writeProject(buf, project);
            send(player, Packets.PROJECT_CHANGED, buf);
            if (!project.removed().isEmpty()) {
                PacketByteBuf gone = new PacketByteBuf(Unpooled.buffer());
                Packets.writeRemoved(gone, project);
                send(player, Packets.REMOVED_ITEMS, gone);
            }
            if (!project.surplus().isEmpty()) {
                PacketByteBuf offers = new PacketByteBuf(Unpooled.buffer());
                Packets.writeSurplus(offers, project);
                send(player, Packets.SURPLUS, offers);
            }
            if (!project.storage().isEmpty()) {
                PacketByteBuf storage = new PacketByteBuf(Unpooled.buffer());
                Packets.writeStorage(storage, project);
                send(player, Packets.STORAGE, storage);
            }
            if (!project.zones().isEmpty()) {
                PacketByteBuf zones = new PacketByteBuf(Unpooled.buffer());
                Packets.writeZones(zones, project);
                send(player, Packets.ZONES, zones);
            }
        }
    }

    private static final Map<UUID, Long> LAST_ACTIVITY = new HashMap<>();
    private static final Map<UUID, Long> LAST_FIND = new HashMap<>();

    private static void registerStorage() {
        dev.materialshare.McNet.receive(Packets.STORAGE_ADD, (server, player, handler, buf, sender) -> {
            Packets.AreaAdd add = Packets.readAreaAdd(buf);
            server.execute(() -> {
                MaterialShareState state = MaterialShareState.get(server);
                state.registry().get(add.project()).ifPresent(project -> {
                    if (!project.storage().add(add.area())) {
                        tell(player, "litematica_materialshare.storage.refused");
                        return;
                    }
                    project.touch(player.getUuid(), System.currentTimeMillis());
                    StorageTracker.scanProject(server, state, project);
                    state.markDirty();
                    broadcastStorage(server, project);
                });
            });
        });
        dev.materialshare.McNet.receive(Packets.STORAGE_REMOVE, (server, player, handler, buf, sender) -> {
            String name = buf.readString(Packets.MAX_STRING);
            int index = buf.readVarInt();
            server.execute(() -> {
                MaterialShareState state = MaterialShareState.get(server);
                state.registry().get(name).ifPresent(project -> {
                    if (project.storage().remove(index)) {
                        project.touch(player.getUuid(), System.currentTimeMillis());
                        StorageTracker.scanProject(server, state, project);
                        state.markDirty();
                        broadcastStorage(server, project);
                    }
                });
            });
        });
        dev.materialshare.McNet.receive(Packets.FIND, (server, player, handler, buf, sender) -> {
            String name = buf.readString(Packets.MAX_STRING);
            String itemId = buf.readString(Packets.MAX_STRING);
            server.execute(() -> {
                long now = System.currentTimeMillis();
                Long last = LAST_FIND.get(player.getUuid());
                if (last != null && now - last < 500) {
                    return;
                }
                LAST_FIND.put(player.getUuid(), now);
                MaterialShareState.get(server).registry().get(name).ifPresent(project -> {
                    PacketByteBuf out = new PacketByteBuf(Unpooled.buffer());
                    Packets.writeFound(out, new Packets.Found(name, itemId, StorageTracker.find(server, project, itemId)));
                    send(player, Packets.FOUND, out);
                });
            });
        });
        dev.materialshare.McNet.receive(Packets.TAKE, (server, player, handler, buf, sender) -> {
            Packets.Import take = Packets.readImport(buf);
            server.execute(() -> MaterialShareState.get(server).registry().get(take.project()).ifPresent(project -> {
                int moved = StorageTracker.takeOut(player, project, take.counts());
                if (moved < 0) {
                    tell(player, "litematica_materialshare.take.notopen");
                } else if (moved == 0) {
                    tell(player, "litematica_materialshare.take.none");
                } else {
                    player.sendMessage(dev.materialshare.McCompat.translatable("litematica_materialshare.take.done", moved), true);
                }
            }));
        });
        dev.materialshare.McNet.receive(Packets.ZONE_ADD, (server, player, handler, buf, sender) -> {
            Packets.ZoneAdd add = Packets.readZoneAdd(buf);
            server.execute(() -> {
                MaterialShareState state = MaterialShareState.get(server);
                state.registry().get(add.project()).ifPresent(project -> {
                    if (!project.putZone(add.zone())) {
                        tell(player, "litematica_materialshare.zone.refused");
                        return;
                    }
                    project.touch(player.getUuid(), System.currentTimeMillis());
                    state.markDirty();
                    broadcastZones(server, project);
                });
            });
        });
        dev.materialshare.McNet.receive(Packets.ZONE_REMOVE, (server, player, handler, buf, sender) -> {
            String name = buf.readString(Packets.MAX_STRING);
            String zone = buf.readString(Packets.MAX_STRING);
            server.execute(() -> {
                MaterialShareState state = MaterialShareState.get(server);
                state.registry().get(name).ifPresent(project -> {
                    if (project.removeZone(zone)) {
                        project.touch(player.getUuid(), System.currentTimeMillis());
                        state.markDirty();
                        broadcastZones(server, project);
                    }
                });
            });
        });
        dev.materialshare.McNet.receive(Packets.ACTIVITY_REQUEST, (server, player, handler, buf, sender) -> {
            String name = buf.readString(Packets.MAX_STRING);
            server.execute(() -> {
                long now = System.currentTimeMillis();
                Long last = LAST_ACTIVITY.get(player.getUuid());
                if (last != null && now - last < 1000) {
                    return;
                }
                LAST_ACTIVITY.put(player.getUuid(), now);
                MaterialShareState.get(server).registry().get(name).ifPresent(project -> {
                    PacketByteBuf out = new PacketByteBuf(Unpooled.buffer());
                    Packets.writeActivity(out, project);
                    send(player, Packets.ACTIVITY, out);
                });
            });
        });
    }

    static void broadcastZones(MinecraftServer server, Project project) {
        broadcast(server, Packets.ZONES, out -> Packets.writeZones(out, project));
    }

    public static void broadcastStorage(MinecraftServer server, Project project) {
        broadcast(server, Packets.STORAGE, out -> Packets.writeStorage(out, project));
    }

    /** Sends the current state of some items to everybody (the counts follow a chest being used almost at once). */
    static void broadcastItems(MinecraftServer server, Project project, java.util.List<String> ids) {
        for (String id : ids) {
            dev.materialshare.common.Entry entry = project.items().get(id);
            if (entry != null) {
                broadcast(server, Packets.ITEM_CHANGED, out -> Packets.writeItem(out, project.name(), id, entry));
            }
        }
    }

    /** Items a player put into (or took out of) a storage chest: one history line when the chest is closed. */
    static void recordStorage(MinecraftServer server, Project project, ServerPlayerEntity player, java.util.List<String> changed) {
        changed.removeIf(id -> !project.items().containsKey(id));
        if (changed.isEmpty()) {
            return;
        }
        if (changed.size() == 1) {
            String id = changed.get(0);
            record(project, player.getUuid(), History.Kind.DELIVERED, id, null,
                    project.items().get(id).delivered().getOrDefault(player.getUuid(), 0));
        } else {
            record(project, player.getUuid(), History.Kind.DELIVERED, "", null, changed.size());
        }
        MaterialShareState.get(server).markDirty();
    }

    private static final long FEED_COOLDOWN_MS = 2000;
    private static final Map<UUID, Long> LAST_FEED = new HashMap<>();

    private static void sendActionFailed(ServerPlayerEntity player, String name, int reason) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeVarInt(reason);
        buf.writeString(name.length() > Packets.MAX_STRING ? name.substring(0, Packets.MAX_STRING) : name, Packets.MAX_STRING);
        send(player, Packets.ACTION_FAILED, buf);
    }

    private static int changeReason(ProjectRegistry.Change change) {
        return switch (change) {
            case NAME_TAKEN -> Packets.FAIL_NAME_TAKEN;
            case TOO_MANY -> Packets.FAIL_TOO_MANY;
            case BAD_NAME -> Packets.FAIL_BAD_NAME_PROJECT;
            default -> Packets.FAIL_NOT_FOUND;
        };
    }

    /** Targets (promised amounts), handing claims over, delivery, items, names, copies, new required amounts, activity. */
    private static void registerShares() {
        dev.materialshare.McNet.receive(Packets.SET_TARGETS, (server, player, handler, buf, sender) -> {
            Packets.Targets targets = Packets.readTargets(buf);
            server.execute(() -> {
                MaterialShareState state = MaterialShareState.get(server);
                state.registry().get(targets.project()).ifPresent(project -> {
                    dev.materialshare.common.Entry entry = project.items().get(targets.itemId());
                    if (entry == null) {
                        return;
                    }
                    boolean changed = false;
                    for (UUID who : new java.util.ArrayList<>(entry.targets().keySet())) {
                        if (!targets.targets().containsKey(who)) {
                            changed |= project.setTarget(who, targets.itemId(), 0);
                        }
                    }
                    for (Map.Entry<UUID, Integer> t : targets.targets().entrySet()) {
                        changed |= project.setTarget(t.getKey(), targets.itemId(), t.getValue());
                    }
                    if (changed) {
                        UUID single = targets.targets().size() == 1 ? targets.targets().keySet().iterator().next() : null;
                        int value = single != null ? targets.targets().get(single) : targets.targets().size();
                        record(project, player.getUuid(), History.Kind.TARGET, targets.itemId(), single, value);
                        state.markDirty();
                        broadcast(server, Packets.ITEM_CHANGED,
                                out -> Packets.writeItem(out, project.name(), targets.itemId(), project.items().get(targets.itemId())));
                    }
                });
            });
        });

        dev.materialshare.McNet.receive(Packets.TRANSFER, (server, player, handler, buf, sender) -> {
            String name = buf.readString(Packets.MAX_STRING);
            String itemId = buf.readString(Packets.MAX_STRING);
            UUID from = buf.readUuid();
            UUID to = buf.readUuid();
            server.execute(() -> {
                MaterialShareState state = MaterialShareState.get(server);
                if (!state.names().containsKey(to)) {
                    tell(player, "litematica_materialshare.unknown.player");
                    return;
                }
                state.registry().get(name).ifPresent(project -> {
                    if (project.transfer(itemId, from, to)) {
                        record(project, player.getUuid(), History.Kind.TRANSFER, itemId, to, 0);
                        state.markDirty();
                        broadcast(server, Packets.ITEM_CHANGED,
                                out -> Packets.writeItem(out, project.name(), itemId, project.items().get(itemId)));
                    }
                });
            });
        });

        dev.materialshare.McNet.receive(Packets.SET_DELIVERED, (server, player, handler, buf, sender) -> {
            String name = buf.readString(Packets.MAX_STRING);
            String itemId = buf.readString(Packets.MAX_STRING);
            UUID target = buf.readUuid();
            int n = buf.readVarInt();
            server.execute(() -> edit(server, player, name, target, itemId,
                    project -> project.setDelivered(target, itemId, n), History.Kind.DELIVERED, n));
        });

        dev.materialshare.McNet.receive(Packets.BULK_DELIVERED, (server, player, handler, buf, sender) -> {
            Packets.Import delivered = Packets.readImport(buf);
            server.execute(() -> {
                MaterialShareState state = MaterialShareState.get(server);
                state.registry().get(delivered.project()).ifPresent(project -> {
                    java.util.List<String> changed = new java.util.ArrayList<>();
                    delivered.counts().forEach((itemId, n) -> {
                        if (project.setDelivered(player.getUuid(), itemId, n)) {
                            changed.add(itemId);
                        }
                    });
                    if (changed.isEmpty()) {
                        return;
                    }
                    record(project, player.getUuid(), History.Kind.DELIVERED, "", null, changed.size());
                    state.markDirty();
                    if (changed.size() > 30) {
                        broadcastProject(server, project);
                    } else {
                        for (String itemId : changed) {
                            broadcast(server, Packets.ITEM_CHANGED,
                                    out -> Packets.writeItem(out, project.name(), itemId, project.items().get(itemId)));
                        }
                    }
                });
            });
        });

        dev.materialshare.McNet.receive(Packets.REMOVE_ITEM, (server, player, handler, buf, sender) -> {
            String name = buf.readString(Packets.MAX_STRING);
            String itemId = buf.readString(Packets.MAX_STRING);
            server.execute(() -> {
                MaterialShareState state = MaterialShareState.get(server);
                state.registry().get(name).ifPresent(project -> {
                    if (project.removeItem(itemId)) {
                        record(project, player.getUuid(), History.Kind.ITEM_REMOVED, itemId, null, 0);
                        state.markDirty();
                        broadcastProject(server, project);
                        broadcastRemoved(server, project);
                    }
                });
            });
        });

        dev.materialshare.McNet.receive(Packets.RESET_CLAIMS, (server, player, handler, buf, sender) -> {
            String name = buf.readString(Packets.MAX_STRING);
            String itemId = buf.readString(Packets.MAX_STRING);
            UUID who = buf.readBoolean() ? buf.readUuid() : null;
            server.execute(() -> {
                MaterialShareState state = MaterialShareState.get(server);
                state.registry().get(name).ifPresent(project -> {
                    if (who != null) {
                        int changed = project.clearPlayer(who);
                        if (changed > 0) {
                            record(project, player.getUuid(), History.Kind.RESET_CLAIMS, "", who, changed);
                            state.markDirty();
                            broadcastProject(server, project);
                        }
                        return;
                    }
                    if (itemId.isEmpty()) {
                        int changed = project.clearAllAssignments();
                        if (changed > 0) {
                            record(project, player.getUuid(), History.Kind.RESET_CLAIMS, "", null, changed);
                            state.markDirty();
                            broadcastProject(server, project);
                        }
                        return;
                    }
                    dev.materialshare.common.Entry item = project.items().get(itemId);
                    if (item != null && item.clearAssignments()) {
                        record(project, player.getUuid(), History.Kind.RESET_CLAIMS, itemId, null, 1);
                        state.markDirty();
                        broadcast(server, Packets.ITEM_CHANGED, out -> Packets.writeItem(out, project.name(), itemId, item));
                    }
                });
            });
        });

        dev.materialshare.McNet.receive(Packets.RESTORE_ITEM, (server, player, handler, buf, sender) -> {
            String name = buf.readString(Packets.MAX_STRING);
            String itemId = buf.readString(Packets.MAX_STRING);
            server.execute(() -> {
                MaterialShareState state = MaterialShareState.get(server);
                state.registry().get(name).ifPresent(project -> {
                    if (project.restoreItem(itemId)) {
                        record(project, player.getUuid(), History.Kind.ITEM_RESTORED, itemId, null, 0);
                        state.markDirty();
                        broadcastProject(server, project);
                        broadcastRemoved(server, project);
                    }
                });
            });
        });

        dev.materialshare.McNet.receive(Packets.RENAME, (server, player, handler, buf, sender) -> {
            String from = buf.readString(Packets.MAX_STRING);
            String to = buf.readString(Packets.MAX_STRING);
            server.execute(() -> {
                MaterialShareState state = MaterialShareState.get(server);
                ProjectRegistry.Change result = state.registry().rename(from, to);
                if (result != ProjectRegistry.Change.OK) {
                    sendActionFailed(player, to, changeReason(result));
                    return;
                }
                state.registry().get(to).ifPresent(project -> {
                    record(project, player.getUuid(), History.Kind.RENAMED, "", null, 0);
                    state.markDirty();
                    broadcast(server, Packets.PROJECT_DELETED, out -> out.writeString(from, Packets.MAX_STRING));
                    broadcastProject(server, project);
                    if (!project.zones().isEmpty()) {
                        broadcastZones(server, project);
                    }
                });
            });
        });

        dev.materialshare.McNet.receive(Packets.CLONE, (server, player, handler, buf, sender) -> {
            String from = buf.readString(Packets.MAX_STRING);
            String to = buf.readString(Packets.MAX_STRING);
            boolean keepClaims = buf.readBoolean();
            server.execute(() -> {
                MaterialShareState state = MaterialShareState.get(server);
                ProjectRegistry.Change result = state.registry().copy(from, to, keepClaims);
                if (result != ProjectRegistry.Change.OK) {
                    sendActionFailed(player, to, changeReason(result));
                    return;
                }
                state.registry().get(to).ifPresent(project -> {
                    record(project, player.getUuid(), History.Kind.CLONED, "", null, project.items().size());
                    state.markDirty();
                    broadcastProject(server, project);
                    if (!project.zones().isEmpty()) {
                        broadcastZones(server, project);
                    }
                });
            });
        });

        dev.materialshare.McNet.receive(Packets.UPDATE_REQUIRED, (server, player, handler, buf, sender) -> {
            String name = buf.readString(Packets.MAX_STRING);
            int n = Math.min(buf.readVarInt(), Project.MAX_ITEMS);
            Map<String, Integer> required = new HashMap<>();
            for (int i = 0; i < n; i++) {
                required.put(buf.readString(Packets.MAX_STRING), buf.readVarInt());
            }
            server.execute(() -> {
                MaterialShareState state = MaterialShareState.get(server);
                state.registry().get(name).ifPresent(project -> {
                    Project.Update update = project.updateRequired(required);
                    if (update.added() + update.changed() > 0) {
                        record(project, player.getUuid(), History.Kind.UPDATED, "", null, update.added() + update.changed());
                        state.markDirty();
                        broadcastProject(server, project);
                    }
                });
            });
        });

        dev.materialshare.McNet.receive(Packets.FEED_REQUEST, (server, player, handler, buf, sender) ->
                server.execute(() -> {
                    long now = System.currentTimeMillis();
                    Long last = LAST_FEED.get(player.getUuid());
                    if (last != null && now - last < FEED_COOLDOWN_MS) {
                        return;
                    }
                    LAST_FEED.put(player.getUuid(), now);
                    java.util.List<Packets.FeedEntry> all = new java.util.ArrayList<>();
                    for (Project p : MaterialShareState.get(server).registry().all()) {
                        for (History.Event e : p.history().events()) {
                            all.add(new Packets.FeedEntry(p.name(), e));
                        }
                    }
                    all.sort((a, b) -> Long.compare(b.event().time(), a.event().time()));
                    PacketByteBuf out = new PacketByteBuf(Unpooled.buffer());
                    Packets.writeFeed(out, all.subList(0, Math.min(all.size(), Packets.FEED_MAX)));
                    send(player, Packets.FEED, out);
                }));
    }

    private static int failReason(ProjectRegistry.TrashResult result) {
        return switch (result) {
            case FULL -> Packets.FAIL_TRASH_FULL;
            case NAME_TAKEN -> Packets.FAIL_NAME_TAKEN;
            case TOO_MANY -> Packets.FAIL_TOO_MANY;
            default -> Packets.FAIL_NOT_FOUND;
        };
    }

    private static void sendTrashFailed(ServerPlayerEntity player, String name, int reason) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeVarInt(reason);
        buf.writeString(name.length() > Packets.MAX_STRING ? name.substring(0, Packets.MAX_STRING) : name,
                Packets.MAX_STRING);
        send(player, Packets.TRASH_FAILED, buf);
    }

    private static void sendTrash(ServerPlayerEntity player, MaterialShareState state) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        Packets.writeTrash(buf, state.registry().trashed());
        send(player, Packets.TRASH_LIST, buf);
    }

    private static void broadcastTrash(MinecraftServer server, MaterialShareState state) {
        broadcast(server, Packets.TRASH_LIST, out -> Packets.writeTrash(out, state.registry().trashed()));
    }

    private static void sendNames(ServerPlayerEntity player, MaterialShareState state) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        Packets.writeNames(buf, state.names());
        send(player, Packets.PLAYER_NAMES, buf);
    }

    private static void broadcastSurplus(MinecraftServer server, Project project) {
        broadcast(server, Packets.SURPLUS, out -> Packets.writeSurplus(out, project));
    }

    private static void broadcastRemoved(MinecraftServer server, Project project) {
        broadcast(server, Packets.REMOVED_ITEMS, out -> Packets.writeRemoved(out, project));
    }

    private static void broadcastProject(MinecraftServer server, Project project) {
        broadcast(server, Packets.PROJECT_CHANGED, out -> Packets.writeProject(out, project));
        if (!project.removed().isEmpty()) {
            broadcastRemoved(server, project);
        }
        if (!project.surplus().isEmpty()) {
            broadcastSurplus(server, project); // a renamed or restored project brings its spare items along
        }
        if (!project.storage().isEmpty()) {
            broadcastStorage(server, project);
        }
    }

    private static void broadcastNames(MinecraftServer server, MaterialShareState state) {
        broadcast(server, Packets.PLAYER_NAMES, out -> Packets.writeNames(out, state.names()));
    }

    private static void broadcast(MinecraftServer server, net.minecraft.util.Identifier channel,
            Consumer<PacketByteBuf> writer) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            writer.accept(buf);
            send(player, channel, buf);
        }
    }
}
