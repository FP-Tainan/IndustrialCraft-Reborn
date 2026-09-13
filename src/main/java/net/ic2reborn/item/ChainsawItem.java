package net.ic2reborn.item;

import net.craftenergy.api.EnergyUnits;
import net.craftenergy.content.block.RubberLeavesBlock;
import net.craftenergy.content.block.RubberWoodBlock;
import net.craftenergy.content.item.ElectricItem;
import net.craftenergy.content.item.EnergyItems;
import net.ic2reborn.registry.IC2Components;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
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
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Motosserra do IC2 ({@code ItemElectricToolChainsaw}): machado, espada e tesoura elétricos.
 * Botão direito liga/desliga o motor: ligada a corrente gira e cortar um tronco derruba a árvore
 * inteira (um uso de energia por tronco). Agachado + botão direito liga/desliga a tosquia.
 */
public class ChainsawItem extends ElectricItem {
    /** IC2: 30.000 EU, 100 EU/t, nível 1, 100 EU por uso. */
    private static final long CAPACITY = EnergyUnits.fromCWh(15_000);
    public static final long OPERATION_ENERGY = EnergyUnits.fromCWh(50);
    private static final float SPEED = 12.0F;
    /** IC2: 9 de dano com carga (1 do soco + 8). */
    private static final float DAMAGE_BONUS = 8.0F;
    /** Maior árvore derrubada de uma vez. */
    public static final int MAX_LOGS = 256;

    /** Evita que os troncos quebrados pela derrubada disparem outra derrubada. */
    private static boolean felling;

    public ChainsawItem(Properties properties) {
        super(properties, CAPACITY, 50_000, 220);
    }

    private static boolean charged(ItemStack stack) {
        return EnergyItems.getStored(stack) >= OPERATION_ENERGY;
    }

    public static boolean isActive(ItemStack stack) {
        return stack.has(IC2Components.ACTIVE.get());
    }

    private static boolean shearDisabled(ItemStack stack) {
        return stack.has(IC2Components.NO_SHEAR.get());
    }

    private static boolean shearBlock(BlockState state) {
        return state.is(BlockTags.LEAVES) || state.is(BlockTags.WOOL) || state.is(Blocks.COBWEB) || state.is(Blocks.VINE);
    }

    private static boolean isLog(BlockState state) {
        return state.is(BlockTags.LOGS) || state.getBlock() instanceof RubberWoodBlock;
    }

    private static boolean isNaturalLeaves(BlockState state) {
        boolean leaves = state.is(BlockTags.LEAVES) || state.getBlock() instanceof RubberLeavesBlock;
        return leaves && !(state.hasProperty(LeavesBlock.PERSISTENT) && state.getValue(LeavesBlock.PERSISTENT));
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
            if (!felling && isActive(stack) && isLog(state) && level instanceof ServerLevel serverLevel
                    && miner instanceof ServerPlayer player && !player.isShiftKeyDown()) {
                fellTree(stack, serverLevel, pos, player);
            }
        }
        return true;
    }

    /**
     * Derruba os troncos ligados ao que foi cortado (para cima e para os lados). Só vale para
     * árvores: precisa ter folhas naturais encostadas, para não desmontar casas de madeira.
     *
     * @return quantos troncos caíram além do primeiro
     */
    public static int fellTree(ItemStack stack, ServerLevel level, BlockPos origin, ServerPlayer player) {
        Set<BlockPos> logs = new LinkedHashSet<>();
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        visited.add(origin);
        queue.add(origin);
        boolean leaves = false;
        while (!queue.isEmpty() && logs.size() < MAX_LOGS) {
            BlockPos current = queue.poll();
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos next = current.offset(dx, dy, dz);
                        if (next.getY() < origin.getY() || !visited.add(next)) continue;
                        BlockState state = level.getBlockState(next);
                        if (isLog(state)) {
                            logs.add(next);
                            queue.add(next);
                        } else if (isNaturalLeaves(state)) {
                            leaves = true;
                        }
                    }
                }
            }
        }
        if (!leaves) return 0;

        int felled = 0;
        felling = true;
        try {
            for (BlockPos log : logs) {
                if (!EnergyItems.use(stack, OPERATION_ENERGY)) break;
                if (level.destroyBlock(log, true, player)) felled++;
            }
        } finally {
            felling = false;
        }
        return felled;
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
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
        if (player.isShiftKeyDown()) {
            boolean disable = !shearDisabled(stack);
            if (disable) {
                stack.set(IC2Components.NO_SHEAR.get(), Unit.INSTANCE);
            } else {
                stack.remove(IC2Components.NO_SHEAR.get());
            }
            serverPlayer.sendSystemMessage(modeText(disable));
        } else if (isActive(stack)) {
            stack.remove(IC2Components.ACTIVE.get());
        } else if (charged(stack)) {
            stack.set(IC2Components.ACTIVE.get(), Unit.INSTANCE);
            level.playSound(null, player.blockPosition(), SoundEvents.MINECART_RIDING, SoundSource.PLAYERS, 0.4F, 1.6F);
        } else {
            return InteractionResult.PASS;
        }
        return InteractionResult.SUCCESS;
    }

    private static MutableComponent modeText(boolean noShear) {
        return Component.translatableWithFallback("message.ic2reborn.tool.mode", "Mode: %s",
                noShear ? Component.translatableWithFallback("message.ic2reborn.tool.mode.no_shear", "No Shearing")
                        : Component.translatableWithFallback("message.ic2reborn.tool.mode.normal", "Normal"));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        tooltip.accept(modeText(shearDisabled(stack)).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatableWithFallback("tooltip.ic2reborn.chainsaw",
                "Right-click: motor on/off (on: fells whole trees)").withStyle(ChatFormatting.DARK_GRAY));
    }
}
