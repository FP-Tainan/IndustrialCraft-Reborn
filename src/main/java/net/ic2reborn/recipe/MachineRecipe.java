package net.ic2reborn.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

/**
 * Receita de máquina em {@code data/<namespace>/machine_recipe/*.json}:
 * <pre>{@code
 * { "machine": "macerator", "input": "#c:ores/iron", "result": "ic2reborn:crushed_iron", "result_count": 2 }
 * }</pre>
 * {@code input} é um item ({@code "minecraft:cobblestone"}) ou uma tag ({@code "#c:ores/iron"}).
 *
 * <p>Campos opcionais para máquinas maiores:
 * <ul>
 *   <li>{@code secondary_input}/{@code secondary_count}: segundo slot de entrada (latas do enlatador);</li>
 *   <li>{@code results}: lista de {@code {"item", "count"}} para várias saídas (lavadora de minério);</li>
 *   <li>{@code fluid}/{@code fluid_amount}: fluido (e mB) gastos do tanque por operação — sem
 *   {@code fluid}, vale o que estiver no tanque;</li>
 *   <li>{@code fluid_result}: {@code {"fluid", "amount"}} produzido no tanque de saída (enlatadora).</li>
 * </ul>
 */
public record MachineRecipe(String machine, String input, int inputCount, Optional<String> secondaryInput,
                            int secondaryCount, Optional<Identifier> result, int resultCount, List<Output> results,
                            Optional<Identifier> fluid, int fluidAmount, Optional<FluidOutput> fluidResult) {
    public record Output(Identifier item, int count) {
        public static final Codec<Output> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("item").forGetter(Output::item),
                Codec.INT.optionalFieldOf("count", 1).forGetter(Output::count)
        ).apply(instance, Output::new));
    }

    public record FluidOutput(Identifier fluid, int amount) {
        public static final Codec<FluidOutput> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Identifier.CODEC.fieldOf("fluid").forGetter(FluidOutput::fluid),
                Codec.INT.optionalFieldOf("amount", 1000).forGetter(FluidOutput::amount)
        ).apply(instance, FluidOutput::new));
    }

    public static final Codec<MachineRecipe> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("machine").forGetter(MachineRecipe::machine),
            Codec.STRING.fieldOf("input").forGetter(MachineRecipe::input),
            Codec.INT.optionalFieldOf("input_count", 1).forGetter(MachineRecipe::inputCount),
            Codec.STRING.optionalFieldOf("secondary_input").forGetter(MachineRecipe::secondaryInput),
            Codec.INT.optionalFieldOf("secondary_count", 1).forGetter(MachineRecipe::secondaryCount),
            Identifier.CODEC.optionalFieldOf("result").forGetter(MachineRecipe::result),
            Codec.INT.optionalFieldOf("result_count", 1).forGetter(MachineRecipe::resultCount),
            Output.CODEC.listOf().optionalFieldOf("results", List.of()).forGetter(MachineRecipe::results),
            Identifier.CODEC.optionalFieldOf("fluid").forGetter(MachineRecipe::fluid),
            Codec.INT.optionalFieldOf("fluid_amount", 0).forGetter(MachineRecipe::fluidAmount),
            FluidOutput.CODEC.optionalFieldOf("fluid_result").forGetter(MachineRecipe::fluidResult)
    ).apply(instance, MachineRecipe::new));

    /** Todas as saídas: {@code result} (se houver) seguido de {@code results}. */
    public List<Output> allOutputs() {
        List<Output> outputs = new java.util.ArrayList<>();
        this.result.ifPresent(item -> outputs.add(new Output(item, this.resultCount)));
        outputs.addAll(this.results);
        return outputs;
    }
}
