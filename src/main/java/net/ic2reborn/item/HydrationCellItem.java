package net.ic2reborn.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Célula de hidratação do IC2: 10.000 mB de água gastos aos poucos (a durabilidade é a água
 * que resta). Usada na plantação à mão ou no slot de água do Cropmatron.
 */
public class HydrationCellItem extends Item {
    public static final int CHARGES = 10_000;

    public HydrationCellItem(Properties properties) {
        super(properties.durability(CHARGES));
    }

    /** Tira até {@code wanted} mB de água; a célula some quando acaba. Retorna quanto saiu. */
    public static int drain(ItemStack stack, int wanted) {
        if (!(stack.getItem() instanceof HydrationCellItem) || wanted <= 0) return 0;
        int taken = Math.min(wanted, stack.getMaxDamage() - stack.getDamageValue());
        if (taken <= 0) return 0;
        if (stack.getDamageValue() + taken >= stack.getMaxDamage()) {
            stack.shrink(1);
        } else {
            stack.setDamageValue(stack.getDamageValue() + taken);
        }
        return taken;
    }
}
