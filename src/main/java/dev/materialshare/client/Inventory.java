package dev.materialshare.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;

/** What the player has: hotbar, inventory, armor and off hand (and the ender chest), counted per item id. */
final class Inventory {
    private Inventory() {
    }

    static Map<String, Integer> counts(ClientPlayerEntity player) {
        Map<String, Integer> counts = new HashMap<>();
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty()) {
                counts.merge(dev.materialshare.McCompat.itemId(stack.getItem()).toString(), stack.getCount(), Integer::sum);
            }
        }
        return counts;
    }

    /** What you carry plus, when the setting is on, what lies in your ender chest. */
    static Map<String, Integer> owned(ClientPlayerEntity player) {
        Map<String, Integer> counts = counts(player);
        ClientProjectCache.ender().forEach((id, n) -> counts.merge(id, n, Integer::sum));
        return counts;
    }

    static String idOf(ItemStack stack) {
        return dev.materialshare.McCompat.itemId(stack.getItem()).toString();
    }
}
