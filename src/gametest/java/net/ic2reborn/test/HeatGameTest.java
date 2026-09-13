package net.ic2reborn.test;

import net.craftenergy.content.item.BatteryItem;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.base.SingleFluidStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.ic2reborn.block.MachineBlock;
import net.ic2reborn.block.entity.MachineBlockEntity;
import net.ic2reborn.fluid.IC2Fluids;
import net.ic2reborn.registry.IC2AutoBlocks;
import net.ic2reborn.registry.IC2AutoItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/** Geradores de calor e fermentador (calor passado frente a frente). */
public class HeatGameTest {
    private static final BlockPos FERMENTER = new BlockPos(1, 1, 1);
    private static final BlockPos SOURCE = new BlockPos(2, 1, 1);

    /** Gerador de calor sólido com carvão, virado para o fermentador: biomassa vira biogás. */
    @GameTest(maxTicks = 400)
    public void solidHeatGeneratorFeedsFermenter(GameTestHelper helper) {
        MachineBlockEntity fermenter = fermenterWithBiomass(helper);
        MachineBlockEntity source = place(helper, SOURCE, IC2AutoBlocks.SOLID_HEAT_GENERATOR.get(), Direction.WEST);
        source.getInventory().setItem(0, new ItemStack(Items.COAL));

        helper.succeedWhen(() -> expectBiogas(helper, fermenter.getTank(1)));
    }

    /** Gerador de calor elétrico com 10 bobinas (100 HU/t) e bateria avançada. */
    @GameTest(maxTicks = 200)
    public void electricHeatGeneratorFeedsFermenter(GameTestHelper helper) {
        MachineBlockEntity fermenter = fermenterWithBiomass(helper);
        MachineBlockEntity source = place(helper, SOURCE, IC2AutoBlocks.ELECTRIC_HEAT_GENERATOR.get(), Direction.WEST);
        for (int slot = 0; slot < 10; slot++) {
            source.getInventory().setItem(slot, new ItemStack(IC2AutoItems.COIL.get()));
        }
        source.getInventory().setItem(10, ((BatteryItem) IC2AutoItems.ADVANCED_RE_BATTERY.get()).charged());

        helper.succeedWhen(() -> {
            if (source.maxHeatEmitted() != 100) helper.fail("10 bobinas deveriam dar 100 HU/t");
            expectBiogas(helper, fermenter.getTank(1));
        });
    }

    /** Gerador virado para o outro lado não aquece o fermentador. */
    @GameTest(maxTicks = 100)
    public void heatNeedsFaceToFace(GameTestHelper helper) {
        MachineBlockEntity fermenter = fermenterWithBiomass(helper);
        MachineBlockEntity source = place(helper, SOURCE, IC2AutoBlocks.SOLID_HEAT_GENERATOR.get(), Direction.NORTH);
        source.getInventory().setItem(0, new ItemStack(Items.COAL));

        helper.runAfterDelay(60, () -> {
            if (!fermenter.getTank(1).isResourceBlank()) helper.fail("sem estar de frente não deveria passar calor");
            helper.succeed();
        });
    }

    /** RT: 3 pastilhas = 2² × 2 = 8 HU/t. Fluido: célula de biomassa = 16 HU/t e devolve a célula. */
    @GameTest(maxTicks = 40)
    public void rtAndFluidHeatGenerators(GameTestHelper helper) {
        MachineBlockEntity rt = place(helper, new BlockPos(1, 1, 1), IC2AutoBlocks.RT_HEAT_GENERATOR.get(), Direction.NORTH);
        for (int slot = 0; slot < 3; slot++) {
            rt.getInventory().setItem(slot, new ItemStack(IC2AutoItems.RTG_PELLET.get()));
        }
        MachineBlockEntity fluid = place(helper, new BlockPos(3, 1, 1), IC2AutoBlocks.FLUID_HEAT_GENERATOR.get(), Direction.NORTH);
        fluid.getInventory().setItem(0, new ItemStack(IC2Fluids.BIOMASS.cell().get()));

        helper.succeedWhen(() -> {
            if (rt.maxHeatEmitted() != 8) helper.fail("3 pastilhas RT deveriam dar 8 HU/t, deu " + rt.maxHeatEmitted());
            if (fluid.maxHeatEmitted() != 16) helper.fail("biomassa deveria dar 16 HU/t, deu " + fluid.maxHeatEmitted());
            if (fluid.getInventory().getItem(1).getItem() != IC2AutoItems.FLUID_CELL.get()) helper.fail("a célula vazia deveria sair");
        });
    }

    private static MachineBlockEntity fermenterWithBiomass(GameTestHelper helper) {
        MachineBlockEntity fermenter = place(helper, FERMENTER, IC2AutoBlocks.FERMENTER.get(), Direction.EAST);
        try (Transaction transaction = Transaction.openOuter()) {
            long inserted = fermenter.getFluidStorage(null).insert(FluidVariant.of(IC2Fluids.BIOMASS.fluid()), FluidConstants.BUCKET, transaction);
            if (inserted != FluidConstants.BUCKET) helper.fail("o fermentador deveria aceitar biomassa");
            transaction.commit();
        }
        return fermenter;
    }

    private static MachineBlockEntity place(GameTestHelper helper, BlockPos pos, Block block, Direction facing) {
        helper.setBlock(pos, block.defaultBlockState().setValue(MachineBlock.FACING, facing));
        return helper.getBlockEntity(pos, MachineBlockEntity.class);
    }

    private static void expectBiogas(GameTestHelper helper, SingleFluidStorage tank) {
        if (tank.isResourceBlank() || tank.getResource().getFluid() != IC2Fluids.BIOGAS.fluid()
                || tank.getAmount() < FluidConstants.BUCKET * 400 / 1000) {
            helper.fail("ainda sem biogás: " + tank.getAmount());
        }
    }
}
