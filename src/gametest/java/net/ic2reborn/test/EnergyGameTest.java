package net.ic2reborn.test;

import net.craftenergy.api.EnergyUnits;
import net.craftenergy.content.CEBlocks;
import net.craftenergy.content.CEItems;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.ic2reborn.block.MachineBlock;
import net.ic2reborn.block.entity.MachineBlockEntity;
import net.ic2reborn.registry.IC2AutoBlocks;
import net.ic2reborn.registry.IC2AutoItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Testes no mundo: máquinas do IC2 Reborn ligadas pela rede do Craft Energy. */
public class EnergyGameTest {
    private static final int GENERATOR_FUEL_SLOT = 1;
    private static final int STORAGE_DISCHARGE_SLOT = 1;
    private static final int PROCESSOR_INPUT_SLOT = 0;
    private static final int PROCESSOR_OUTPUT_SLOT = 1;

    // ── gerador, cabos e armazenamento ────────────────────────────────────

    /** Gerador (5.000 CW, 220 MV) → 2 cabos de cobre → macerador (2.000 CW): ferro vira pó. */
    @GameTest(maxTicks = 900)
    public void generatorPowersMaceratorThroughCopperCable(GameTestHelper helper) {
        MachineBlockEntity macerator = poweredProcessor(helper, IC2AutoBlocks.MACERATOR.get(), new ItemStack(Items.IRON_INGOT));
        helper.succeedWhen(() -> expectOutput(helper, macerator, IC2AutoItems.DUST_IRON.get(), 1));
    }

    /** Gerador → cobre → entrada lateral da BatBox: carrega a 4.400 CW (20 RA, no limite do cobre). */
    @GameTest(maxTicks = 200)
    public void batboxStoresGeneratorEnergy(GameTestHelper helper) {
        BlockPos generator = new BlockPos(1, 1, 1);
        BlockPos batboxPos = new BlockPos(3, 1, 1);
        helper.setBlock(generator, IC2AutoBlocks.GENERATOR.get());
        cableLine(helper, CEBlocks.CABLE_COPPER.get(), 2, 2);
        helper.setBlock(batboxPos, facing(IC2AutoBlocks.BATBOX.get(), Direction.NORTH));

        machine(helper, generator).getInventory().setItem(GENERATOR_FUEL_SLOT, new ItemStack(Items.COAL));
        MachineBlockEntity batbox = machine(helper, batboxPos);

        helper.succeedWhen(() -> {
            if (batbox.getStoredEnergy() <= 0) helper.fail("a BatBox ainda está vazia");
        });
    }

    /**
     * A montagem do jogo: gerador (220 MV) → CESU (entrada lateral) → saída da CESU (1.000 MV, cabo
     * isolado) → frente do transformador 220/1.000 → BatBox. A CESU carrega e repassa para a BatBox.
     */
    @GameTest(maxTicks = 400)
    public void cesuChargesFromGeneratorAndFeedsBatboxThroughTransformer(GameTestHelper helper) {
        BlockPos generator = new BlockPos(0, 1, 1);
        BlockPos cesuPos = new BlockPos(2, 1, 1);
        BlockPos batboxPos = new BlockPos(6, 1, 1);
        helper.setBlock(generator, IC2AutoBlocks.GENERATOR.get());
        helper.setBlock(new BlockPos(1, 1, 1), CEBlocks.CABLE_COPPER.get());
        helper.setBlock(cesuPos, facing(IC2AutoBlocks.CESU.get(), Direction.EAST));
        helper.setBlock(new BlockPos(3, 1, 1), CEBlocks.CABLE_COPPER_INSULATED.get());
        helper.setBlock(new BlockPos(4, 1, 1), facing(IC2AutoBlocks.LV_TRANSFORMER.get(), Direction.WEST));
        helper.setBlock(new BlockPos(5, 1, 1), CEBlocks.CABLE_COPPER.get());
        helper.setBlock(batboxPos, facing(IC2AutoBlocks.BATBOX.get(), Direction.NORTH));

        machine(helper, generator).getInventory().setItem(GENERATOR_FUEL_SLOT, new ItemStack(Items.COAL, 4));
        MachineBlockEntity cesu = machine(helper, cesuPos);
        MachineBlockEntity batbox = machine(helper, batboxPos);

        helper.succeedWhen(() -> {
            if (cesu.getStoredEnergy() <= 0 && batbox.getStoredEnergy() <= 0) helper.fail("a CESU não recebeu energia do gerador");
            if (batbox.getStoredEnergy() <= 0) helper.fail("a BatBox não recebeu energia pela CESU e pelo transformador");
        });
    }

