package net.ic2reborn.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.ic2reborn.block.MachineBlock;
import net.ic2reborn.block.entity.MachineBlockEntity;
import net.ic2reborn.fluid.IC2Fluids;
import net.ic2reborn.registry.IC2AutoBlocks;
import net.ic2reborn.registry.IC2AutoItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluids;

/** Trocador de calor + Stirling, caldeira e turbina a vapor. */
public class SteamGameTest {
    private static void insert(MachineBlockEntity machine, FluidVariant fluid, long droplets) {
        try (Transaction transaction = Transaction.openOuter()) {
            machine.getFluidStorage(null).insert(fluid, droplets, transaction);
            transaction.commit();
        }
    }

    private static MachineBlockEntity place(GameTestHelper helper, BlockPos pos, Block block, Direction facing) {
        helper.setBlock(pos, block.defaultBlockState().setValue(MachineBlock.FACING, facing));
        return helper.getBlockEntity(pos, MachineBlockEntity.class);
    }

    /** Refrigerante quente no trocador vira calor, que o gerador Stirling transforma em energia. */
    @GameTest(maxTicks = 100)
    public void heatExchangerFeedsStirling(GameTestHelper helper) {
        MachineBlockEntity exchanger = place(helper, new BlockPos(1, 1, 1), IC2AutoBlocks.LIQUID_HEAT_EXCHANGER.get(), Direction.EAST);
        MachineBlockEntity stirling = place(helper, new BlockPos(2, 1, 1), IC2AutoBlocks.STIRLING_GENERATOR.get(), Direction.WEST);
        exchanger.getInventory().setItem(7, new ItemStack(IC2AutoItems.HEAT_CONDUCTOR.get()));
        exchanger.getInventory().setItem(8, new ItemStack(IC2AutoItems.HEAT_CONDUCTOR.get()));
        insert(exchanger, FluidVariant.of(IC2Fluids.HOT_COOLANT.fluid()), FluidConstants.BUCKET);
        helper.succeedWhen(() -> {
            if (stirling.getStoredEnergy() <= 0) helper.fail("o Stirling deveria gerar energia");
            if (exchanger.getTank(1).amount <= 0) helper.fail("deveria sair refrigerante frio");
        });
    }

    /** Caldeira com quatro geradores de calor elétricos ferve a água e manda vapor para o condensador em cima. */
    @GameTest(maxTicks = 1200)
    public void steamGeneratorBoils(GameTestHelper helper) {
        BlockPos center = new BlockPos(2, 1, 2);
        MachineBlockEntity boiler = place(helper, center, IC2AutoBlocks.STEAM_GENERATOR.get(), Direction.NORTH);
        MachineBlockEntity condenser = place(helper, center.above(), IC2AutoBlocks.CONDENSER.get(), Direction.NORTH);
        java.util.List<MachineBlockEntity> heaters = new java.util.ArrayList<>();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            MachineBlockEntity heater = place(helper, center.relative(direction), IC2AutoBlocks.ELECTRIC_HEAT_GENERATOR.get(), direction.getOpposite());
            for (int slot = 0; slot < 10; slot++) heater.getInventory().setItem(slot, new ItemStack(IC2AutoItems.COIL.get()));
            heater.setStoredEnergy(Long.MAX_VALUE);
            heaters.add(heater);
        }
        insert(boiler, FluidVariant.of(IC2Fluids.DISTILLED_WATER.fluid()), 10 * FluidConstants.BUCKET);
        boiler.handleMenuButton(MachineBlockEntity.BUTTON_STEAM_WATER + 2);
        helper.succeedWhen(() -> {
            heaters.forEach(heater -> heater.setStoredEnergy(Long.MAX_VALUE));
            if (condenser.getTank(0).amount <= 0 && condenser.getTank(1).amount <= 0) helper.fail("deveria chegar vapor ao condensador: heatIn=" + boiler.getContainerData().get(MachineBlockEntity.DATA_POWER * 2) + " heat=" + boiler.getContainerData().get(MachineBlockEntity.DATA_HEAT * 2) + " mode=" + boiler.getContainerData().get(MachineBlockEntity.DATA_MODE * 2) + " out=" + boiler.getContainerData().get(MachineBlockEntity.DATA_ENERGY * 2) + " water=" + boiler.getTank(0).amount);
        });
    }

    /** Vapor na turbina com turbina a vapor vira KU, que o gerador cinético transforma em energia. */
    @GameTest(maxTicks = 100)
    public void steamTurbineMakesPower(GameTestHelper helper) {
        MachineBlockEntity turbine = place(helper, new BlockPos(1, 1, 1), IC2AutoBlocks.STEAM_KINETIC_GENERATOR.get(), Direction.EAST);
        MachineBlockEntity generator = place(helper, new BlockPos(2, 1, 1), IC2AutoBlocks.KINETIC_GENERATOR.get(), Direction.WEST);
        turbine.getInventory().setItem(1, new ItemStack(IC2AutoItems.STEAM_TURBINE.get()));
        helper.succeedWhen(() -> {
            insert(turbine, FluidVariant.of(IC2Fluids.STEAM.fluid()), FluidConstants.BUCKET / 10);
            if (generator.getStoredEnergy() <= 0) helper.fail("o gerador cinético deveria receber KU da turbina");
        });
    }
}
