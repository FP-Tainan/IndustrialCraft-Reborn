package net.ic2reborn.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.craftenergy.api.EnergyUnits;
import net.craftenergy.content.item.EnergyItems;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.ic2reborn.IC2Reborn;
import net.ic2reborn.item.ArmorEffects;
import net.ic2reborn.item.ElectricArmorItem;
import net.ic2reborn.item.UtilityArmorItem;
import net.ic2reborn.network.NightVisionTogglePayload;
import net.ic2reborn.registry.IC2Entities;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.object.boat.BoatModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.BoatRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * Parte de cliente das armaduras e barcos: tecla de visão noturna, empuxo dos jetpacks, corrida e
 * salto quânticos (o movimento do jogador é calculado no cliente) e desenho dos barcos.
 */
public final class ArmorClient {
    private static final long QUANTUM_ACTION = EnergyUnits.fromCWh(500);
    private static final long QUANTUM_JUMP = EnergyUnits.fromCWh(2_000);

    private static KeyMapping nightVision;
    private static float jumpCharge;

    private ArmorClient() {}

    public static void init() {
        nightVision = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.ic2reborn.nightvision", InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_N, KeyMapping.Category.register(Identifier.fromNamespaceAndPath(IC2Reborn.MODID, "ic2reborn"))));
        ClientTickEvents.END_CLIENT_TICK.register(ArmorClient::tick);

        boat(IC2Entities.RUBBER_BOAT.get(), "rubber");
        boat(IC2Entities.CARBON_BOAT.get(), "carbon");
        boat(IC2Entities.ELECTRIC_BOAT.get(), "electric");
        EntityRendererRegistry.register(IC2Entities.DYNAMITE.get(), net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
    }

    private static <T extends net.ic2reborn.entity.IC2Boat> void boat(EntityType<T> type, String name) {
        ModelLayerLocation layer = new ModelLayerLocation(Identifier.fromNamespaceAndPath(IC2Reborn.MODID, "boat/" + name), "main");
        ModelLayerRegistry.registerModelLayer(layer, BoatModel::createBoatModel);
        EntityRendererRegistry.register(type, context -> new BoatRenderer(context, layer));
    }

    private static void tick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null) return;
        while (nightVision.consumeClick()) {
            ClientPlayNetworking.send(NightVisionTogglePayload.INSTANCE);
        }
        if (player.getAbilities().flying || player.isSpectator()) return;

        // jetpack: pulo segurado sobe, com teto de velocidade conforme a potência
        double power = jetpackPower(player.getItemBySlot(EquipmentSlot.CHEST));
        if (power > 0.0 && minecraft.options.keyJump.isDown()) {
            Vec3 motion = player.getDeltaMovement();
            player.setDeltaMovement(motion.x, Math.min(motion.y + 0.14 * power, 0.5 * power), motion.z);
            player.resetFallDistance();
        }

        // calças quânticas: correndo para a frente, empurrão extra
        ItemStack legs = player.getItemBySlot(EquipmentSlot.LEGS);
        if (isQuantum(legs) && EnergyItems.getStored(legs) >= QUANTUM_ACTION && player.isSprinting()
                && (player.onGround() || player.isInWater()) && minecraft.options.keyUp.isDown()) {
            player.moveRelative(player.isInWater() ? 0.1F : 0.15F, new Vec3(0.0, 0.0, 1.0));
            if (player.isInWater() && minecraft.options.keyJump.isDown()) {
                player.setDeltaMovement(player.getDeltaMovement().add(0.0, 0.1, 0.0));
            }
        }

        // botas quânticas: pulo + correr = super salto
        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
        if (isQuantum(boots)) {
            if (EnergyItems.getStored(boots) >= QUANTUM_JUMP && player.onGround()) jumpCharge = 1.0F;
            Vec3 motion = player.getDeltaMovement();
            if (motion.y >= 0.0 && jumpCharge > 0.0F && !player.isInWater()) {
                if (minecraft.options.keyJump.isDown() && minecraft.options.keySprint.isDown()) {
                    double x = motion.x;
                    double z = motion.z;
                    if (jumpCharge == 1.0F) {
                        x *= 3.5;
                        z *= 3.5;
                    }
                    player.setDeltaMovement(x, motion.y + jumpCharge * 0.3, z);
                    jumpCharge *= 0.75F;
                } else if (jumpCharge < 1.0F) {
                    jumpCharge = 0.0F;
                }
            }
        } else {
            jumpCharge = 0.0F;
        }
    }

    private static boolean isQuantum(ItemStack stack) {
        return stack.getItem() instanceof ElectricArmorItem armor && armor.kind() == ElectricArmorItem.Kind.QUANTUM;
    }

    /** IC2: elétrico 0,7; quântico e a combustível 1,0; 0 sem carga. */
    private static double jetpackPower(ItemStack chest) {
        if (chest.getItem() instanceof ElectricArmorItem armor && EnergyItems.getStored(chest) >= ArmorEffects.JETPACK_ENERGY) {
            if (armor.kind() == ElectricArmorItem.Kind.ELECTRIC_JETPACK) return 0.7;
            if (armor.kind() == ElectricArmorItem.Kind.QUANTUM) return 1.0;
        }
        if (chest.getItem() instanceof UtilityArmorItem utility && utility.kind() == UtilityArmorItem.Kind.FUEL_JETPACK
                && UtilityArmorItem.fuel(chest) > 0) {
            return 1.0;
        }
        return 0.0;
    }
}
