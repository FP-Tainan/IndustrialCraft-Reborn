package net.ic2reborn.block;

import net.ic2reborn.registry.IC2Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Espuma de construção do IC2 ({@code BlockFoam}): dá para atravessar e endurece em parede. Com
 * luz endurece em ~5 min, no escuro até 16× mais devagar; areia na mão endurece na hora.
 */
public class FoamBlock extends Block {
    /** IC2: FoamType.normal.hardenTime (segundos com luz máxima). */
    private static final int HARDEN_TIME = 300;
    /** Velocidade padrão dos ticks aleatórios (a regra randomTickSpeed). */
    private static final float RANDOM_TICK_SPEED = 3.0F;

    public FoamBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int light = level.getMaxLocalRawBrightness(pos);
        for (Direction direction : Direction.values()) {
            light = Math.max(light, level.getMaxLocalRawBrightness(pos.relative(direction)));
        }
        float averageTicks = HARDEN_TIME * (16 - Math.min(15, light)) * 20.0F;
        float chance = 4096.0F / (averageTicks * RANDOM_TICK_SPEED);
        if (random.nextFloat() < chance) harden(level, pos);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(Items.SAND)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!level.isClientSide()) {
            harden(level, pos);
            if (!player.getAbilities().instabuild) stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }

    public static void harden(Level level, BlockPos pos) {
        level.setBlock(pos, IC2Blocks.WALL.get().defaultBlockState(), Block.UPDATE_ALL);
    }
}
