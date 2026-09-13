package net.ic2reborn.recipe;

import net.ic2reborn.IC2Reborn;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/** Carrega as receitas das máquinas dos datapacks ({@code machine_recipe/}) a cada reload. */
public final class MachineRecipes extends SimpleJsonResourceReloadListener<MachineRecipe> {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(IC2Reborn.MODID, "machine_recipes");
    public static final MachineRecipes INSTANCE = new MachineRecipes();

    private volatile Map<String, List<Compiled>> byMachine = Map.of();

    private MachineRecipes() {
        super(MachineRecipe.CODEC, FileToIdConverter.json("machine_recipe"));
    }

    /** Primeira receita da máquina que aceita {@code input} (incluindo a quantidade). */
    public Optional<Compiled> find(String machine, ItemStack input) {
        if (input.isEmpty()) return Optional.empty();
        for (Compiled recipe : this.byMachine.getOrDefault(machine, List.of())) {
            if (input.getCount() >= recipe.inputCount() && recipe.input().test(input)) return Optional.of(recipe);
        }
        return Optional.empty();
    }

    @Override
    protected void apply(Map<Identifier, MachineRecipe> recipes, ResourceManager manager, ProfilerFiller profiler) {
        Map<String, List<Compiled>> grouped = new HashMap<>();
        recipes.forEach((id, recipe) -> {
            Optional<Item> result = BuiltInRegistries.ITEM.getOptional(recipe.result());
            if (result.isEmpty()) {
                IC2Reborn.LOGGER.warn("Receita de máquina {} ignorada: item {} não existe", id, recipe.result());
                return;
            }
            Optional<Predicate<ItemStack>> input = parseInput(recipe.input());
            if (input.isEmpty()) {
                IC2Reborn.LOGGER.warn("Receita de máquina {} ignorada: entrada {} não existe", id, recipe.input());
                return;
            }
            grouped.computeIfAbsent(recipe.machine(), key -> new ArrayList<>())
                    .add(new Compiled(input.get(), recipe.inputCount(), result.get(), recipe.resultCount()));
        });
        grouped.replaceAll((machine, list) -> List.copyOf(list));
        this.byMachine = Map.copyOf(grouped);
        IC2Reborn.LOGGER.info("IC2 Reborn: {} receitas de máquina carregadas", recipes.size());
    }

    private static Optional<Predicate<ItemStack>> parseInput(String input) {
        if (input.startsWith("#")) {
            TagKey<Item> tag = TagKey.create(Registries.ITEM, Identifier.parse(input.substring(1)));
            return Optional.of(stack -> BuiltInRegistries.ITEM.wrapAsHolder(stack.getItem()).is(tag));
        }
        return BuiltInRegistries.ITEM.getOptional(Identifier.parse(input))
                .map(item -> stack -> stack.getItem() == item);
    }

    public record Compiled(Predicate<ItemStack> input, int inputCount, Item result, int resultCount) {
        public ItemStack createResult() {
            return new ItemStack(this.result, this.resultCount);
        }
    }
}
