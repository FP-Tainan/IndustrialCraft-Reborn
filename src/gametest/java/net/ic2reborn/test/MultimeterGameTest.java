package net.ic2reborn.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.ic2reborn.item.MultimeterItem;
import net.ic2reborn.registry.IC2AutoBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

/** O multímetro lê tensão, corrente e potência de uma máquina. */
public class MultimeterGameTest {
    @GameTest(maxTicks = 20)
    public void multimeterReadsMachine(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, IC2AutoBlocks.MACERATOR.get());
        var reading = MultimeterItem.measure(helper.getLevel(), helper.absolutePos(pos));
        if (!reading.units().containsAll(java.util.List.of("MV", "RA", "CW"))) helper.fail("deveria ler MV, RA e CW: " + reading.units());
        helper.succeed();
    }
}
