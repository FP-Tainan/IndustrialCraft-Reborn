package net.ic2reborn.effect;

import net.ic2reborn.IC2Reborn;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/** Efeitos do IC2 Reborn. */
public final class IC2Effects {
    public static final ResourceKey<DamageType> RADIATION_DAMAGE =
            ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(IC2Reborn.MODID, "radiation"));

    private static Holder<MobEffect> radiation;

    private IC2Effects() {}

    public static void init() {
        radiation = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
                Identifier.fromNamespaceAndPath(IC2Reborn.MODID, "radiation"), new RadiationEffect());
    }

    public static Holder<MobEffect> radiation() {
        return radiation;
    }

    /**
     * Radiação do IC2 ({@code IC2Potion.radiation}): machuca a cada {@code 25 >> nível} ticks com
     * {@code nível / 100 + 0,5} de dano, atravessando armadura (menos a hazmat completa).
     */
    static final class RadiationEffect extends MobEffect {
        RadiationEffect() {
            super(MobEffectCategory.HARMFUL, 0x4E9A06);
        }

        @Override
        public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
            int rate = 25 >> amplifier;
            return rate <= 0 || duration % rate == 0;
        }

        @Override
        public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
            if (!RadiationHandler.hasCompleteHazmat(entity)) {
                entity.hurtServer(level, level.damageSources().source(RADIATION_DAMAGE), amplifier / 100 + 0.5F);
            }
            return true;
        }
    }
}
