package dev.materialshare.client;

import dev.materialshare.common.Breakdown;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeType;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/** Gives {@link Breakdown} the crafting recipes of the game the player is in. */
final class RecipeAdapter {
    private RecipeAdapter() {
    }

    static int stackSize(String itemId) {
        Identifier id = Identifier.tryParse(itemId);
        return id == null || !Registries.ITEM.containsId(id) ? 64 : Registries.ITEM.get(id).getMaxCount();
    }

    /**
     * {@code preferred} are the items the project needs: where a recipe accepts several items (any planks), one that
     * is in the project is taken, so the result stays in the kinds the build uses.
     */
    static Breakdown.Recipes recipes(Set<String> preferred) {
        MinecraftClient client = MinecraftClient.getInstance();
        Map<String, List<Breakdown.Recipe>> byOutput = new HashMap<>();
        if (client.world != null) {
            var manager = client.world.getRecipeManager();
            for (CraftingRecipe recipe : manager.listAllOfType(RecipeType.CRAFTING)) {
                ItemStack out = recipe.getOutput(client.world.getRegistryManager());
                if (out.isEmpty()) {
                    continue;
                }
                Map<String, Integer> ingredients = new LinkedHashMap<>();
                boolean usable = true;
                for (Ingredient ingredient : recipe.getIngredients()) {
                    if (ingredient.isEmpty()) {
                        continue;
                    }
                    String chosen = null;
                    for (ItemStack stack : ingredient.getMatchingStacks()) {
                        String id = Registries.ITEM.getId(stack.getItem()).toString();
                        if (chosen == null) {
                            chosen = id;
                        }
                        if (preferred.contains(id)) {
                            chosen = id;
                            break;
                        }
                    }
                    if (chosen == null) {
                        usable = false;
                        break;
                    }
                    ingredients.merge(chosen, 1, Integer::sum);
                }
                if (usable && !ingredients.isEmpty()) {
                    byOutput.computeIfAbsent(Registries.ITEM.getId(out.getItem()).toString(), k -> new ArrayList<>())
                            .add(new Breakdown.Recipe(out.getCount(), ingredients));
                }
            }
        }
        return id -> byOutput.getOrDefault(id, List.of());
    }
}
