package net.ic2reborn.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * Rotor dos geradores cinéticos eólico e de água (IC2: {@code ItemWindRotor}).
 * A durabilidade gasta com o tempo girando; ventos acima do máximo gastam 4× mais.
 */
public class RotorItem extends Item {
    private final int diameter;
    private final float efficiency;
    private final int minWind;
    private final int maxWind;
    private final boolean water;

    /**
     * @param diameter   diâmetro em blocos (precisa de espaço livre na frente)
     * @param durability desgaste máximo
     * @param efficiency fração do vento/água convertida em KU
     * @param water      serve no gerador de água (o de madeira não serve)
     */
    public RotorItem(Properties properties, int diameter, int durability, float efficiency, int minWind, int maxWind, boolean water) {
        super(properties.durability(durability));
        this.diameter = diameter;
        this.efficiency = efficiency;
        this.minWind = minWind;
        this.maxWind = maxWind;
        this.water = water;
    }

    public int diameter() {
        return this.diameter;
    }

    public float efficiency() {
        return this.efficiency;
    }

    public int minWind() {
        return this.minWind;
    }

    public int maxWind() {
        return this.maxWind;
    }

    public boolean acceptsWater() {
        return this.water;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatableWithFallback("item.ic2reborn.rotor.wind", "Wind: %s–%s",
                this.minWind, this.maxWind).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatableWithFallback("item.ic2reborn.rotor.stats", "Diameter %s · efficiency %s%%",
                this.diameter, Math.round(this.efficiency * 100)).withStyle(ChatFormatting.DARK_GRAY));
        if (!this.water) {
            tooltip.accept(Component.translatableWithFallback("item.ic2reborn.rotor.wind_only", "Wind only")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
