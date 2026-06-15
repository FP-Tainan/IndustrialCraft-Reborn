package net.ic2reborn.item;

import net.minecraft.world.item.Item;

/**
 * Martelo de forja — usado como ferramenta nas receitas de placas.
 *
 * No Minecraft/Forge 26.1.x, o Item não tem mais o override com ItemStack para
 * crafting remainder. O dano da ferramenta no craft é tratado em
 * CraftingToolEvents.
 */
public class HammerItem extends Item {
    public HammerItem(Properties props) {
        super(props);
    }
}
