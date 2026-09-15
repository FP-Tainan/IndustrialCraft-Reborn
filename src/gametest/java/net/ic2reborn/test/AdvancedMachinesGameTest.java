package net.ic2reborn.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.ic2reborn.block.entity.MachineBlockEntity;
import net.ic2reborn.energy.MachineEnergyProfile;
import net.ic2reborn.menu.MachineGuiType;
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

import java.util.List;

/** Advanced Machines: 13.800 MV, aquecimento que acelera, reciclador compactador e as máquinas de água. */
public class AdvancedMachinesGameTest {
    private static final BlockPos POS = new BlockPos(1, 1, 1);

    private static MachineBlockEntity place(GameTestHelper helper, Block block) {
        helper.setBlock(POS, block);
        MachineBlockEntity machine = helper.getBlockEntity(POS, MachineBlockEntity.class);
        machine.setStoredEnergy(machine.getEnergyProfile().capacity());
        return machine;
    }

    private static Item item(String path) {
        return BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("ic2reborn", path));
    }

    private static boolean outputsContain(MachineBlockEntity machine, int from, int to, Item item) {
        for (int slot = from; slot <= to; slot++) {
            if (machine.getInventory().getItem(slot).getItem() == item) return true;
        }
        return false;
    }

    /** Todas as oito são de fim de jogo: processadoras de 13.800 MV. */
    @GameTest(maxTicks = 20)
    public void advancedMachinesRunAtExtraHighVoltage(GameTestHelper helper) {
        List<Block> blocks = List.of(IC2AutoBlocks.ROTARY_MACERATOR.get(), IC2AutoBlocks.SINGULARITY_COMPRESSOR.get(),
                IC2AutoBlocks.CENTRIFUGE_EXTRACTOR.get(), IC2AutoBlocks.COMPACTING_RECYCLER.get(),
                IC2AutoBlocks.LIQUESCENT_EXTRUDER.get(), IC2AutoBlocks.IMPELLERIZED_ROLLER.get(),
                IC2AutoBlocks.WATER_JET_CUTTER.get(), IC2AutoBlocks.THERMAL_WASHER.get());
        for (Block block : blocks) {
            MachineGuiType type = MachineGuiType.fromBlockId(BuiltInRegistries.BLOCK.getKey(block).getPath());
            MachineEnergyProfile profile = MachineEnergyProfile.of(type);
            if (!type.isAdvancedMachine()) helper.fail(block + " deveria ser uma máquina avançada");
            if (profile.role() != MachineEnergyProfile.Role.PROCESSOR || profile.voltage() != 13_800) {
                helper.fail(block + " deveria processar em 13.800 MV, está " + profile);
            }
            if (type.layout().upgradeSlots().size() != 2) helper.fail(block + " deveria ter 2 slots de upgrade");
        }
        helper.succeed();
    }

    /** Overclocker não acelera (a velocidade vem do calor); o transformador sobe para 69.000 MV. */
    @GameTest(maxTicks = 40)
    public void overclockerIgnoredTransformerRaisesVoltage(GameTestHelper helper) {
        MachineBlockEntity machine = place(helper, IC2AutoBlocks.ROTARY_MACERATOR.get());
        List<Integer> upgrades = machine.getGuiType().layout().upgradeSlots();
        machine.getInventory().setItem(upgrades.get(0), new ItemStack(IC2AutoItems.UPGRADE_OVERCLOCKER.get(), 4));
        machine.getInventory().setItem(upgrades.get(1), new ItemStack(IC2AutoItems.UPGRADE_TRANSFORMER.get()));
        helper.succeedWhen(() -> {
            MachineEnergyProfile profile = machine.getEnergyProfile();
            if (profile.voltage() != 69_000) helper.fail("o transformador deveria subir para 69.000 MV, está " + profile.voltage());
            if (profile.power() != 30_000 || profile.operationTicks() != MachineEnergyProfile.ADVANCED_PROGRESS) {
                helper.fail("overclocker não deveria mudar consumo nem progresso: " + profile);
            }
        });
    }

    /** Frio, o triturador rotativo demora para a primeira operação, mas aquece e tritura o minério de ferro. */
    @GameTest(maxTicks = 700)
    public void rotaryMaceratorWarmsUpAndCrushes(GameTestHelper helper) {
        MachineBlockEntity machine = place(helper, IC2AutoBlocks.ROTARY_MACERATOR.get());
        machine.getInventory().setItem(0, new ItemStack(Items.IRON_ORE, 4));
        helper.runAfterDelay(100, () -> {
            if (outputsContain(machine, 1, 2, IC2AutoItems.CRUSHED_IRON.get())) helper.fail("frio, não deveria terminar em 100 ticks");
            if (machine.getHeat() <= 0) helper.fail("deveria estar aquecendo");
        });
        helper.succeedWhen(() -> {
            if (!outputsContain(machine, 1, 2, IC2AutoItems.CRUSHED_IRON.get())) helper.fail("ainda sem minério triturado");
        });
    }

    /** Reciclador compactador: 9 sucatas viram uma caixa de sucata. */
    @GameTest(maxTicks = 300)
    public void compactingRecyclerPacksScrap(GameTestHelper helper) {
        MachineBlockEntity machine = place(helper, IC2AutoBlocks.COMPACTING_RECYCLER.get());
        machine.getInventory().setItem(0, new ItemStack(IC2AutoItems.SCRAP.get(), 9));
        helper.succeedWhen(() -> {
            if (machine.getInventory().getItem(1).getItem() != IC2AutoItems.SCRAP_BOX.get()) helper.fail("ainda sem caixa de sucata");
            if (!machine.getInventory().getItem(0).isEmpty()) helper.fail("as 9 sucatas deveriam ser consumidas");
        });
    }

    /** Cortador a jato d'água: sem água não corta; com um balde no slot de recipiente corta a carcaça em moeda. */
    @GameTest(maxTicks = 700)
    public void waterJetCutterNeedsWater(GameTestHelper helper) {
        MachineBlockEntity machine = place(helper, IC2AutoBlocks.WATER_JET_CUTTER.get());
        machine.getInventory().setItem(0, new ItemStack(item("casing_iron")));
        helper.runAfterDelay(60, () -> {
            if (machine.getHeat() > 0) helper.fail("sem água não deveria aquecer");
            machine.getInventory().setItem(5, new ItemStack(Items.WATER_BUCKET));
        });
        helper.succeedWhen(() -> {
            if (machine.getInventory().getItem(1).getItem() != item("coin")) helper.fail("ainda sem moeda");
            if (machine.getInventory().getItem(6).getItem() != Items.BUCKET) helper.fail("o balde vazio deveria sair");
        });
    }

    /** Enlatadora a vácuo (13.800 MV): aquece e enlata a maçã em 4 latas, como a enlatadora comum. */
    @GameTest(maxTicks = 700)
    public void vacuumCannerCansFood(GameTestHelper helper) {
        MachineBlockEntity machine = place(helper, IC2AutoBlocks.VACUUM_CANNER.get());
        if (machine.getEnergyProfile().voltage() != 13_800) helper.fail("a enlatadora a vácuo deveria ser de 13.800 MV");
        machine.getInventory().setItem(0, new ItemStack(Items.APPLE));
        machine.getInventory().setItem(7, new ItemStack(IC2AutoItems.TIN_CAN.get(), 4));
        helper.succeedWhen(() -> {
            ItemStack output = machine.getInventory().getItem(1);
            if (output.getItem() != net.ic2reborn.registry.IC2Items.FILLED_TIN_CAN.get() || output.getCount() != 4) {
                helper.fail("ainda sem 4 latas cheias");
            }
        });
    }

    /** Lavadora térmica: água e minério triturado de ferro dão o purificado. */
    @GameTest(maxTicks = 700)
    public void thermalWasherWashesCrushedIron(GameTestHelper helper) {
        MachineBlockEntity machine = place(helper, IC2AutoBlocks.THERMAL_WASHER.get());
        machine.getInventory().setItem(7, new ItemStack(Items.WATER_BUCKET));
        machine.getInventory().setItem(0, new ItemStack(IC2AutoItems.CRUSHED_IRON.get()));
        helper.succeedWhen(() -> {
            if (!outputsContain(machine, 1, 3, IC2AutoItems.PURIFIED_IRON.get())) helper.fail("ainda sem minério purificado");
        });
    }
}
