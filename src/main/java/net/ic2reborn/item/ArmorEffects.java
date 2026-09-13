package net.ic2reborn.item;

import net.craftenergy.api.EnergyUnits;
import net.craftenergy.content.item.EnergyItems;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.ic2reborn.effect.IC2Effects;
import net.ic2reborn.effect.RadiationHandler;
import net.ic2reborn.registry.IC2AutoItems;
import net.ic2reborn.registry.IC2Items;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Efeitos das armaduras do IC2 no servidor: absorção de dano das elétricas, queda, hazmat, visão
 * noturna, capacete quântico, jetpacks, capacete solar e botas estáticas. O empuxo do jetpack e os
 * saltos/corrida quânticos rodam no cliente ({@code ArmorClient}); aqui só se gasta a energia.
 */
public final class ArmorEffects {
    /** IC2: 1 EU por tick de visão noturna. */
    private static final long NIGHTVISION_ENERGY = EnergyUnits.fromCWh(0.5);
    private static final long QUANTUM_ACTION = EnergyUnits.fromCWh(500);
    private static final long QUANTUM_JUMP = EnergyUnits.fromCWh(2_000);
    /** Jetpack elétrico e peitoral quântico: IC2 gasta ~8 EU por tick de voo. */
    public static final long JETPACK_ENERGY = EnergyUnits.fromCWh(4);
    /** Energia do capacete solar por tick de sol (IC2: 1 EU/t). */
    private static final long SOLAR_ENERGY = EnergyUnits.fromCWh(0.5);

    private static final Map<UUID, double[]> STATIC_BOOTS_LAST = new HashMap<>();
    private static final Map<UUID, Boolean> WAS_ON_GROUND = new HashMap<>();

    private ArmorEffects() {}

