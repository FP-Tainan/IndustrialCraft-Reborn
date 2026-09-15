package net.ic2reborn.test;

import net.craftenergy.content.item.MultimeterItem;
import net.craftenergy.network.MultimeterReadingPayload;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.ic2reborn.registry.IC2AutoBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;

/** O multímetro do Craft Energy lê as máquinas do IC2 Reborn, e o IC2 não tem multímetro próprio. */
public class MultimeterGameTest {
    @GameTest(maxTicks = 20)
    public void craftEnergyMultimeterReadsIc2Machines(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, IC2AutoBlocks.MACERATOR.get());
        MultimeterReadingPayload reading = MultimeterItem.measure(helper.getLevel(), helper.absolutePos(pos));
        if (!reading.units().containsAll(java.util.List.of("MV", "RA", "CW"))) helper.fail("o multímetro deveria mostrar MV, RA e CW do macerador: " + reading.units());
        if (BuiltInRegistries.ITEM.containsKey(Identifier.fromNamespaceAndPath("ic2reborn", "multimeter"))) helper.fail("o IC2 não pode ter multímetro próprio");
        if (!BuiltInRegistries.ITEM.containsKey(Identifier.fromNamespaceAndPath("craftenergy", "multimeter"))) helper.fail("o multímetro vem do Craft Energy");
        helper.succeed();
    }
}
