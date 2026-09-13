package net.ic2reborn.item;

import net.craftenergy.api.EnergyUnits;
import net.craftenergy.content.item.ElectricItem;
import net.craftenergy.content.item.EnergyItems;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Perfuradora do IC2 ({@code ItemDrill}): picareta e pá elétrica. Sem energia vira um bloco de
 * metal na mão. No minerador, define quanto tempo e energia cada bloco leva.
 */
public class DrillItem extends ElectricItem {
    /**
     * Valores do IC2 convertidos (EU × 0,5 = CWh; EU/t × 500 = CW).
     *
     * @param energyPerBlock CW·tick gastos por bloco quebrado (IC2: 50 / 80 / 800 EU)
     * @param speed          velocidade de mineração (IC2: 8 / 16 / 24)
     * @param incorrect      blocos duros demais para esta broca
     * @param minerPower     CW por tick no minerador (IC2: 6 / 20 / 200 EU/t)
     * @param minerTicks     ticks por bloco no minerador (IC2: 200 / 50 / 20)
     */
    public enum Tier {
        BASIC(EnergyUnits.fromCWh(15_000), 50_000, 220, EnergyUnits.fromCWh(25), 8.0F, BlockTags.INCORRECT_FOR_IRON_TOOL, 3_000, 200),
        DIAMOND(EnergyUnits.fromCWh(15_000), 50_000, 220, EnergyUnits.fromCWh(40), 16.0F, BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 10_000, 50),
        IRIDIUM(EnergyUnits.fromCWh(150_000), 500_000, 2_400, EnergyUnits.fromCWh(400), 24.0F, BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 100_000, 20);

        final long capacity;
        final long transferLimit;
        final int voltage;
        final long energyPerBlock;
        final float speed;
        final TagKey<Block> incorrect;
        final long minerPower;
        final int minerTicks;

        Tier(long capacity, long transferLimit, int voltage, long energyPerBlock, float speed, TagKey<Block> incorrect,
             long minerPower, int minerTicks) {
            this.capacity = capacity;
            this.transferLimit = transferLimit;
            this.voltage = voltage;
            this.energyPerBlock = energyPerBlock;
            this.speed = speed;
            this.incorrect = incorrect;
            this.minerPower = minerPower;
            this.minerTicks = minerTicks;
        }
    }

    private final Tier tier;

    public DrillItem(Properties properties, Tier tier) {
        super(properties, tier.capacity, tier.transferLimit, tier.voltage);
        this.tier = tier;
    }

    /** CW·tick gastos da broca por bloco quebrado. */
    public long energyPerBlock() {
        return this.tier.energyPerBlock;
    }

    /** CW por tick no minerador. */
    public long minerPower() {
        return this.tier.minerPower;
    }

    /** Ticks por bloco no minerador. */
    public int minerTicks() {
        return this.tier.minerTicks;
    }

    /** Picareta ou pá, e não duro demais para o nível da broca. */
    public boolean canHarvest(BlockState state) {
        return (state.is(BlockTags.MINEABLE_WITH_PICKAXE) || state.is(BlockTags.MINEABLE_WITH_SHOVEL))
                && !state.is(this.tier.incorrect);
    }

    private boolean hasEnergy(ItemStack stack) {
        return EnergyItems.getStored(stack) >= this.tier.energyPerBlock;
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return hasEnergy(stack) && canHarvest(state) ? this.tier.speed : 1.0F;
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return hasEnergy(stack) && canHarvest(state);
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner) {
        if (!level.isClientSide() && state.getDestroySpeed(level, pos) != 0.0F) {
            EnergyItems.use(stack, this.tier.energyPerBlock);
        }
        return true;
    }
}
