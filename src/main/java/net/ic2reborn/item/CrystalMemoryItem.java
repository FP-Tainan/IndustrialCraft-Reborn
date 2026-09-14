package net.ic2reborn.item;

import net.ic2reborn.registry.IC2Components;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/** Memória de cristal do IC2: guarda um molde de UU-matter (o item que o scanner leu). */
public class CrystalMemoryItem extends Item {
    public CrystalMemoryItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static @Nullable Item pattern(ItemStack stack) {
        return stack.get(IC2Components.PATTERN.get());
    }

    public static void setPattern(ItemStack stack, @Nullable Item pattern) {
        if (pattern == null) {
            stack.remove(IC2Components.PATTERN.get());
        } else {
            stack.set(IC2Components.PATTERN.get(), pattern);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        Item pattern = pattern(stack);
        tooltip.accept(pattern == null
                ? Component.translatableWithFallback("tooltip.ic2reborn.crystal_memory.empty", "Empty").withStyle(ChatFormatting.GRAY)
                : Component.translatableWithFallback("tooltip.ic2reborn.crystal_memory.item", "Pattern: %s",
                        new ItemStack(pattern).getHoverName()).withStyle(ChatFormatting.GRAY));
    }
}
