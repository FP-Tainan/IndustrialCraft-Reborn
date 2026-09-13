package net.craftenergy.content;

import net.craftenergy.content.item.DamageableCraftingToolItem;
import net.craftenergy.content.item.TreetapItem;
import net.craftenergy.fabric.CraftEnergyApi;
import net.craftenergy.registry.DeferredRegister;
import net.craftenergy.registry.RegistryObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/** Itens do Craft Energy: cabos, seringueira, estanho, placas e ferramentas de crafting. */
public final class CEItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, CraftEnergyApi.MOD_ID);

    // ── cabos ─────────────────────────────────────────────────────────────
    public static final RegistryObject<BlockItem> CABLE_TIN = block("cable_tin", CEBlocks.CABLE_TIN);
    public static final RegistryObject<BlockItem> CABLE_TIN_INSULATED = block("cable_tin_insulated", CEBlocks.CABLE_TIN_INSULATED);
    public static final RegistryObject<BlockItem> CABLE_COPPER = block("cable_copper", CEBlocks.CABLE_COPPER);
    public static final RegistryObject<BlockItem> CABLE_COPPER_INSULATED = block("cable_copper_insulated", CEBlocks.CABLE_COPPER_INSULATED);
    public static final RegistryObject<BlockItem> CABLE_GOLD = block("cable_gold", CEBlocks.CABLE_GOLD);
    public static final RegistryObject<BlockItem> CABLE_GOLD_INSULATED = block("cable_gold_insulated", CEBlocks.CABLE_GOLD_INSULATED);
    public static final RegistryObject<BlockItem> CABLE_GOLD_DOUBLE_INSULATED = block("cable_gold_double_insulated", CEBlocks.CABLE_GOLD_DOUBLE_INSULATED);
    public static final RegistryObject<BlockItem> CABLE_IRON = block("cable_iron", CEBlocks.CABLE_IRON);
    public static final RegistryObject<BlockItem> CABLE_IRON_INSULATED = block("cable_iron_insulated", CEBlocks.CABLE_IRON_INSULATED);
    public static final RegistryObject<BlockItem> CABLE_IRON_DOUBLE_INSULATED = block("cable_iron_double_insulated", CEBlocks.CABLE_IRON_DOUBLE_INSULATED);
    public static final RegistryObject<BlockItem> CABLE_IRON_TRIPLE_INSULATED = block("cable_iron_triple_insulated", CEBlocks.CABLE_IRON_TRIPLE_INSULATED);
    public static final RegistryObject<BlockItem> GLASS_FIBRE_CABLE = block("glass_fibre_cable", CEBlocks.GLASS_FIBRE_CABLE);
    public static final RegistryObject<BlockItem> CABLE_DETECTOR = block("cable_detector", CEBlocks.CABLE_DETECTOR);
    public static final RegistryObject<BlockItem> CABLE_SPLITTER = block("cable_splitter", CEBlocks.CABLE_SPLITTER);

    // ── seringueira e borracha ────────────────────────────────────────────
    public static final RegistryObject<BlockItem> RUBBER_LOG = block("rubber_log", CEBlocks.RUBBER_LOG);
    public static final RegistryObject<BlockItem> RUBBER_LEAVES = block("rubber_leaves", CEBlocks.RUBBER_LEAVES);
    public static final RegistryObject<BlockItem> RUBBER_SAPLING = block("rubber_sapling", CEBlocks.RUBBER_SAPLING);
    public static final RegistryObject<BlockItem> RUBBER_WOOD_PLANKS = block("rubber_wood_planks", CEBlocks.RUBBER_WOOD_PLANKS);
    public static final RegistryObject<Item> RUBBER_WOOD = item("rubber_wood");
    public static final RegistryObject<Item> RUBBER = item("rubber");

    // ── estanho ───────────────────────────────────────────────────────────
    public static final RegistryObject<BlockItem> TIN_ORE = block("tin_ore", CEBlocks.TIN_ORE);
    public static final RegistryObject<BlockItem> DEEPSLATE_TIN_ORE = block("deepslate_tin_ore", CEBlocks.DEEPSLATE_TIN_ORE);
    public static final RegistryObject<BlockItem> TIN_BLOCK = block("tin_block", CEBlocks.TIN_BLOCK);
    public static final RegistryObject<Item> RAW_TIN = item("raw_tin");
    public static final RegistryObject<Item> INGOT_TIN = item("ingot_tin");

    // ── placas ────────────────────────────────────────────────────────────
    public static final RegistryObject<Item> PLATE_COPPER = item("plate_copper");
    public static final RegistryObject<Item> PLATE_TIN = item("plate_tin");
    public static final RegistryObject<Item> PLATE_GOLD = item("plate_gold");
    public static final RegistryObject<Item> PLATE_IRON = item("plate_iron");

    // ── ferramentas ───────────────────────────────────────────────────────
    public static final RegistryObject<TreetapItem> TREETAP = ITEMS.register("treetap",
            () -> new TreetapItem(new Item.Properties().setId(ITEMS.key("treetap")).durability(20)));
    public static final RegistryObject<DamageableCraftingToolItem> CUTTER = ITEMS.register("cutter",
            () -> new DamageableCraftingToolItem(new Item.Properties().setId(ITEMS.key("cutter")).durability(50)));
    public static final RegistryObject<DamageableCraftingToolItem> HAMMER = ITEMS.register("hammer",
            () -> new DamageableCraftingToolItem(new Item.Properties().setId(ITEMS.key("hammer")).durability(100)));

    private CEItems() {}

    private static RegistryObject<BlockItem> block(String name, RegistryObject<? extends Block> block) {
        return ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties().setId(ITEMS.key(name))));
    }

    private static RegistryObject<Item> item(String name) {
        return ITEMS.register(name, () -> new Item(new Item.Properties().setId(ITEMS.key(name))));
    }
}
