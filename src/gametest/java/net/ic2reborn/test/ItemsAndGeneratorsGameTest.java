package net.ic2reborn.test;

import net.craftenergy.api.EnergyUnits;
import net.craftenergy.content.item.BatteryItem;
import net.craftenergy.content.item.EnergyItems;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.ic2reborn.block.MachineBlock;
import net.ic2reborn.block.entity.MachineBlockEntity;
import net.ic2reborn.energy.WindSim;
import net.ic2reborn.menu.MachineMenu;
import net.ic2reborn.registry.IC2AutoBlocks;
import net.ic2reborn.registry.IC2AutoItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Botões do transformador, slots de carga/descarga com baterias e geradores sem combustível. */
public class ItemsAndGeneratorsGameTest {
    private static final int GENERATOR_FUEL_SLOT = 1;
    private static final int STORAGE_CHARGE_SLOT = 0;
    private static final int PROCESSOR_INPUT_SLOT = 0;
    private static final int PROCESSOR_OUTPUT_SLOT = 1;
    private static final int PROCESSOR_DISCHARGE_SLOT = 2;
    private static final int WATER_FUEL_SLOT = 0;

    // ── transformador ─────────────────────────────────────────────────────

    /** Botão "eleva fixo": gerador (220) → lateral do transformador → frente (1.000 MV) → macerador explode. */
    @GameTest(maxTicks = 100)
    public void transformerStepUpButtonRaisesVoltage(GameTestHelper helper) {
        BlockPos transformerPos = new BlockPos(2, 1, 1);
        BlockPos maceratorPos = new BlockPos(3, 1, 1);
        BlockPos generator = new BlockPos(1, 1, 1);
        helper.setBlock(generator, IC2AutoBlocks.GENERATOR.get());
        helper.setBlock(transformerPos, facing(IC2AutoBlocks.LV_TRANSFORMER.get(), Direction.EAST));
        helper.setBlock(maceratorPos, IC2AutoBlocks.MACERATOR.get());
        machine(helper, generator).getInventory().setItem(GENERATOR_FUEL_SLOT, new ItemStack(Items.COAL));

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        MachineBlockEntity transformer = machine(helper, transformerPos);
        MachineMenu menu = new MachineMenu(0, player.getInventory(), transformer);
        if (!menu.clickMenuButton(player, 2)) helper.fail("o botão de modo deveria ser aceito");
        if (transformer.getTransformerMode() != MachineBlockEntity.TransformerMode.STEP_UP) helper.fail("modo deveria ser eleva fixo");

        helper.succeedWhen(() -> helper.assertBlockPresent(Blocks.AIR, maceratorPos));
    }

    /** Modo redstone (padrão): com um bloco de redstone encostado, o transformador eleva a tensão. */
    @GameTest(maxTicks = 100)
    public void redstoneModeStepsUpWhenPowered(GameTestHelper helper) {
        BlockPos maceratorPos = new BlockPos(3, 1, 1);
        BlockPos generator = new BlockPos(1, 1, 1);
        helper.setBlock(generator, IC2AutoBlocks.GENERATOR.get());
        helper.setBlock(new BlockPos(2, 1, 1), facing(IC2AutoBlocks.LV_TRANSFORMER.get(), Direction.EAST));
        helper.setBlock(new BlockPos(2, 2, 1), Blocks.REDSTONE_BLOCK);
        helper.setBlock(maceratorPos, IC2AutoBlocks.MACERATOR.get());
        machine(helper, generator).getInventory().setItem(GENERATOR_FUEL_SLOT, new ItemStack(Items.COAL));

        helper.succeedWhen(() -> helper.assertBlockPresent(Blocks.AIR, maceratorPos));
    }

    // ── baterias ──────────────────────────────────────────────────────────

    /** BatBox carregada enche a RE-Battery do slot de carga. */
    @GameTest(maxTicks = 40)
    public void batboxChargesBatteryInChargeSlot(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, IC2AutoBlocks.BATBOX.get());
        MachineBlockEntity batbox = machine(helper, pos);
        batbox.setStoredEnergy(EnergyUnits.fromCWh(10_000));
        batbox.getInventory().setItem(STORAGE_CHARGE_SLOT, new ItemStack(IC2AutoItems.RE_BATTERY.get()));

