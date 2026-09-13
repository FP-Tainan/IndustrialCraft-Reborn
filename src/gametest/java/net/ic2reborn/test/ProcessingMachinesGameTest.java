package net.ic2reborn.test;

import net.craftenergy.content.item.BatteryItem;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.ic2reborn.block.entity.MachineBlockEntity;
import net.ic2reborn.registry.IC2AutoBlocks;
import net.ic2reborn.registry.IC2AutoItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/** Conformador de metal, cortador de blocos, forno de indução e centrífuga térmica. */
public class ProcessingMachinesGameTest {
    private static final BlockPos POS = new BlockPos(1, 1, 1);

    /** Modo inicial (extrudar): lingote de cobre vira 3 cabos de cobre. */
    @GameTest(maxTicks = 300)
    public void metalFormerExtrudesCopperCable(GameTestHelper helper) {
        MachineBlockEntity former = machine(helper, IC2AutoBlocks.METAL_FORMER.get(), lowBattery(), 2);
        former.getInventory().setItem(0, new ItemStack(Items.COPPER_INGOT));

        helper.succeedWhen(() -> expectOutput(helper, former, 1, item("craftenergy:cable_copper"), 3));
    }

    /** Botão de modo uma vez (laminar): lingote de ferro vira placa de ferro. */
    @GameTest(maxTicks = 300)
    public void metalFormerRollsIronPlate(GameTestHelper helper) {
        MachineBlockEntity former = machine(helper, IC2AutoBlocks.METAL_FORMER.get(), lowBattery(), 2);
        if (!former.handleMenuButton(MachineBlockEntity.BUTTON_METAL_FORMER_MODE)) helper.fail("o botão de modo deveria ser aceito");
        former.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT));

        helper.succeedWhen(() -> expectOutput(helper, former, 1, item("craftenergy:plate_iron"), 1));
    }

    /** Lâmina de aço (dureza 6) corta bloco de ferro (dureza 5) em 9 placas. */
    @GameTest(maxTicks = 600)
    public void blockCutterCutsIronBlock(GameTestHelper helper) {
        MachineBlockEntity cutter = machine(helper, IC2AutoBlocks.BLOCK_CUTTER.get(), lowBattery(), 2);
        cutter.getInventory().setItem(3, new ItemStack(IC2AutoItems.BLOCK_CUTTING_BLADE_STEEL.get()));
        cutter.getInventory().setItem(0, new ItemStack(Items.IRON_BLOCK));

        helper.succeedWhen(() -> expectOutput(helper, cutter, 1, item("craftenergy:plate_iron"), 9));
    }

    /** Lâmina de ferro (dureza 3) não corta bloco de aço (dureza 8). */
    @GameTest(maxTicks = 40)
    public void blockCutterRefusesWeakBlade(GameTestHelper helper) {
        MachineBlockEntity cutter = machine(helper, IC2AutoBlocks.BLOCK_CUTTER.get(), lowBattery(), 2);
        cutter.getInventory().setItem(3, new ItemStack(IC2AutoItems.BLOCK_CUTTING_BLADE_IRON.get()));
        cutter.getInventory().setItem(0, new ItemStack(item("ic2reborn:steel_block")));

        helper.runAfterDelay(20, () -> {
            if (!cutter.isBladeTooWeak()) helper.fail("deveria avisar que a lâmina é fraca");
            if (!cutter.getInventory().getItem(1).isEmpty()) helper.fail("não deveria cortar");
            helper.succeed();
        });
    }

    /** Forno de indução (1.000 MV) aquece e funde as duas entradas de uma vez. */
    @GameTest(maxTicks = 900)
    public void inductionFurnaceSmeltsBothInputs(GameTestHelper helper) {
        MachineBlockEntity furnace = machine(helper, IC2AutoBlocks.INDUCTION_FURNACE.get(), advancedBattery(), 4);
        furnace.getInventory().setItem(0, new ItemStack(Items.RAW_IRON));
        furnace.getInventory().setItem(1, new ItemStack(Items.RAW_GOLD));

        helper.succeedWhen(() -> {
            expectOutput(helper, furnace, 2, Items.IRON_INGOT, 1);
            expectOutput(helper, furnace, 3, Items.GOLD_INGOT, 1);
            if (furnace.getHeat() <= 0) helper.fail("deveria ter aquecido");
        });
    }

    /** Centrífuga térmica aquece até 100 e transforma pedregulho em pó de pedra. */
    @GameTest(maxTicks = 900)
    public void thermalCentrifugeHeatsAndProcesses(GameTestHelper helper) {
        MachineBlockEntity centrifuge = machine(helper, IC2AutoBlocks.CENTRIFUGE.get(), advancedBattery(), 1);
        centrifuge.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE));

        helper.succeedWhen(() -> expectOutput(helper, centrifuge, 2, IC2AutoItems.DUST_STONE.get(), 1));
    }

    private static MachineBlockEntity machine(GameTestHelper helper, Block block, ItemStack battery, int dischargeSlot) {
        helper.setBlock(POS, block);
        MachineBlockEntity machine = helper.getBlockEntity(POS, MachineBlockEntity.class);
        machine.getInventory().setItem(dischargeSlot, battery);
        return machine;
    }

    private static ItemStack lowBattery() {
        return ((BatteryItem) IC2AutoItems.RE_BATTERY.get()).charged();
    }

    private static ItemStack advancedBattery() {
        return ((BatteryItem) IC2AutoItems.ADVANCED_RE_BATTERY.get()).charged();
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).orElseThrow();
    }

    private static void expectOutput(GameTestHelper helper, MachineBlockEntity machine, int slot, Item item, int count) {
        ItemStack stack = machine.getInventory().getItem(slot);
        if (stack.getItem() != item || stack.getCount() < count) {
            helper.fail("slot " + slot + " deveria ter " + count + " " + item + ", tem " + stack);
        }
    }
}
