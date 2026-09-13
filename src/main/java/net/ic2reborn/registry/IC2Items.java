package net.ic2reborn.registry;

import net.craftenergy.registry.DeferredRegister;
import net.craftenergy.registry.RegistryObject;
import net.ic2reborn.IC2Reborn;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;

/** Itens dos minérios exclusivos do IC2 Reborn. Estanho, borracha e ferramentas ficam no Craft Energy. */
public class IC2Items {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, IC2Reborn.MODID);

    public static final RegistryObject<BlockItem> LEAD_ORE =
            ITEMS.register("lead_ore", () -> new BlockItem(IC2Blocks.LEAD_ORE.get(), new Item.Properties().setId(ITEMS.key("lead_ore")).useBlockDescriptionPrefix()));
    public static final RegistryObject<BlockItem> DEEPSLATE_LEAD_ORE =
            ITEMS.register("deepslate_lead_ore", () -> new BlockItem(IC2Blocks.DEEPSLATE_LEAD_ORE.get(), new Item.Properties().setId(ITEMS.key("deepslate_lead_ore")).useBlockDescriptionPrefix()));
    public static final RegistryObject<BlockItem> URANIUM_ORE =
            ITEMS.register("uranium_ore", () -> new BlockItem(IC2Blocks.URANIUM_ORE.get(), new Item.Properties().setId(ITEMS.key("uranium_ore")).useBlockDescriptionPrefix()));
    public static final RegistryObject<BlockItem> DEEPSLATE_URANIUM_ORE =
            ITEMS.register("deepslate_uranium_ore", () -> new BlockItem(IC2Blocks.DEEPSLATE_URANIUM_ORE.get(), new Item.Properties().setId(ITEMS.key("deepslate_uranium_ore")).useBlockDescriptionPrefix()));

    /** Lata do enlatador de sólidos: 2 de fome e devolve a lata vazia. Registrada depois de IC2AutoItems. */
    public static final RegistryObject<Item> FILLED_TIN_CAN =
            ITEMS.register("filled_tin_can", () -> new Item(new Item.Properties().setId(ITEMS.key("filled_tin_can"))
                    .food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(2).saturationModifier(0.3F).build())
                    .usingConvertsTo(IC2AutoItems.TIN_CAN.get())));

    public static final RegistryObject<Item> RAW_LEAD =
            ITEMS.register("raw_lead", () -> new Item(new Item.Properties().setId(ITEMS.key("raw_lead"))));
    public static final RegistryObject<Item> RAW_URANIUM =
            ITEMS.register("raw_uranium", () -> new Item(new Item.Properties().setId(ITEMS.key("raw_uranium"))));

    public static final RegistryObject<Item> INGOT_LEAD =
            ITEMS.register("ingot_lead", () -> new Item(new Item.Properties().setId(ITEMS.key("ingot_lead"))));
    public static final RegistryObject<Item> INGOT_URANIUM =
            ITEMS.register("ingot_uranium", () -> new Item(new Item.Properties().setId(ITEMS.key("ingot_uranium"))));
}
