package net.ic2reborn.test;

import net.craftenergy.content.item.EnergyItems;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.ic2reborn.block.MachineBlock;
import net.ic2reborn.block.entity.MachineBlockEntity;
import net.ic2reborn.registry.IC2AutoBlocks;
import net.ic2reborn.registry.IC2AutoItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

/** Placa de carga, RTG, bobina de Tesla, eletrolisador e luminária. */
public class UtilityGameTest {
    /** Quem pisa na placa de carga tem as baterias carregadas. */
    @GameTest(maxTicks = 40)
    @SuppressWarnings("removal")
    public void chargepadChargesPlayer(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, IC2AutoBlocks.CHARGEPAD_MFE.get());
        MachineBlockEntity pad = helper.getBlockEntity(pos, MachineBlockEntity.class);
        pad.setStoredEnergy(Long.MAX_VALUE);
        var player = helper.makeMockServerPlayerInLevel();
        player.setPos(helper.absoluteVec(new Vec3(1.5, 2.0, 1.5)));
        player.getInventory().setItem(0, new ItemStack(IC2AutoItems.ADVANCED_RE_BATTERY.get()));
        helper.succeedWhen(() -> {
            if (EnergyItems.getStored(player.getInventory().getItem(0)) <= 0) helper.fail("a bateria deveria carregar");
        });
    }

    /** Três pastilhas no RTG geram energia. */
    @GameTest(maxTicks = 20)
    public void rtgMakesPower(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, IC2AutoBlocks.RT_GENERATOR.get());
        MachineBlockEntity rtg = helper.getBlockEntity(pos, MachineBlockEntity.class);
        for (int slot = 0; slot < 3; slot++) rtg.getInventory().setItem(slot, new ItemStack(IC2AutoItems.RTG_PELLET.get()));
        helper.succeedWhen(() -> {
            if (rtg.getStoredEnergy() <= 0) helper.fail("o RTG deveria gerar energia");
        });
    }

    /** A bobina de Tesla com redstone dá choque num zumbi por perto. */
    @GameTest(maxTicks = 80)
    public void teslaShocksMobs(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, IC2AutoBlocks.TESLA_COIL.get());
        helper.setBlock(pos.below(), Blocks.REDSTONE_BLOCK);
        MachineBlockEntity tesla = helper.getBlockEntity(pos, MachineBlockEntity.class);
        @SuppressWarnings("unchecked")
        EntityType<? extends LivingEntity> zombieType = (EntityType<? extends LivingEntity>) BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("zombie"));
        LivingEntity zombie = helper.spawn(zombieType, new BlockPos(3, 1, 1));
        helper.succeedWhen(() -> {
            tesla.setStoredEnergy(Long.MAX_VALUE);
            if (zombie.getHealth() >= zombie.getMaxHealth()) helper.fail("o zumbi deveria tomar choque");
        });
    }

    /** O eletrolisador separa a água: hidrogênio no tanque de baixo. */
    @GameTest(maxTicks = 100)
    public void electrolyzerSplitsWater(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos.below(), IC2AutoBlocks.IRON_TANK.get());
        helper.setBlock(pos, IC2AutoBlocks.ELECTROLYZER.get());
        helper.setBlock(pos.above(), IC2AutoBlocks.IRON_TANK.get());
        MachineBlockEntity electrolyzer = helper.getBlockEntity(pos, MachineBlockEntity.class);
        MachineBlockEntity below = helper.getBlockEntity(pos.below(), MachineBlockEntity.class);
        try (Transaction transaction = Transaction.openOuter()) {
            electrolyzer.getFluidStorage(null).insert(FluidVariant.of(Fluids.WATER), FluidConstants.BUCKET, transaction);
            transaction.commit();
        }
        helper.succeedWhen(() -> {
            electrolyzer.setStoredEnergy(Long.MAX_VALUE);
            if (below.getTank(0).amount <= 0) helper.fail("deveria haver hidrogênio no tanque de baixo");
        });
    }

    /** Com energia a luminária acende. */
    @GameTest(maxTicks = 20)
    public void luminatorLights(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, IC2AutoBlocks.LUMINATOR_FLAT.get());
        MachineBlockEntity luminator = helper.getBlockEntity(pos, MachineBlockEntity.class);
        helper.succeedWhen(() -> {
            luminator.setStoredEnergy(Long.MAX_VALUE);
            if (!helper.getBlockState(pos).getValue(MachineBlock.ACTIVE)) helper.fail("a luminária deveria acender");
        });
    }
}
