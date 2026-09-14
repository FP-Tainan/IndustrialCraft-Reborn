package net.ic2reborn.item;

import net.ic2reborn.registry.IC2Components;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * Upgrades de máquina do IC2 ({@code ItemUpgradeModule}). Ejetores e puxadores: agachado + botão
 * direito numa face escolhe o lado (de novo na mesma face volta para todos os lados).
 */
public class UpgradeItem extends Item {
    public enum Kind {
        OVERCLOCKER, TRANSFORMER, ENERGY_STORAGE, EJECTOR, PULLING, FLUID_EJECTOR, FLUID_PULLING, REDSTONE_INVERTER;

        boolean directional() {
            return this == EJECTOR || this == PULLING || this == FLUID_EJECTOR || this == FLUID_PULLING;
        }
    }

    private final Kind kind;

    public UpgradeItem(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
    }

    public Kind kind() {
        return this.kind;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!this.kind.directional()) return InteractionResult.PASS;
        if (!(context.getPlayer() instanceof ServerPlayer player)) return InteractionResult.SUCCESS;
        ItemStack stack = context.getItemInHand();
        Direction face = context.getClickedFace();
        if (face == stack.get(IC2Components.UPGRADE_DIRECTION.get())) {
            stack.remove(IC2Components.UPGRADE_DIRECTION.get());
        } else {
            stack.set(IC2Components.UPGRADE_DIRECTION.get(), face);
        }
        player.sendSystemMessage(sideText(stack), true);
        return InteractionResult.SUCCESS;
    }

    private Component sideText(ItemStack stack) {
        Direction direction = stack.get(IC2Components.UPGRADE_DIRECTION.get());
        Component side = direction == null
                ? Component.translatableWithFallback("tooltip.ic2reborn.upgrade.any_side", "any side")
                : Component.translatableWithFallback("tooltip.ic2reborn.upgrade.side." + direction.getSerializedName(), direction.getSerializedName());
        boolean eject = this.kind == Kind.EJECTOR || this.kind == Kind.FLUID_EJECTOR;
        return Component.translatableWithFallback(eject ? "tooltip.ic2reborn.upgrade.ejector" : "tooltip.ic2reborn.upgrade.pulling",
                eject ? "Ejects to %s" : "Pulls from %s", side);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        int count = stack.getCount();
        switch (this.kind) {
            case OVERCLOCKER -> {
                tooltip.accept(Component.translatableWithFallback("tooltip.ic2reborn.upgrade.overclocker.time", "Time: %s%%",
                        String.format(Locale.ROOT, "%.0f", 100 * Math.pow(0.7, count))).withStyle(ChatFormatting.GRAY));
                tooltip.accept(Component.translatableWithFallback("tooltip.ic2reborn.upgrade.overclocker.power", "Power: %s%%",
                        String.format(Locale.ROOT, "%.0f", 100 * Math.pow(1.6, count))).withStyle(ChatFormatting.GRAY));
            }
            case TRANSFORMER -> tooltip.accept(Component.translatableWithFallback("tooltip.ic2reborn.upgrade.transformer",
                    "Voltage tier +%s", count).withStyle(ChatFormatting.GRAY));
            case ENERGY_STORAGE -> tooltip.accept(Component.translatableWithFallback("tooltip.ic2reborn.upgrade.storage",
                    "Energy storage +%s CWh", 5_000 * count).withStyle(ChatFormatting.GRAY));
            case REDSTONE_INVERTER -> tooltip.accept(Component.translatableWithFallback("tooltip.ic2reborn.upgrade.redstone",
                    "Inverts the redstone signal").withStyle(ChatFormatting.GRAY));
            default -> {
                tooltip.accept(sideText(stack).copy().withStyle(ChatFormatting.GRAY));
                tooltip.accept(Component.translatableWithFallback("tooltip.ic2reborn.upgrade.choose_side",
                        "Sneak + right-click a block face to choose the side").withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }
}
