package net.ic2reborn.item;

import net.ic2reborn.registry.IC2Blocks;
import net.ic2reborn.registry.IC2Components;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Pulverizador de espuma do IC2 ({@code ItemSprayer}): espalha espuma de construção a partir do
 * bloco mirado (até 10 blocos, ou 1 no modo único), 100 mB por bloco, usando primeiro o CF pack
 * vestido. Abastece com célula de espuma na outra mão; agachado + botão direito troca o modo.
 */
public class FoamSprayerItem extends Item {
    public static final int CAPACITY = 8_000;
    public static final int PER_BLOCK = 100;

    public FoamSprayerItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static int foam(ItemStack stack) {
        return stack.getOrDefault(IC2Components.FUEL.get(), 0);
    }

    public static void setFoam(ItemStack stack, int millibuckets) {
        stack.set(IC2Components.FUEL.get(), Math.max(0, Math.min(CAPACITY, millibuckets)));
    }

    private static boolean single(ItemStack stack) {
        return stack.has(IC2Components.ACTIVE.get());
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        ItemStack cell = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (UtilityArmorItem.isFoamCell(cell) && foam(stack) + 1_000 <= CAPACITY) {
            if (!level.isClientSide()) {
                setFoam(stack, foam(stack) + 1_000);
                UtilityArmorItem.useUpCell(player, cell);
            }
            return InteractionResult.SUCCESS;
        }
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        if (player instanceof ServerPlayer serverPlayer) {
            boolean toSingle = !single(stack);
            if (toSingle) {
                stack.set(IC2Components.ACTIVE.get(), Unit.INSTANCE);
            } else {
                stack.remove(IC2Components.ACTIVE.get());
            }
            serverPlayer.sendSystemMessage(Component.translatableWithFallback("message.ic2reborn.tool.mode", "Mode: %s", modeName(toSingle)));
        }
        return InteractionResult.SUCCESS;
    }

    private static Component modeName(boolean single) {
        return single ? Component.translatableWithFallback("message.ic2reborn.tool.mode.single", "Single")
                : Component.translatableWithFallback("message.ic2reborn.tool.mode.normal", "Normal");
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || player.isShiftKeyDown()) return InteractionResult.PASS;
        Level level = context.getLevel();
        ItemStack stack = context.getItemInHand();
        ItemStack pack = player.getItemBySlot(EquipmentSlot.CHEST);
        int packFoam = pack.getItem() instanceof UtilityArmorItem utility && utility.kind() == UtilityArmorItem.Kind.CF_PACK
                ? UtilityArmorItem.fuel(pack) : 0;
        int maxBlocks = Math.min((foam(stack) + packFoam) / PER_BLOCK, single(stack) ? 1 : 10);
        if (maxBlocks <= 0) return InteractionResult.FAIL;
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        BlockPos start = context.getClickedPos().relative(context.getClickedFace());
        int placed = spray(level, start, towards(player.getLookAngle()).getOpposite(), maxBlocks);
        int cost = placed * PER_BLOCK;
        int fromPack = Math.min(cost, packFoam);
        if (fromPack > 0) UtilityArmorItem.setFuel(pack, packFoam - fromPack);
        if (cost > fromPack) setFoam(stack, foam(stack) - (cost - fromPack));
        return placed > 0 ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    /** Enche a partir de {@code start} (sem voltar na direção de quem pulveriza); retorna quantos blocos entraram. */
    public static int spray(Level level, BlockPos start, Direction excluded, int maxBlocks) {
        Set<BlockPos> positions = new LinkedHashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty() && positions.size() < maxBlocks) {
            BlockPos pos = queue.poll();
            if (level.getBlockState(pos).canBeReplaced() && positions.add(pos)) {
                for (Direction direction : Direction.values()) {
                    if (direction != excluded) queue.add(pos.relative(direction));
                }
            }
        }
        int placed = 0;
        for (BlockPos pos : positions) {
            if (level.setBlock(pos, IC2Blocks.FOAM.get().defaultBlockState(), Block.UPDATE_ALL)) placed++;
        }
        return placed;
    }

    private static Direction towards(Vec3 look) {
        double ax = Math.abs(look.x);
        double ay = Math.abs(look.y);
        double az = Math.abs(look.z);
        if (ay >= ax && ay >= az) return look.y > 0 ? Direction.UP : Direction.DOWN;
        if (ax >= az) return look.x > 0 ? Direction.EAST : Direction.WEST;
        return look.z > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * foam(stack) / CAPACITY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0xC8C8C8;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatableWithFallback("tooltip.ic2reborn.foam", "Construction foam: %s / %s mB", foam(stack), CAPACITY)
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatableWithFallback("message.ic2reborn.tool.mode", "Mode: %s", modeName(single(stack)))
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
