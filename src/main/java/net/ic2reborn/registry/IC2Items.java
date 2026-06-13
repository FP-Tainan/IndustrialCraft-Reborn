package net.ic2reborn.registry;

import net.ic2reborn.IC2Reborn;
import net.ic2reborn.item.TreetapItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class IC2Items {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, IC2Reborn.MODID);

    public static final RegistryObject<BlockItem> RUBBER_LOG =
            ITEMS.register("rubber_log", () -> new BlockItem(IC2Blocks.RUBBER_LOG.get(), new Item.Properties().setId(ITEMS.key("rubber_log"))));
    public static final RegistryObject<BlockItem> RUBBER_LEAVES =
            ITEMS.register("rubber_leaves", () -> new BlockItem(IC2Blocks.RUBBER_LEAVES.get(), new Item.Properties().setId(ITEMS.key("rubber_leaves"))));
    public static final RegistryObject<BlockItem> RUBBER_SAPLING =
            ITEMS.register("rubber_sapling", () -> new BlockItem(IC2Blocks.RUBBER_SAPLING.get(), new Item.Properties().setId(ITEMS.key("rubber_sapling"))));
    public static final RegistryObject<BlockItem> TIN_ORE =
            ITEMS.register("tin_ore", () -> new BlockItem(IC2Blocks.TIN_ORE.get(), new Item.Properties().setId(ITEMS.key("tin_ore"))));
    public static final RegistryObject<BlockItem> DEEPSLATE_TIN_ORE =
            ITEMS.register("deepslate_tin_ore", () -> new BlockItem(IC2Blocks.DEEPSLATE_TIN_ORE.get(), new Item.Properties().setId(ITEMS.key("deepslate_tin_ore"))));
    public static final RegistryObject<BlockItem> LEAD_ORE =
            ITEMS.register("lead_ore", () -> new BlockItem(IC2Blocks.LEAD_ORE.get(), new Item.Properties().setId(ITEMS.key("lead_ore"))));
    public static final RegistryObject<BlockItem> DEEPSLATE_LEAD_ORE =
            ITEMS.register("deepslate_lead_ore", () -> new BlockItem(IC2Blocks.DEEPSLATE_LEAD_ORE.get(), new Item.Properties().setId(ITEMS.key("deepslate_lead_ore"))));
    public static final RegistryObject<BlockItem> URANIUM_ORE =
            ITEMS.register("uranium_ore", () -> new BlockItem(IC2Blocks.URANIUM_ORE.get(), new Item.Properties().setId(ITEMS.key("uranium_ore"))));
    public static final RegistryObject<BlockItem> DEEPSLATE_URANIUM_ORE =
            ITEMS.register("deepslate_uranium_ore", () -> new BlockItem(IC2Blocks.DEEPSLATE_URANIUM_ORE.get(), new Item.Properties().setId(ITEMS.key("deepslate_uranium_ore"))));

    public static final RegistryObject<Item> RAW_TIN =
            ITEMS.register("raw_tin", () -> new Item(new Item.Properties().setId(ITEMS.key("raw_tin"))));
    public static final RegistryObject<Item> RAW_LEAD =
            ITEMS.register("raw_lead", () -> new Item(new Item.Properties().setId(ITEMS.key("raw_lead"))));
    public static final RegistryObject<Item> RAW_URANIUM =
            ITEMS.register("raw_uranium", () -> new Item(new Item.Properties().setId(ITEMS.key("raw_uranium"))));

    public static final RegistryObject<Item> RUBBER =
            ITEMS.register("rubber", () -> new Item(new Item.Properties().setId(ITEMS.key("rubber"))));
    public static final RegistryObject<Item> INGOT_TIN =
            ITEMS.register("ingot_tin", () -> new Item(new Item.Properties().setId(ITEMS.key("ingot_tin"))));
    public static final RegistryObject<Item> INGOT_LEAD =
            ITEMS.register("ingot_lead", () -> new Item(new Item.Properties().setId(ITEMS.key("ingot_lead"))));
    public static final RegistryObject<Item> INGOT_URANIUM =
            ITEMS.register("ingot_uranium", () -> new Item(new Item.Properties().setId(ITEMS.key("ingot_uranium"))));

    public static final RegistryObject<TreetapItem> TREETAP =
            ITEMS.register("treetap", () -> new TreetapItem(new Item.Properties().setId(ITEMS.key("treetap")).durability(20)));
}
