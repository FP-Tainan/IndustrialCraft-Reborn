package net.ic2reborn.recipe;

import net.ic2reborn.IC2Reborn;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * Custo em UU-matter de cada item (IC2: {@code UuGraph}/{@code UuIndex}). Na unidade do IC2, 1 = um
 * pedregulho = 0,01 mB. Parte de valores base de matérias-primas e desce pelas receitas de
 * fabricação, fornalha e cortador de pedras: o custo de um item é o menor custo das receitas que o
 * produzem. Itens sem caminho até um valor base não podem ser replicados.
 */
public final class UuValues {
    /** mB de UU-matter por unidade. */
    public static final double MB_PER_UNIT = 0.01;

    private static final Map<Class<?>, Field> RESULT_FIELDS = new HashMap<>();
    private static RecipeManager cachedFor;
    private static Map<Item, Double> values = Map.of();

    private UuValues() {}

    /** Custo do item em unidades (1 = pedregulho), se ele puder ser replicado. */
    public static synchronized OptionalDouble cost(MinecraftServer server, Item item) {
        RecipeManager manager = server.getRecipeManager();
        if (manager != cachedFor) {
            values = compute(manager);
            cachedFor = manager;
        }
        Double value = values.get(item);
        return value == null ? OptionalDouble.empty() : OptionalDouble.of(value);
    }

    private static Map<Item, Double> compute(RecipeManager manager) {
        Map<Item, Double> result = new HashMap<>();
        base(result);

        List<RecipeHolder<?>> recipes = new ArrayList<>(manager.getRecipes());
        for (int pass = 0; pass < 16; pass++) {
            boolean changed = false;
            for (RecipeHolder<?> holder : recipes) {
                Recipe<?> recipe = holder.value();
                ItemStackTemplate output = resultOf(recipe);
                if (output == null || output.count() <= 0) continue;
                List<Ingredient> ingredients;
                try {
                    ingredients = recipe.placementInfo().ingredients();
                } catch (RuntimeException e) {
                    continue;
                }
                if (ingredients.isEmpty()) continue;
                double sum = 0;
                boolean valid = true;
                for (Ingredient ingredient : ingredients) {
                    double cheapest = ingredient.items()
                            .map(Holder::value)
                            .map(result::get)
                            .filter(value -> value != null)
                            .mapToDouble(Double::doubleValue)
                            .min().orElse(Double.NaN);
                    if (Double.isNaN(cheapest)) {
                        valid = false;
                        break;
                    }
                    sum += cheapest;
                }
                if (!valid) continue;
                Item item = output.typeHolder().value();
                double cost = sum / output.count();
                Double current = result.get(item);
                if (current == null || cost < current - 1.0E-9) {
                    result.put(item, cost);
                    changed = true;
                }
            }
            if (!changed) break;
        }
        IC2Reborn.LOGGER.info("UU-matter: {} itens com custo conhecido", result.size());
        return Map.copyOf(result);
    }

    /** Resultado da receita: o primeiro campo {@link ItemStackTemplate} da classe (ou das superclasses). */
    private static ItemStackTemplate resultOf(Recipe<?> recipe) {
        Field field = RESULT_FIELDS.computeIfAbsent(recipe.getClass(), type -> {
            for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
                for (Field candidate : current.getDeclaredFields()) {
                    if (candidate.getType() == ItemStackTemplate.class) {
                        try {
                            candidate.setAccessible(true);
                            return candidate;
                        } catch (RuntimeException e) {
                            return null;
                        }
                    }
                }
            }
            return null;
        });
        if (field == null) return null;
        try {
            return (ItemStackTemplate) field.get(recipe);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    // ── valores base (pedregulho = 1) ─────────────────────────────────────
    private static void base(Map<Item, Double> map) {
        put(map, 1, "cobblestone", "cobbled_deepslate", "dirt", "sand", "red_sand", "gravel", "netherrack", "snowball",
                "andesite", "diorite", "granite", "tuff", "basalt", "blackstone", "stone", "deepslate", "leaf_litter");
        put(map, 2, "grass_block", "clay_ball", "end_stone", "soul_sand", "soul_soil", "ice", "calcite", "bamboo", "kelp",
                "vine", "moss_block", "melon_slice", "wheat_seeds", "pointed_dripstone", "mud", "mycelium", "podzol");
        put(map, 4, "sugar_cane", "cactus", "wheat", "carrot", "potato", "beetroot", "egg", "feather", "rotten_flesh", "flint",
                "red_mushroom", "brown_mushroom", "lily_pad", "sweet_berries", "glow_berries", "seagrass", "dandelion", "poppy");
        put(map, 8, "pumpkin", "apple", "string", "bone", "cocoa_beans", "rabbit_hide", "crimson_stem", "warped_stem",
                "crimson_fungus", "warped_fungus", "honeycomb", "chorus_fruit", "twisting_vines", "weeping_vines");
        put(map, 16, "leather", "spider_eye", "ink_sac", "nether_wart", "turtle_scute", "armadillo_scute", "phantom_membrane");
        put(map, 32, "coal", "gunpowder", "slime_ball", "prismarine_shard", "prismarine_crystals", "glow_ink_sac", "magma_cream");
        put(map, 64, "raw_copper", "redstone", "quartz", "glowstone_dust", "obsidian", "amethyst_shard", "ic2reborn:raw_tin",
                "ic2reborn:resin", "ic2reborn:sticky_resin");
        put(map, 128, "raw_iron", "lapis_lazuli", "crying_obsidian", "ic2reborn:raw_lead", "ic2reborn:raw_silver");
        put(map, 256, "ender_pearl", "blaze_rod", "breeze_rod");
        put(map, 512, "raw_gold", "ghast_tear", "nautilus_shell", "ic2reborn:raw_uranium");
        put(map, 1_024, "shulker_shell", "heart_of_the_sea");
        put(map, 1_333, "ic2reborn:iridium_shard");
        put(map, 2_048, "echo_shard");
        put(map, 4_096, "diamond", "emerald");
        put(map, 12_000, "ic2reborn:iridium_ore");
        put(map, 16_384, "netherite_scrap", "nether_star");
        tag(map, 8, ItemTags.LOGS);
        tag(map, 4, ItemTags.SAPLINGS);
        tag(map, 1, ItemTags.LEAVES);
        tag(map, 4, ItemTags.WOOL);
    }

    private static void put(Map<Item, Double> map, double value, String... ids) {
        for (String id : ids) {
            Identifier identifier = id.contains(":") ? Identifier.parse(id) : Identifier.withDefaultNamespace(id);
            BuiltInRegistries.ITEM.getOptional(identifier).ifPresent(item -> map.merge(item, value, Math::min));
        }
    }

    private static void tag(Map<Item, Double> map, double value, TagKey<Item> tag) {
        for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
            map.merge(holder.value(), value, Math::min);
        }
    }
}
