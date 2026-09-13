package net.ic2reborn.test;

import net.craftenergy.api.EnergyBuffer;
import net.craftenergy.content.CEBlocks;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.ic2reborn.block.entity.MachineBlockEntity;
import net.ic2reborn.registry.IC2AutoBlocks;
import net.ic2reborn.registry.IC2AutoItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Testes no mundo: máquinas do IC2 Reborn ligadas pela rede do Craft Energy. */
public class EnergyGameTest {
    private static final int GENERATOR_FUEL_SLOT = 1;
    private static final int MACERATOR_INPUT_SLOT = 0;
    private static final int MACERATOR_OUTPUT_SLOT = 1;

    /** Gerador (5.000 CW, 220 MV) → 2 cabos de cobre → macerador (2.000 CW): ferro vira pó. */
    @GameTest(maxTicks = 900)
    public void generatorPowersMaceratorThroughCopperCable(GameTestHelper helper) {
        BlockPos generator = new BlockPos(1, 1, 1);
        BlockPos maceratorPos = new BlockPos(4, 1, 1);
        helper.setBlock(generator, IC2AutoBlocks.GENERATOR.get());
        cableLine(helper, CEBlocks.CABLE_COPPER.get(), 2, 3);
        helper.setBlock(maceratorPos, IC2AutoBlocks.MACERATOR.get());

        machine(helper, generator).getInventory().setItem(GENERATOR_FUEL_SLOT, new ItemStack(Items.COAL, 2));
        MachineBlockEntity macerator = machine(helper, maceratorPos);
        macerator.getInventory().setItem(MACERATOR_INPUT_SLOT, new ItemStack(Items.IRON_INGOT));

        helper.succeedWhen(() -> {
            ItemStack output = macerator.getInventory().getItem(MACERATOR_OUTPUT_SLOT);
            if (output.getItem() != IC2AutoItems.DUST_IRON.get()) {
                helper.fail("o macerador ainda não produziu pó de ferro");
            }
        });
    }

    /** Gerador → cobre → BatBox: a BatBox acumula energia (carga de 4.400 CW = 20 RA, no limite do cobre). */
    @GameTest(maxTicks = 200)
    public void batboxStoresGeneratorEnergy(GameTestHelper helper) {
        BlockPos generator = new BlockPos(1, 1, 1);
        BlockPos batboxPos = new BlockPos(3, 1, 1);
        helper.setBlock(generator, IC2AutoBlocks.GENERATOR.get());
        cableLine(helper, CEBlocks.CABLE_COPPER.get(), 2, 2);
        helper.setBlock(batboxPos, IC2AutoBlocks.BATBOX.get());

        machine(helper, generator).getInventory().setItem(GENERATOR_FUEL_SLOT, new ItemStack(Items.COAL));
        EnergyBuffer batbox = (EnergyBuffer) machine(helper, batboxPos).getEnergyNode();

        helper.succeedWhen(() -> {
            if (batbox.storedEnergy() <= 0) helper.fail("a BatBox ainda está vazia");
        });
    }

    /** Gerador → estanho (10 RA) → BatBox puxando 20 RA: o cabo esquenta e queima. */
    @GameTest(maxTicks = 200)
    public void overloadedTinCableBurns(GameTestHelper helper) {
        BlockPos generator = new BlockPos(1, 1, 1);
        BlockPos cable = new BlockPos(2, 1, 1);
        helper.setBlock(generator, IC2AutoBlocks.GENERATOR.get());
        helper.setBlock(cable, CEBlocks.CABLE_TIN.get());
        helper.setBlock(new BlockPos(3, 1, 1), IC2AutoBlocks.BATBOX.get());

        machine(helper, generator).getInventory().setItem(GENERATOR_FUEL_SLOT, new ItemStack(Items.COAL));

        helper.succeedWhen(() -> helper.assertBlockPresent(Blocks.AIR, cable));
    }

    /** CESU (1.000 MV) ao lado de um macerador (220 MV): sobretensão, o macerador explode. */
    @GameTest(maxTicks = 100)
    public void maceratorExplodesOnMediumVoltage(GameTestHelper helper) {
        BlockPos macerator = new BlockPos(2, 1, 1);
        helper.setBlock(new BlockPos(1, 1, 1), IC2AutoBlocks.CESU.get());
        helper.setBlock(macerator, IC2AutoBlocks.MACERATOR.get());

        helper.succeedWhen(() -> helper.assertBlockPresent(Blocks.AIR, macerator));
    }

    private static void cableLine(GameTestHelper helper, Block cable, int fromX, int toX) {
        for (int x = fromX; x <= toX; x++) {
            helper.setBlock(new BlockPos(x, 1, 1), cable);
        }
    }

    private static MachineBlockEntity machine(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos, MachineBlockEntity.class);
    }
}
