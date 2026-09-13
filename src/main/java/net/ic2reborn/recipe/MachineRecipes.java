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
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

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
        return find(machine, input, ItemStack.EMPTY, null, 0);
    }

    /**
     * Primeira receita que aceita as entradas e o conteúdo do tanque.
     *
     * @param tankFluid fluido no tanque (null se vazio ou sem tanque)
     * @param tankMb    quantidade no tanque, em mB
     */
    public Optional<Compiled> find(String machine, ItemStack input, ItemStack secondary, @Nullable Fluid tankFluid, long tankMb) {
        if (input.isEmpty()) return Optional.empty();
        for (Compiled recipe : this.byMachine.getOrDefault(machine, List.of())) {
            if (input.getCount() < recipe.inputCount() || !recipe.input().test(input)) continue;
            if (recipe.secondary() != null
                    && (secondary.getCount() < recipe.secondaryCount() || !recipe.secondary().test(secondary))) continue;
            if (recipe.fluid() != null && (tankFluid != recipe.fluid() || tankMb < recipe.fluidAmount())) continue;
            return Optional.of(recipe);
        }
        return Optional.empty();
    }

    @Override
    protected void apply(Map<Identifier, MachineRecipe> recipes, ResourceManager manager, ProfilerFiller profiler) {
        Map<String, List<Compiled>> grouped = new HashMap<>();
        int loaded = 0;
        for (Map.Entry<Identifier, MachineRecipe> entry : recipes.entrySet()) {
            Identifier id = entry.getKey();
            MachineRecipe recipe = entry.getValue();

            List<Stack> results = new ArrayList<>();
            boolean missing = false;
            for (MachineRecipe.Output output : recipe.allOutputs()) {
                Optional<Item> item = BuiltInRegistries.ITEM.getOptional(output.item());
                if (item.isEmpty()) {
                    IC2Reborn.LOGGER.warn("Receita de máquina {} ignorada: item {} não existe", id, output.item());
                    missing = true;
                    break;
                }
                results.add(new Stack(item.get(), output.count()));
            }
            if (missing) continue;

            Optional<Predicate<ItemStack>> input = parseInput(recipe.input());
            if (input.isEmpty()) {
                IC2Reborn.LOGGER.warn("Receita de máquina {} ignorada: entrada {} não existe", id, recipe.input());
                continue;
            }
            Predicate<ItemStack> secondary = null;
            if (recipe.secondaryInput().isPresent()) {
                Optional<Predicate<ItemStack>> parsed = parseInput(recipe.secondaryInput().get());
                if (parsed.isEmpty()) {
                    IC2Reborn.LOGGER.warn("Receita de máquina {} ignorada: entrada {} não existe", id, recipe.secondaryInput().get());
                    continue;
                }
                secondary = parsed.get();
            }

            Fluid fluid = null;
            if (recipe.fluid().isPresent()) {
                fluid = BuiltInRegistries.FLUID.getOptional(recipe.fluid().get()).orElse(null);
                if (fluid == null) {
                    IC2Reborn.LOGGER.warn("Receita de máquina {} ignorada: fluido {} não existe", id, recipe.fluid().get());
                    continue;
                }
            }
            Fluid resultFluid = null;
            int resultFluidAmount = 0;
            if (recipe.fluidResult().isPresent()) {
                resultFluid = BuiltInRegistries.FLUID.getOptional(recipe.fluidResult().get().fluid()).orElse(null);
                if (resultFluid == null) {
                    IC2Reborn.LOGGER.warn("Receita de máquina {} ignorada: fluido {} não existe", id, recipe.fluidResult().get().fluid());
                    continue;
                }
                resultFluidAmount = recipe.fluidResult().get().amount();
            }

            grouped.computeIfAbsent(recipe.machine(), key -> new ArrayList<>())
                    .add(new Compiled(input.get(), recipe.inputCount(), secondary, recipe.secondaryCount(),
                            List.copyOf(results), fluid, recipe.fluidAmount(), resultFluid, resultFluidAmount, recipe.minHeat(), recipe.hardness()));
            loaded++;
        }
        grouped.replaceAll((machine, list) -> List.copyOf(list));
        this.byMachine = Map.copyOf(grouped);
        IC2Reborn.LOGGER.info("IC2 Reborn: {} receitas de máquina carregadas", loaded);
    }

    private static Optional<Predicate<ItemStack>> parseInput(String input) {
        if (input.startsWith("#")) {
            TagKey<Item> tag = TagKey.create(Registries.ITEM, Identifier.parse(input.substring(1)));
            return Optional.of(stack -> BuiltInRegistries.ITEM.wrapAsHolder(stack.getItem()).is(tag));
        }
        return BuiltInRegistries.ITEM.getOptional(Identifier.parse(input))
                .map(item -> stack -> stack.getItem() == item);
    }

    /** Item e quantidade; o ItemStack só é criado na hora (no reload os componentes ainda não existem). */
    public record Stack(Item item, int count) {
        public ItemStack create() {
            return new ItemStack(this.item, this.count);
        }
    }

    /**
     * Receita pronta para uso.
     *
     * @param fluid             fluido exigido no tanque (null = o que houver)
     * @param fluidAmount       mB gastos do tanque por operação
     * @param resultFluid       fluido produzido no tanque de saída (null = nenhum)
     * @param resultFluidAmount mB produzidos
     */
    public record Compiled(Predicate<ItemStack> input, int inputCount, @Nullable Predicate<ItemStack> secondary,
                           int secondaryCount, List<Stack> results, @Nullable Fluid fluid, int fluidAmount,
                           @Nullable Fluid resultFluid, int resultFluidAmount, int minHeat, int hardness) {
        /** Cópias novas das saídas. */
        public List<ItemStack> createResults() {
            return this.results.stream().map(Stack::create).toList();
        }

        /** Primeira saída (máquinas de uma saída só). */
        public ItemStack createResult() {
            return this.results.isEmpty() ? ItemStack.EMPTY : this.results.getFirst().create();
        }
    }
}
