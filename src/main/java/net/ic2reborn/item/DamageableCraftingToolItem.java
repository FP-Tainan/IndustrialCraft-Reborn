package net.ic2reborn.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

/**
 * Ferramenta de crafting reutilizavel, estilo IC2.
 *
 * Quando usada numa receita, ela volta para o mesmo slot da grade com +1 de dano.
 * Quando chega no limite de durabilidade, nao deixa remainder e some/quebra.
 */
public class DamageableCraftingToolItem extends Item {
    public DamageableCraftingToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStackTemplate getCraftingRemainder(ItemStack stack) {
        if (stack.isEmpty()) return null;

        ItemStack remainder = stack.copyWithCount(1);
        if (!remainder.isDamageableItem()) {
            return ItemStackTemplate.fromNonEmptyStack(remainder);
        }

        int nextDamage = remainder.getDamageValue() + 1;
        if (nextDamage >= remainder.getMaxDamage()) {
            return null;
        }

        remainder.setDamageValue(nextDamage);
        return ItemStackTemplate.fromNonEmptyStack(remainder);
    }
}
