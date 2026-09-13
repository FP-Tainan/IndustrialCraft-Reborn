package net.ic2reborn.test;

import net.craftenergy.api.EnergyUnits;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.ic2reborn.block.MachineBlock;
import net.ic2reborn.block.entity.MachineBlockEntity;
import net.ic2reborn.item.WrenchItem;
import net.ic2reborn.registry.IC2AutoBlocks;
import net.ic2reborn.registry.IC2AutoItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Testes da chave inglesa. */
public class WrenchGameTest {

    /**
     * CESU virada para o norte com um macerador a leste (entrada lateral: seguro). Girar a CESU
     * para leste liga a saída de 1.000 MV no macerador, que explode — a rede foi reconstruída.
     */
    @GameTest(maxTicks = 100)
    public void wrenchRotatesStorageAndRewiresNetwork(GameTestHelper helper) {
        BlockPos cesuPos = new BlockPos(1, 1, 1);
        BlockPos maceratorPos = new BlockPos(2, 1, 1);
        helper.setBlock(cesuPos, facing(IC2AutoBlocks.CESU.get(), Direction.NORTH));
        helper.setBlock(maceratorPos, IC2AutoBlocks.MACERATOR.get());
        helper.getBlockEntity(cesuPos, MachineBlockEntity.class).setStoredEnergy(EnergyUnits.fromCWh(10_000));

        boolean[] rotated = {false};
        helper.runAfterDelay(10, () -> {
            helper.assertBlockPresent(IC2AutoBlocks.MACERATOR.get(), maceratorPos);
            ItemStack wrench = useWrench(helper, IC2AutoItems.WRENCH.get(), cesuPos, Direction.EAST, new Vec3(1.0, 0.5, 0.5), false);
            if (helper.getBlockState(cesuPos).getValue(MachineBlock.FACING) != Direction.EAST) {
                helper.fail("a CESU deveria estar virada para leste");
            }
            if (wrench.getDamageValue() != WrenchItem.ROTATE_DAMAGE) helper.fail("girar deveria gastar 1 de durabilidade");
            rotated[0] = true;
        });

        helper.succeedWhen(() -> {
            if (!rotated[0]) helper.fail("ainda não girou");
            helper.assertBlockPresent(Blocks.AIR, maceratorPos);
        });
    }

    /** Clicar na frente com a chave clássica desmonta: solta a máquina e o inventário, gasta 10. */
    @GameTest(maxTicks = 40)
    public void wrenchDismantlesMachineWhenClickingItsFront(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, facing(IC2AutoBlocks.MACERATOR.get(), Direction.NORTH));
        helper.getBlockEntity(pos, MachineBlockEntity.class).getInventory().setItem(0, new ItemStack(Items.IRON_INGOT, 3));

        ItemStack wrench = useWrench(helper, IC2AutoItems.WRENCH.get(), pos, Direction.NORTH, new Vec3(0.5, 0.5, 0.0), false);

        helper.assertBlockPresent(Blocks.AIR, pos);
        if (wrench.getDamageValue() != WrenchItem.REMOVE_DAMAGE) helper.fail("desmontar deveria gastar 10 de durabilidade");

        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(pos)).inflate(2));
        boolean machineDropped = drops.stream().anyMatch(e -> e.getItem().getItem() == IC2AutoItems.MACERATOR.get());
        int ingots = drops.stream().filter(e -> e.getItem().getItem() == Items.IRON_INGOT).mapToInt(e -> e.getItem().getCount()).sum();
        if (!machineDropped) helper.fail("o macerador deveria cair como item");
        if (ingots != 3) helper.fail("os 3 lingotes do inventário deveriam cair no chão, caíram " + ingots);
        helper.succeed();
    }

    /** Agachado, a chave clássica vira a frente para o lado oposto da face clicada. */
    @GameTest(maxTicks = 20)
    public void sneakingWrenchTurnsToTheOppositeSide(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, facing(IC2AutoBlocks.BATBOX.get(), Direction.NORTH));

        useWrench(helper, IC2AutoItems.WRENCH.get(), pos, Direction.EAST, new Vec3(1.0, 0.5, 0.5), true);

        if (helper.getBlockState(pos).getValue(MachineBlock.FACING) != Direction.WEST) {
            helper.fail("agachado, clicar no leste deveria virar a frente para oeste");
        }
        helper.succeed();
    }

    /** Chave nova: grade 3×3 na face; no topo, a faixa da borda sul vira a frente para o sul. */
    @GameTest(maxTicks = 20)
    public void newWrenchRotatesByHitPosition(GameTestHelper helper) {
        expect(helper, WrenchItem.rotateByHit(Direction.UP, 0.5, 1.0, 0.5), Direction.UP, "centro do topo");
        expect(helper, WrenchItem.rotateByHit(Direction.UP, 0.9, 1.0, 0.5), Direction.EAST, "borda leste do topo");
        expect(helper, WrenchItem.rotateByHit(Direction.UP, 0.1, 1.0, 0.1), Direction.DOWN, "canto do topo");
        expect(helper, WrenchItem.rotateByHit(Direction.NORTH, 0.5, 0.9, 0.0), Direction.UP, "borda de cima da face norte");

        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, facing(IC2AutoBlocks.CESU.get(), Direction.NORTH));

        useWrench(helper, IC2AutoItems.WRENCH_NEW.get(), pos, Direction.UP, new Vec3(0.5, 1.0, 0.95), false);
        expect(helper, helper.getBlockState(pos).getValue(MachineBlock.FACING), Direction.SOUTH, "CESU após a chave nova");

        // centro do topo = para cima: máquina só gira na horizontal, e a chave nova nunca desmonta
        useWrench(helper, IC2AutoItems.WRENCH_NEW.get(), pos, Direction.UP, new Vec3(0.5, 1.0, 0.5), false);
        helper.assertBlockPresent(IC2AutoBlocks.CESU.get(), pos);
        helper.succeed();
    }

    private static ItemStack useWrench(GameTestHelper helper, Item wrench, BlockPos relative, Direction face,
                                       Vec3 hitInBlock, boolean sneaking) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack stack = new ItemStack(wrench);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.setShiftKeyDown(sneaking);
        // jogador de sobrevivência: no criativo o Minecraft não gasta durabilidade
        player.getAbilities().instabuild = false;

        BlockPos absolute = helper.absolutePos(relative);
        BlockHitResult hit = new BlockHitResult(Vec3.atLowerCornerOf(absolute).add(hitInBlock), face, absolute, false);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        return stack;
    }

    private static void expect(GameTestHelper helper, Direction actual, Direction expected, String what) {
        if (actual != expected) helper.fail(what + ": esperado " + expected + ", veio " + actual);
    }

    private static BlockState facing(Block block, Direction direction) {
        return block.defaultBlockState().setValue(MachineBlock.FACING, direction);
    }
}
