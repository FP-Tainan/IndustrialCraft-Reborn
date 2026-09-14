package net.ic2reborn.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.ic2reborn.block.entity.MachineBlockEntity;
import net.ic2reborn.fluid.IC2Fluids;
import net.ic2reborn.recipe.UuValues;
import net.ic2reborn.registry.IC2AutoBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;

/** Custos de UU, fabricador de massa e replicador com armazenamento de moldes. */
public class UuMatterGameTest {
    /** Diamante tem custo base; graveto vem das receitas (tronco → tábua → graveto); rocha-mãe não replica. */
    @GameTest(maxTicks = 20)
    public void uuValuesFromRecipes(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        if (UuValues.cost(server, Items.DIAMOND).orElse(0) <= 0) helper.fail("diamante deveria ter custo");
        double stick = UuValues.cost(server, Items.STICK).orElse(-1);
        if (stick <= 0 || stick > 8) helper.fail("graveto deveria custar pouco, custou " + stick);
        if (UuValues.cost(server, Items.BEDROCK).isPresent()) helper.fail("rocha-mãe não deveria ter custo");
        helper.succeed();
    }

    /** Com o buffer cheio o fabricador faz 1 mB de UU-matter. */
    @GameTest(maxTicks = 40)
    public void fabricatorMakesUu(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, IC2AutoBlocks.MATTER_GENERATOR.get());
        MachineBlockEntity fabricator = helper.getBlockEntity(pos, MachineBlockEntity.class);
        fabricator.setStoredEnergy(Long.MAX_VALUE);
        helper.succeedWhen(() -> {
            if (fabricator.getTank(0).amount < FluidConstants.BUCKET / 1000) helper.fail("deveria haver 1 mB de UU-matter");
        });
    }

    /** O replicador encostado no armazenamento de moldes recria pedregulho. */
    @GameTest(maxTicks = 60)
    public void replicatorCopiesPattern(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, IC2AutoBlocks.REPLICATOR.get());
        helper.setBlock(pos.east(), IC2AutoBlocks.PATTERN_STORAGE.get());
        MachineBlockEntity replicator = helper.getBlockEntity(pos, MachineBlockEntity.class);
        MachineBlockEntity storage = helper.getBlockEntity(pos.east(), MachineBlockEntity.class);
        storage.addPattern(Items.COBBLESTONE);
        try (Transaction transaction = Transaction.openOuter()) {
            replicator.getFluidStorage(null).insert(FluidVariant.of(IC2Fluids.UU_MATTER.fluid()), FluidConstants.BUCKET, transaction);
            transaction.commit();
        }
        helper.runAfterDelay(2, () -> replicator.handleMenuButton(MachineBlockEntity.BUTTON_UU + 15));
        helper.succeedWhen(() -> {
            replicator.setStoredEnergy(Long.MAX_VALUE);
            if (!replicator.getInventory().getItem(1).is(Items.COBBLESTONE)) helper.fail("deveria sair pedregulho replicado");
        });
    }
}
