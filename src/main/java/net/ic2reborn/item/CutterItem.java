package net.ic2reborn.item;

import net.minecraft.world.item.Item;

/**
 * Alicate cortador — usado como ferramenta nas receitas de cabos.
 *
 * No Minecraft/Forge 26.1.x, o Item não tem mais o override com ItemStack para
 * crafting remainder. O dano da ferramenta no craft é tratado em
 * CraftingToolEvents.
 */
public class CutterItem extends Item {
    public CutterItem(Properties props) {
        super(props);
    }
}
