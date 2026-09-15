package net.ic2reborn.item;

import net.ic2reborn.block.entity.BrewingLogic;
import net.ic2reborn.registry.IC2AutoItems;
import net.ic2reborn.registry.IC2Components;
import net.ic2reborn.registry.IC2Items;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Caneca de bebida do IC2 ({@code ItemBooze}): cerveja (nome e efeitos pela proporção de sólidos,
 * de lúpulo e pelo tempo no barril) ou rum. Bebida, devolve a caneca vazia.
 */
public class BoozeMugItem extends Item {
    private static final String[] TIME = {"brew", "youngster", "beer", "ale", "dragon_blood", "black_stuff"};
    private static final String[] TIME_FALLBACK = {"Brew", "Youngster", "Beer", "Ale", "Dragonblood", "Black Stuff"};
    private static final String[] SOLID = {"watery", "clear", "lite", "", "strong", "thick", "stodge", "x"};
    private static final String[] SOLID_FALLBACK = {"Watery ", "Clear ", "Lite ", "", "Strong ", "Thick ", "Stodge ", "X"};
    private static final String[] HOPS = {"soup", "alcfree", "white", "", "dark", "full", "black", "x"};
    private static final String[] HOPS_FALLBACK = {"Soup ", "Alcfree ", "White ", "", "Dark ", "Full ", "Black ", "X"};
    private static final int[] BASE_DURATION = {300, 600, 900, 1200, 1600, 2000, 2400};
    private static final float[] BASE_INTENSITY = {0.4F, 0.75F, 1.0F, 1.5F, 2.0F, 2.0F};
    private static final float RUM_STACKABILITY = 2.0F;
    private static final int RUM_DURATION = 600;

    public BoozeMugItem(Properties properties) {
        super(properties.stacksTo(1).component(DataComponents.CONSUMABLE, Consumables.defaultDrink().consumeSeconds(1.6F).build()));
    }

