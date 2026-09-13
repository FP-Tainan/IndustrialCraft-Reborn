package net.ic2reborn.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;

/** Materiais das ferramentas comuns do IC2. */
public final class IC2ToolMaterials {
    public static final TagKey<Item> BRONZE_INGOTS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "ingots/bronze"));

    /** IC2: nível de ferro, 350 usos, velocidade 6, +2 de dano, encantabilidade 13, reparo com bronze. */
    public static final ToolMaterial BRONZE = new ToolMaterial(BlockTags.INCORRECT_FOR_IRON_TOOL, 350, 6.0F, 2.0F, 13, BRONZE_INGOTS);

    private IC2ToolMaterials() {}
}
