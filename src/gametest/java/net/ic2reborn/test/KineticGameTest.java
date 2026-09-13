package net.ic2reborn.test;

import net.craftenergy.content.item.BatteryItem;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.ic2reborn.block.MachineBlock;
import net.ic2reborn.block.entity.MachineBlockEntity;
import net.ic2reborn.registry.IC2AutoBlocks;
import net.ic2reborn.registry.IC2AutoItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Geradores cinéticos: fontes de KU e o gerador cinético que converte em energia. */
public class KineticGameTest {
    private static final BlockPos SOURCE = new BlockPos(1, 1, 1);
    private static final BlockPos GENERATOR = new BlockPos(2, 1, 1);

    /** Gerador cinético elétrico (10 motores) virado para o gerador cinético: sai energia. */
    @GameTest(maxTicks = 60)
    public void electricKineticDrivesKineticGenerator(GameTestHelper helper) {
        MachineBlockEntity motor = place(helper, SOURCE, IC2AutoBlocks.ELECTRIC_KINETIC_GENERATOR.get(), Direction.EAST);
        for (int slot = 0; slot < 10; slot++) {
            motor.getInventory().setItem(slot, new ItemStack(IC2AutoItems.ELECTRIC_MOTOR.get()));
        }
        motor.getInventory().setItem(10, ((BatteryItem) IC2AutoItems.ADVANCED_RE_BATTERY.get()).charged());
        MachineBlockEntity generator = place(helper, GENERATOR, IC2AutoBlocks.KINETIC_GENERATOR.get(), Direction.WEST);

        helper.succeedWhen(() -> {
            if (generator.getStoredEnergy() <= 0) {
                helper.fail("o gerador cinético ainda não produziu; motor energia=" + motor.getStoredEnergy()
                        + " KU=" + data(motor, MachineBlockEntity.DATA_PROGRESS) + " maxKU=" + data(motor, MachineBlockEntity.DATA_MAX_PROGRESS)
                        + " gerador disponível=" + data(generator, MachineBlockEntity.DATA_MAX_PROGRESS)
                        + " estado motor=" + helper.getBlockState(SOURCE) + " estado gerador=" + helper.getBlockState(GENERATOR));
            }
        });
    }

    private static int data(MachineBlockEntity machine, int field) {
        return (machine.getContainerData().get(field * 2) & 0xFFFF) | ((machine.getContainerData().get(field * 2 + 1) & 0xFFFF) << 16);
    }

    /** Manivela: três cliques enchem o buffer, o gerador cinético converte. */
    @GameTest(maxTicks = 40)
    public void manualCrankDrivesKineticGenerator(GameTestHelper helper) {
        MachineBlockEntity crank = place(helper, SOURCE, IC2AutoBlocks.MANUAL_KINETIC_GENERATOR.get(), Direction.NORTH);
        MachineBlockEntity generator = place(helper, GENERATOR, IC2AutoBlocks.KINETIC_GENERATOR.get(), Direction.WEST);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        crank.crank(player);
        crank.crank(player);
        crank.crank(player);

        helper.succeedWhen(() -> {
            if (generator.getStoredEnergy() <= 0) helper.fail("a manivela deveria virar energia");
        });
    }

    /** Eólico: sem rotor avisa; com rotor e um bloco na frente, avisa que falta espaço. */
    @GameTest(maxTicks = 60)
    public void windKineticChecksRotorAndSpace(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 3, 3);
        MachineBlockEntity wind = place(helper, pos, IC2AutoBlocks.WIND_KINETIC_GENERATOR.get(), Direction.NORTH);

        helper.runAfterDelay(2, () -> {
            if (wind.kineticStatus() != 0) helper.fail("sem rotor o estado deveria ser 0, veio " + wind.kineticStatus());
            helper.setBlock(pos.north(), Blocks.STONE);
            wind.getInventory().setItem(0, new ItemStack(IC2AutoItems.ROTOR_IRON.get()));
        });
        helper.runAfterDelay(40, () -> {
            if (wind.kineticStatus() != 1) helper.fail("com bloco na frente o estado deveria ser 1, veio " + wind.kineticStatus());
            helper.succeed();
        });
    }

    /** Água fora de oceano ou rio não funciona. */
    @GameTest(maxTicks = 40)
    public void waterKineticNeedsOceanOrRiver(GameTestHelper helper) {
        MachineBlockEntity water = place(helper, SOURCE, IC2AutoBlocks.WATER_KINETIC_GENERATOR.get(), Direction.NORTH);
        water.getInventory().setItem(0, new ItemStack(IC2AutoItems.ROTOR_IRON.get()));

        helper.runAfterDelay(25, () -> {
            if (water.kineticStatus() != 0) helper.fail("fora de oceano/rio o estado deveria ser 0, veio " + water.kineticStatus());
            helper.succeed();
        });
    }

    private static MachineBlockEntity place(GameTestHelper helper, BlockPos pos, Block block, Direction facing) {
        helper.setBlock(pos, block.defaultBlockState().setValue(MachineBlock.FACING, facing));
        return helper.getBlockEntity(pos, MachineBlockEntity.class);
    }
}
