package dev.materialshare.common;

import java.util.UUID;
import java.util.function.Function;

/** One line of text per history event. */
public final class HistoryText {
    private HistoryText() {
    }

    /**
     * Whether an event fits a search: every word of the query (separated by spaces) must appear, in any order, in
     * what the event says (the sentence with the item, the action and the players) or in the names of the item and of
     * the players. Capitals, full-width letters and the like do not matter; an empty query fits everything.
     */
    public static boolean matches(History.Event e, String query, Function<UUID, String> player, Function<String, String> item,
            Translator tr) {
        String[] words = fold(query).split("\\s+");
        StringBuilder hay = new StringBuilder(describe(e, player, item, tr));
        if (e.actor() != null) {
            hay.append(' ').append(player.apply(e.actor()));
        }
        if (e.target() != null) {
            hay.append(' ').append(player.apply(e.target()));
        }
        if (e.itemId() != null && !e.itemId().isEmpty()) {
            hay.append(' ').append(item.apply(e.itemId())).append(' ').append(e.itemId());
        }
        String text = fold(hay.toString());
        for (String word : words) {
            if (!word.isEmpty() && !text.contains(word)) {
                return false;
            }
        }
        return true;
    }

    private static String fold(String text) {
        return java.text.Normalizer.normalize(text == null ? "" : text, java.text.Normalizer.Form.NFKC)
                .toLowerCase(java.util.Locale.ROOT).strip();
    }

    public static String describe(History.Event e, Function<UUID, String> player, Function<String, String> item,
            Translator tr) {
        String actor = e.actor() == null ? tr.tr("litematica_materialshare.history.unknown") : player.apply(e.actor());
        String target = e.target() == null ? "" : player.apply(e.target());
        boolean self = e.target() == null || e.target().equals(e.actor());
        String itemName = e.itemId() == null || e.itemId().isEmpty() ? "" : item.apply(e.itemId());
        return switch (e.kind()) {
            case CLAIM -> self ? tr.tr("litematica_materialshare.history.claim.self", actor, itemName)
                    : tr.tr("litematica_materialshare.history.claim.other", actor, target, itemName);
            case UNCLAIM -> self ? tr.tr("litematica_materialshare.history.unclaim.self", actor, itemName)
                    : tr.tr("litematica_materialshare.history.unclaim.other", actor, target, itemName);
            case COLLECTED -> self ? tr.tr("litematica_materialshare.history.collected.self", actor, itemName, e.value())
                    : tr.tr("litematica_materialshare.history.collected.other", actor, target, itemName, e.value());
            case BULK -> tr.tr("litematica_materialshare.history.bulk", actor, e.value());
            case CREATED -> tr.tr("litematica_materialshare.history.created", actor, e.value());
            case IMPORT -> tr.tr("litematica_materialshare.history.import", actor, e.value());
            case ARCHIVE -> e.value() == 1 ? tr.tr("litematica_materialshare.history.archived", actor)
                    : tr.tr("litematica_materialshare.history.unarchived", actor);
            case RESTORED -> tr.tr("litematica_materialshare.history.restored", actor);
            case DEADLINE -> tr.tr("litematica_materialshare.history.legacy", actor);
            case ITEM_INFO -> tr.tr("litematica_materialshare.history.iteminfo", actor, itemName);
            case TARGET -> e.target() == null ? tr.tr("litematica_materialshare.history.split", actor, itemName, e.value())
                    : tr.tr("litematica_materialshare.history.target", actor, target, itemName, e.value());
            case TRANSFER -> tr.tr("litematica_materialshare.history.transfer", actor, itemName, target);
            case DELIVERED -> itemName.isEmpty() ? tr.tr("litematica_materialshare.history.delivered.bulk", actor, e.value())
                    : tr.tr("litematica_materialshare.history.delivered", actor, itemName, e.value());
            case ITEM_REMOVED -> tr.tr("litematica_materialshare.history.itemremoved", actor, itemName);
            case RENAMED -> tr.tr("litematica_materialshare.history.renamed", actor);
            case CLONED -> tr.tr("litematica_materialshare.history.cloned", actor);
            case UPDATED -> tr.tr("litematica_materialshare.history.updated", actor, e.value());
            case SURPLUS -> e.value() > 0 ? tr.tr("litematica_materialshare.history.surplus", actor, itemName, e.value())
                    : tr.tr("litematica_materialshare.history.surplus.off", actor, itemName);
            case RESET_CLAIMS -> e.target() != null ? tr.tr("litematica_materialshare.history.reset.player", actor, target, e.value())
                    : itemName.isEmpty() ? tr.tr("litematica_materialshare.history.reset", actor, e.value())
                    : tr.tr("litematica_materialshare.history.reset.item", actor, itemName);
            case ITEM_RESTORED -> tr.tr("litematica_materialshare.history.itemrestored", actor, itemName);
            case SURPLUS_TAKEN -> tr.tr("litematica_materialshare.history.surplus.taken", actor, itemName, e.value(), target);
            case NOTE -> tr.tr("litematica_materialshare.history.note", actor);
        };
    }
}
