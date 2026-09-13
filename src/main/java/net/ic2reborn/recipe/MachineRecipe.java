package net.ic2reborn.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

/**
 * Receita de máquina em {@code data/<namespace>/machine_recipe/*.json}:
 * <pre>{@code
 * { "machine": "macerator", "input": "#c:ores/iron", "result": "ic2reborn:crushed_iron", "result_count": 2 }
 * }</pre>
 * {@code input} é um item ({@code "minecraft:cobblestone"}) ou uma tag ({@code "#c:ores/iron"}).
 */
public record MachineRecipe(String machine, String input, int inputCount, Identifier result, int resultCount) {
    public static final Codec<MachineRecipe> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("machine").forGetter(MachineRecipe::machine),
            Codec.STRING.fieldOf("input").forGetter(MachineRecipe::input),
            Codec.INT.optionalFieldOf("input_count", 1).forGetter(MachineRecipe::inputCount),
            Identifier.CODEC.fieldOf("result").forGetter(MachineRecipe::result),
            Codec.INT.optionalFieldOf("result_count", 1).forGetter(MachineRecipe::resultCount)
    ).apply(instance, MachineRecipe::new));
}