    /** Pó de redstone no slot de descarga da BatBox vira 400 CWh, sem rede nenhuma. */
    @GameTest(maxTicks = 40)
    public void redstoneChargesBatbox(GameTestHelper helper) {
        BlockPos batboxPos = new BlockPos(1, 1, 1);
        helper.setBlock(batboxPos, IC2AutoBlocks.BATBOX.get());
        MachineBlockEntity batbox = machine(helper, batboxPos);
        batbox.getInventory().setItem(STORAGE_DISCHARGE_SLOT, new ItemStack(Items.REDSTONE, 2));

        helper.succeedWhen(() -> {
            if (batbox.getStoredEnergy() < 2 * MachineBlockEntity.REDSTONE_ENERGY) helper.fail("a redstone ainda não virou energia");
            if (!batbox.getInventory().getItem(STORAGE_DISCHARGE_SLOT).isEmpty()) helper.fail("a redstone deveria ter sido consumida");
        });
    }

    /** Gerador → estanho (10 RA) → BatBox puxando 20 RA: o cabo esquenta e queima. */
    @GameTest(maxTicks = 200)
    public void overloadedTinCableBurns(GameTestHelper helper) {
        BlockPos generator = new BlockPos(1, 1, 1);
        BlockPos cable = new BlockPos(2, 1, 1);
        helper.setBlock(generator, IC2AutoBlocks.GENERATOR.get());
        helper.setBlock(cable, CEBlocks.CABLE_TIN.get());
        helper.setBlock(new BlockPos(3, 1, 1), facing(IC2AutoBlocks.BATBOX.get(), Direction.NORTH));

        machine(helper, generator).getInventory().setItem(GENERATOR_FUEL_SLOT, new ItemStack(Items.COAL));

        helper.succeedWhen(() -> helper.assertBlockPresent(Blocks.AIR, cable));
    }

    /** Saída da CESU (1.000 MV) direto num macerador (220 MV): sobretensão, o macerador explode. */
    @GameTest(maxTicks = 100)
    public void maceratorExplodesOnMediumVoltage(GameTestHelper helper) {
        BlockPos macerator = new BlockPos(2, 1, 1);
        helper.setBlock(new BlockPos(1, 1, 1), facing(IC2AutoBlocks.CESU.get(), Direction.EAST));
        helper.setBlock(macerator, IC2AutoBlocks.MACERATOR.get());

        helper.succeedWhen(() -> helper.assertBlockPresent(Blocks.AIR, macerator));
    }

    /** CESU carregada → frente do transformador 220/1.000 → macerador (220 MV): funciona sem explodir. */
    @GameTest(maxTicks = 900)
    public void transformerStepsDownForMacerator(GameTestHelper helper) {
        BlockPos cesuPos = new BlockPos(1, 1, 1);
        BlockPos maceratorPos = new BlockPos(3, 1, 1);
        helper.setBlock(cesuPos, facing(IC2AutoBlocks.CESU.get(), Direction.EAST));
        helper.setBlock(new BlockPos(2, 1, 1), facing(IC2AutoBlocks.LV_TRANSFORMER.get(), Direction.WEST));
        helper.setBlock(maceratorPos, IC2AutoBlocks.MACERATOR.get());

        machine(helper, cesuPos).setStoredEnergy(EnergyUnits.fromCWh(100_000));
        MachineBlockEntity macerator = machine(helper, maceratorPos);
        macerator.getInventory().setItem(PROCESSOR_INPUT_SLOT, new ItemStack(Items.IRON_INGOT));

        helper.succeedWhen(() -> {
            helper.assertBlockPresent(IC2AutoBlocks.MACERATOR.get(), maceratorPos);
            expectOutput(helper, macerator, IC2AutoItems.DUST_IRON.get(), 1);
        });
    }

