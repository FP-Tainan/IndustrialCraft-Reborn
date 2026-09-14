package net.ic2reborn.test;

import net.craftenergy.api.EnergyUnits;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.ic2reborn.block.entity.MachineBlockEntity;
import net.ic2reborn.menu.layout.MachineLayout;
import net.ic2reborn.registry.IC2AutoBlocks;
import net.ic2reborn.registry.IC2AutoItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Upgrades de máquina: overclocker, ejetor e kit da MFSU. */
public class UpgradesGameTest {
    private static int firstOutput(MachineLayout layout) {
        for (int i = 0; i < layout.slotCount(); i++) {
            if (layout.isOutputSlot(i)) return i;
        }
        return -1;
    }

    /** Dois overclockers: operação 49% (147 ticks) e consumo 256% (5.120 CW) no macerador. */
    @GameTest(maxTicks = 40)
    public void overclockerSpeedsUp(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, IC2AutoBlocks.MACERATOR.get());
        MachineBlockEntity macerator = helper.getBlockEntity(pos, MachineBlockEntity.class);
        int slot = macerator.getGuiType().layout().upgradeSlots().getFirst();
        macerator.getInventory().setItem(slot, new ItemStack(IC2AutoItems.UPGRADE_OVERCLOCKER.get(), 2));
        if (macerator.getInventory().canPlaceItem(slot, new ItemStack(Items.DIRT))) helper.fail("slot de upgrade não aceita terra");
        helper.succeedWhen(() -> {
            if (macerator.getEnergyProfile().operationTicks() != 147) helper.fail("duração deveria ser 147, é " + macerator.getEnergyProfile().operationTicks());
            if (macerator.getEnergyProfile().power() != 5_120) helper.fail("consumo deveria ser 5.120 CW, é " + macerator.getEnergyProfile().power());
        });
    }

    /** Ejetor tira a saída do macerador para o baú do lado. */
    @GameTest(maxTicks = 40)
    public void ejectorMovesOutput(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, IC2AutoBlocks.MACERATOR.get());
        helper.setBlock(new BlockPos(2, 1, 1), Blocks.CHEST);
        MachineBlockEntity macerator = helper.getBlockEntity(pos, MachineBlockEntity.class);
        macerator.getInventory().setItem(firstOutput(macerator.getGuiType().layout()), new ItemStack(Items.GRAVEL, 4));
        macerator.getInventory().setItem(macerator.getGuiType().layout().upgradeSlots().getFirst(), new ItemStack(IC2AutoItems.UPGRADE_EJECTOR.get()));
        helper.succeedWhen(() -> {
            ChestBlockEntity chest = helper.getBlockEntity(new BlockPos(2, 1, 1), ChestBlockEntity.class);
            if (chest.countItem(Items.GRAVEL) < 1) helper.fail("o baú deveria receber o cascalho");
        });
    }

    /** O kit transforma a MFE em MFSU guardando a energia. */
    @GameTest(maxTicks = 20)
    public void mfsuKitUpgradesMfe(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, IC2AutoBlocks.MFE.get());
        helper.getBlockEntity(pos, MachineBlockEntity.class).setStoredEnergy(EnergyUnits.fromCWh(1_000));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(IC2AutoItems.UPGRADE_KIT_MFSU.get()));
        BlockPos absolute = helper.absolutePos(pos);
        player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false)));
        if (!helper.getBlockState(pos).is(IC2AutoBlocks.MFSU.get())) helper.fail("deveria virar MFSU");
        if (helper.getBlockEntity(pos, MachineBlockEntity.class).getStoredEnergy() != EnergyUnits.fromCWh(1_000)) helper.fail("deveria manter a energia");
        helper.succeed();
    }
}
