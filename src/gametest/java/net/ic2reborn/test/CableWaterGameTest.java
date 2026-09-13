package net.ic2reborn.test;

import net.craftenergy.content.CEBlocks;
import net.craftenergy.content.block.CableBlock;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;

/** Cabos dentro d'água: não quebram (como no IC2). */
public class CableWaterGameTest {
    /** Água escorrendo por cima não quebra o cabo. */
    @GameTest(maxTicks = 60)
    public void flowingWaterDoesNotBreakCable(GameTestHelper helper) {
        BlockPos cable = new BlockPos(1, 1, 1);
        helper.setBlock(cable, CEBlocks.CABLE_COPPER_INSULATED.get());
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.WATER);

        helper.runAfterDelay(40, () -> {
            helper.assertBlockPresent(CEBlocks.CABLE_COPPER_INSULATED.get(), cable);
            helper.succeed();
        });
    }

    /** Cabo alagado continua cabo e segura a água. */
    @GameTest(maxTicks = 40)
    public void waterloggedCableKeepsWater(GameTestHelper helper) {
        BlockPos cable = new BlockPos(1, 1, 1);
        helper.setBlock(cable, CEBlocks.CABLE_COPPER_INSULATED.get().defaultBlockState().setValue(CableBlock.WATERLOGGED, true));

        helper.runAfterDelay(20, () -> {
            helper.assertBlockPresent(CEBlocks.CABLE_COPPER_INSULATED.get(), cable);
            if (!helper.getBlockState(cable).getFluidState().is(Fluids.WATER)) helper.fail("o cabo deveria estar com água");
            helper.succeed();
        });
    }
}
