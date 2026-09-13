package net.ic2reborn.test;

import net.craftenergy.api.EnergyUnits;
import net.craftenergy.content.CEComponents;
import net.craftenergy.content.item.EnergyItem;
import net.craftenergy.content.item.EnergyItems;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.ic2reborn.block.MachineBlock;
import net.ic2reborn.block.entity.MachineBlockEntity;
import net.ic2reborn.menu.MachineMenu;
import net.ic2reborn.registry.IC2AutoBlocks;
import net.ic2reborn.registry.IC2AutoItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ArmorSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Energia guardada nos armazenamentos quebrados, chave inglesa elétrica e armadura na GUI. */
public class StorageAndToolsGameTest {
    /** BatBox com 10.000 CWh solta o item com 8.000 CWh, e colocada de novo volta com essa energia. */
    @GameTest(maxTicks = 20)
    public void batboxKeepsEnergyInItem(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, IC2AutoBlocks.BATBOX.get());
        MachineBlockEntity batbox = helper.getBlockEntity(pos, MachineBlockEntity.class);
        batbox.setStoredEnergy(EnergyUnits.fromCWh(10_000));

        List<ItemStack> drops = Block.getDrops(helper.getBlockState(pos), helper.getLevel(), helper.absolutePos(pos), batbox);
        ItemStack drop = drops.stream().filter(stack -> stack.getItem() == IC2AutoItems.BATBOX.get()).findFirst().orElse(ItemStack.EMPTY);
        Long stored = drop.get(CEComponents.STORED_ENERGY.get());
        if (stored == null || stored != EnergyUnits.fromCWh(8_000)) helper.fail("o item deveria guardar 8.000 CWh, guardou " + stored);

        BlockPos other = new BlockPos(3, 1, 1);
        helper.setBlock(other, IC2AutoBlocks.BATBOX.get());
        MachineBlockEntity placed = helper.getBlockEntity(other, MachineBlockEntity.class);
        placed.applyComponentsFromItemStack(drop);
        if (placed.getStoredEnergy() != EnergyUnits.fromCWh(8_000)) helper.fail("colocada de novo deveria ter 8.000 CWh");
        helper.succeed();
    }

    /** Chave elétrica carregada gira gastando 50 CWh; vazia não faz nada. */
    @GameTest(maxTicks = 20)
    public void electricWrenchUsesEnergy(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, IC2AutoBlocks.BATBOX.get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getAbilities().instabuild = false;

        ItemStack empty = new ItemStack(IC2AutoItems.ELECTRIC_WRENCH.get());
        use(helper, player, empty, pos);
        if (helper.getBlockState(pos).getValue(MachineBlock.FACING) != Direction.NORTH) helper.fail("vazia não deveria girar");

        ItemStack charged = new ItemStack(IC2AutoItems.ELECTRIC_WRENCH.get());
        long capacity = ((EnergyItem) charged.getItem()).energyCapacity(charged);
        EnergyItems.setStored(charged, capacity);
        use(helper, player, charged, pos);
        if (helper.getBlockState(pos).getValue(MachineBlock.FACING) != Direction.EAST) helper.fail("carregada deveria girar para leste");
        if (EnergyItems.getStored(charged) != capacity - EnergyUnits.fromCWh(50)) helper.fail("girar deveria gastar 50 CWh");
        helper.succeed();
    }

    /** A GUI dos armazenamentos tem os 4 slots de armadura do jogador. */
    @GameTest(maxTicks = 20)
    public void storageMenuHasArmorSlots(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, IC2AutoBlocks.CESU.get());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        MachineMenu menu = new MachineMenu(0, player.getInventory(), helper.getBlockEntity(pos, MachineBlockEntity.class));
        long armor = menu.slots.stream().filter(slot -> slot instanceof ArmorSlot).count();
        if (armor != 4) helper.fail("deveria ter 4 slots de armadura, tem " + armor);
        helper.succeed();
    }

    private static void use(GameTestHelper helper, ServerPlayer player, ItemStack stack, BlockPos pos) {
        BlockPos absolute = helper.absolutePos(pos);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute).add(0.5, 0.0, 0.0), Direction.EAST, absolute, false);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }
}
