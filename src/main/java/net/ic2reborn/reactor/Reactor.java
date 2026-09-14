package net.ic2reborn.reactor;

import net.minecraft.world.item.ItemStack;

/** O que os componentes do reator enxergam do reator nuclear (IC2: {@code IReactor}). */
public interface Reactor {
    /** Componente na grade (x = coluna, y = linha); vazio fora do tamanho atual. */
    ItemStack getItemAt(int x, int y);

    void setItemAt(int x, int y, ItemStack stack);

    int getHeat();

    void setHeat(int heat);

    void addHeat(int amount);

    int getMaxHeat();

    void setMaxHeat(int maxHeat);

    /** Multiplicador dos efeitos do calor (placas de contenção reduzem). */
    float getHeatEffectModifier();

    void setHeatEffectModifier(float modifier);

    /** Soma à produção do ciclo (1 = 5 EU/t no IC2). */
    void addOutput(float output);

    /** Calor que ventoinhas tiraram; no modo fluido vira refrigerante quente. */
    void addEmitHeat(int heat);

    /** Com sinal de redstone o combustível trabalha. */
    boolean produceEnergy();

    boolean isFluidCooled();
}
