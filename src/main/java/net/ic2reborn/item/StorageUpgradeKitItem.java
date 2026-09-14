package net.ic2reborn.item;

import net.ic2reborn.block.MachineBlock;
import net.ic2reborn.block.entity.MachineBlockEntity;
import net.ic2reborn.registry.IC2AutoBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Kit de melhoria da MFSU do IC2: clicado numa MFE, transforma em MFSU mantendo a energia e a direção. */
public class StorageUpgradeKitItem extends Item {
    public StorageUpgradeKitItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (!state.is(IC2AutoBlocks.MFE.get()) || !(level.getBlockEntity(pos) instanceof MachineBlockEntity mfe)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        long energy = mfe.getStoredEnergy();
        BlockState upgraded = IC2AutoBlocks.MFSU.get().defaultBlockState().setValue(MachineBlock.FACING, state.getValue(MachineBlock.FACING));
        level.removeBlockEntity(pos);
        level.setBlock(pos, upgraded, Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof MachineBlockEntity mfsu) mfsu.setStoredEnergy(energy);
        if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) context.getItemInHand().shrink(1);
        return InteractionResult.SUCCESS;
    }
}
