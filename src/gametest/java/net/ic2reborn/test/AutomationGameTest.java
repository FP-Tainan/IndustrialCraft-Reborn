package net.ic2reborn.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.ic2reborn.block.entity.MachineBlockEntity;
import net.ic2reborn.registry.IC2AutoBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/** Fabricador em lote e Trade-O-Mat. */
public class AutomationGameTest {
    /** Com tronco no molde e nos ingredientes, o fabricador em lote faz tábuas. */
    @GameTest(maxTicks = 120)
    public void batchCrafterMakesPlanks(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, IC2AutoBlocks.BATCH_CRAFTER.get());
        MachineBlockEntity crafter = helper.getBlockEntity(pos, MachineBlockEntity.class);
        crafter.getInventory().setItem(1, new ItemStack(Items.OAK_LOG));
        crafter.getInventory().setItem(11, new ItemStack(Items.OAK_LOG, 4));
        helper.succeedWhen(() -> {
            crafter.setStoredEnergy(Long.MAX_VALUE);
            if (!crafter.getInventory().getItem(10).is(Items.OAK_PLANKS)) helper.fail("deveriam sair tábuas");
        });
    }

    /** O Trade-O-Mat troca terra por um diamante do baú do lado e guarda a terra lá. */
    @GameTest(maxTicks = 40)
    public void tradeOMatTrades(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, IC2AutoBlocks.TRADE_O_MAT.get());
        helper.setBlock(pos.north(), Blocks.CHEST);
        ChestBlockEntity chest = helper.getBlockEntity(pos.north(), ChestBlockEntity.class);
        chest.setItem(0, new ItemStack(Items.DIAMOND, 5));
        MachineBlockEntity trader = helper.getBlockEntity(pos, MachineBlockEntity.class);
        trader.getInventory().setItem(0, new ItemStack(Items.DIRT));
        trader.getInventory().setItem(1, new ItemStack(Items.DIAMOND));
        trader.getInventory().setItem(2, new ItemStack(Items.DIRT, 3));
        helper.succeedWhen(() -> {
            if (!trader.getInventory().getItem(3).is(Items.DIAMOND)) helper.fail("deveria sair um diamante");
            if (chest.countItem(Items.DIRT) <= 0) helper.fail("a terra deveria ir para o baú");
        });
    }
}
