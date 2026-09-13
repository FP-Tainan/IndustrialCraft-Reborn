package net.ic2reborn.item;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

/** Fungo da Terra do IC2: comer tira os efeitos ruins. */
public class TerraWartItem extends Item {
    private static final List<Holder<MobEffect>> CURED = List.of(MobEffects.NAUSEA, MobEffects.MINING_FATIGUE, MobEffects.HUNGER,
            MobEffects.SLOWNESS, MobEffects.WEAKNESS, MobEffects.BLINDNESS, MobEffects.POISON, MobEffects.WITHER);

    public TerraWartItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide()) {
            CURED.forEach(entity::removeEffect);
        }
        return super.finishUsingItem(stack, level, entity);
    }
}
