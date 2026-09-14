package net.ic2reborn.block;

import net.ic2reborn.entity.ItntEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** TNT industrial do IC2: acende com redstone, isqueiro ou outra explosão, como a TNT comum, mas explode mais forte. */
public class ItntBlock extends Block {
    public ItntBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(state.getBlock()) && level.hasNeighborSignal(pos)) prime(level, pos, 60);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, @Nullable Orientation orientation, boolean movedByPiston) {
        if (level.hasNeighborSignal(pos)) prime(level, pos, 60);
    }

    @Override
    public void wasExploded(ServerLevel level, BlockPos pos, Explosion explosion) {
        ItntEntity entity = new ItntEntity(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        entity.setCountdown(level.getRandom().nextInt(15) + 10);
        level.addFreshEntity(entity);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(Items.FLINT_AND_STEEL) && !stack.is(Items.FIRE_CHARGE)) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        prime(level, pos, 60);
        if (!player.getAbilities().instabuild) {
            if (stack.is(Items.FLINT_AND_STEEL)) {
                stack.hurtAndBreak(1, player, hand);
            } else {
                stack.shrink(1);
            }
        }
        return InteractionResult.SUCCESS;
    }

    private static void prime(Level level, BlockPos pos, int fuse) {
        if (level instanceof ServerLevel server) {
            ItntEntity entity = new ItntEntity(server, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            entity.setCountdown(fuse);
            server.addFreshEntity(entity);
            server.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        level.removeBlock(pos, false);
    }
}
