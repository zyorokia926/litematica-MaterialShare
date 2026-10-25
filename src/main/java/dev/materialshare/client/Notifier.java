package dev.materialshare.client;

import dev.materialshare.client.config.Configs;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

/** Tells the player when an item (or a whole project) has been collected. */
public final class Notifier {
    private static final List<String> DONE_ITEMS = new ArrayList<>();
    private static String completedProject;
    private static String projectOfItems = "";
    private static final List<String> ASSIGNED = new ArrayList<>();
    private static String assignedProject = "";
    private static final List<String> UNASSIGNED = new ArrayList<>();
    private static String unassignedProject = "";

    private Notifier() {
    }

    private static boolean wanted(boolean mine) {
        return Configs.Generic.NOTIFY_DONE.getBooleanValue()
                && (mine || !Configs.Generic.NOTIFY_ONLY_MINE.getBooleanValue());
    }

    static void itemDone(String project, String itemId, boolean mine) {
        if (wanted(mine)) {
            projectOfItems = project;
            DONE_ITEMS.add(itemId);
        }
    }

    /** Somebody else made this player a claimant of an item. */
    static void assigned(String project, String itemId) {
        if (Configs.Generic.NOTIFY_ASSIGNED.getBooleanValue()) {
            assignedProject = project;
            ASSIGNED.add(itemId);
        }
    }

    /** Somebody else took this player off an item. */
    static void unassigned(String project, String itemId) {
        if (Configs.Generic.NOTIFY_ASSIGNED.getBooleanValue()) {
            unassignedProject = project;
            UNASSIGNED.add(itemId);
        }
    }

    static void projectDone(String project, boolean mine) {
        if (wanted(mine)) {
            completedProject = project;
        }
    }

    /** Bundled per tick, so a bulk update that finishes many items shows one toast, not a wall of them. */
    public static void tick(MinecraftClient client) {
        if (!ASSIGNED.isEmpty()) {
            if (ASSIGNED.size() <= 2) {
                for (String id : ASSIGNED) {
                    toast(client, Lang.t("toast.assigned.title"), Lang.t("toast.assigned.text", UiText.itemName(id), assignedProject));
                }
            } else {
                toast(client, Lang.t("toast.assigned.title"), Lang.t("toast.assigned.many", ASSIGNED.size(), assignedProject));
            }
            ASSIGNED.clear();
        }
        if (!UNASSIGNED.isEmpty()) {
            if (UNASSIGNED.size() <= 2) {
                for (String id : UNASSIGNED) {
                    toast(client, Lang.t("toast.unassigned.title"), Lang.t("toast.unassigned.text", UiText.itemName(id), unassignedProject));
                }
            } else {
                toast(client, Lang.t("toast.unassigned.title"), Lang.t("toast.unassigned.many", UNASSIGNED.size(), unassignedProject));
            }
            UNASSIGNED.clear();
        }
        if (DONE_ITEMS.isEmpty() && completedProject == null) {
            return;
        }
        if (completedProject != null) {
            toast(client, Lang.t("toast.project.title"), Lang.t("toast.project.text", completedProject));
            play(client, true);
        } else if (DONE_ITEMS.size() <= 2) {
            for (String id : DONE_ITEMS) {
                toast(client, Lang.t("toast.item.title"), Lang.t("toast.item.text", UiText.itemName(id), projectOfItems));
            }
            play(client, false);
        } else {
            toast(client, Lang.t("toast.item.title"), Lang.t("toast.items.text", DONE_ITEMS.size(), projectOfItems));
            play(client, false);
        }
        DONE_ITEMS.clear();
        completedProject = null;
    }

    private static void toast(MinecraftClient client, String title, String text) {
        if (Configs.Generic.NOTIFY_CHAT.getBooleanValue() && client.player != null) {
            client.player.sendMessage(dev.materialshare.McCompat.literal("§a[Litematica MaterialShare]§r §e" + title + "§r " + text), false);
        }
        SystemToast.add(client.getToastManager(), SystemToast.Type.NARRATOR_TOGGLE, dev.materialshare.McCompat.literal(title),
                dev.materialshare.McCompat.literal(text));
    }

    private static void play(MinecraftClient client, boolean big) {
        client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance
                .master(big ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
                        1.0F));
    }
}
