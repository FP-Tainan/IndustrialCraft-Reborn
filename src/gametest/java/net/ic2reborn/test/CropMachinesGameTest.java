package net.ic2reborn.test;

import net.craftenergy.api.EnergyUnits;
import net.craftenergy.content.item.ElectricItem;
import net.craftenergy.content.item.EnergyItems;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.ic2reborn.block.entity.MachineBlockEntity;
import net.ic2reborn.crop.CropBlockEntity;
import net.ic2reborn.crop.CropCards;
import net.ic2reborn.fluid.IC2Fluids;
import net.ic2reborn.item.CropSeedItem;
import net.ic2reborn.item.CropnalyzerItem;
import net.ic2reborn.menu.CropnalyzerMenu;
import net.ic2reborn.registry.IC2AutoBlocks;
import net.ic2reborn.registry.IC2AutoItems;
import net.ic2reborn.registry.IC2Blocks;
import net.ic2reborn.registry.IC2Items;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/** Colheitadeira, Cropmatron, Cropnalyzer e célula de hidratação. */
public class CropMachinesGameTest {
    /** Posições olhadas numa volta completa (9 × 9 × 3). */
    private static final int SCAN_CYCLE = 9 * 9 * 3;
    private static final BlockPos MACHINE = new BlockPos(4, 2, 4);

    private static CropBlockEntity wheat(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos.below(), Blocks.FARMLAND);
        helper.setBlock(pos, IC2Blocks.CROP.get());
        CropBlockEntity crop = helper.getBlockEntity(pos, CropBlockEntity.class);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHEAT_SEEDS));
        if (!crop.useItem(player, InteractionHand.MAIN_HAND)) helper.fail("deveria plantar trigo");
        return crop;
    }

    private static MachineBlockEntity machine(GameTestHelper helper, Block block) {
        helper.setBlock(MACHINE, block);
        MachineBlockEntity machine = helper.getBlockEntity(MACHINE, MachineBlockEntity.class);
        machine.setStoredEnergy(EnergyUnits.fromCWh(10_000));
        return machine;
    }

    private static int count(MachineBlockEntity machine, Item item) {
        int total = 0;
        for (int slot = 1; slot <= 15; slot++) {
            ItemStack stack = machine.getInventory().getItem(slot);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    private static void fill(GameTestHelper helper, MachineBlockEntity machine, Fluid fluid) {
        try (Transaction transaction = Transaction.openOuter()) {
            long inserted = machine.getFluidStorage(null).insert(FluidVariant.of(fluid), FluidConstants.BUCKET, transaction);
            if (inserted != FluidConstants.BUCKET) helper.fail("o Cropmatron deveria aceitar 1 balde de " + fluid);
            transaction.commit();
        }
    }

    /** A colheitadeira acha o trigo maduro na área e guarda a colheita. */
    @GameTest(maxTicks = 20)
    public void harvesterCollectsMatureCrop(GameTestHelper helper) {
        MachineBlockEntity harvester = machine(helper, IC2AutoBlocks.CROP_HARVESTER.get());
        CropBlockEntity crop = wheat(helper, new BlockPos(4, 2, 6));
        boolean harvested = false;
        for (int i = 0; i < SCAN_CYCLE * 20 && !harvested; i++) {
            if (crop.getSize() < 7) crop.setSize(7);
            harvester.performCropScan();
            harvested = count(harvester, Items.WHEAT) > 0;
        }
        if (!harvested) helper.fail("a colheitadeira deveria guardar o trigo");
        helper.succeed();
    }

    /** Numa volta o Cropmatron fertiliza, rega, põe herbicida na planta e molha a terra arada vizinha. */
    @GameTest(maxTicks = 20)
    public void cropmatronCaresForCrops(GameTestHelper helper) {
        MachineBlockEntity cropmatron = machine(helper, IC2AutoBlocks.CROPMATRON.get());
        cropmatron.getInventory().setItem(1, new ItemStack(IC2AutoItems.FERTILIZER.get(), 2));
        fill(helper, cropmatron, Fluids.WATER);
        fill(helper, cropmatron, IC2Fluids.WEED_EX.fluid());
        CropBlockEntity crop = wheat(helper, new BlockPos(5, 2, 4));

        for (int i = 0; i < SCAN_CYCLE; i++) {
            cropmatron.performCropScan();
        }
        if (crop.getStorageNutrients() != 90) helper.fail("nutrientes deveriam ser 90, são " + crop.getStorageNutrients());
        if (crop.getStorageWater() != 200) helper.fail("água deveria ser 200, é " + crop.getStorageWater());
        if (crop.getStorageWeedEx() != 150) helper.fail("herbicida deveria ser 150, é " + crop.getStorageWeedEx());
        if (cropmatron.getInventory().getItem(1).getCount() != 1) helper.fail("deveria gastar um fertilizante");
        int moisture = helper.getBlockState(new BlockPos(5, 1, 4)).getValue(FarmlandBlock.MOISTURE);
        if (moisture != 7) helper.fail("a terra arada deveria ficar molhada, umidade " + moisture);
        helper.succeed();
    }

    /** O Cropnalyzer analisa o saco nível por nível até 4, gastando 10 + 90 + 900 + 9.000 CWh. */
    @GameTest(maxTicks = 20)
    public void cropnalyzerScansSeedBag(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setItemInHand(InteractionHand.MAIN_HAND, ((ElectricItem) IC2AutoItems.CROPNALYZER.get()).charged());
        CropnalyzerMenu menu = new CropnalyzerMenu(1, player.getInventory(), InteractionHand.MAIN_HAND);
        menu.getSlot(CropnalyzerMenu.INPUT).set(CropSeedItem.create(CropCards.get("ferru"), 1, 2, 3, 0));

        for (int level = 1; level <= 4; level++) {
            menu.tickScan();
            ItemStack scanned = menu.getSlot(CropnalyzerMenu.OUTPUT).getItem();
            CropSeedItem.CropSeed seed = CropSeedItem.data(scanned);
            if (seed == null || seed.scan() != level) helper.fail("o saco deveria estar no nível " + level + ": " + seed);
            menu.getSlot(CropnalyzerMenu.OUTPUT).set(ItemStack.EMPTY);
            menu.getSlot(CropnalyzerMenu.INPUT).set(scanned);
        }
        long used = CropnalyzerItem.CAPACITY - EnergyItems.getStored(player.getMainHandItem());
        if (used != EnergyUnits.fromCWh(10 + 90 + 900 + 9_000)) helper.fail("deveria gastar 10.000 CWh, gastou " + used);
        helper.succeed();
    }

    /** A célula de hidratação enche a água da planta até 200 e perde essa água. */
    @GameTest(maxTicks = 20)
    public void hydrationCellWatersCrop(GameTestHelper helper) {
        CropBlockEntity crop = wheat(helper, new BlockPos(1, 2, 1));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(IC2Items.HYDRATION_CELL.get()));
        if (!crop.useItem(player, InteractionHand.MAIN_HAND) || crop.getStorageWater() != 200) {
            helper.fail("a célula deveria encher a água até 200, ficou " + crop.getStorageWater());
        }
        if (player.getMainHandItem().getDamageValue() != 200) helper.fail("a célula deveria perder 200 mB");
        helper.succeed();
    }
}
