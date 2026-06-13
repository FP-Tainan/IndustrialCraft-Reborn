package net.ic2reborn.item;

import net.ic2reborn.block.RubberWoodBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class TreetapItem extends Item {
    public TreetapItem(Properties props) { super(props); }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Player player = ctx.getPlayer();

        if (!(state.getBlock() instanceof RubberWoodBlock rubberLog)) return InteractionResult.PASS;
        if (!rubberLog.hasResin(state)) return InteractionResult.PASS;

        if (!level.isClientSide()) {
            level.playSound(null, pos, SoundEvents.BAMBOO_STEP, SoundSource.BLOCKS, 1.0f, 0.8f);
            int amount = level.getRandom().nextFloat() < 0.25f ? 2 : 1;
            for (int i = 0; i < amount; i++) {
                level.addFreshEntity(new ItemEntity(level,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        new ItemStack(Items.RESIN_CLUMP)));
            }
            level.setBlock(pos, state.setValue(RubberWoodBlock.WOOD_TYPE, RubberWoodBlock.WoodType.TAPPED), 3);
            if (player != null) ctx.getItemInHand().hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        }
        return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
    }
}
