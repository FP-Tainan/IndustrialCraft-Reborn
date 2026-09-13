package net.ic2reborn.item;

import net.ic2reborn.IC2Reborn;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

import java.util.EnumMap;
import java.util.Map;

/**
 * Materiais das armaduras do IC2 ({@code BlocksItems}). As elétricas e utilitárias não gastam
 * durabilidade: o material só dá a proteção e a textura no corpo.
 */
public final class IC2ArmorMaterials {
    private static final TagKey<Item> NO_REPAIR = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(IC2Reborn.MODID, "no_repair"));

    /** IC2: IC2_BRONZE (15, {2, 5, 6, 2}, 9). */
    public static final ArmorMaterial BRONZE = material(15, 2, 6, 5, 2, 9, 0.0F, IC2ToolMaterials.BRONZE_INGOTS, "bronze");
    /** IC2: IC2_ALLOY (50, {4, 7, 9, 4}, 12, tenacidade 2). */
    public static final ArmorMaterial ALLOY = material(50, 4, 9, 7, 4, 12, 2.0F,
            TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(IC2Reborn.MODID, "advanced_alloy")), "alloy");
    /** Hazmat: durabilidade de diamante, sem proteção comum (protege de fogo, lava e radiação). */
    public static final ArmorMaterial HAZMAT = material(33, 0, 0, 0, 0, 1, 0.0F, NO_REPAIR, "hazmat");
    public static final ArmorMaterial RUBBER = material(33, 0, 0, 0, 0, 1, 0.0F, NO_REPAIR, "rubber");
    /** Nano: IC2 absorve 90% (15/40/30/15% por peça) enquanto tem carga. */
    public static final ArmorMaterial NANO = material(0, 3, 7, 5, 3, 1, 2.0F, NO_REPAIR, "nano");
    /** Quântica: absorve tudo (peitoral 120%) enquanto tem carga. */
    public static final ArmorMaterial QUANTUM = material(0, 3, 10, 6, 3, 1, 3.0F, NO_REPAIR, "quantum");

    private IC2ArmorMaterials() {}

    public static ResourceKey<EquipmentAsset> asset(String name) {
        @SuppressWarnings("unchecked")
        ResourceKey<? extends Registry<EquipmentAsset>> root = (ResourceKey<? extends Registry<EquipmentAsset>>) EquipmentAssets.ROOT_ID;
        return ResourceKey.create(root, Identifier.fromNamespaceAndPath(IC2Reborn.MODID, name));
    }

    private static ArmorMaterial material(int durability, int helmet, int chestplate, int leggings, int boots, int enchantment,
                                          float toughness, TagKey<Item> repair, String asset) {
        Map<ArmorType, Integer> defense = new EnumMap<>(ArmorType.class);
        defense.put(ArmorType.HELMET, helmet);
        defense.put(ArmorType.CHESTPLATE, chestplate);
        defense.put(ArmorType.LEGGINGS, leggings);
        defense.put(ArmorType.BOOTS, boots);
        defense.put(ArmorType.BODY, chestplate);
        return new ArmorMaterial(durability, defense, enchantment, SoundEvents.ARMOR_EQUIP_IRON, toughness, 0.0F, repair, asset(asset));
    }
}
