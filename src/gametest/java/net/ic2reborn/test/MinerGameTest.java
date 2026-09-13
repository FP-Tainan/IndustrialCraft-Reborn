package net.ic2reborn.test;

import net.craftenergy.content.item.BatteryItem;
import net.craftenergy.content.item.ElectricItem;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.ic2reborn.block.MiningPipeBlock;
import net.ic2reborn.block.entity.MachineBlockEntity;
import net.ic2reborn.registry.IC2AutoBlocks;
import net.ic2reborn.registry.IC2AutoItems;
import net.ic2reborn.registry.IC2Items;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Minerador, tubos, perfuradoras e scanner. */
public class MinerGameTest {
    private static final int DISCHARGE = 0;
    private static final int SCANNER = 1;
    private static final int PIPES = 2;
    private static final int DRILL = 3;

    /** Perfuradora carregada quebra pedra rápido; descarregada, não. */
    @GameTest(maxTicks = 20)
    public void drillIsFastOnlyWhenCharged(GameTestHelper helper) {
        BlockState stone = Blocks.STONE.defaultBlockState();
        ItemStack charged = ((ElectricItem) IC2AutoItems.DRILL.get()).charged();
        ItemStack empty = new ItemStack(IC2AutoItems.DRILL.get());
        if (charged.getDestroySpeed(stone) <= 1.0F) helper.fail("a perfuradora carregada deveria ser rápida, veio " + charged.getDestroySpeed(stone));
        if (empty.getDestroySpeed(stone) != 1.0F) helper.fail("a perfuradora vazia deveria ser lenta");
        helper.succeed();
    }

    /** Com broca e tubos, o minerador desce e deixa a ponta do tubo no bloco de baixo. */
    @GameTest(maxTicks = 200)
    public void minerDigsDown(GameTestHelper helper) {
        BlockPos minerPos = new BlockPos(1, 5, 1);
        helper.setBlock(new BlockPos(1, 4, 1), Blocks.STONE);
        MachineBlockEntity miner = miner(helper, minerPos);
        miner.getInventory().setItem(DRILL, ((ElectricItem) IC2AutoItems.DIAMOND_DRILL.get()).charged());
        miner.getInventory().setItem(PIPES, new ItemStack(IC2AutoItems.MINING_PIPE.get(), 10));

        helper.succeedWhen(() -> {
            BlockState state = helper.getBlockState(new BlockPos(1, 4, 1));
            if (!state.is(IC2AutoBlocks.MINING_PIPE.get()) || !state.getValue(MiningPipeBlock.TIP)) helper.fail("deveria ter a ponta do tubo");
            if (miner.getInventory().getItem(PIPES).getCount() != 9) helper.fail("deveria ter gasto um tubo");
            if (!hasInBuffer(miner, Items.COBBLESTONE)) helper.fail("o pedregulho deveria ir para o buffer");
        });
    }

    /** Com scanner, na camada da ponta ele vai até o minério de ferro e guarda o ferro bruto. */
    @GameTest(maxTicks = 400)
    public void minerFindsOreWithScanner(GameTestHelper helper) {
        BlockPos minerPos = new BlockPos(2, 5, 2);
        helper.setBlock(new BlockPos(2, 4, 2), IC2AutoBlocks.MINING_PIPE.get().defaultBlockState().setValue(MiningPipeBlock.TIP, true));
        BlockPos ore = new BlockPos(4, 4, 2);
        helper.setBlock(ore, Blocks.IRON_ORE);
        MachineBlockEntity miner = miner(helper, minerPos);
        miner.getInventory().setItem(DRILL, ((ElectricItem) IC2AutoItems.DIAMOND_DRILL.get()).charged());
        miner.getInventory().setItem(PIPES, new ItemStack(IC2AutoItems.MINING_PIPE.get(), 10));
        miner.getInventory().setItem(SCANNER, ((ElectricItem) IC2Items.OD_SCANNER.get()).charged());

        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.AIR, ore);
            if (!hasInBuffer(miner, Items.RAW_IRON)) helper.fail("o ferro bruto deveria estar no buffer");
        });
    }

    /** Sem broca, o minerador recolhe o tubo de volta para o buffer. */
    @GameTest(maxTicks = 100)
    public void minerWithdrawsPipeWithoutDrill(GameTestHelper helper) {
        BlockPos minerPos = new BlockPos(1, 5, 1);
        BlockPos tip = new BlockPos(1, 4, 1);
        helper.setBlock(tip, IC2AutoBlocks.MINING_PIPE.get().defaultBlockState().setValue(MiningPipeBlock.TIP, true));
        MachineBlockEntity miner = miner(helper, minerPos);

        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.AIR, tip);
            if (!hasInBuffer(miner, IC2AutoItems.MINING_PIPE.get().asItem())) helper.fail("o tubo deveria voltar para o buffer");
        });
    }

    private static MachineBlockEntity miner(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, IC2AutoBlocks.MINER.get());
        MachineBlockEntity miner = helper.getBlockEntity(pos, MachineBlockEntity.class);
        miner.getInventory().setItem(DISCHARGE, ((BatteryItem) IC2AutoItems.RE_BATTERY.get()).charged());
        return miner;
    }

    private static boolean hasInBuffer(MachineBlockEntity miner, Item item) {
        for (int slot = 5; slot < 20; slot++) {
            if (miner.getInventory().getItem(slot).getItem() == item) return true;
        }
        return false;
    }
}
