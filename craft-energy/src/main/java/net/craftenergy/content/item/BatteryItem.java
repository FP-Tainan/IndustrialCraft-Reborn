package net.craftenergy.content.item;

import net.craftenergy.api.EnergyUnits;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/** Bateria genérica: guarda energia, mostra a carga na barra do item e no tooltip. */
public class BatteryItem extends Item implements EnergyItem {
    private static final int BAR_COLOR = 0x4FA8FF;

    private final long capacity;
    private final long transferLimit;
    private final int voltage;
    private final boolean rechargeable;

    /**
     * @param capacity      energia em CW·tick
     * @param transferLimit CW por tick
     * @param voltage       nível de tensão em MV
     * @param rechargeable  {@code false} para baterias descartáveis (vêm cheias e somem ao esvaziar)
     */
    public BatteryItem(Properties properties, long capacity, long transferLimit, int voltage, boolean rechargeable) {
        super(properties.stacksTo(1));
        this.capacity = capacity;
        this.transferLimit = transferLimit;
        this.voltage = voltage;
        this.rechargeable = rechargeable;
    }

    /** Uma pilha desta bateria totalmente carregada (aba criativa, testes). */
    public ItemStack charged() {
        ItemStack stack = new ItemStack(this);
        EnergyItems.setStored(stack, this.capacity);
        return stack;
    }

    public boolean isRechargeable() {
        return this.rechargeable;
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
    public boolean canCharge(ItemStack stack) {
        return this.rechargeable;
    }

    @Override
    public boolean consumedWhenEmpty(ItemStack stack) {
        return !this.rechargeable;
    }

    @Override
    public long initialEnergy(ItemStack stack) {
        return this.rechargeable ? 0 : this.capacity;
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