    public static void init() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(ArmorEffects::allowDamage);
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> drainAfterDamage(entity, source, baseDamage));
    }

    // ── elétricas ─────────────────────────────────────────────────────────
    static void tickWorn(ElectricArmorItem item, ItemStack stack, ServerLevel level, ServerPlayer player) {
        switch (item.kind()) {
            case NANO, NIGHTVISION_GOGGLES -> {
                if (item.slot() == EquipmentSlot.HEAD) nightVision(stack, player);
            }
            case QUANTUM -> {
                switch (item.slot()) {
                    case HEAD -> {
                        quantumHelmet(stack, player);
                        nightVision(stack, player);
                    }
                    case CHEST -> {
                        player.clearFire();
                        jetpackServer(player, () -> EnergyItems.use(stack, JETPACK_ENERGY));
                    }
                    case LEGS -> {
                        if (player.isSprinting() && (player.onGround() || player.isInWater()) && level.getGameTime() % 10 == 0) {
                            EnergyItems.use(stack, QUANTUM_ACTION);
                        }
                    }
                    case FEET -> {
                        boolean wasOnGround = WAS_ON_GROUND.getOrDefault(player.getUUID(), true);
                        var input = player.getLastClientInput();
                        if (wasOnGround && !player.onGround() && input.jump() && input.sprint()) {
                            EnergyItems.use(stack, QUANTUM_JUMP);
                        }
                        WAS_ON_GROUND.put(player.getUUID(), player.onGround());
                    }
                    default -> {
                    }
                }
            }
            case ELECTRIC_JETPACK -> jetpackServer(player, () -> EnergyItems.use(stack, JETPACK_ENERGY));
            default -> {
            }
        }
    }

    /** Liga/desliga a visão noturna do capacete vestido (tecla do cliente). */
    public static void toggleNightVision(ServerPlayer player) {
        ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!(helmet.getItem() instanceof ElectricArmorItem item) || item.kind() == ElectricArmorItem.Kind.BATPACK) return;
        boolean on = !ElectricArmorItem.nightVisionOn(helmet);
        if (on) {
            helmet.set(net.ic2reborn.registry.IC2Components.ACTIVE.get(), net.minecraft.util.Unit.INSTANCE);
        } else {
            helmet.remove(net.ic2reborn.registry.IC2Components.ACTIVE.get());
            player.removeEffect(MobEffects.NIGHT_VISION);
        }
        player.sendSystemMessage(on
                ? net.minecraft.network.chat.Component.translatableWithFallback("message.ic2reborn.nightvision.on", "Nightvision enabled.")
                : net.minecraft.network.chat.Component.translatableWithFallback("message.ic2reborn.nightvision.off", "Nightvision disabled."));
    }

    private static void nightVision(ItemStack stack, ServerPlayer player) {
        if (ElectricArmorItem.nightVisionOn(stack) && EnergyItems.use(stack, NIGHTVISION_ENERGY)) {
            MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
            if (current == null || current.getDuration() < 220) {
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, true, false));
            }
        }
    }

    /** Capacete quântico: ar embaixo d'água, comida das latas e cura de veneno, radiação e wither. */
    private static void quantumHelmet(ItemStack stack, ServerPlayer player) {
        if (player.getAirSupply() < 100 && EnergyItems.use(stack, QUANTUM_ACTION)) {
            player.setAirSupply(player.getAirSupply() + 200);
        }
        if (player.getFoodData().needsFood() && EnergyItems.getStored(stack) >= QUANTUM_ACTION) {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack can = player.getInventory().getItem(i);
                if (can.is(IC2Items.FILLED_TIN_CAN.get())) {
                    can.shrink(1);
                    player.getFoodData().eat(2, 0.3F);
                    ItemStack empty = new ItemStack(IC2AutoItems.TIN_CAN.get());
                    if (!player.getInventory().add(empty)) player.drop(empty, false);
                    EnergyItems.use(stack, QUANTUM_ACTION);
                    break;
                }
            }
        }
        cure(stack, player, MobEffects.POISON, 5_000);
        cure(stack, player, IC2Effects.radiation(), 5_000);
        cure(stack, player, MobEffects.WITHER, 12_500);
    }

    private static void cure(ItemStack stack, ServerPlayer player, Holder<MobEffect> effect, long costCWh) {
        MobEffectInstance instance = player.getEffect(effect);
        if (instance != null && EnergyItems.use(stack, EnergyUnits.fromCWh(costCWh * (instance.getAmplifier() + 1)))) {
            player.removeEffect(effect);
        }
    }

    /** Voando (pulo segurado no ar): gasta o combustível e zera a queda. */
    private static void jetpackServer(ServerPlayer player, java.util.function.BooleanSupplier drain) {
        if (player.getLastClientInput().jump() && !player.onGround() && !player.getAbilities().flying && drain.getAsBoolean()) {
            player.resetFallDistance();
        }
    }

    // ── utilitárias ───────────────────────────────────────────────────────
    static void tickUtility(UtilityArmorItem item, ItemStack stack, ServerLevel level, ServerPlayer player) {
        switch (item.kind()) {
            case HAZMAT -> {
                if (!RadiationHandler.hasCompleteHazmat(player) || player.getItemBySlot(EquipmentSlot.HEAD) != stack) return;
                if (player.isOnFire()) player.clearFire();
                // ar comprimido: uma célula de ar enche o fôlego
                if (player.getAirSupply() < 100) {
                    for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                        ItemStack cell = player.getInventory().getItem(i);
                        if (cell.is(net.ic2reborn.fluid.IC2Fluids.AIR.cell().get())) {
                            cell.shrink(1);
                            ItemStack empty = new ItemStack(IC2AutoItems.FLUID_CELL.get());
                            if (!player.getInventory().add(empty)) player.drop(empty, false);
                            player.setAirSupply(player.getMaxAirSupply());
                            break;
                        }
                    }
                }
            }
            case SOLAR_HELMET -> {
                BlockPos head = BlockPos.containing(player.getEyePosition());
                if (level.isBrightOutside() && !level.isRaining() && level.canSeeSky(head.above())) {
                    UtilityArmorItem.chargeChest(player, SOLAR_ENERGY);
                }
            }
            case STATIC_BOOTS -> {
                double[] last = STATIC_BOOTS_LAST.computeIfAbsent(player.getUUID(), id -> new double[]{player.getX(), player.getZ()});
                if (player.isPassenger() || player.isInWater()) {
                    last[0] = player.getX();
                    last[1] = player.getZ();
                    return;
                }
                double distance = Math.hypot(player.getX() - last[0], player.getZ() - last[1]);
                if (distance >= 5.0) {
                    last[0] = player.getX();
                    last[1] = player.getZ();
                    UtilityArmorItem.chargeChest(player, EnergyUnits.fromCWh(0.5 * Math.min(3.0, distance / 5.0)));
                }
            }
            case FUEL_JETPACK -> jetpackServer(player, () -> {
                int fuel = UtilityArmorItem.fuel(stack);
                if (fuel <= 0) return false;
                UtilityArmorItem.setFuel(stack, fuel - 1);
                return true;
            });
            default -> {
            }
        }
    }

    // ── dano ──────────────────────────────────────────────────────────────
    private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
        if (RadiationHandler.hasCompleteHazmat(entity)
                && (source.is(DamageTypeTags.IS_FIRE) || source.is(IC2Effects.RADIATION_DAMAGE) || source.is(DamageTypes.LAVA))) {
            if (source.is(DamageTypes.LAVA)) entity.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 60, 1));
            return false;
        }

        if (source.is(DamageTypeTags.IS_FALL)) {
            ItemStack boots = entity.getItemBySlot(EquipmentSlot.FEET);
            if (boots.getItem() instanceof ElectricArmorItem armor) {
                boolean quantum = armor.kind() == ElectricArmorItem.Kind.QUANTUM;
                if ((quantum || amount < 8.0F) && EnergyItems.use(boots, (long) Math.ceil(amount) * armor.energyPerDamage())) {
                    return false;
                }
            } else if (boots.getItem() instanceof UtilityArmorItem utility
                    && (utility.kind() == UtilityArmorItem.Kind.RUBBER_BOOTS || utility.kind() == UtilityArmorItem.Kind.HAZMAT)
                    && amount < 8.0F) {
                if (entity instanceof Player player && !player.getAbilities().instabuild) {
                    boots.hurtAndBreak((int) (amount + 1) / 2, entity, EquipmentSlot.FEET);
                }
                return false;
            }
        }

        if (source.is(DamageTypeTags.BYPASSES_ARMOR)) return true;
        double ratio = 0.0;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (stack.getItem() instanceof ElectricArmorItem armor && armor.energyPerDamage() > 0 && armor.isCharged(stack)) {
                ratio += armor.absorptionRatio();
            }
        }
        // conjunto quântico carregado: absorve tudo, pagando em energia
        if (ratio >= 1.0) {
            drainAfterDamage(entity, source, amount);
            return false;
        }
        return true;
    }

    /** Cada peça elétrica carregada paga energia pela sua parte do dano (IC2: damageArmor). */
    private static void drainAfterDamage(LivingEntity entity, DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_ARMOR) || amount <= 0.0F) return;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (stack.getItem() instanceof ElectricArmorItem armor && armor.energyPerDamage() > 0 && armor.isCharged(stack)) {
                long cost = (long) Math.ceil(amount * armor.absorptionRatio() * 4.0) * armor.energyPerDamage() / 4;
                long stored = EnergyItems.getStored(stack);
                EnergyItems.setStored(stack, Math.max(0, stored - Math.max(armor.energyPerDamage() / 4, cost)));
            }
        }
    }
}
