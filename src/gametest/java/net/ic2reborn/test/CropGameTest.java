package net.ic2reborn.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.ic2reborn.crop.CropBlock;
import net.ic2reborn.crop.CropBlockEntity;
import net.ic2reborn.crop.CropCards;
import net.ic2reborn.crop.CropKind;
import net.ic2reborn.item.CropSeedItem;
import net.ic2reborn.registry.IC2AutoItems;
import net.ic2reborn.registry.IC2Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Plantações do IC2: vareta, vareta dupla, plantio, crescimento, colheita, capina e saco de sementes. */
public class CropGameTest {
    private static final BlockPos CROP = new BlockPos(1, 2, 1);

    private static CropBlockEntity place(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos.below(), Blocks.FARMLAND);
        helper.setBlock(pos, IC2Blocks.CROP.get());
        return helper.getBlockEntity(pos, CropBlockEntity.class);
    }

    private static ServerPlayer holding(GameTestHelper helper, ItemStack stack) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getAbilities().instabuild = false;
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return player;
    }

    private static void useOn(GameTestHelper helper, ServerPlayer player, BlockPos pos) {
        BlockPos absolute = helper.absolutePos(pos);
        player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false)));
    }

    /** A vareta de cultivo só fica em cima de terra arada e gasta um item. */
    @GameTest(maxTicks = 20)
    public void stickPlacesOnFarmland(GameTestHelper helper) {
        helper.setBlock(CROP.below(), Blocks.FARMLAND);
        ServerPlayer player = holding(helper, new ItemStack(IC2AutoItems.CROP_STICK.get(), 2));
        useOn(helper, player, CROP.below());
        if (!helper.getBlockState(CROP).is(IC2Blocks.CROP.get())) helper.fail("a vareta deveria ficar em cima da terra arada");
        if (player.getMainHandItem().getCount() != 1) helper.fail("deveria gastar uma vareta");

        helper.setBlock(new BlockPos(3, 1, 1), Blocks.STONE);
        useOn(helper, player, new BlockPos(3, 1, 1));
        if (helper.getBlockState(new BlockPos(3, 2, 1)).is(IC2Blocks.CROP.get())) helper.fail("não deveria ficar em cima de pedra");
        helper.succeed();
    }

    /** Outra vareta vira vareta dupla, que solta as duas varetas. */
    @GameTest(maxTicks = 20)
    public void crossingStickDropsTwoSticks(GameTestHelper helper) {
        CropBlockEntity crop = place(helper, CROP);
        ServerPlayer player = holding(helper, new ItemStack(IC2AutoItems.CROP_STICK.get()));
        if (!crop.useItem(player, InteractionHand.MAIN_HAND) || !crop.isCrossingBase()) helper.fail("deveria virar vareta dupla");
        if (!helper.getBlockState(CROP).getValue(CropBlock.CROSSING)) helper.fail("o bloco deveria mostrar a vareta dupla");
        int sticks = Block.getDrops(helper.getBlockState(CROP), helper.getLevel(), helper.absolutePos(CROP), crop).stream()
                .filter(stack -> stack.is(IC2AutoItems.CROP_STICK.get()))
                .mapToInt(ItemStack::getCount)
                .sum();
        if (sticks != 2) helper.fail("a vareta dupla deveria soltar 2 varetas, soltou " + sticks);
        helper.succeed();
    }

    /** Semente de trigo planta trigo; maduro, colhe trigo e volta ao tamanho 2. */
    @GameTest(maxTicks = 20)
    public void seedsPlantAndHarvest(GameTestHelper helper) {
        CropBlockEntity crop = place(helper, CROP);
        ServerPlayer player = holding(helper, new ItemStack(Items.WHEAT_SEEDS));
        if (!crop.useItem(player, InteractionHand.MAIN_HAND) || crop.getCrop() != CropCards.get("wheat")) helper.fail("deveria plantar trigo");
        if (!player.getMainHandItem().isEmpty()) helper.fail("a semente deveria ser gasta");
        if (helper.getBlockState(CROP).getValue(CropBlock.CROP) != CropKind.WHEAT) helper.fail("o bloco deveria mostrar o trigo");

        List<ItemStack> drops = List.of();
        for (int attempt = 0; attempt < 30 && drops.isEmpty(); attempt++) {
            crop.setSize(7);
            List<ItemStack> result = crop.performHarvest();
            if (result == null) helper.fail("trigo maduro deveria poder ser colhido");
            drops = result;
        }
        if (drops.stream().noneMatch(stack -> stack.is(Items.WHEAT))) helper.fail("a colheita deveria dar trigo: " + drops);
        if (crop.getSize() != 2) helper.fail("depois da colheita o trigo volta ao tamanho 2, ficou " + crop.getSize());
        helper.succeed();
    }

    /** Fungo do Nether com areia das almas embaixo da terra arada cresce rápido até o máximo. */
    @GameTest(maxTicks = 20)
    public void netherWartGrowsOnSoulSand(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 0, 1), Blocks.SOUL_SAND);
        CropBlockEntity crop = place(helper, CROP);
        crop.useItem(holding(helper, new ItemStack(Items.NETHER_WART)), InteractionHand.MAIN_HAND);
        for (int i = 0; i < 80 && crop.getSize() < 3; i++) {
            crop.performTick();
        }
        if (crop.getSize() != 3) helper.fail("o fungo do Nether deveria chegar ao tamanho 3, ficou " + crop.getSize());
        if (helper.getBlockState(CROP).getValue(CropBlock.SIZE) != 3) helper.fail("o bloco deveria mostrar o tamanho 3");
        helper.succeed();
    }

    /** A espátula de capina tira a erva daninha. */
    @GameTest(maxTicks = 20)
    public void trowelRemovesWeed(GameTestHelper helper) {
        CropBlockEntity crop = place(helper, CROP);
        crop.setCrop(CropCards.WEED);
        crop.setSize(3);
        if (helper.getBlockState(CROP).getValue(CropBlock.CROP) != CropKind.WEED) helper.fail("o bloco deveria mostrar a erva daninha");
        useOn(helper, holding(helper, new ItemStack(IC2AutoItems.WEEDING_TROWEL.get())), CROP);
        if (crop.getCrop() != null) helper.fail("a espátula deveria tirar a erva daninha");
        if (helper.getBlockState(CROP).getValue(CropBlock.CROP) != CropKind.NONE) helper.fail("o bloco deveria voltar a ser só a vareta");
        helper.succeed();
    }

    /** Saco de sementes guarda planta e atributos e planta com eles; trigo vermelho maduro emite redstone e luz. */
    @GameTest(maxTicks = 20)
    public void seedBagAndRedwheat(GameTestHelper helper) {
        ItemStack bag = CropSeedItem.create(CropCards.get("ferru"), 5, 6, 7, 4);
        CropSeedItem.CropSeed seed = CropSeedItem.data(bag);
        if (seed == null || !seed.crop().equals("ferru") || seed.growth() != 5 || seed.gain() != 6 || seed.resistance() != 7) {
            helper.fail("o saco deveria guardar ferru 5/6/7: " + seed);
        }
        CropBlockEntity crop = place(helper, CROP);
        if (!crop.useItem(holding(helper, bag), InteractionHand.MAIN_HAND) || crop.getCrop() != CropCards.get("ferru")
                || crop.getStatResistance() != 7) {
            helper.fail("o saco de sementes deveria plantar ferru com os atributos");
        }

        BlockPos other = new BlockPos(3, 2, 1);
        helper.setBlock(other.below(), Blocks.FARMLAND);
        helper.setBlock(other, IC2Blocks.CROP.get().defaultBlockState().setValue(CropBlock.CROP, CropKind.REDWHEAT).setValue(CropBlock.SIZE, 7));
        if (helper.getBlockState(other).getSignal(helper.getLevel(), helper.absolutePos(other), Direction.NORTH) != 15) {
            helper.fail("trigo vermelho maduro deveria emitir sinal 15");
        }
        if (helper.getBlockState(other).getLightEmission() != 7) helper.fail("trigo vermelho maduro deveria emitir luz 7");
        helper.succeed();
    }
}
