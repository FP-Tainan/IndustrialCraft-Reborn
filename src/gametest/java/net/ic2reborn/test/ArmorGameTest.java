package net.ic2reborn.test;

import net.craftenergy.content.item.ElectricItem;
import net.craftenergy.content.item.EnergyItems;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.ic2reborn.fluid.IC2Fluids;
import net.ic2reborn.item.UtilityArmorItem;
import net.ic2reborn.registry.IC2AutoItems;
import net.ic2reborn.registry.IC2Entities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.GameType;

/** Armaduras do IC2: quântica, hazmat, botas de borracha, batpack, proteção com carga, jetpack e barcos. */
public class ArmorGameTest {
    private static ServerPlayer survivor(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.getAbilities().invulnerable = false;
        player.getAbilities().instabuild = false;
        return player;
    }

    /** Zumbi parado: o jogador de teste nasce invulnerável por alguns ticks. */
    private static Zombie zombie(GameTestHelper helper) {
        @SuppressWarnings("unchecked")
        EntityType<Zombie> type = (EntityType<Zombie>) net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                .getValue(net.minecraft.resources.Identifier.withDefaultNamespace("zombie"));
        return helper.spawnWithNoFreeWill(type, new BlockPos(1, 2, 1));
    }

    private static ItemStack charged(Item item) {
        return ((ElectricItem) item).charged();
    }

    /** Conjunto quântico carregado absorve o dano todo, pagando com energia. */
    @GameTest(maxTicks = 20)
    public void quantumSuitAbsorbsDamage(GameTestHelper helper) {
        Zombie player = zombie(helper);
        player.setItemSlot(EquipmentSlot.HEAD, charged(IC2AutoItems.QUANTUM_HELMET.get()));
        player.setItemSlot(EquipmentSlot.CHEST, charged(IC2AutoItems.QUANTUM_CHESTPLATE.get()));
        player.setItemSlot(EquipmentSlot.LEGS, charged(IC2AutoItems.QUANTUM_LEGGINGS.get()));
        player.setItemSlot(EquipmentSlot.FEET, charged(IC2AutoItems.QUANTUM_BOOTS.get()));
        long before = EnergyItems.getStored(player.getItemBySlot(EquipmentSlot.CHEST));
        float health = player.getHealth();
        player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().cactus(), 6.0F);
        if (player.getHealth() != health) helper.fail("a quântica deveria absorver o dano, vida " + player.getHealth());
        if (EnergyItems.getStored(player.getItemBySlot(EquipmentSlot.CHEST)) >= before) helper.fail("deveria gastar energia do peitoral");
        helper.succeed();
    }

    /** Hazmat completa protege do fogo; botas de borracha seguram queda pequena. */
    @GameTest(maxTicks = 20)
    public void hazmatAndRubberBootsProtect(GameTestHelper helper) {
        Zombie player = zombie(helper);
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(IC2AutoItems.RUBBER_BOOTS.get()));
        float health = player.getHealth();
        player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().fall(), 4.0F);
        if (player.getHealth() != health) helper.fail("botas de borracha deveriam segurar a queda");

        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(IC2AutoItems.HAZMAT_HELMET.get()));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(IC2AutoItems.HAZMAT_CHESTPLATE.get()));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(IC2AutoItems.HAZMAT_LEGGINGS.get()));
        player.hurtServer(helper.getLevel(), helper.getLevel().damageSources().inFire(), 4.0F);
        if (player.getHealth() != health) helper.fail("hazmat completa deveria proteger do fogo");
        helper.succeed();
    }

    /** Batpack vestido carrega a perfuradora da hotbar. */
    @GameTest(maxTicks = 20)
    public void batpackChargesHotbar(GameTestHelper helper) {
        ServerPlayer player = survivor(helper);
        ItemStack pack = charged(IC2AutoItems.BATPACK.get());
        player.setItemSlot(EquipmentSlot.CHEST, pack);
        ItemStack drill = new ItemStack(IC2AutoItems.DRILL.get());
        player.getInventory().setItem(0, drill);
        pack.inventoryTick(helper.getLevel(), player, EquipmentSlot.CHEST);
        if (EnergyItems.getStored(player.getInventory().getItem(0)) <= 0) helper.fail("o batpack deveria carregar a perfuradora");
        helper.succeed();
    }

    /** Peitoral nano só dá proteção com carga. */
    @GameTest(maxTicks = 20)
    public void nanoProtectionNeedsCharge(GameTestHelper helper) {
        ServerPlayer player = survivor(helper);
        ItemStack full = charged(IC2AutoItems.NANO_CHESTPLATE.get());
        full.inventoryTick(helper.getLevel(), player, null);
        if (full.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().isEmpty()) {
            helper.fail("carregado deveria proteger");
        }
        ItemStack empty = new ItemStack(IC2AutoItems.NANO_CHESTPLATE.get());
        empty.inventoryTick(helper.getLevel(), player, null);
        if (!empty.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().isEmpty()) {
            helper.fail("sem carga não deveria proteger");
        }
        helper.succeed();
    }

    /** Jetpack a biogás abastece com a célula na outra mão. */
    @GameTest(maxTicks = 20)
    public void fuelJetpackRefuels(GameTestHelper helper) {
        ServerPlayer player = survivor(helper);
        ItemStack jetpack = new ItemStack(IC2AutoItems.JETPACK.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, jetpack);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(IC2Fluids.BIOGAS.cell().get()));
        jetpack.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        if (UtilityArmorItem.fuel(jetpack) != 1_000) helper.fail("deveria ter 1.000 mB, tem " + UtilityArmorItem.fuel(jetpack));
        if (!player.getOffhandItem().isEmpty()) helper.fail("a célula de biogás deveria ser gasta");
        helper.succeed();
    }

    /** Os três barcos existem no mundo. */
    @GameTest(maxTicks = 20)
    public void boatsSpawn(GameTestHelper helper) {
        helper.spawn(IC2Entities.RUBBER_BOAT.get(), new BlockPos(1, 2, 1));
        helper.spawn(IC2Entities.CARBON_BOAT.get(), new BlockPos(3, 2, 1));
        helper.spawn(IC2Entities.ELECTRIC_BOAT.get(), new BlockPos(5, 2, 1));
        helper.assertEntityPresent(IC2Entities.ELECTRIC_BOAT.get());
        if (!IC2Entities.ELECTRIC_BOAT.get().fireImmune()) helper.fail("o barco elétrico não deveria pegar fogo");
        helper.succeed();
    }
}
