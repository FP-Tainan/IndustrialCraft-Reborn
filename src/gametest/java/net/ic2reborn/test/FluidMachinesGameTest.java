package net.ic2reborn.test;

import net.craftenergy.content.item.BatteryItem;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.ic2reborn.block.entity.MachineBlockEntity;
import net.ic2reborn.registry.IC2AutoBlocks;
import net.ic2reborn.registry.IC2AutoItems;
import net.ic2reborn.registry.IC2Items;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

/** Tanques, slots de fluido e as máquinas que usam fluidos ou duas entradas. */
public class FluidMachinesGameTest {
    private static final int GEO_FLUID_SLOT = 0;
    private static final int GEO_CONTAINER_SLOT = 1;
    private static final int WASHER_INPUT = 0;
    private static final int WASHER_FIRST_OUTPUT = 1;
    private static final int WASHER_FLUID_SLOT = 8;
    private static final int WASHER_DISCHARGE = 10;
    private static final int CANNER_INPUT = 0;
    private static final int CANNER_CANS = 1;
    private static final int CANNER_OUTPUT = 2;
    private static final int CANNER_DISCHARGE = 3;

    /** Balde de lava no geotérmico: a lava vai para o tanque, o balde volta vazio e há produção. */
    @GameTest(maxTicks = 40)
    public void geoGeneratorBurnsLavaBucket(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, IC2AutoBlocks.GEO_GENERATOR.get());
        MachineBlockEntity generator = machine(helper, pos);
        generator.getInventory().setItem(GEO_FLUID_SLOT, new ItemStack(Items.LAVA_BUCKET));

        helper.succeedWhen(() -> {
            if (generator.getInventory().getItem(GEO_CONTAINER_SLOT).getItem() != Items.BUCKET) helper.fail("o balde vazio deveria sair");
            if (generator.getStoredEnergy() <= 0) helper.fail("o geotérmico ainda não produziu");
        });
    }

    /** Célula de lava esvazia no tanque e devolve a célula vazia. */
    @GameTest(maxTicks = 40)
    public void geoGeneratorEmptiesLavaCell(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, IC2AutoBlocks.GEO_GENERATOR.get());
        MachineBlockEntity generator = machine(helper, pos);
        generator.getInventory().setItem(GEO_FLUID_SLOT, new ItemStack(IC2AutoItems.LAVA_CELL.get(), 2));

        helper.succeedWhen(() -> {
            if (generator.getInventory().getItem(GEO_CONTAINER_SLOT).getCount() != 2
                    || generator.getInventory().getItem(GEO_CONTAINER_SLOT).getItem() != IC2AutoItems.FLUID_CELL.get()) {
                helper.fail("as duas células vazias deveriam sair");
            }
        });
    }

    /** Canos de outros mods: o tanque aceita lava pelo Transfer API e recusa água. */
    @GameTest(maxTicks = 20)
    public void geoGeneratorTankAcceptsOnlyLava(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, IC2AutoBlocks.GEO_GENERATOR.get());
        Storage<FluidVariant> tank = FluidStorage.SIDED.find(helper.getLevel(), helper.absolutePos(pos), Direction.UP);
        if (tank == null) helper.fail("o geotérmico deveria expor um tanque");

        try (Transaction transaction = Transaction.openOuter()) {
            if (tank.insert(FluidVariant.of(Fluids.WATER), FluidConstants.BUCKET, transaction) != 0) helper.fail("não deveria aceitar água");
            if (tank.insert(FluidVariant.of(Fluids.LAVA), FluidConstants.BUCKET, transaction) != FluidConstants.BUCKET) helper.fail("deveria aceitar um balde de lava");
            transaction.commit();
        }
        helper.succeed();
    }

    /** Lavadora (1.000 MV) com água e bateria avançada: minério triturado de ferro vira purificado + pós. */
    @GameTest(maxTicks = 700)
    public void oreWashingPlantWashesCrushedIron(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, IC2AutoBlocks.ORE_WASHING_PLANT.get());
        MachineBlockEntity washer = machine(helper, pos);
        washer.getInventory().setItem(WASHER_FLUID_SLOT, new ItemStack(Items.WATER_BUCKET));
        washer.getInventory().setItem(WASHER_INPUT, new ItemStack(IC2AutoItems.CRUSHED_IRON.get()));
        washer.getInventory().setItem(WASHER_DISCHARGE, ((BatteryItem) IC2AutoItems.ADVANCED_RE_BATTERY.get()).charged());

        helper.succeedWhen(() -> {
            boolean purified = false;
            boolean stone = false;
            for (int slot = WASHER_FIRST_OUTPUT; slot < WASHER_FIRST_OUTPUT + 3; slot++) {
                ItemStack stack = washer.getInventory().getItem(slot);
                purified |= stack.getItem() == IC2AutoItems.PURIFIED_IRON.get();
                stone |= stack.getItem() == IC2AutoItems.DUST_STONE.get();
            }
            if (!purified || !stone) helper.fail("a lavadora ainda não terminou");
        });
    }

    /** Enlatador de sólidos: maçã + 4 latas vazias = 4 latas cheias. */
    @GameTest(maxTicks = 300)
    public void solidCannerFillsTinCans(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, IC2AutoBlocks.SOLID_CANNER.get());
        MachineBlockEntity canner = machine(helper, pos);
        canner.getInventory().setItem(CANNER_INPUT, new ItemStack(Items.APPLE));
        canner.getInventory().setItem(CANNER_CANS, new ItemStack(IC2AutoItems.TIN_CAN.get(), 4));
        canner.getInventory().setItem(CANNER_DISCHARGE, ((BatteryItem) IC2AutoItems.RE_BATTERY.get()).charged());

        helper.succeedWhen(() -> {
            ItemStack output = canner.getInventory().getItem(CANNER_OUTPUT);
            if (output.getItem() != IC2Items.FILLED_TIN_CAN.get() || output.getCount() != 4) helper.fail("ainda sem 4 latas cheias");
            if (!canner.getInventory().getItem(CANNER_CANS).isEmpty()) helper.fail("as latas vazias deveriam ter sido usadas");
        });
    }

    private static MachineBlockEntity machine(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos, MachineBlockEntity.class);
    }
}
