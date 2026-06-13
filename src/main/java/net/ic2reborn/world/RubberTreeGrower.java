package net.ic2reborn.world;

import net.minecraft.world.level.block.grower.TreeGrower;

/**
 * Placeholder — a árvore customizada é gerada via RubberSaplingBlock.growTree()
 * sem necessidade de ResourceLocation (que mudou de pacote no MC 1.21.5).
 */
public class RubberTreeGrower {
    public static final TreeGrower INSTANCE = TreeGrower.OAK;
}
