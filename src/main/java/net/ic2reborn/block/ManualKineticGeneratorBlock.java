package net.ic2reborn.block;

import net.ic2reborn.block.entity.MachineBlockEntity;
import net.ic2reborn.item.WrenchItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Gerador cinético manual do IC2: sem GUI, cada clique gira a manivela. */
public class ManualKineticGeneratorBlock extends MachineBlock {
    public ManualKineticGeneratorBlock(Properties props) {
        super(props);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof MachineBlockEntity machine) {
            machine.crank(player);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof WrenchItem) {
            return InteractionResult.PASS;
        }
        return useWithoutItem(state, level, pos, player, hit);
    }
}
