package net.ic2reborn.item;

import net.craftenergy.api.EnergyUnits;
import net.craftenergy.content.block.RubberWoodBlock;
import net.craftenergy.content.item.ElectricItem;
import net.craftenergy.content.item.EnergyItems;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Torneira elétrica do IC2 ({@code ItemTreetapElectric}): tira resina do tronco de seringueira como
 * a torneira comum, mas gasta energia em vez de quebrar.
 */
public class ElectricTreetapItem extends ElectricItem {
    /** IC2: 10.000 EU, 100 EU/t, nível 1, 50 EU por uso. */
    private static final long CAPACITY = EnergyUnits.fromCWh(5_000);
    public static final long OPERATION_ENERGY = EnergyUnits.fromCWh(25);

    public ElectricTreetapItem(Properties properties) {
        super(properties, CAPACITY, 50_000, 220);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        ItemStack stack = context.getItemInHand();
        if (!(state.getBlock() instanceof RubberWoodBlock log) || !log.hasResin(state)
                || EnergyItems.getStored(stack) < OPERATION_ENERGY) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            level.playSound(null, pos, SoundEvents.BAMBOO_STEP, SoundSource.BLOCKS, 1.0F, 0.8F);
            int amount = level.getRandom().nextFloat() < 0.25F ? 2 : 1;
            Block.popResourceFromFace(level, pos, context.getClickedFace(), new ItemStack(Items.RESIN_CLUMP, amount));
            level.setBlock(pos, state.setValue(RubberWoodBlock.WOOD_TYPE, RubberWoodBlock.WoodType.TAPPED), Block.UPDATE_ALL);
            EnergyItems.use(stack, OPERATION_ENERGY);
        }
        return InteractionResult.SUCCESS;
    }
}