    /** Caneca com o valor do barril; o modelo muda pelo tipo (rum) ou pelo tempo da cerveja. */
    public static ItemStack create(int value) {
        ItemStack stack = new ItemStack(IC2Items.BOOZE_MUG.get());
        stack.set(IC2Components.BOOZE.get(), value);
        String variant = type(value) == BrewingLogic.RUM ? "rum" : TIME[Math.min(time(value), TIME.length - 1)];
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(variant), List.of()));
        return stack;
    }

    private static int value(ItemStack stack) {
        return stack.getOrDefault(IC2Components.BOOZE.get(), 0);
    }

    private static int type(int value) {
        return BrewingLogic.unpack(value, 0, 2);
    }

    private static int solid(int value) {
        return BrewingLogic.unpack(value, 7, 3);
    }

    private static int hops(int value) {
        return BrewingLogic.unpack(value, 10, 3);
    }

    private static int time(int value) {
        return BrewingLogic.unpack(value, 13, 3);
    }

    @Override
    public Component getName(ItemStack stack) {
        int value = value(stack);
        int type = type(value);
        if (type == BrewingLogic.RUM) return Component.translatableWithFallback("item.ic2reborn.booze_mug.rum", "Rum");
        if (type != BrewingLogic.BEER) return Component.translatableWithFallback("item.ic2reborn.booze_mug.zero", "Zero");
        int time = Math.min(time(value), TIME.length - 1);
        Component timeName = Component.translatableWithFallback("item.ic2reborn.booze_mug." + TIME[time], TIME_FALLBACK[time]);
        if (time == TIME.length - 1) return timeName;
        int solid = solid(value);
        int hops = hops(value);
        Component solidName = SOLID[solid].isEmpty() ? Component.empty()
                : Component.translatableWithFallback("item.ic2reborn.booze_mug.solid." + SOLID[solid], SOLID_FALLBACK[solid]);
        Component hopsName = HOPS[hops].isEmpty() ? Component.empty()
                : Component.translatableWithFallback("item.ic2reborn.booze_mug.hops." + HOPS[hops], HOPS_FALLBACK[hops]);
        return Component.translatableWithFallback("item.ic2reborn.booze_mug.name", "%s%s%s", solidName, hopsName, timeName);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide()) drink(value(stack), entity);
        ItemStack result = super.finishUsingItem(stack, level, entity);
        ItemStack empty = new ItemStack(IC2AutoItems.MUG_EMPTY.get());
        if (result.isEmpty()) return empty;
        if (entity instanceof Player player && !player.getAbilities().instabuild && !player.getInventory().add(empty)) {
            player.drop(empty, false);
        }
        return result;
    }

    /** Efeitos do IC2: cansaço e força que sobem a cada caneca; demais dá lentidão, resistência, enjoo e dano. */
    private static void drink(int value, LivingEntity living) {
        int type = type(value);
        if (type == BrewingLogic.BEER) {
            if (time(value) == 5) {
                blackStuff(living);
                return;
            }
            int solid = Math.min(solid(value), BASE_DURATION.length - 1);
            int alcohol = hops(value);
            int duration = BASE_DURATION[solid];
            float intensity = BASE_INTENSITY[Math.min(time(value), BASE_INTENSITY.length - 1)];
            if (living instanceof Player player) player.getFoodData().eat(Math.max(0, 6 - alcohol), solid * 0.15F);
            int max = (int) (intensity * (alcohol * 0.5F));
            MobEffectInstance fatigue = living.getEffect(MobEffects.MINING_FATIGUE);
            int level = fatigue == null ? -1 : fatigue.getAmplifier();
            amplify(living, MobEffects.MINING_FATIGUE, max, intensity, duration);
            if (level > -1) {
                amplify(living, MobEffects.STRENGTH, max, intensity, duration);
                if (level > 0) {
                    amplify(living, MobEffects.SLOWNESS, max / 2, intensity, duration);
                    if (level > 1) {
                        amplify(living, MobEffects.RESISTANCE, max - 1, intensity, duration);
                        if (level > 2) {
                            amplify(living, MobEffects.NAUSEA, 0, intensity, duration);
                            if (level > 3) {
                                living.addEffect(new MobEffectInstance(MobEffects.INSTANT_DAMAGE, 1, living.getRandom().nextInt(3)));
                            }
                        }
                    }
                }
            }
        } else if (type == BrewingLogic.RUM) {
            if (BrewingLogic.unpack(value, 7, 7) < 100) {
                blackStuff(living);
                return;
            }
            amplify(living, MobEffects.FIRE_RESISTANCE, 0, RUM_STACKABILITY, RUM_DURATION);
            MobEffectInstance resistance = living.getEffect(MobEffects.RESISTANCE);
            int level = resistance == null ? -1 : resistance.getAmplifier();
            amplify(living, MobEffects.RESISTANCE, 2, RUM_STACKABILITY, RUM_DURATION);
            if (level >= 0) amplify(living, MobEffects.BLINDNESS, 0, RUM_STACKABILITY, RUM_DURATION);
            if (level >= 1) amplify(living, MobEffects.NAUSEA, 0, RUM_STACKABILITY, RUM_DURATION);
        }
    }

    private static void blackStuff(LivingEntity living) {
        switch (living.getRandom().nextInt(6)) {
            case 1 -> living.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 1200, 0));
            case 2 -> living.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 2400, 0));
            case 3 -> living.addEffect(new MobEffectInstance(MobEffects.POISON, 2400, 0));
            case 4 -> living.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 2));
            case 5 -> living.addEffect(new MobEffectInstance(MobEffects.INSTANT_DAMAGE, 1, living.getRandom().nextInt(4)));
            default -> {
            }
        }
    }

    private static void amplify(LivingEntity living, Holder<MobEffect> effect, int max, float intensity, int duration) {
        MobEffectInstance current = living.getEffect(effect);
        if (current == null) {
            living.addEffect(new MobEffectInstance(effect, duration, 0));
            return;
        }
        int currentDuration = current.getDuration();
        int extra = Math.max(0, (int) (duration * (1.0F + intensity * 2.0F) - currentDuration) / 2);
        int added = Math.min(duration, extra);
        int amplifier = current.getAmplifier() < max ? current.getAmplifier() + 1 : current.getAmplifier();
        living.addEffect(new MobEffectInstance(effect, currentDuration + added, amplifier));
    }
}
