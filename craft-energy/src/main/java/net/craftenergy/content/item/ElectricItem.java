package net.craftenergy.content.item;

import net.craftenergy.api.EnergyUnits;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * Ferramenta elétrica: guarda energia como uma bateria, mas só recebe carga (não alimenta
 * máquinas). Mostra a carga na barra do item e no tooltip.
 */
public class ElectricItem extends Item implements EnergyItem {
    private static final int BAR_COLOR = 0x4FA8FF;

    private final long capacity;
    private final long transferLimit;
    private final int voltage;

    /**
     * @param capacity      energia em CW·tick
     * @param transferLimit CW por tick ao carregar
     * @param voltage       nível de tensão em MV
     */
    public ElectricItem(Properties properties, long capacity, long transferLimit, int voltage) {
        super(properties.stacksTo(1));
        this.capacity = capacity;
        this.transferLimit = transferLimit;
        this.voltage = voltage;
    }

    /** Uma pilha deste item totalmente carregada (aba criativa, testes). */
    public ItemStack charged() {
        ItemStack stack = new ItemStack(this);
        EnergyItems.setStored(stack, this.capacity);
        return stack;
    }

    @Override
    public long energyCapacity(ItemStack stack) {
        return this.capacity;
    }

    @Override
    public long transferLimit(ItemStack stack) {
        return this.transferLimit;
    }

    @Override
    public int voltage(ItemStack stack) {
        return this.voltage;
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
        return Math.round(13.0F * EnergyItems.getStored(stack) / this.capacity);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BAR_COLOR;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.literal(EnergyUnits.formatEnergy(EnergyItems.getStored(stack))
                + " / " + EnergyUnits.formatEnergy(this.capacity)).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.literal(EnergyUnits.formatVoltage(this.voltage)
                + " · " + EnergyUnits.formatPower(this.transferLimit)).withStyle(ChatFormatting.DARK_GRAY));
    }
}
