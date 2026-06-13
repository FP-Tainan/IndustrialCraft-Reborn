package net.ic2reborn.block;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;

import java.util.List;

/**
 * Wrapper para garantir que o tronco de borracha dropa a si mesmo.
 */
public class RubberLogDropBlock extends RubberWoodBlock {

    public RubberLogDropBlock(Properties props) {
        super(props);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return List.of(new ItemStack(this.asItem(), 1));
    }
}
