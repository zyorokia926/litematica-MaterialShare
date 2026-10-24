package dev.materialshare.client;

import dev.materialshare.common.Entry;
import dev.materialshare.common.UndoStack;
import fi.dy.masa.malilib.gui.Message;
import fi.dy.masa.malilib.util.InfoUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Takes back the player's own recent edits by restoring what each claim looked like before. */
public final class Undo {
    /** The state to go back to: whether {@code target} claimed the item, and with what collected count. */
    record Op(String project, String itemId, UUID target, boolean claimed, int collected) {
    }

    private static final UndoStack<Op> STACK = new UndoStack<>(50, 1500);

    private Undo() {
    }

    static Op before(String project, String itemId, UUID target) {
        Entry entry = ClientProjectCache.exists(project)
                ? ClientProjectCache.projects().stream().filter(p -> p.name().equals(project)).findFirst()
                        .map(p -> p.items().get(itemId)).orElse(null)
                : null;
        boolean claimed = entry != null && entry.claims().containsKey(target);
        return new Op(project, itemId, target, claimed, claimed ? entry.claims().get(target) : 0);
    }

    static void recordClaim(String project, String itemId, UUID target) {
        STACK.push(List.of(before(project, itemId, target)), null, System.currentTimeMillis());
    }

    /** Turning the wheel on one number many times is one step. */
    static void recordCollected(String project, String itemId, UUID target) {
        STACK.push(List.of(before(project, itemId, target)), project + "\n" + itemId + "\n" + target,
                System.currentTimeMillis());
    }

    static void recordMany(List<Op> before) {
        STACK.push(before, null, System.currentTimeMillis());
    }

    public static boolean isEmpty() {
        return STACK.isEmpty();
    }

    public static void clear() {
        STACK.clear();
    }

    public static void undo() {
        var step = STACK.pop();
        if (step.isEmpty()) {
            InfoUtils.showGuiMessage(Message.MessageType.INFO, Lang.t("undo.none"));
            return;
        }
        List<Op> ops = new ArrayList<>(step.get());
        for (Op op : ops) {
            Op now = before(op.project, op.itemId, op.target);
            if (!op.claimed) {
                if (now.claimed) {
                    ClientNet.sendClaimRaw(op.project, op.itemId, op.target, false);
                }
                continue;
            }
            if (!now.claimed) {
                ClientNet.sendClaimRaw(op.project, op.itemId, op.target, true);
            }
            if (now.collected != op.collected || !now.claimed) {
                ClientNet.sendCollectedRaw(op.project, op.itemId, op.target, op.collected);
            }
        }
        InfoUtils.showGuiMessage(Message.MessageType.SUCCESS,
                ops.size() == 1 ? Lang.t("undo.one") : Lang.t("undo.many", ops.size()));
    }
}
