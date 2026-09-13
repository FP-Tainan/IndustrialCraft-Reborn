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

    /** Palha orgânica: macerar plantas; na enlatadora, com água, vira biomassa. */
    public static final RegistryObject<Item> BIO_CHAFF =
            ITEMS.register("bio_chaff", () -> new Item(new Item.Properties().setId(ITEMS.key("bio_chaff"))));

    // itens que as receitas do conformador, cortador e centrífuga do IC2 pedem
    public static final RegistryObject<Item> PLATE_OBSIDIAN = simple("plate_obsidian");
    /** Crédito industrial (IC2: ic2:crafting#coin). */
    public static final RegistryObject<Item> COIN = simple("coin");
    /** Barra de combustível vazia, enchida na enlatadora com urânio. */
    public static final RegistryObject<Item> FUEL_ROD = simple("fuel_rod");
    public static final RegistryObject<Item> SLAG = simple("slag");
    public static final RegistryObject<Item> DUST_CLAY = simple("dust_clay");
    public static final RegistryObject<Item> CRUSHED_URANIUM = simple("crushed_uranium");
    public static final RegistryObject<Item> PURIFIED_URANIUM = simple("purified_uranium");
    /** Cinzas do gerador de calor sólido. */
    public static final RegistryObject<Item> ASHES = simple("ashes");

    /** Scanner OD do IC2 ("scanner" já é o bloco scanner de matéria UU). */
    public static final RegistryObject<Item> OD_SCANNER = ITEMS.register("od_scanner",
            () -> new net.ic2reborn.item.ScannerItem(new Item.Properties().setId(ITEMS.key("od_scanner")),
                    net.ic2reborn.item.ScannerItem.Tier.OD));

    // plantações do IC2
    /** Fungo da Terra: comer tira os efeitos ruins. */
    public static final RegistryObject<Item> TERRA_WART = ITEMS.register("terra_wart",
            () -> new net.ic2reborn.item.TerraWartItem(new Item.Properties().setId(ITEMS.key("terra_wart"))
                    .food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(0).saturationModifier(1.0F).alwaysEdible().build())));
    public static final RegistryObject<Item> OIL_BERRY = simple("oil_berry");
    public static final RegistryObject<Item> MILK_WART = simple("milk_wart");
    public static final RegistryObject<Item> BOBS_YER_UNCLE_RANKS_BERRY = simple("bobs_yer_uncle_ranks_berry");
    public static final RegistryObject<Item> HOPS = simple("hops");
    /** Erva daninha tirada com a espátula de capina. */
    public static final RegistryObject<Item> WEED = simple("weed");
    public static final RegistryObject<Item> DUST_SMALL_DIAMOND = simple("dust_small_diamond");
    public static final RegistryObject<Item> DUST_ENDER_PEARL = simple("dust_ender_pearl");
    /** Saco de sementes com planta e atributos (componente ic2reborn:crop_seed). */
    public static final RegistryObject<Item> CROP_SEED_BAG = ITEMS.register("crop_seed_bag",
            () -> new net.ic2reborn.item.CropSeedItem(new Item.Properties().setId(ITEMS.key("crop_seed_bag"))));
    /** Célula de hidratação: 10.000 mB de água para plantações e Cropmatron. */
    public static final RegistryObject<Item> HYDRATION_CELL = ITEMS.register("hydration_cell",
            () -> new net.ic2reborn.item.HydrationCellItem(new Item.Properties().setId(ITEMS.key("hydration_cell"))));
    private static RegistryObject<Item> simple(String name) {
        return ITEMS.register(name, () -> new Item(new Item.Properties().setId(ITEMS.key(name))));
    }

    public static final RegistryObject<Item> RAW_LEAD =
            ITEMS.register("raw_lead", () -> new Item(new Item.Properties().setId(ITEMS.key("raw_lead"))));
    public static final RegistryObject<Item> RAW_URANIUM =
            ITEMS.register("raw_uranium", () -> new Item(new Item.Properties().setId(ITEMS.key("raw_uranium"))));

    public static final RegistryObject<Item> INGOT_LEAD =
            ITEMS.register("ingot_lead", () -> new Item(new Item.Properties().setId(ITEMS.key("ingot_lead"))));
    public static final RegistryObject<Item> INGOT_URANIUM =
            ITEMS.register("ingot_uranium", () -> new Item(new Item.Properties().setId(ITEMS.key("ingot_uranium"))));
}
