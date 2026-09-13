package net.ic2reborn.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.ic2reborn.block.WallBlock;
import net.ic2reborn.item.FoamSprayerItem;
import net.ic2reborn.registry.IC2AutoItems;
import net.ic2reborn.registry.IC2Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Espuma de construção: pulverizador, endurecer com areia e pintor. */
public class FoamGameTest {
    /** Espalha 10 blocos de espuma e gasta 1.000 mB. */
    @GameTest(maxTicks = 20)
    public void sprayerPlacesFoam(GameTestHelper helper) {
        ItemStack sprayer = new ItemStack(IC2AutoItems.FOAM_SPRAYER.get());
        FoamSprayerItem.setFoam(sprayer, FoamSprayerItem.CAPACITY);
        int placed = FoamSprayerItem.spray(helper.getLevel(), helper.absolutePos(new BlockPos(3, 2, 3)), Direction.DOWN, 10);
        if (placed != 10) helper.fail("deveria colocar 10 espumas, colocou " + placed);
        if (!helper.getBlockState(new BlockPos(3, 2, 3)).is(IC2Blocks.FOAM.get())) helper.fail("o primeiro bloco deveria ser espuma");
        helper.succeed();
    }

    /** Areia endurece a espuma em parede, e o pintor muda a cor gastando um uso. */
    @GameTest(maxTicks = 20)
    public void sandHardensAndPainterColors(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, IC2Blocks.FOAM.get());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.getAbilities().instabuild = false;
        BlockPos absolute = helper.absolutePos(pos);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false);

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SAND));
        helper.getBlockState(pos).useItemOn(player.getMainHandItem(), helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
        if (!helper.getBlockState(pos).is(IC2Blocks.WALL.get())) helper.fail("a areia deveria endurecer a espuma");
        if (helper.getBlockState(pos).getValue(WallBlock.COLOR) != DyeColor.LIGHT_GRAY) helper.fail("a parede nasce cinza-clara");

        ItemStack painter = new ItemStack(IC2AutoItems.PAINTER_RED.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, painter);
        painter.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        if (helper.getBlockState(pos).getValue(WallBlock.COLOR) != DyeColor.RED) helper.fail("o pintor deveria deixar a parede vermelha");
        if (painter.getDamageValue() != 1) helper.fail("o pintor deveria gastar um uso");
        helper.succeed();
    }
}
