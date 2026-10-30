package dev.materialshare.client;

import dev.materialshare.common.Packets;
import dev.materialshare.common.Project;
import dev.materialshare.client.config.InitHandler;
import fi.dy.masa.malilib.event.InitializationHandler;
import fi.dy.masa.malilib.gui.Message;
import fi.dy.masa.malilib.util.InfoUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;

public final class ClientNet implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        register();
        InitializationHandler.getInstance().registerInitializationHandler(new InitHandler());
        var ticks = net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK;
        ticks.register(ProjectNames::tick);
        ticks.register(Notifier::tick);
        ticks.register(Importer::tick);
        ticks.register(Pickup::tick);
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register(Pickup::onScreenInit);
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(HudOverlay::render);
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.LAST.register(StorageRender::render);
        dev.materialshare.McClientCompat.registerCommands();
    }

    public static void register() {
        dev.materialshare.McNet.init();
        ClientLoginNetworking.registerGlobalReceiver(Packets.HELLO,
                (client, handler, buf, listenerAdder) -> {
                    PacketByteBuf reply = PacketByteBufs.create();
                    reply.writeVarInt(dev.materialshare.common.ProtocolCheck.PROTOCOL_VERSION);
                    return CompletableFuture.completedFuture(reply);
                });

        dev.materialshare.McClientNet.receive(Packets.SNAPSHOT, (client, handler, buf, sender) -> {
            int n = buf.readVarInt();
            List<Project> projects = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                projects.add(Packets.readProject(buf));
            }
            client.execute(() -> ClientProjectCache.replaceAll(projects));
        });

        dev.materialshare.McClientNet.receive(Packets.PROJECT_CHANGED, (client, handler, buf, sender) -> {
            Project project = Packets.readProject(buf);
            client.execute(() -> ClientProjectCache.put(project));
        });

        dev.materialshare.McClientNet.receive(Packets.ITEM_CHANGED, (client, handler, buf, sender) -> {
            Packets.ItemUpdate update = Packets.readItem(buf);
            client.execute(() -> ClientProjectCache.putItem(update));
        });

        dev.materialshare.McClientNet.receive(Packets.PROJECT_DELETED, (client, handler, buf, sender) -> {
            String name = buf.readString(Packets.MAX_STRING);
            client.execute(() -> ClientProjectCache.remove(name));
        });

        dev.materialshare.McClientNet.receive(Packets.PLAYER_NAMES, (client, handler, buf, sender) -> {
            Map<UUID, String> names = Packets.readNames(buf);
            client.execute(() -> ClientProjectCache.setNames(names));
        });

        dev.materialshare.McClientNet.receive(Packets.CREATE_FAILED, (client, handler, buf, sender) -> {
            String name = buf.readString(Packets.MAX_STRING);
            int reason = buf.readVarInt();
            client.execute(() -> ProjectNames.onCreateFailed(name, reason));
        });

        dev.materialshare.McClientNet.receive(Packets.LOOKUP_RESULT, (client, handler, buf, sender) -> {
            String name = buf.readString(Packets.MAX_STRING);
            boolean found = buf.readBoolean();
            client.execute(() -> {
                if (found) {
                    InfoUtils.showGuiMessage(Message.MessageType.SUCCESS, Lang.t("lookup.found", name));
                } else {
                    InfoUtils.showGuiMessage(Message.MessageType.ERROR,
                            Lang.t("lookup.notfound", name));
                }
            });
        });

        dev.materialshare.McClientNet.receive(Packets.TRASH_LIST, (client, handler, buf, sender) -> {
            var list = Packets.readTrash(buf);
            client.execute(() -> ClientProjectCache.setTrash(list));
        });

        dev.materialshare.McClientNet.receive(Packets.TRASH_FAILED, (client, handler, buf, sender) -> {
            int reason = buf.readVarInt();
            String name = buf.readString(Packets.MAX_STRING);
            client.execute(() -> InfoUtils.showGuiMessage(Message.MessageType.ERROR,
                    Lang.t(switch (reason) {
                        case Packets.FAIL_TRASH_FULL -> "trash.failed.full";
                        case Packets.FAIL_NAME_TAKEN -> "trash.failed.exists";
                        case Packets.FAIL_TOO_MANY -> "trash.failed.many";
                        default -> "trash.failed.notfound";
                    }, UiText.escapeFormat(name))));
        });

        dev.materialshare.McClientNet.receive(Packets.FEED, (client, handler, buf, sender) -> {
            var feed = Packets.readFeed(buf);
            client.execute(() -> ClientProjectCache.setFeed(feed));
        });

        dev.materialshare.McClientNet.receive(Packets.ACTION_FAILED, (client, handler, buf, sender) -> {
            int reason = buf.readVarInt();
            String name = buf.readString(Packets.MAX_STRING);
            client.execute(() -> InfoUtils.showGuiMessage(Message.MessageType.ERROR,
                    Lang.t(switch (reason) {
                        case Packets.FAIL_NAME_TAKEN -> "action.failed.exists";
                        case Packets.FAIL_TOO_MANY -> "action.failed.many";
                        case Packets.FAIL_BAD_NAME_PROJECT -> "action.failed.badname";
                        default -> "action.failed.notfound";
                    }, UiText.escapeFormat(name))));
        });

        dev.materialshare.McClientNet.receive(Packets.SURPLUS, (client, handler, buf, sender) -> {
            Packets.SurplusList list = Packets.readSurplus(buf);
            client.execute(() -> ClientProjectCache.setSurplus(list));
        });

        dev.materialshare.McClientNet.receive(Packets.STORAGE, (client, handler, buf, sender) -> {
            Packets.StorageData data = Packets.readStorage(buf);
            client.execute(() -> ClientProjectCache.setStorage(data));
        });

        dev.materialshare.McClientNet.receive(Packets.FOUND, (client, handler, buf, sender) -> {
            Packets.Found found = Packets.readFound(buf);
            client.execute(() -> Pickup.onFound(found));
        });

        dev.materialshare.McClientNet.receive(Packets.ZONES, (client, handler, buf, sender) -> {
            Packets.ZoneList list = Packets.readZones(buf);
            client.execute(() -> ClientProjectCache.setZones(list));
        });

        dev.materialshare.McClientNet.receive(Packets.ENDER, (client, handler, buf, sender) -> {
            Map<String, Integer> counts = Packets.readCounts(buf);
            client.execute(() -> ClientProjectCache.setEnder(counts));
        });

        dev.materialshare.McClientNet.receive(Packets.ACTIVITY, (client, handler, buf, sender) -> {
            Packets.ActivityData data = Packets.readActivity(buf);
            client.execute(() -> ClientProjectCache.setActivity(data));
        });

        dev.materialshare.McClientNet.receive(Packets.REMOVED_ITEMS, (client, handler, buf, sender) -> {
            Packets.RemovedList list = Packets.readRemoved(buf);
            client.execute(() -> ClientProjectCache.setRemoved(list));
        });

        dev.materialshare.McClientNet.receive(Packets.HISTORY, (client, handler, buf, sender) -> {
            Packets.HistoryReply reply = Packets.readHistory(buf);
            client.execute(() -> ClientProjectCache.setHistory(reply));
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            ClientProjectCache.clear();
            Undo.clear();
        }));
    }

    /** Items per CREATE packet; the server accepts at most 32767 bytes per client payload. */
    private static final int CREATE_CHUNK = 200;

    public static void sendCreate(String project, Map<String, Integer> required) {
        List<Map.Entry<String, Integer>> all = new ArrayList<>(required.entrySet());
        int from = 0;
        do {
            List<Map.Entry<String, Integer>> chunk = all.subList(from, Math.min(all.size(), from + CREATE_CHUNK));
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeString(project, Packets.MAX_STRING);
            buf.writeVarInt(chunk.size());
            for (Map.Entry<String, Integer> e : chunk) {
                buf.writeString(e.getKey(), Packets.MAX_STRING);
                buf.writeVarInt(e.getValue());
            }
            dev.materialshare.McClientNet.send(Packets.CREATE, buf);
            from += CREATE_CHUNK;
        } while (from < all.size());
    }

    /** Asks the server to add everyone from its usercache to the known players. */
    public static void requestPlayers() {
        dev.materialshare.McClientNet.send(Packets.REQUEST_PLAYERS, PacketByteBufs.create());
    }

    /** Asks the server to find a player by exact name and remember them. */
    public static void sendLookup(String name) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(name, Packets.MAX_STRING);
        dev.materialshare.McClientNet.send(Packets.LOOKUP_PLAYER, buf);
    }

    public static void sendItemInfo(String project, String itemId, boolean priority) {
        String substitute = ClientProjectCache.projects().stream().filter(p -> p.name().equals(project)).findFirst()
                .map(p -> p.items().get(itemId)).map(dev.materialshare.common.Entry::substitute).orElse("");
        sendItemInfo(project, itemId, priority, substitute);
    }

    public static void sendItemInfo(String project, String itemId, boolean priority, String substitute) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(project, Packets.MAX_STRING);
        buf.writeString(itemId, Packets.MAX_STRING);
        buf.writeBoolean(priority);
        buf.writeString(dev.materialshare.common.Entry.cleanText(substitute), Packets.MAX_STRING);
        dev.materialshare.McClientNet.send(Packets.SET_ITEM_INFO, buf);
    }

    /** Sets what each claimant of an item promises to collect (players not listed lose their promise). */
    public static void sendTargets(String project, String itemId, Map<UUID, Integer> targets) {
        PacketByteBuf buf = PacketByteBufs.create();
        Packets.writeTargets(buf, project, itemId, targets);
        dev.materialshare.McClientNet.send(Packets.SET_TARGETS, buf);
    }

    public static void sendTransfer(String project, String itemId, UUID from, UUID to) {
        OwnActions.noteRelease(project, itemId, from);
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(project, Packets.MAX_STRING);
        buf.writeString(itemId, Packets.MAX_STRING);
        buf.writeUuid(from);
        buf.writeUuid(to);
        dev.materialshare.McClientNet.send(Packets.TRANSFER, buf);
    }

    public static void sendDelivered(String project, String itemId, UUID target, int n) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(project, Packets.MAX_STRING);
        buf.writeString(itemId, Packets.MAX_STRING);
        buf.writeUuid(target);
        buf.writeVarInt(n);
        dev.materialshare.McClientNet.send(Packets.SET_DELIVERED, buf);
    }

    /** The player's own delivered counts for many items (chunked like the other bulk packets). */
    public static void sendBulkDelivered(String project, Map<String, Integer> counts) {
        sendCounts(Packets.BULK_DELIVERED, project, counts);
    }

    private static void sendCounts(net.minecraft.util.Identifier channel, String project, Map<String, Integer> counts) {
        List<Map.Entry<String, Integer>> all = new ArrayList<>(counts.entrySet());
        for (int from = 0; from < all.size(); from += Packets.BULK_CHUNK) {
            Map<String, Integer> chunk = new java.util.LinkedHashMap<>();
            all.subList(from, Math.min(all.size(), from + Packets.BULK_CHUNK)).forEach(e -> chunk.put(e.getKey(), e.getValue()));
            PacketByteBuf buf = PacketByteBufs.create();
            Packets.writeImport(buf, project, chunk);
            dev.materialshare.McClientNet.send(channel, buf);
        }
    }

    /** Takes everybody off every item of the project (the dialog that asks first lives in the tools screen). */
    public static void sendResetClaims(String project) {
        OwnActions.quietFor(5000);
        sendResetClaims(project, "");
    }

    /** Takes one player off every item of the project. */
    public static void sendResetPlayer(String project, UUID player) {
        OwnActions.quietFor(5000);
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(project);
        buf.writeString("");
        buf.writeBoolean(true);
        buf.writeUuid(player);
        dev.materialshare.McClientNet.send(Packets.RESET_CLAIMS, buf);
    }

    /** An empty item id means every item. */
    public static void sendResetClaims(String project, String itemId) {
        OwnActions.quietFor(5000);
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(project);
        buf.writeString(itemId);
        buf.writeBoolean(false);
        dev.materialshare.McClientNet.send(Packets.RESET_CLAIMS, buf);
    }

    public static void sendRestoreItem(String project, String itemId) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(project);
        buf.writeString(itemId);
        dev.materialshare.McClientNet.send(Packets.RESTORE_ITEM, buf);
    }

    public static void sendRemoveItem(String project, String itemId) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(project, Packets.MAX_STRING);
        buf.writeString(itemId, Packets.MAX_STRING);
        dev.materialshare.McClientNet.send(Packets.REMOVE_ITEM, buf);
    }

    public static void sendRename(String from, String to) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(from, Packets.MAX_STRING);
        buf.writeString(to, Packets.MAX_STRING);
        dev.materialshare.McClientNet.send(Packets.RENAME, buf);
    }

    public static void sendClone(String from, String to, boolean keepClaims) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(from, Packets.MAX_STRING);
        buf.writeString(to, Packets.MAX_STRING);
        buf.writeBoolean(keepClaims);
        dev.materialshare.McClientNet.send(Packets.CLONE, buf);
    }

    /** New required amounts after the design changed (sent in chunks, each one adds and changes items). */
    public static void sendUpdateRequired(String project, Map<String, Integer> required) {
        List<Map.Entry<String, Integer>> all = new ArrayList<>(required.entrySet());
        for (int from = 0; from < all.size() || from == 0; from += CREATE_CHUNK) {
            List<Map.Entry<String, Integer>> chunk = all.subList(Math.min(from, all.size()), Math.min(all.size(), from + CREATE_CHUNK));
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeString(project, Packets.MAX_STRING);
            buf.writeVarInt(chunk.size());
            for (Map.Entry<String, Integer> e : chunk) {
                buf.writeString(e.getKey(), Packets.MAX_STRING);
                buf.writeVarInt(e.getValue());
            }
            dev.materialshare.McClientNet.send(Packets.UPDATE_REQUIRED, buf);
            if (all.isEmpty()) {
                break;
            }
        }
    }

    public static void requestFeed() {
        dev.materialshare.McClientNet.send(Packets.FEED_REQUEST, PacketByteBufs.create());
    }

    public static void sendArchive(String project, boolean archived) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(project, Packets.MAX_STRING);
        buf.writeBoolean(archived);
        dev.materialshare.McClientNet.send(Packets.SET_ARCHIVED, buf);
    }

    public static void sendTrashRestore(long id) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeLong(id);
        dev.materialshare.McClientNet.send(Packets.TRASH_RESTORE, buf);
    }

    public static void sendTrashPurge(long id) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeLong(id);
        dev.materialshare.McClientNet.send(Packets.TRASH_PURGE, buf);
    }

    public static void sendDelete(String project) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(project, Packets.MAX_STRING);
        dev.materialshare.McClientNet.send(Packets.DELETE, buf);
    }

    /** A claim made by the player: remembered so it can be undone. */
    public static void sendClaim(String project, String itemId, UUID target, boolean on) {
        Undo.recordClaim(project, itemId, target);
        sendClaimRaw(project, itemId, target, on);
    }

    public static void sendCollected(String project, String itemId, UUID target, int n) {
        Undo.recordCollected(project, itemId, target);
        sendCollectedRaw(project, itemId, target, n);
    }

    /** Gives items nobody has taken yet to players (one undo step; sent in chunks that fit the packet limit). */
    public static void sendBulk(String project, java.util.List<dev.materialshare.common.Assignments.Assignment> list) {
        if (list.isEmpty()) {
            return;
        }
        Undo.recordMany(list.stream().map(a -> Undo.before(project, a.itemId(), a.player())).toList());
        list.forEach(a -> OwnActions.noteClaim(project, a.itemId(), a.player()));
        for (int from = 0; from < list.size(); from += Packets.BULK_CHUNK) {
            PacketByteBuf buf = PacketByteBufs.create();
            Packets.writeBulk(buf, project, list.subList(from, Math.min(list.size(), from + Packets.BULK_CHUNK)));
            dev.materialshare.McClientNet.send(Packets.BULK_CLAIM, buf);
        }
    }

    /** The player's own collected counts for many items at once (inventory import). */
    static void sendImport(String project, Map<String, Integer> counts) {
        java.util.List<Map.Entry<String, Integer>> all = new ArrayList<>(counts.entrySet());
        for (int from = 0; from < all.size(); from += Packets.BULK_CHUNK) {
            Map<String, Integer> chunk = new java.util.LinkedHashMap<>();
            all.subList(from, Math.min(all.size(), from + Packets.BULK_CHUNK))
                    .forEach(e -> chunk.put(e.getKey(), e.getValue()));
            PacketByteBuf buf = PacketByteBufs.create();
            Packets.writeImport(buf, project, chunk);
            dev.materialshare.McClientNet.send(Packets.BULK_COLLECTED, buf);
        }
    }

    public static void sendNote(String project, String note) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(project, Packets.MAX_STRING);
        buf.writeString(note, Packets.MAX_STRING);
        dev.materialshare.McClientNet.send(Packets.SET_NOTE, buf);
    }

    /** Offers {@code n} spare pieces of an item to the others (0 takes the offer back). */
    public static void sendSurplus(String project, String itemId, int n) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(project, Packets.MAX_STRING);
        buf.writeString(itemId, Packets.MAX_STRING);
        buf.writeVarInt(n);
        dev.materialshare.McClientNet.send(Packets.SET_SURPLUS, buf);
    }

    /** Takes {@code n} pieces from somebody's offer. */
    public static void sendTakeSurplus(String project, String itemId, UUID from, int n) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(project, Packets.MAX_STRING);
        buf.writeString(itemId, Packets.MAX_STRING);
        buf.writeUuid(from);
        buf.writeVarInt(n);
        dev.materialshare.McClientNet.send(Packets.TAKE_SURPLUS, buf);
    }

    public static void sendStorageAdd(String project, dev.materialshare.common.Area area) {
        PacketByteBuf buf = PacketByteBufs.create();
        Packets.writeAreaAdd(buf, project, area);
        dev.materialshare.McClientNet.send(Packets.STORAGE_ADD, buf);
    }

    public static void sendStorageRemove(String project, int index) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(project, Packets.MAX_STRING);
        buf.writeVarInt(index);
        dev.materialshare.McClientNet.send(Packets.STORAGE_REMOVE, buf);
    }

    static void sendFind(String project, String itemId) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(project, Packets.MAX_STRING);
        buf.writeString(itemId, Packets.MAX_STRING);
        dev.materialshare.McClientNet.send(Packets.FIND, buf);
    }

    static void sendTake(String project, Map<String, Integer> counts) {
        PacketByteBuf buf = PacketByteBufs.create();
        Packets.writeImport(buf, project, counts);
        dev.materialshare.McClientNet.send(Packets.TAKE, buf);
    }

    static void sendZoneAdd(String project, dev.materialshare.common.Zone zone) {
        PacketByteBuf buf = PacketByteBufs.create();
        Packets.writeZoneAdd(buf, project, zone);
        dev.materialshare.McClientNet.send(Packets.ZONE_ADD, buf);
    }

    static void sendZoneRemove(String project, String zone) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(project, Packets.MAX_STRING);
        buf.writeString(zone, Packets.MAX_STRING);
        dev.materialshare.McClientNet.send(Packets.ZONE_REMOVE, buf);
    }

    static void requestActivity(String project) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(project, Packets.MAX_STRING);
        dev.materialshare.McClientNet.send(Packets.ACTIVITY_REQUEST, buf);
    }

    public static void requestHistory(String project) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(project, Packets.MAX_STRING);
        dev.materialshare.McClientNet.send(Packets.HISTORY_REQUEST, buf);
    }

    static void sendClaimRaw(String project, String itemId, UUID target, boolean on) {
        if (on) {
            OwnActions.noteClaim(project, itemId, target);
        } else {
            OwnActions.noteRelease(project, itemId, target);
        }
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(project, Packets.MAX_STRING);
        buf.writeString(itemId, Packets.MAX_STRING);
        buf.writeUuid(target);
        buf.writeBoolean(on);
        dev.materialshare.McClientNet.send(Packets.CLAIM, buf);
    }

    static void sendCollectedRaw(String project, String itemId, UUID target, int n) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(project, Packets.MAX_STRING);
        buf.writeString(itemId, Packets.MAX_STRING);
        buf.writeUuid(target);
        buf.writeVarInt(n);
        dev.materialshare.McClientNet.send(Packets.SET_COLLECTED, buf);
    }
}
