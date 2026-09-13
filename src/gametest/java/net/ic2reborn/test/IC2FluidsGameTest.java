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
import net.ic2reborn.registry.IC2Items;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/** Fluidos do IC2, gerador semifluido, enlatadora (4 modos) e textura ativa. */
public class IC2FluidsGameTest {
    private static final int CANNER_INPUT = 0;
    private static final int CANNER_OUTPUT = 1;
    private static final int CANNER_DISCHARGE = 2;
    private static final int CANNER_CONTAINER = 7;

    /** Célula de biomassa no semifluido: a biomassa entra no tanque, sai a célula vazia e há produção. */
    @GameTest(maxTicks = 40)
    public void semifluidGeneratorBurnsBiomass(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, IC2AutoBlocks.SEMIFLUID_GENERATOR.get());
        MachineBlockEntity generator = machine(helper, pos);
        generator.getInventory().setItem(0, new ItemStack(IC2Fluids.BIOMASS.cell().get()));

        helper.succeedWhen(() -> {
            if (generator.getInventory().getItem(1).getItem() != IC2AutoItems.FLUID_CELL.get()) helper.fail("a célula vazia deveria sair");
            if (generator.getStoredEnergy() <= 0) helper.fail("o semifluido ainda não produziu");
        });
    }

    /** O semifluido recusa água no tanque. */
    @GameTest(maxTicks = 20)
    public void semifluidGeneratorRefusesWater(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, IC2AutoBlocks.SEMIFLUID_GENERATOR.get());
        try (Transaction transaction = Transaction.openOuter()) {
            if (machine(helper, pos).getFluidStorage(null).insert(FluidVariant.of(Fluids.WATER), FluidConstants.BUCKET, transaction) != 0) {
                helper.fail("não deveria aceitar água");
            }
        }
        helper.succeed();
    }

    /** Enlatadora no modo de sólidos (padrão): maçã + 4 latas = 4 latas cheias. */
    @GameTest(maxTicks = 300)
    public void cannerCansSolids(GameTestHelper helper) {
        MachineBlockEntity canner = canner(helper);
        canner.getInventory().setItem(CANNER_INPUT, new ItemStack(Items.APPLE));
        canner.getInventory().setItem(CANNER_CONTAINER, new ItemStack(IC2AutoItems.TIN_CAN.get(), 4));

        helper.succeedWhen(() -> {
            ItemStack output = canner.getInventory().getItem(CANNER_OUTPUT);
            if (output.getItem() != IC2Items.FILLED_TIN_CAN.get() || output.getCount() != 4) helper.fail("ainda sem 4 latas cheias");
        });
    }

    /** Modo esvaziar: célula de lava vai para o tanque de saída e a célula vazia sai. */
    @GameTest(maxTicks = 300)
    public void cannerEmptiesCellIntoOutputTank(GameTestHelper helper) {
        MachineBlockEntity canner = canner(helper);
        canner.handleMenuButton(MachineBlockEntity.BUTTON_CANNER_MODE + 1);
        canner.getInventory().setItem(CANNER_CONTAINER, new ItemStack(IC2AutoItems.LAVA_CELL.get()));

        helper.succeedWhen(() -> {
            if (canner.getInventory().getItem(CANNER_OUTPUT).getItem() != IC2AutoItems.FLUID_CELL.get()) helper.fail("a célula vazia deveria sair");
            expectTank(helper, canner.getTank(1), Fluids.LAVA, FluidConstants.BUCKET);
        });
    }

    /** Modo encher: água do tanque de entrada enche a célula vazia. */
    @GameTest(maxTicks = 300)
    public void cannerFillsCellFromInputTank(GameTestHelper helper) {
        MachineBlockEntity canner = canner(helper);
        canner.handleMenuButton(MachineBlockEntity.BUTTON_CANNER_MODE + 2);
        insert(helper, canner, Fluids.WATER, FluidConstants.BUCKET);
        canner.getInventory().setItem(CANNER_CONTAINER, new ItemStack(IC2AutoItems.FLUID_CELL.get()));

        helper.succeedWhen(() -> {
            if (canner.getInventory().getItem(CANNER_OUTPUT).getItem() != IC2AutoItems.WATER_CELL.get()) helper.fail("a célula de água deveria sair");
        });
    }

    /** Modo enriquecer: água + palha orgânica = biomassa no tanque de saída. */
    @GameTest(maxTicks = 300)
    public void cannerEnrichesWaterIntoBiomass(GameTestHelper helper) {
        MachineBlockEntity canner = canner(helper);
        canner.handleMenuButton(MachineBlockEntity.BUTTON_CANNER_MODE + 3);
        insert(helper, canner, Fluids.WATER, FluidConstants.BUCKET);
        canner.getInventory().setItem(CANNER_INPUT, new ItemStack(IC2Items.BIO_CHAFF.get()));

        helper.succeedWhen(() -> expectTank(helper, canner.getTank(1), IC2Fluids.BIOMASS.fluid(), FluidConstants.BUCKET));
    }

    /** Botão de trocar tanques. */
    @GameTest(maxTicks = 20)
    public void cannerSwapsTanks(GameTestHelper helper) {
        MachineBlockEntity canner = canner(helper);
        insert(helper, canner, Fluids.WATER, FluidConstants.BUCKET);
        canner.handleMenuButton(MachineBlockEntity.BUTTON_SWAP_TANKS);
        expectTank(helper, canner.getTank(1), Fluids.WATER, FluidConstants.BUCKET);
        if (!canner.getTank(0).isResourceBlank()) helper.fail("o tanque de entrada deveria ficar vazio");
        helper.succeed();
    }

    /** Macerador trabalhando mostra a textura ativa. */
    @GameTest(maxTicks = 100)
    public void workingMachineBecomesActive(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, IC2AutoBlocks.MACERATOR.get());
        MachineBlockEntity macerator = machine(helper, pos);
        macerator.getInventory().setItem(2, ((BatteryItem) IC2AutoItems.RE_BATTERY.get()).charged());
        macerator.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT));

        helper.succeedWhen(() -> {
            if (!helper.getBlockState(pos).getValue(MachineBlock.ACTIVE)) helper.fail("o macerador deveria estar ativo");
        });
    }

    private static MachineBlockEntity canner(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, IC2AutoBlocks.CANNER.get());
        MachineBlockEntity canner = machine(helper, pos);
        canner.getInventory().setItem(CANNER_DISCHARGE, ((BatteryItem) IC2AutoItems.RE_BATTERY.get()).charged());
        return canner;
    }

    private static void insert(GameTestHelper helper, MachineBlockEntity machine, Fluid fluid, long amount) {
        try (Transaction transaction = Transaction.openOuter()) {
            if (machine.getFluidStorage(null).insert(FluidVariant.of(fluid), amount, transaction) != amount) {
                helper.fail("o tanque deveria aceitar o fluido");
            }
            transaction.commit();
        }
    }

    private static void expectTank(GameTestHelper helper, SingleFluidStorage tank, Fluid fluid, long amount) {
        if (tank.isResourceBlank() || tank.getResource().getFluid() != fluid || tank.getAmount() < amount) {
            helper.fail("tanque deveria ter " + amount + " de " + fluid + ", tem " + tank.getAmount() + " de " + tank.getResource());
        }
    }

    private static MachineBlockEntity machine(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos, MachineBlockEntity.class);
    }
}
