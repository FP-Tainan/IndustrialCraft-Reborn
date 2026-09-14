package net.ic2reborn.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.ic2reborn.block.MachineBlock;
import net.ic2reborn.block.entity.MachineBlockEntity;
import net.ic2reborn.recipe.MachineRecipes;
import net.ic2reborn.registry.IC2AutoBlocks;
import net.ic2reborn.registry.IC2AutoItems;
import net.ic2reborn.registry.IC2Items;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/** Fornalha de ferro, receitas do alto-forno e estrutura do forno de coque. */
public class MetallurgyGameTest {
    /** A fornalha de ferro queima carvão e derrete ferro bruto em lingote. */
    @GameTest(maxTicks = 400)
    public void ironFurnaceSmelts(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, IC2AutoBlocks.IRON_FURNACE.get());
        MachineBlockEntity furnace = helper.getBlockEntity(pos, MachineBlockEntity.class);
        furnace.getInventory().setItem(0, new ItemStack(Items.RAW_IRON, 2));
        furnace.getInventory().setItem(2, new ItemStack(Items.COAL));
        helper.succeedWhen(() -> {
            if (!furnace.getInventory().getItem(1).is(Items.IRON_INGOT)) helper.fail("deveria sair um lingote de ferro");
        });
    }

    /** Ferro no alto-forno vira aço e escória. */
    @GameTest(maxTicks = 20)
    public void blastFurnaceMakesSteel(GameTestHelper helper) {
        var recipe = MachineRecipes.INSTANCE.find("blast_furnace", new ItemStack(Items.IRON_INGOT));
        if (recipe.isEmpty()) helper.fail("deveria haver receita de aço para lingote de ferro");
        List<ItemStack> results = recipe.get().createResults();
        if (results.stream().noneMatch(stack -> stack.is(IC2AutoItems.INGOT_STEEL.get()))
                || results.stream().noneMatch(stack -> stack.is(IC2Items.SLAG.get()))) {
            helper.fail("deveria sair aço e escória: " + results);
        }
        helper.succeed();
    }

    /** O forno de coque só se forma com a estrutura 3x3x3 completa. */
    @GameTest(maxTicks = 20)
    public void cokeKilnStructure(GameTestHelper helper) {
        BlockPos center = new BlockPos(2, 2, 2);
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    helper.setBlock(center.offset(dx, dy, dz), IC2AutoBlocks.REFRACTORY_BRICKS.get());
                }
            }
        }
        helper.setBlock(center, Blocks.AIR);
        helper.setBlock(center.below(), IC2AutoBlocks.COKE_KILN_GRATE.get());
        helper.setBlock(center.above(), IC2AutoBlocks.COKE_KILN_HATCH.get());
        BlockPos controllerPos = center.north();
        helper.setBlock(controllerPos, IC2AutoBlocks.COKE_KILN.get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        MachineBlockEntity controller = helper.getBlockEntity(controllerPos, MachineBlockEntity.class);
        if (!controller.cokeKilnFormed()) helper.fail("a estrutura completa deveria formar o forno de coque");

        helper.setBlock(center.east(), Blocks.STONE);
        if (controller.cokeKilnFormed()) helper.fail("sem um tijolo não deveria formar");
        helper.succeed();
    }
}
