package net.ic2reborn.item;

import net.ic2reborn.registry.IC2AutoItems;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.level.Level;

/**
 * Canecas de café do IC2 ({@code ItemMug}): cada gole aumenta Velocidade e Pressa; exagerar dá
 * enjoo e até dano. O café com leite é o mais suave. Bebida, devolve a caneca vazia.
 */
public class MugItem extends Item {
    /** Tipo da caneca: nível máximo dos efeitos e ticks acrescentados por gole. */
    public enum Kind {
        COLD_COFFEE(1, 600),
        DARK_COFFEE(5, 1200),
        COFFEE(6, 1200);

        final int maxAmplifier;
        final int extraDuration;

        Kind(int maxAmplifier, int extraDuration) {
            this.maxAmplifier = maxAmplifier;
            this.extraDuration = extraDuration;
        }
    }

    private final Kind kind;

    public MugItem(Properties properties, Kind kind) {
        super(properties.stacksTo(1).component(DataComponents.CONSUMABLE, Consumables.defaultDrink().consumeSeconds(1.6F).build()));
        this.kind = kind;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide() && entity instanceof Player player) {
            int highest = Math.max(amplify(player, MobEffects.SPEED), amplify(player, MobEffects.HASTE));
            if (this.kind == Kind.COFFEE) highest -= 2;
            if (highest >= 3) {
                player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, (highest - 2) * 200, 0));
                if (highest >= 4) {
                    player.addEffect(new MobEffectInstance(MobEffects.INSTANT_DAMAGE, 1, highest - 3));
                }
            }
        }
        ItemStack result = super.finishUsingItem(stack, level, entity);
        ItemStack empty = new ItemStack(IC2AutoItems.MUG_EMPTY.get());
        if (result.isEmpty()) return empty;
        if (entity instanceof Player player && !player.getAbilities().instabuild && !player.getInventory().add(empty)) {
            player.drop(empty, false);
        }
        return result;
    }

    /** Sobe um nível (até o máximo do tipo) e soma a duração; sem o efeito, começa com 15 s. */
    private int amplify(Player player, Holder<MobEffect> effect) {
        MobEffectInstance current = player.getEffect(effect);
        if (current == null) {
            player.addEffect(new MobEffectInstance(effect, 300, 0));
            return 1;
        }
        int amplifier = current.getAmplifier() < this.kind.maxAmplifier ? current.getAmplifier() + 1 : current.getAmplifier();
        player.addEffect(new MobEffectInstance(effect, current.getDuration() + this.kind.extraDuration, amplifier));
        return amplifier;
    }
}
