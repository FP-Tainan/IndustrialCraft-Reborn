package net.ic2reborn.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.ic2reborn.block.entity.BarrelBlockEntity;
import net.ic2reborn.block.entity.MachineBlockEntity;
import net.ic2reborn.item.BoozeMugItem;
import net.ic2reborn.menu.IndustrialWorkbenchMenu;
import net.ic2reborn.registry.IC2AutoBlocks;
import net.ic2reborn.registry.IC2Items;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Barril de cerveja e bancada industrial. */
public class BarrelAndWorkbenchGameTest {
    /** Água, trigo e lúpulo viram cerveja; a dose sai numa caneca com nome. */
    @GameTest(maxTicks = 20)
    @SuppressWarnings("removal")
    public void barrelBrewsBeer(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, IC2AutoBlocks.BARREL.get());
        BarrelBlockEntity barrel = helper.getBlockEntity(pos, BarrelBlockEntity.class);
        var player = helper.makeMockServerPlayerInLevel();
        barrel.addIngredient(player, new ItemStack(Items.WATER_BUCKET));
        barrel.addIngredient(player, new ItemStack(Items.WATER_BUCKET));
        barrel.addIngredient(player, new ItemStack(Items.WHEAT, 2));
        barrel.addIngredient(player, new ItemStack(IC2Items.HOPS.get(), 1));
        if (barrel.type() != BarrelBlockEntity.BEER || barrel.amount() != 2) helper.fail("deveria haver 2 doses de cerveja");
        ItemStack mug = BoozeMugItem.create(barrel.calculateValue());
        if (!barrel.drainLiquid(1) || barrel.amount() != 1) helper.fail("deveria tirar uma dose");
        if (mug.getHoverName().getString().isBlank()) helper.fail("a caneca deveria ter nome");
        helper.succeed();
    }

    /** A bancada fabrica a receita da grade e reabastece a grade com o estoque. */
    @GameTest(maxTicks = 20)
    @SuppressWarnings("removal")
    public void workbenchCraftsAndRefills(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, IC2AutoBlocks.INDUSTRIAL_WORKBENCH.get());
        MachineBlockEntity bench = helper.getBlockEntity(pos, MachineBlockEntity.class);
        var player = helper.makeMockServerPlayerInLevel();
        bench.getInventory().setItem(IndustrialWorkbenchMenu.GRID, new ItemStack(Items.OAK_LOG));
        bench.getInventory().setItem(IndustrialWorkbenchMenu.STORAGE, new ItemStack(Items.OAK_LOG, 5));
        var menu = new IndustrialWorkbenchMenu(0, player.getInventory(), bench.getInventory(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(pos)));
        menu.broadcastChanges();
        var result = menu.slots.get(0);
        if (!result.getItem().is(Items.OAK_PLANKS)) helper.fail("deveria mostrar tábuas no resultado: " + result.getItem());
        result.onTake(player, result.getItem().copy());
        if (!bench.getInventory().getItem(IndustrialWorkbenchMenu.GRID).is(Items.OAK_LOG)
                || bench.getInventory().getItem(IndustrialWorkbenchMenu.GRID).getCount() != 5) {
            helper.fail("a grade deveria ser reabastecida pelo estoque");
        }
        helper.succeed();
    }
}
