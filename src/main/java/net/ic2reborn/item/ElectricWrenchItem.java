package net.ic2reborn.item;

import net.craftenergy.api.EnergyUnits;
import net.craftenergy.content.item.EnergyItem;
import net.craftenergy.content.item.EnergyItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;

import java.util.function.Consumer;

/**
 * Chave inglesa elétrica do IC2 ({@code ItemToolWrenchElectric}): funciona como a clássica, mas
 * cada ponto de desgaste gasta energia em vez de durabilidade (girar 50 CWh, desmontar 500 CWh).
 */
public class ElectricWrenchItem extends WrenchItem implements EnergyItem {
    /** IC2: 12.000 EU, 250 EU/t, tier 1; 100 EU por ponto de desgaste. */
    private static final long CAPACITY = EnergyUnits.fromCWh(6_000);
    private static final long TRANSFER_LIMIT = 125_000;
    private static final int VOLTAGE = 220;
    private static final long ENERGY_PER_DAMAGE = EnergyUnits.fromCWh(50);
    private static final int BAR_COLOR = 0x4FA8FF;

    public ElectricWrenchItem(Properties properties) {
        super(properties.stacksTo(1), false);
    }

    @Override
    protected boolean canTakeDamage(ItemStack stack, int amount) {
        return EnergyItems.getStored(stack) >= amount * ENERGY_PER_DAMAGE;
    }

    @Override
    protected void applyDamage(ItemStack stack, Player player, UseOnContext context, int amount) {
        EnergyItems.use(stack, amount * ENERGY_PER_DAMAGE);
    }

    @Override
    public long energyCapacity(ItemStack stack) {
        return CAPACITY;
    }

    @Override
    public long transferLimit(ItemStack stack) {
        return TRANSFER_LIMIT;
    }

    @Override
    public int voltage(ItemStack stack) {
        return VOLTAGE;
    }

    @Override
    public boolean canDischarge(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * EnergyItems.getStored(stack) / CAPACITY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BAR_COLOR;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.literal(EnergyUnits.formatEnergy(EnergyItems.getStored(stack))
                + " / " + EnergyUnits.formatEnergy(CAPACITY)).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.literal(EnergyUnits.formatVoltage(VOLTAGE)
                + " · " + EnergyUnits.formatPower(TRANSFER_LIMIT)).withStyle(ChatFormatting.DARK_GRAY));
    }
}
