package net.ic2reborn.test;

import net.craftenergy.content.item.ElectricItem;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.ic2reborn.effect.IC2Effects;
import net.ic2reborn.effect.RadiationHandler;
import net.ic2reborn.item.ChainsawItem;
import net.ic2reborn.item.ScrapBoxItem;
import net.ic2reborn.menu.BoxMenu;
import net.ic2reborn.registry.IC2AutoItems;
import net.ic2reborn.registry.IC2Components;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/** Motosserra derrubando árvores, canecas, radiação e iodo, caixa de sucata e caixas. */
public class UtilityItemsGameTest {
    private static ServerPlayer survivor(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.getAbilities().invulnerable = false;
        player.getAbilities().instabuild = false;
        return player;
    }

    /** Motosserra ligada derruba os troncos acima quando a árvore tem folhas. */
    @GameTest(maxTicks = 20)
    public void chainsawFellsTree(GameTestHelper helper) {
        for (int y = 2; y <= 5; y++) {
            helper.setBlock(new BlockPos(1, y, 1), Blocks.OAK_LOG);
        }
        helper.setBlock(new BlockPos(2, 5, 1), Blocks.OAK_LEAVES);
        ItemStack saw = ((ElectricItem) IC2AutoItems.CHAINSAW.get()).charged();
        saw.set(IC2Components.ACTIVE.get(), Unit.INSTANCE);
        int felled = ChainsawItem.fellTree(saw, helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)), survivor(helper));
        if (felled != 4) helper.fail("deveria derrubar 4 troncos, derrubou " + felled);
        for (int y = 2; y <= 5; y++) {
            if (!helper.getBlockState(new BlockPos(1, y, 1)).isAir()) helper.fail("o tronco na altura " + y + " deveria cair");
        }
        helper.succeed();
    }

    /** Troncos sem folhas (uma casa) não são derrubados. */
    @GameTest(maxTicks = 20)
    public void chainsawSparesBuildings(GameTestHelper helper) {
        for (int y = 2; y <= 4; y++) {
            helper.setBlock(new BlockPos(1, y, 1), Blocks.OAK_LOG);
        }
        ItemStack saw = ((ElectricItem) IC2AutoItems.CHAINSAW.get()).charged();
        int felled = ChainsawItem.fellTree(saw, helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)), survivor(helper));
        if (felled != 0 || !helper.getBlockState(new BlockPos(1, 2, 1)).is(Blocks.OAK_LOG)) helper.fail("sem folhas não deveria derrubar");
        helper.succeed();
    }

    /** Café gelado dá Velocidade e Pressa e devolve a caneca vazia. */
    @GameTest(maxTicks = 20)
    public void coffeeGivesSpeed(GameTestHelper helper) {
        ServerPlayer player = survivor(helper);
        ItemStack mug = new ItemStack(IC2AutoItems.MUG_COLD_COFFEE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, mug);
        ItemStack result = mug.finishUsingItem(helper.getLevel(), player);
        if (!player.hasEffect(MobEffects.SPEED) || !player.hasEffect(MobEffects.HASTE)) helper.fail("deveria dar Velocidade e Pressa");
        if (!result.is(IC2AutoItems.MUG_EMPTY.get())) helper.fail("deveria sobrar a caneca vazia, veio " + result);
        helper.succeed();
    }

    /** Urânio no inventário dá radiação; com a hazmat completa, não. */
    @GameTest(maxTicks = 20)
    public void uraniumIrradiatesWithoutHazmat(GameTestHelper helper) {
        ServerPlayer player = survivor(helper);
        player.getInventory().add(new ItemStack(IC2AutoItems.URANIUM.get()));
        RadiationHandler.tickPlayer(player);
        if (!player.hasEffect(IC2Effects.radiation())) helper.fail("urânio deveria dar radiação");

        player.removeEffect(IC2Effects.radiation());
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(IC2AutoItems.HAZMAT_HELMET.get()));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(IC2AutoItems.HAZMAT_CHESTPLATE.get()));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(IC2AutoItems.HAZMAT_LEGGINGS.get()));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(IC2AutoItems.RUBBER_BOOTS.get()));
        RadiationHandler.tickPlayer(player);
        if (player.hasEffect(IC2Effects.radiation())) helper.fail("com hazmat completa não deveria ter radiação");
        helper.succeed();
    }

    /** Cada pastilha de iodo tira 1 segundo de radiação. */
    @GameTest(maxTicks = 20)
    public void iodineRemovesRadiation(GameTestHelper helper) {
        ServerPlayer player = survivor(helper);
        player.addEffect(new MobEffectInstance(IC2Effects.radiation(), 10 * 20, 0));
        ItemStack tablets = new ItemStack(IC2AutoItems.IODINE_TABLET.get(), 20);
        player.setItemInHand(InteractionHand.MAIN_HAND, tablets);
        tablets.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        if (player.hasEffect(IC2Effects.radiation())) helper.fail("a radiação deveria sumir");
        if (tablets.getCount() != 10) helper.fail("deveria gastar 10 pastilhas, sobraram " + tablets.getCount());
        helper.succeed();
    }

    /** A caixa de sucata sempre dá algum item. */
    @GameTest(maxTicks = 20)
    public void scrapBoxAlwaysDrops(GameTestHelper helper) {
        for (int i = 0; i < 200; i++) {
            if (ScrapBoxItem.roll(helper.getLevel().getRandom()).isEmpty()) helper.fail("a caixa de sucata veio vazia");
        }
        helper.succeed();
    }

    /** A caixa de ferramentas guarda ferramentas no próprio item e recusa blocos. */
    @GameTest(maxTicks = 20)
    public void toolBoxKeepsTools(GameTestHelper helper) {
        ServerPlayer player = survivor(helper);
        ItemStack box = new ItemStack(IC2AutoItems.TOOL_BOX.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, box);
        BoxMenu menu = new BoxMenu(1, player.getInventory(), new BoxMenu.OpenData(BoxMenu.Kind.TOOL_BOX, InteractionHand.MAIN_HAND));
        if (menu.getSlot(0).mayPlace(new ItemStack(Items.DIRT))) helper.fail("terra não deveria entrar na caixa de ferramentas");
        menu.getSlot(0).set(new ItemStack(Items.IRON_PICKAXE));
        ItemContainerContents contents = box.get(DataComponents.CONTAINER);
        if (contents == null || contents.nonEmptyItemCopyStream().noneMatch(stack -> stack.is(Items.IRON_PICKAXE))) {
            helper.fail("a picareta deveria ficar guardada no item");
        }
        BoxMenu containment = new BoxMenu(2, player.getInventory(), new BoxMenu.OpenData(BoxMenu.Kind.CONTAINMENT_BOX, InteractionHand.MAIN_HAND));
        if (!containment.getSlot(0).mayPlace(new ItemStack(IC2AutoItems.URANIUM.get()))) helper.fail("urânio deveria entrar na caixa de contenção");
        helper.succeed();
    }
}
