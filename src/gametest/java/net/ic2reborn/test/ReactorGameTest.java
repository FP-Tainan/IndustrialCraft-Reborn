package net.ic2reborn.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.ic2reborn.block.entity.MachineBlockEntity;
import net.ic2reborn.reactor.ReactorComponentItem;
import net.ic2reborn.registry.IC2AutoBlocks;
import net.ic2reborn.registry.IC2AutoItems;
import net.ic2reborn.registry.IC2Items;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/** Reator nuclear: energia, calor, ventoinha, câmaras e barra esgotada. */
public class ReactorGameTest {
    private static int data(MachineBlockEntity machine, int field) {
        var data = machine.getContainerData();
        return (data.get(field * 2) & 0xFFFF) | ((data.get(field * 2 + 1) & 0xFFFF) << 16);
    }

    private static MachineBlockEntity reactor(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, IC2AutoBlocks.NUCLEAR_REACTOR.get());
        helper.setBlock(pos.above(), Blocks.REDSTONE_BLOCK);
        return helper.getBlockEntity(pos, MachineBlockEntity.class);
    }

    /** Uma barra de urânio com redstone gera energia e, sem refrigeração, esquenta o reator. */
    @GameTest(maxTicks = 80)
    public void uraniumRodMakesPowerAndHeat(GameTestHelper helper) {
        MachineBlockEntity reactor = reactor(helper, new BlockPos(1, 1, 1));
        reactor.getInventory().setItem(0, new ItemStack(IC2AutoItems.URANIUM_FUEL_ROD.get()));
        helper.succeedWhen(() -> {
            if (reactor.getStoredEnergy() <= 0) helper.fail("o reator deveria gerar energia");
            if (data(reactor, MachineBlockEntity.DATA_HEAT) <= 0) helper.fail("a barra sem refrigeração deveria esquentar o reator");
        });
    }

    /** Uma ventoinha ao lado da barra tira todo o calor. */
    @GameTest(maxTicks = 100)
    public void ventKeepsReactorCool(GameTestHelper helper) {
        MachineBlockEntity reactor = reactor(helper, new BlockPos(1, 1, 1));
        reactor.getInventory().setItem(0, new ItemStack(IC2AutoItems.URANIUM_FUEL_ROD.get()));
        reactor.getInventory().setItem(1, new ItemStack(IC2AutoItems.HEAT_VENT.get()));
        helper.runAfterDelay(65, () -> {
            if (data(reactor, MachineBlockEntity.DATA_HEAT) != 0) helper.fail("a ventoinha deveria manter o reator frio");
            if (!reactor.getInventory().getItem(1).is(IC2AutoItems.HEAT_VENT.get())) helper.fail("a ventoinha não deveria derreter");
            if (reactor.getStoredEnergy() <= 0) helper.fail("deveria gerar energia");
            helper.succeed();
        });
    }

    /** Cada câmara encostada soma uma coluna à grade. */
    @GameTest(maxTicks = 60)
    public void chambersAddColumns(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        MachineBlockEntity reactor = reactor(helper, pos);
        helper.setBlock(pos.east(), IC2AutoBlocks.REACTOR_CHAMBER.get());
        helper.setBlock(pos.west(), IC2AutoBlocks.REACTOR_CHAMBER.get());
        helper.succeedWhen(() -> {
            if ((data(reactor, MachineBlockEntity.DATA_MODE) & 15) != 5) helper.fail("duas câmaras deveriam dar 5 colunas");
        });
    }

    /** A barra no fim da vida vira barra esgotada. */
    @GameTest(maxTicks = 60)
    public void rodDepletes(GameTestHelper helper) {
        MachineBlockEntity reactor = reactor(helper, new BlockPos(1, 1, 1));
        ItemStack rod = new ItemStack(IC2AutoItems.URANIUM_FUEL_ROD.get());
        ReactorComponentItem.setDamage(rod, 19_999);
        reactor.getInventory().setItem(0, rod);
        helper.succeedWhen(() -> {
            if (!reactor.getInventory().getItem(0).is(IC2Items.DEPLETED_URANIUM_FUEL_ROD.get())) helper.fail("deveria sobrar a barra esgotada");
        });
    }
}
