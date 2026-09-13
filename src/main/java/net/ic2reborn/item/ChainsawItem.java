package net.ic2reborn.item;

import net.craftenergy.api.EnergyUnits;
import net.craftenergy.content.item.ElectricItem;
import net.craftenergy.content.item.EnergyItems;
import net.ic2reborn.registry.IC2Components;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

import java.util.function.Consumer;

/**
 * Motosserra do IC2 ({@code ItemElectricToolChainsaw}): machado, espada e tesoura elétricos.
 * Agachado + botão direito liga/desliga a tosquia.
 */
public class ChainsawItem extends ElectricItem {
    /** IC2: 30.000 EU, 100 EU/t, nível 1, 100 EU por uso. */
    private static final long CAPACITY = EnergyUnits.fromCWh(15_000);
    public static final long OPERATION_ENERGY = EnergyUnits.fromCWh(50);
    private static final float SPEED = 12.0F;
    /** IC2: 9 de dano com carga (1 do soco + 8). */
    private static final float DAMAGE_BONUS = 8.0F;

    public ChainsawItem(Properties properties) {
        super(properties, CAPACITY, 50_000, 220);
    }

    private static boolean charged(ItemStack stack) {
        return EnergyItems.getStored(stack) >= OPERATION_ENERGY;
    }

    private static boolean shearDisabled(ItemStack stack) {
        return stack.has(IC2Components.NO_SHEAR.get());
    }

    private static boolean shearBlock(BlockState state) {
        return state.is(BlockTags.LEAVES) || state.is(BlockTags.WOOL) || state.is(Blocks.COBWEB) || state.is(Blocks.VINE);
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return charged(stack) && (state.is(BlockTags.MINEABLE_WITH_AXE) || shearBlock(state)) ? SPEED : 1.0F;
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return charged(stack) && (state.is(BlockTags.MINEABLE_WITH_AXE) && !state.is(BlockTags.INCORRECT_FOR_IRON_TOOL) || shearBlock(state));
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner) {
        if (!level.isClientSide() && state.getDestroySpeed(level, pos) != 0.0F) {
            EnergyItems.use(stack, OPERATION_ENERGY);
        }
        return true;
    }

    @Override
    public float getAttackDamageBonus(Entity target, float damage, DamageSource source) {
        ItemStack weapon = source.getWeaponItem();
        return weapon != null && weapon.getItem() == this && charged(weapon) ? DAMAGE_BONUS : 0.0F;
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        EnergyItems.use(stack, OPERATION_ENERGY);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof Shearable shearable) || shearDisabled(stack) || !charged(stack) || !shearable.readyForShearing()) {
            return InteractionResult.PASS;
        }
        if (player.level() instanceof ServerLevel level) {
            shearable.shear(level, SoundSource.PLAYERS, stack);
            target.gameEvent(GameEvent.SHEAR, player);
            EnergyItems.use(player.getItemInHand(hand), OPERATION_ENERGY);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            boolean disable = !shearDisabled(stack);
            if (disable) {
                stack.set(IC2Components.NO_SHEAR.get(), Unit.INSTANCE);
            } else {
                stack.remove(IC2Components.NO_SHEAR.get());
            }
            serverPlayer.sendSystemMessage(modeText(disable));
        }
        return InteractionResult.SUCCESS;
    }

    private static net.minecraft.network.chat.MutableComponent modeText(boolean noShear) {
        return Component.translatableWithFallback("message.ic2reborn.tool.mode", "Mode: %s",
                noShear ? Component.translatableWithFallback("message.ic2reborn.tool.mode.no_shear", "No Shearing")
                        : Component.translatableWithFallback("message.ic2reborn.tool.mode.normal", "Normal"));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        tooltip.accept(modeText(shearDisabled(stack)).withStyle(ChatFormatting.GRAY));
    }
}
