package net.ic2reborn.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.ic2reborn.block.entity.MachineBlockEntity;
import net.ic2reborn.fluid.IC2Fluids;
import net.ic2reborn.registry.IC2AutoBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.material.Fluids;

/** Caixas, tanques, bomba, triagem, envasadora e condensador. */
public class LogisticsGameTest {
    private static long insert(MachineBlockEntity machine, FluidVariant fluid, long droplets) {
        try (Transaction transaction = Transaction.openOuter()) {
            long inserted = machine.getFluidStorage(null).insert(fluid, droplets, transaction);
            transaction.commit();
            return inserted;
        }
    }

    /** A caixa guarda o conteúdo no item; o tanque de ferro tem 32 baldes. */
    @GameTest(maxTicks = 20)
    public void boxAndTankCapacity(GameTestHelper helper) {
        BlockPos boxPos = new BlockPos(1, 1, 1);
        helper.setBlock(boxPos, IC2AutoBlocks.IRIDIUM_STORAGE_BOX.get());
        MachineBlockEntity box = helper.getBlockEntity(boxPos, MachineBlockEntity.class);
        if (box.getInventory().getContainerSize() != 126) helper.fail("caixa de irídio deveria ter 126 slots");
        box.getInventory().setItem(100, new ItemStack(Items.DIAMOND, 5));
        var contents = box.collectComponents().get(DataComponents.CONTAINER);
        if (contents == null || contents.nonEmptyItemCopyStream().noneMatch(stack -> stack.is(Items.DIAMOND))) {
            helper.fail("o item da caixa deveria levar os diamantes");
        }

        BlockPos tankPos = new BlockPos(3, 1, 1);
        helper.setBlock(tankPos, IC2AutoBlocks.IRON_TANK.get());
        MachineBlockEntity tank = helper.getBlockEntity(tankPos, MachineBlockEntity.class);
        long inserted = insert(tank, FluidVariant.of(Fluids.WATER), 40 * FluidConstants.BUCKET);
        if (inserted != 32 * FluidConstants.BUCKET) helper.fail("tanque de ferro deveria aceitar 32 baldes, aceitou " + inserted);
        helper.succeed();
    }

    /** A bomba tira a fonte de água de baixo e guarda 1 balde. */
    @GameTest(maxTicks = 100)
    public void pumpTakesWater(GameTestHelper helper) {
        BlockPos pumpPos = new BlockPos(1, 2, 1);
        helper.setBlock(pumpPos.below(), Blocks.WATER);
        helper.setBlock(pumpPos, IC2AutoBlocks.PUMP.get());
        MachineBlockEntity pump = helper.getBlockEntity(pumpPos, MachineBlockEntity.class);
        pump.setStoredEnergy(Long.MAX_VALUE);
        helper.succeedWhen(() -> {
            pump.setStoredEnergy(Long.MAX_VALUE);
            if (pump.getTank(0).amount < FluidConstants.BUCKET) helper.fail("a bomba deveria ter 1 balde de água");
        });
    }

    /** Triagem: pedregulho vai para o filtro norte; terra, sem filtro, para a rota padrão leste. */
    @GameTest(maxTicks = 60)
    public void sortingMachineRoutes(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, IC2AutoBlocks.SORTING_MACHINE.get());
        helper.setBlock(pos.north(), Blocks.CHEST);
        helper.setBlock(pos.east(), Blocks.CHEST);
        MachineBlockEntity sorter = helper.getBlockEntity(pos, MachineBlockEntity.class);
        sorter.setStoredEnergy(Long.MAX_VALUE);
        sorter.getInventory().setItem(15 + 2 * 7, new ItemStack(Items.COBBLESTONE));
        sorter.handleMenuButton(MachineBlockEntity.BUTTON_SORTING_DEFAULT + 5);
        sorter.getInventory().setItem(4, new ItemStack(Items.COBBLESTONE, 10));
        sorter.getInventory().setItem(5, new ItemStack(Items.DIRT, 3));
        helper.succeedWhen(() -> {
            ChestBlockEntity north = helper.getBlockEntity(pos.north(), ChestBlockEntity.class);
            ChestBlockEntity east = helper.getBlockEntity(pos.east(), ChestBlockEntity.class);
            if (north.countItem(Items.COBBLESTONE) != 10) helper.fail("o baú norte deveria ter o pedregulho");
            if (east.countItem(Items.DIRT) != 3) helper.fail("o baú leste deveria ter a terra");
            if (east.countItem(Items.COBBLESTONE) != 0) helper.fail("pedregulho não deveria ir para a rota padrão");
        });
    }

    /** Envasadora esvazia um balde de água no tanque. */
    @GameTest(maxTicks = 200)
    public void bottlerEmptiesBucket(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, IC2AutoBlocks.FLUID_BOTTLER.get());
        MachineBlockEntity bottler = helper.getBlockEntity(pos, MachineBlockEntity.class);
        bottler.getInventory().setItem(1, new ItemStack(Items.WATER_BUCKET));
        helper.succeedWhen(() -> {
            bottler.setStoredEnergy(Long.MAX_VALUE);
            if (!bottler.getInventory().getItem(3).is(Items.BUCKET) || bottler.getTank(0).amount != FluidConstants.BUCKET) {
                helper.fail("deveria sair um balde vazio e entrar 1 balde de água");
            }
        });
    }

    /** Condensador: 10 baldes de vapor viram 100 mB de água destilada. */
    @GameTest(maxTicks = 200)
    public void condenserMakesDistilledWater(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, IC2AutoBlocks.CONDENSER.get());
        MachineBlockEntity condenser = helper.getBlockEntity(pos, MachineBlockEntity.class);
        insert(condenser, FluidVariant.of(IC2Fluids.STEAM.fluid()), 10 * FluidConstants.BUCKET);
        helper.succeedWhen(() -> {
            if (condenser.getTank(1).amount < FluidConstants.BUCKET / 10) helper.fail("deveria haver 100 mB de água destilada");
        });
    }
}
