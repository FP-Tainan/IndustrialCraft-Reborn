package net.ic2reborn.test;

import net.craftenergy.api.EnergyUnits;
import net.craftenergy.content.CEBlocks;
import net.craftenergy.content.block.RubberWoodBlock;
import net.craftenergy.content.item.ElectricItem;
import net.craftenergy.content.item.EnergyItems;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.ic2reborn.item.ElectricHoeItem;
import net.ic2reborn.item.ElectricTreetapItem;
import net.ic2reborn.item.NanoSaberItem;
import net.ic2reborn.item.WindMeterItem;
import net.ic2reborn.menu.MeterMenu;
import net.ic2reborn.registry.IC2AutoItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Ferramentas de bronze e elétricas, anemômetro e medidor. */
public class ToolsGameTest {
    private static ServerPlayer holding(GameTestHelper helper, ItemStack stack) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return player;
    }

    private static void useOn(GameTestHelper helper, ServerPlayer player, BlockPos pos, Direction face) {
        BlockPos absolute = helper.absolutePos(pos);
        player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(absolute), face, absolute, false)));
    }

    private static ItemStack charged(net.minecraft.world.item.Item item) {
        return ((ElectricItem) item).charged();
    }

    /** Picareta de bronze tem nível de ferro: pega minério de ferro, não diamante. */
    @GameTest(maxTicks = 20)
    public void bronzePickaxeHasIronLevel(GameTestHelper helper) {
        ItemStack pickaxe = new ItemStack(IC2AutoItems.BRONZE_PICKAXE.get());
        if (!pickaxe.isCorrectToolForDrops(Blocks.IRON_ORE.defaultBlockState())) helper.fail("deveria pegar minério de ferro");
        if (pickaxe.isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState())) helper.fail("não deveria pegar obsidiana");
        if (pickaxe.getDestroySpeed(Blocks.STONE.defaultBlockState()) != 6.0F) helper.fail("velocidade na pedra deveria ser 6");
        if (pickaxe.getMaxDamage() != 350) helper.fail("deveria ter 350 usos, tem " + pickaxe.getMaxDamage());
        helper.succeed();
    }

    /** Motosserra carregada corta madeira rápido; vazia, não. */
    @GameTest(maxTicks = 20)
    public void chainsawNeedsEnergy(GameTestHelper helper) {
        BlockState log = Blocks.OAK_LOG.defaultBlockState();
        if (charged(IC2AutoItems.CHAINSAW.get()).getDestroySpeed(log) != 12.0F) helper.fail("carregada deveria ter velocidade 12");
        if (new ItemStack(IC2AutoItems.CHAINSAW.get()).getDestroySpeed(log) != 1.0F) helper.fail("vazia deveria ser lenta");
        helper.succeed();
    }

    /** Enxada elétrica ara a terra gastando energia; vazia não ara. */
    @GameTest(maxTicks = 20)
    public void electricHoeTillsWithEnergy(GameTestHelper helper) {
        BlockPos dirt = new BlockPos(1, 1, 1);
        helper.setBlock(dirt, Blocks.DIRT);
        ServerPlayer empty = holding(helper, new ItemStack(IC2AutoItems.ELECTRIC_HOE.get()));
        useOn(helper, empty, dirt, Direction.UP);
        if (!helper.getBlockState(dirt).is(Blocks.DIRT)) helper.fail("vazia não deveria arar");

        ItemStack hoe = charged(IC2AutoItems.ELECTRIC_HOE.get());
        long before = EnergyItems.getStored(hoe);
        useOn(helper, holding(helper, hoe), dirt, Direction.UP);
        if (!helper.getBlockState(dirt).is(Blocks.FARMLAND)) helper.fail("carregada deveria arar");
        if (before - EnergyItems.getStored(hoe) != ElectricHoeItem.OPERATION_ENERGY) helper.fail("deveria gastar 25 CWh");
        helper.succeed();
    }

    /** Torneira elétrica tira resina do tronco molhado e deixa ele sangrado. */
    @GameTest(maxTicks = 20)
    public void electricTreetapTapsRubberWood(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        if (!(CEBlocks.RUBBER_LOG.get() instanceof RubberWoodBlock)) helper.fail("tronco de seringueira deveria ter resina");
        helper.setBlock(pos, CEBlocks.RUBBER_LOG.get().defaultBlockState().setValue(RubberWoodBlock.WOOD_TYPE, RubberWoodBlock.WoodType.WET));
        ItemStack treetap = charged(IC2AutoItems.ELECTRIC_TREETAP.get());
        long before = EnergyItems.getStored(treetap);
        useOn(helper, holding(helper, treetap), pos, Direction.NORTH);
        if (helper.getBlockState(pos).getValue(RubberWoodBlock.WOOD_TYPE) != RubberWoodBlock.WoodType.TAPPED) helper.fail("o tronco deveria ficar sangrado");
        if (before - EnergyItems.getStored(treetap) != ElectricTreetapItem.OPERATION_ENERGY) helper.fail("deveria gastar 25 CWh");
        helper.succeed();
    }

    /** Nanossabre liga e desliga com o botão direito; ligado corta teia rápido. */
    @GameTest(maxTicks = 20)
    public void nanoSaberToggles(GameTestHelper helper) {
        ItemStack saber = charged(IC2AutoItems.NANO_SABER.get());
        ServerPlayer player = holding(helper, saber);
        saber.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        if (!NanoSaberItem.isActive(saber)) helper.fail("deveria ligar");
        if (saber.getDestroySpeed(Blocks.COBWEB.defaultBlockState()) != 50.0F) helper.fail("ligado deveria cortar teia rápido");
        saber.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        if (NanoSaberItem.isActive(saber)) helper.fail("deveria desligar");

        ItemStack empty = new ItemStack(IC2AutoItems.NANO_SABER.get());
        empty.use(helper.getLevel(), holding(helper, empty), InteractionHand.MAIN_HAND);
        if (NanoSaberItem.isActive(empty)) helper.fail("sem energia não deveria ligar");
        helper.succeed();
    }

    /** Anemômetro no ar gasta 25 CWh por medição. */
    @GameTest(maxTicks = 20)
    public void windMeterUsesEnergy(GameTestHelper helper) {
        ItemStack meter = charged(IC2AutoItems.WIND_METER.get());
        long before = EnergyItems.getStored(meter);
        meter.use(helper.getLevel(), holding(helper, meter), InteractionHand.MAIN_HAND);
        if (before - EnergyItems.getStored(meter) != WindMeterItem.OPERATION_ENERGY) helper.fail("deveria gastar 25 CWh");
        helper.succeed();
    }

    /** O medidor troca de modo pelos botões e zera as leituras. */
    @GameTest(maxTicks = 20)
    public void meterSwitchesModes(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        MeterMenu menu = new MeterMenu(1, player.getInventory(), helper.absolutePos(new BlockPos(1, 1, 1)));
        menu.sample(helper.getLevel());
        menu.sample(helper.getLevel());
        if (!menu.clickMenuButton(player, MeterMenu.MODE_VOLTAGE)) helper.fail("o botão de tensão deveria funcionar");
        if (menu.getMode() != MeterMenu.MODE_VOLTAGE || menu.getCount() != 0) {
            helper.fail("deveria estar no modo tensão e zerado: modo " + menu.getMode() + ", " + menu.getCount());
        }
        if (EnergyUnits.fromCWh(1) <= 0) helper.fail("unidades");
        helper.succeed();
    }
}
