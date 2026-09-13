package net.ic2reborn.item;

import net.craftenergy.api.EnergyUnits;
import net.craftenergy.content.item.ElectricItem;
import net.craftenergy.content.item.EnergyItems;
import net.ic2reborn.registry.IC2Components;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Nanossabre do IC2 ({@code ItemNanoSaber}): botão direito liga e desliga. Ligado dá 20 de dano e
 * corta teias, mas gasta energia só de ficar ligado; sem energia desliga sozinho.
 */
public class NanoSaberItem extends ElectricItem {
    /** IC2: 160.000 EU, 500 EU/t, nível 3. */
    private static final long CAPACITY = EnergyUnits.fromCWh(80_000);
    private static final long HIT_ENERGY = EnergyUnits.fromCWh(200);
    private static final long BREAK_ENERGY = EnergyUnits.fromCWh(40);
    private static final long ACTIVATE_ENERGY = EnergyUnits.fromCWh(8);
    /** Ligado na mão/armadura: 64 EU a cada 16 ticks; no inventário: 16 EU a cada 64 ticks. */
    private static final long HELD_DRAIN = EnergyUnits.fromCWh(32);
    private static final long STORED_DRAIN = EnergyUnits.fromCWh(8);

    public NanoSaberItem(Properties properties) {
        super(properties, CAPACITY, 250_000, 2_400);
    }

    public static boolean isActive(ItemStack stack) {
        return stack.has(IC2Components.ACTIVE.get());
    }

    private static void setActive(ItemStack stack, boolean active) {
        if (active) {
            stack.set(IC2Components.ACTIVE.get(), Unit.INSTANCE);
        } else {
            stack.remove(IC2Components.ACTIVE.get());
        }
    }

    private static void drain(ItemStack stack, long amount) {
        if (!EnergyItems.use(stack, amount)) setActive(stack, false);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            if (isActive(stack)) {
                setActive(stack, false);
            } else if (EnergyItems.getStored(stack) >= ACTIVATE_ENERGY) {
                setActive(stack, true);
                level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.5F, 2.0F);
            } else {
                return InteractionResult.PASS;
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        if (!isActive(stack)) return 1.0F;
        return state.is(Blocks.COBWEB) ? 50.0F : 4.0F;
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return isActive(stack) && state.is(Blocks.COBWEB);
    }

    @Override
    public boolean canDestroyBlock(ItemStack stack, BlockState state, Level level, BlockPos pos, LivingEntity user) {
        return !(user instanceof Player player && player.isCreative());
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner) {
        if (!level.isClientSide() && isActive(stack)) drain(stack, BREAK_ENERGY);
        return true;
    }

    @Override
    public float getAttackDamageBonus(Entity target, float damage, DamageSource source) {
        ItemStack weapon = source.getWeaponItem();
        if (weapon == null || weapon.getItem() != this) return 0.0F;
        // IC2: 4 de dano desligado, 20 ligado (o soco já dá 1)
        return isActive(weapon) && EnergyItems.getStored(weapon) >= HIT_ENERGY ? 19.0F : 3.0F;
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (isActive(stack)) drain(stack, HIT_ENERGY);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
        if (!isActive(stack)) return;
        long time = level.getGameTime();
        if (slot != null) {
            if (time % 16 == 0) drain(stack, HELD_DRAIN);
        } else if (time % 64 == 0) {
            drain(stack, STORED_DRAIN);
        }
    }
}