        helper.succeedWhen(() -> {
            ItemStack battery = batbox.getInventory().getItem(STORAGE_CHARGE_SLOT);
            if (EnergyItems.getStored(battery) <= 0) helper.fail("a bateria ainda está vazia");
        });
    }

    /** Cristal de energia (2.400 MV) não carrega numa BatBox (220 MV). */
    @GameTest(maxTicks = 20)
    public void batboxRefusesHigherVoltageBattery(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, IC2AutoBlocks.BATBOX.get());
        MachineBlockEntity batbox = machine(helper, pos);
        batbox.setStoredEnergy(EnergyUnits.fromCWh(10_000));
        batbox.getInventory().setItem(STORAGE_CHARGE_SLOT, new ItemStack(IC2AutoItems.ENERGY_CRYSTAL.get()));

        helper.runAfterDelay(10, () -> {
            if (EnergyItems.getStored(batbox.getInventory().getItem(STORAGE_CHARGE_SLOT)) != 0) {
                helper.fail("a BatBox não deveria carregar um cristal de 2.400 MV");
            }
            helper.succeed();
        });
    }

    /** Macerador sem rede, só com uma RE-Battery carregada no slot de descarga: ferro vira pó. */
    @GameTest(maxTicks = 600)
    public void maceratorRunsFromBatteryInDischargeSlot(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, IC2AutoBlocks.MACERATOR.get());
        MachineBlockEntity macerator = machine(helper, pos);
        macerator.getInventory().setItem(PROCESSOR_DISCHARGE_SLOT, ((BatteryItem) IC2AutoItems.RE_BATTERY.get()).charged());
        macerator.getInventory().setItem(PROCESSOR_INPUT_SLOT, new ItemStack(Items.IRON_INGOT));

        helper.succeedWhen(() -> {
            if (macerator.getInventory().getItem(PROCESSOR_OUTPUT_SLOT).getItem() != IC2AutoItems.DUST_IRON.get()) {
                helper.fail("o macerador ainda não produziu pó de ferro");
            }
        });
    }

    /** Bateria descartável vem cheia, descarrega na máquina e some ao esvaziar. */
    @GameTest(maxTicks = 40)
    public void singleUseBatteryIsConsumed(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, IC2AutoBlocks.MACERATOR.get());
        MachineBlockEntity macerator = machine(helper, pos);
        macerator.getInventory().setItem(PROCESSOR_DISCHARGE_SLOT, new ItemStack(IC2AutoItems.SINGLE_USE_BATTERY.get()));

        helper.succeedWhen(() -> {
            if (!macerator.getInventory().getItem(PROCESSOR_DISCHARGE_SLOT).isEmpty()) helper.fail("a bateria descartável ainda está no slot");
            if (macerator.getStoredEnergy() <= 0) helper.fail("o macerador deveria ter recebido a energia");
        });
    }

    // ── geradores ─────────────────────────────────────────────────────────

    /** Gerador de água com um balde: produz e devolve o balde vazio. */
    @GameTest(maxTicks = 40)
    public void waterGeneratorProducesFromBucket(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, IC2AutoBlocks.WATER_GENERATOR.get());
        MachineBlockEntity generator = machine(helper, pos);
        generator.getInventory().setItem(WATER_FUEL_SLOT, new ItemStack(Items.WATER_BUCKET));

        helper.succeedWhen(() -> {
            if (generator.getStoredEnergy() <= 0) helper.fail("o gerador de água ainda não produziu");
            if (generator.getInventory().getItem(WATER_FUEL_SLOT).getItem() != Items.BUCKET) helper.fail("deveria devolver o balde vazio");
        });
    }

    /** Curva de vento do IC2: vale 1 no pico e 0 em 1,125 × a altura do mundo. */
    @GameTest(maxTicks = 20)
    public void windCurveMatchesIc2(GameTestHelper helper) {
        int height = 320;
        int sea = 63;
        double[] c = WindSim.coefficients(height, sea);
        double peak = WindSim.peakHeight(height, sea);
        double atPeak = c[0] * peak + c[1] * peak * peak + c[2] * peak * peak * peak;
        double zero = height * 1.125;
        double atZero = c[0] * zero + c[1] * zero * zero + c[2] * zero * zero * zero;
        if (Math.abs(atPeak - 1.0) > 1e-6) helper.fail("no pico a curva deveria ser 1, veio " + atPeak);
        if (Math.abs(atZero) > 1e-6) helper.fail("em 1,125 × altura a curva deveria ser 0, veio " + atZero);
        helper.succeed();
    }

    private static BlockState facing(Block block, Direction direction) {
        return block.defaultBlockState().setValue(MachineBlock.FACING, direction);
    }

    private static MachineBlockEntity machine(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos, MachineBlockEntity.class);
    }
}
