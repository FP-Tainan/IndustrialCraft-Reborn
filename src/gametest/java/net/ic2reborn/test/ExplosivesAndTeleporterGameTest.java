package net.ic2reborn.test;

import net.craftenergy.api.EnergyUnits;
import net.craftenergy.content.item.ElectricItem;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.ic2reborn.block.DynamiteBlock;
import net.ic2reborn.block.entity.MachineBlockEntity;
import net.ic2reborn.block.entity.TeleporterBlockEntity;
import net.ic2reborn.item.MiningLaserItem;
import net.ic2reborn.registry.IC2AutoBlocks;
import net.ic2reborn.registry.IC2AutoItems;
import net.ic2reborn.registry.IC2Blocks;
import net.ic2reborn.registry.IC2Entities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Laser de mineração, dinamite com controle remoto e teletransportador com transmissor. */
public class ExplosivesAndTeleporterGameTest {
    private static void useOn(GameTestHelper helper, ServerPlayer player, BlockPos pos) {
        BlockPos absolute = helper.absolutePos(pos);
        player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false)));
    }

    /** O feixe de mineração quebra a fileira de pedra à frente. */
    @GameTest(maxTicks = 20)
    public void laserMinesStone(GameTestHelper helper) {
        for (int x = 2; x <= 4; x++) {
            helper.setBlock(new BlockPos(x, 2, 1), Blocks.STONE);
        }
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 start = Vec3.atCenterOf(helper.absolutePos(new BlockPos(0, 2, 1)));
        MiningLaserItem.shoot(helper.getLevel(), player, start, new Vec3(1, 0, 0), Float.POSITIVE_INFINITY, 5.0F, Integer.MAX_VALUE, false, false);
        for (int x = 2; x <= 4; x++) {
            if (!helper.getBlockState(new BlockPos(x, 2, 1)).isAir()) helper.fail("a pedra em x=" + x + " deveria ser quebrada");
        }
        helper.succeed();
    }

    /** Dinamite colocada, vinculada ao controle e detonada vira dinamite acesa. */
    @GameTest(maxTicks = 20)
    public void remoteDetonatesDynamite(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(IC2AutoItems.DYNAMITE.get()));
        useOn(helper, player, new BlockPos(1, 1, 1));
        BlockPos dynamite = new BlockPos(1, 2, 1);
        if (!helper.getBlockState(dynamite).is(IC2Blocks.DYNAMITE.get())) helper.fail("a dinamite deveria ser colocada");

        ItemStack remote = new ItemStack(IC2AutoItems.REMOTE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, remote);
        useOn(helper, player, dynamite);
        if (!helper.getBlockState(dynamite).getValue(DynamiteBlock.LINKED)) helper.fail("a dinamite deveria ficar vinculada");
        remote.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        if (!helper.getBlockState(dynamite).isAir()) helper.fail("o controle deveria acender a dinamite");
        helper.assertEntityPresent(IC2Entities.DYNAMITE.get());
        helper.killAllEntitiesOfClass(net.ic2reborn.entity.DynamiteEntity.class);
        helper.succeed();
    }

    /** Dois teletransportadores ligados pelo transmissor levam o item usando energia da BatBox. */
    @GameTest(maxTicks = 100)
    public void teleporterMovesItem(GameTestHelper helper) {
        BlockPos from = new BlockPos(1, 1, 1);
        BlockPos to = new BlockPos(6, 1, 1);
        helper.setBlock(from, IC2AutoBlocks.TELEPORTER.get());
        helper.setBlock(to, IC2AutoBlocks.TELEPORTER.get());
        helper.setBlock(new BlockPos(1, 1, 2), IC2AutoBlocks.BATBOX.get());
        helper.getBlockEntity(new BlockPos(1, 1, 2), MachineBlockEntity.class).setStoredEnergy(EnergyUnits.fromCWh(10_000));

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(IC2AutoItems.FREQUENCY_TRANSMITTER.get()));
        useOn(helper, player, from);
        useOn(helper, player, to);
        if (!helper.absolutePos(to).equals(helper.getBlockEntity(from, TeleporterBlockEntity.class).getTarget())) {
            helper.fail("o transmissor deveria ligar os teletransportadores");
        }

        helper.spawnItem(Items.DIAMOND, new BlockPos(1, 2, 1));
        helper.setBlock(new BlockPos(0, 1, 1), Blocks.REDSTONE_BLOCK);
        helper.succeedWhen(() -> {
            boolean arrived = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                    new net.minecraft.world.phys.AABB(helper.absolutePos(to)).inflate(1.5, 2.5, 1.5)).stream()
                    .anyMatch(item -> item.getItem().is(Items.DIAMOND));
            if (!arrived) helper.fail("o diamante deveria chegar no outro teletransportador");
        });
    }

    /** O laser carregado troca de modo agachado. */
    @GameTest(maxTicks = 20)
    public void laserChangesMode(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack laser = ((ElectricItem) IC2AutoItems.MINING_LASER.get()).charged();
        player.setItemInHand(InteractionHand.MAIN_HAND, laser);
        player.setShiftKeyDown(true);
        laser.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        if (MiningLaserItem.mode(laser) != MiningLaserItem.Mode.LOW_FOCUS) helper.fail("deveria ir para baixo foco, está " + MiningLaserItem.mode(laser));
        helper.succeed();
    }
}
