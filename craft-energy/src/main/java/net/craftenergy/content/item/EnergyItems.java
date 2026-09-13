package net.craftenergy.content.item;

import net.craftenergy.content.CEComponents;
import net.minecraft.world.item.ItemStack;

/** Regras para mover energia entre máquinas e {@link EnergyItem}s. */
public final class EnergyItems {
    private EnergyItems() {}

    public static boolean isEnergyItem(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof EnergyItem;
    }

    /** Energia guardada, em CW·tick. */
    public static long getStored(ItemStack stack) {
        if (!(stack.getItem() instanceof EnergyItem item)) return 0;
        return stack.getOrDefault(CEComponents.STORED_ENERGY.get(), item.initialEnergy(stack));
    }

    public static void setStored(ItemStack stack, long energy) {
        if (!(stack.getItem() instanceof EnergyItem item)) return;
        stack.set(CEComponents.STORED_ENERGY.get(), Math.max(0, Math.min(item.energyCapacity(stack), energy)));
    }

    /**
     * Carrega o item a partir de uma máquina de tensão {@code machineVoltage}.
     *
     * @return quanto entrou no item, em CW·tick (limitado pela transferência e pelo espaço)
     */
    public static long charge(ItemStack stack, long amount, int machineVoltage, boolean simulate) {
        if (amount <= 0 || !(stack.getItem() instanceof EnergyItem item) || !item.canCharge(stack)) return 0;
        if (item.voltage(stack) > machineVoltage) return 0;

        long stored = getStored(stack);
        long moved = Math.max(0, Math.min(amount, Math.min(item.transferLimit(stack), item.energyCapacity(stack) - stored)));
        if (!simulate && moved > 0) setStored(stack, stored + moved);
        return moved;
    }

    /**
     * Descarrega o item para uma máquina de tensão {@code machineVoltage}. Itens descartáveis
     * que esvaziam somem (a pilha é reduzida).
     *
     * @return quanto saiu do item, em CW·tick
     */
    public static long discharge(ItemStack stack, long amount, int machineVoltage, boolean simulate) {
        if (amount <= 0 || !(stack.getItem() instanceof EnergyItem item) || !item.canDischarge(stack)) return 0;
        if (item.voltage(stack) > machineVoltage) return 0;

        long stored = getStored(stack);
        long moved = Math.max(0, Math.min(amount, Math.min(item.transferLimit(stack), stored)));
        if (!simulate && moved > 0) {
            if (stored - moved <= 0 && item.consumedWhenEmpty(stack)) {
                stack.shrink(1);
            } else {
                setStored(stack, stored - moved);
            }
        }
        return moved;
    }
}