    // ── máquinas de processamento ─────────────────────────────────────────

    /** Gerador → cobre → fornalha elétrica (3.000 CW): minério bruto de ferro vira lingote. */
    @GameTest(maxTicks = 500)
    public void electricFurnaceSmeltsRawIron(GameTestHelper helper) {
        MachineBlockEntity furnace = poweredProcessor(helper, IC2AutoBlocks.ELECTRIC_FURNACE.get(), new ItemStack(Items.RAW_IRON));
        helper.succeedWhen(() -> expectOutput(helper, furnace, Items.IRON_INGOT, 1));
    }

    /** Compressor consome 4 areias por operação e faz arenito. */
    @GameTest(maxTicks = 900)
    public void compressorMakesSandstone(GameTestHelper helper) {
        MachineBlockEntity compressor = poweredProcessor(helper, IC2AutoBlocks.COMPRESSOR.get(), new ItemStack(Items.SAND, 6));
        helper.succeedWhen(() -> {
            expectOutput(helper, compressor, Items.SANDSTONE, 1);
            if (compressor.getInventory().getItem(PROCESSOR_INPUT_SLOT).getCount() != 2) {
                helper.fail("o compressor deveria ter consumido 4 areias");
            }
        });
    }

    /** Extrator: resina vira 3 borrachas. */
    @GameTest(maxTicks = 900)
    public void extractorMakesRubberFromResin(GameTestHelper helper) {
        MachineBlockEntity extractor = poweredProcessor(helper, IC2AutoBlocks.EXTRACTOR.get(), new ItemStack(Items.RESIN_CLUMP));
        helper.succeedWhen(() -> expectOutput(helper, extractor, CEItems.RUBBER.get(), 3));
    }

    /** Reciclador consome itens (1 em 8 vira sucata). */
    @GameTest(maxTicks = 400)
    public void recyclerConsumesItems(GameTestHelper helper) {
        MachineBlockEntity recycler = poweredProcessor(helper, IC2AutoBlocks.RECYCLER.get(), new ItemStack(Items.COBBLESTONE, 16));
        helper.succeedWhen(() -> {
            if (recycler.getInventory().getItem(PROCESSOR_INPUT_SLOT).getCount() >= 14) {
                helper.fail("o reciclador ainda não consumiu itens suficientes");
            }
        });
    }

    // ── utilitários ───────────────────────────────────────────────────────

    /** Gerador com carvão → 2 cabos de cobre → máquina com a entrada dada. */
    private static MachineBlockEntity poweredProcessor(GameTestHelper helper, Block machineBlock, ItemStack input) {
        BlockPos generator = new BlockPos(1, 1, 1);
        BlockPos machinePos = new BlockPos(4, 1, 1);
        helper.setBlock(generator, IC2AutoBlocks.GENERATOR.get());
        cableLine(helper, CEBlocks.CABLE_COPPER.get(), 2, 3);
        helper.setBlock(machinePos, machineBlock);

        machine(helper, generator).getInventory().setItem(GENERATOR_FUEL_SLOT, new ItemStack(Items.COAL, 4));
        MachineBlockEntity machine = machine(helper, machinePos);
        machine.getInventory().setItem(PROCESSOR_INPUT_SLOT, input);
        return machine;
    }

    private static void expectOutput(GameTestHelper helper, MachineBlockEntity machine, Item item, int count) {
        ItemStack output = machine.getInventory().getItem(PROCESSOR_OUTPUT_SLOT);
        if (output.getItem() != item || output.getCount() < count) {
            helper.fail("saída esperada: " + count + "× " + item + ", atual: " + output);
        }
    }

    private static BlockState facing(Block block, Direction direction) {
        return block.defaultBlockState().setValue(MachineBlock.FACING, direction);
    }

    private static void cableLine(GameTestHelper helper, Block cable, int fromX, int toX) {
        for (int x = fromX; x <= toX; x++) {
            helper.setBlock(new BlockPos(x, 1, 1), cable);
        }
    }

    private static MachineBlockEntity machine(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos, MachineBlockEntity.class);
    }
}
