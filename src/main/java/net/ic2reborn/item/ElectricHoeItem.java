package net.ic2reborn.item;

import net.craftenergy.api.EnergyUnits;
import net.craftenergy.content.item.ElectricItem;
import net.craftenergy.content.item.EnergyItems;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Enxada elétrica do IC2 ({@code ItemElectricToolHoe}): ara a terra gastando energia em vez de durabilidade. */
public class ElectricHoeItem extends ElectricItem {
    /** IC2: 10.000 EU, 100 EU/t, nível 1, 50 EU por uso. */
    private static final long CAPACITY = EnergyUnits.fromCWh(5_000);
    public static final long OPERATION_ENERGY = EnergyUnits.fromCWh(25);

    public ElectricHoeItem(Properties properties) {
        super(properties, CAPACITY, 50_000, 220);
    }

    private static boolean charged(ItemStack stack) {
        return EnergyItems.getStored(stack) >= OPERATION_ENERGY;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!charged(context.getItemInHand())) return InteractionResult.PASS;
        // mesma regra da enxada comum (terra, grama, caminho e terra grossa)
        InteractionResult result = Items.IRON_HOE.useOn(context);
        if (result.consumesAction() && !context.getLevel().isClientSide()) {
            EnergyItems.use(context.getItemInHand(), OPERATION_ENERGY);
        }
        return result;
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return charged(stack) && state.is(BlockTags.MINEABLE_WITH_HOE) ? 16.0F : 1.0F;
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return charged(stack) && state.is(BlockTags.MINEABLE_WITH_HOE);
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner) {
        if (!level.isClientSide() && state.getDestroySpeed(level, pos) != 0.0F) {
            EnergyItems.use(stack, OPERATION_ENERGY);
        }
        return true;
    }
}
