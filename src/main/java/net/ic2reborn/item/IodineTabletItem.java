package net.ic2reborn.item;

import net.ic2reborn.effect.IC2Effects;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Pastilha de iodo do IC2: cada pastilha tira 1 segundo de radiação (usa quantas precisar da pilha). */
public class IodineTabletItem extends Item {
    public IodineTabletItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        MobEffectInstance radiation = player.getEffect(IC2Effects.radiation());
        if (radiation == null) return InteractionResult.PASS;
        ItemStack stack = player.getItemInHand(hand);
        int seconds = radiation.getDuration() / 20;
        int amount = Math.min(stack.getCount(), seconds);
        if (amount <= 0) return InteractionResult.PASS;
        if (!level.isClientSide()) {
            player.removeEffect(IC2Effects.radiation());
            if (amount < seconds) {
                player.addEffect(new MobEffectInstance(IC2Effects.radiation(), (seconds - amount) * 20, radiation.getAmplifier()));
            }
            if (!player.getAbilities().instabuild) stack.shrink(amount);
            level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EAT.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        return InteractionResult.SUCCESS;
    }
}
