package net.craftenergy.content.item;

import net.minecraft.world.item.ItemStack;

/**
 * Item que guarda energia (baterias, cristais, ferramentas elétricas).
 *
 * <p>A energia fica no componente {@code craftenergy:stored_energy}, em CW·tick. Máquinas só
 * carregam ou descarregam itens de tensão igual ou menor que a sua (como os tiers do IC2);
 * use {@link EnergyItems} para mover energia respeitando essas regras.
 */
public interface EnergyItem {
    /** Capacidade em CW·tick. */
    long energyCapacity(ItemStack stack);

    /** Quanto o item aceita ou entrega por tick, em CW. */
    long transferLimit(ItemStack stack);

    /** Nível de tensão do item, em MV. */
    int voltage(ItemStack stack);

    default boolean canCharge(ItemStack stack) {
        return true;
    }

    default boolean canDischarge(ItemStack stack) {
        return true;
    }

    /** Itens descartáveis somem quando esvaziam. */
    default boolean consumedWhenEmpty(ItemStack stack) {
        return false;
    }

    /** Energia de um item recém-criado (descartáveis já vêm cheios). */
    default long initialEnergy(ItemStack stack) {
        return 0;
    }
}
