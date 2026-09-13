package net.ic2reborn.item;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** Quebra de bloco pelo laser: TNT é acesa; no modo super aquecido os drops saem derretidos e a madeira queima. */
final class LaserDrops {
    private LaserDrops() {}

    static void breakBlock(ServerLevel level, BlockPos pos, BlockState state, LivingEntity owner, boolean smelt) {
        if (state.getBlock() instanceof TntBlock) {
            TntBlock.prime(level, pos);
            level.removeBlock(pos, false);
            return;
        }
        if (!smelt) {
            level.destroyBlock(pos, true, owner);
            return;
        }
        List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), owner, ItemStack.EMPTY);
        level.destroyBlock(pos, false, owner);
        if (state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS)) return;
        for (ItemStack drop : drops) {
            SingleRecipeInput input = new SingleRecipeInput(drop);
            ItemStack result = level.recipeAccess().getRecipeFor(RecipeType.SMELTING, input, level)
                    .map(holder -> holder.value().assemble(input))
                    .orElse(drop);
            if (result != drop && !result.isEmpty()) result.setCount(result.getCount() * drop.getCount());
            if (!result.isEmpty()) Block.popResource(level, pos, result);
        }
    }
}
