package net.ic2reborn.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.craftenergy.content.CEItems;
import net.ic2reborn.block.entity.BrewingLogic;
import net.ic2reborn.block.entity.MachineBlockEntity;
import net.ic2reborn.item.BoozeMugItem;
import net.ic2reborn.menu.IndustrialWorkbenchMenu;
import net.ic2reborn.registry.IC2AutoBlocks;
import net.ic2reborn.registry.IC2AutoItems;
import net.ic2reborn.registry.IC2Items;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Fermentação no tanque básico e bancada industrial. */
public class BarrelAndWorkbenchGameTest {
    /** Tanque básico (antigo barril): água, trigo e lúpulo viram cerveja; com a torneira, a caneca tira uma dose. */
    @GameTest(maxTicks = 20)
    @SuppressWarnings("removal")
    public void basicTankBrewsBeer(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, IC2AutoBlocks.TANK.get());
        helper.setBlock(pos.east(), IC2AutoBlocks.BRONZE_TANK.get());
        BrewingLogic brewing = helper.getBlockEntity(pos, MachineBlockEntity.class).brewing();
        if (brewing == null) throw helper.assertionException("o tanque básico deveria fermentar");
        if (helper.getBlockEntity(pos.east(), MachineBlockEntity.class).brewing() != null) helper.fail("só o tanque básico fermenta");
        var player = helper.makeMockServerPlayerInLevel();
        for (ItemStack stack : new ItemStack[]{new ItemStack(Items.WATER_BUCKET), new ItemStack(Items.WATER_BUCKET),
                new ItemStack(Items.WHEAT, 2), new ItemStack(IC2Items.HOPS.get(), 1)}) {
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            if (!brewing.handleUse(player)) helper.fail("o tanque deveria aceitar " + stack);
        }
        if (brewing.type() != BrewingLogic.BEER || brewing.amount() != 2) helper.fail("deveria haver 2 doses de cerveja");
        if (BoozeMugItem.create(brewing.calculateValue()).getHoverName().getString().isBlank()) helper.fail("a caneca deveria ter nome");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CEItems.TREETAP.get()));
        brewing.handleUse(player);
        if (!brewing.isTapped()) helper.fail("o extrator de seiva deveria virar a torneira");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(IC2AutoItems.MUG_EMPTY.get()));
        brewing.handleUse(player);
        if (brewing.amount() != 1) helper.fail("a caneca deveria tirar uma dose");
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
