package net.ic2reborn.fluid;

import net.craftenergy.registry.DeferredRegister;
import net.craftenergy.registry.RegistryObject;
import net.ic2reborn.IC2Reborn;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Os fluidos do IC2 ({@code ic2.core.ref.FluidName}, sem os metais derretidos): cada um tem
 * bloco no mundo, célula e — os líquidos — balde. Gases (ar, vapor, biogás...) só vão em células.
 */
public final class IC2Fluids {
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, IC2Reborn.MODID);
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, IC2Reborn.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, IC2Reborn.MODID);

    private static final List<Entry> ALL = new ArrayList<>();

    public static final Entry AIR = gas("air");
    public static final Entry BIOGAS = gas("biogas");
    public static final Entry BIOMASS = liquid("biomass", true);
    public static final Entry CONSTRUCTION_FOAM = liquid("construction_foam", true);
    public static final Entry COOLANT = liquid("coolant", true);
    public static final Entry DISTILLED_WATER = liquid("distilled_water", true);
    public static final Entry HOT_COOLANT = liquid("hot_coolant", true);
    public static final Entry HOT_WATER = liquid("hot_water", true);
    public static final Entry PAHOEHOE_LAVA = liquid("pahoehoe_lava", true);
    public static final Entry STEAM = gas("steam");
    public static final Entry SUPERHEATED_STEAM = gas("superheated_steam");
    public static final Entry UU_MATTER = liquid("uu_matter", true);
    /** A célula de Weed-EX já existe ({@code ic2weed_ex_cell}). */
    public static final Entry WEED_EX = register("weed_ex", true, false);
    public static final Entry OXYGEN = gas("oxygen");
    public static final Entry HYDROGEN = gas("hydrogen");
    public static final Entry HEAVY_WATER = liquid("heavy_water", true);
    public static final Entry DEUTERIUM = gas("deuterium");
    public static final Entry CREOSOTE = liquid("creosote", true);
    /** Sem balde próprio para não confundir com o balde de leite do Minecraft. */
    public static final Entry MILK = liquid("milk", false);

    private IC2Fluids() {}

    public static List<Entry> all() {
        return Collections.unmodifiableList(ALL);
    }

    private static Entry gas(String name) {
        return register(name, false, true);
    }

    private static Entry liquid(String name, boolean bucket) {
        return register(name, bucket, true);
    }

    private static Entry register(String name, boolean bucket, boolean cell) {
        Entry entry = new Entry(name);
        entry.source = FLUIDS.register(name, () -> new IC2Fluid.Source(entry));
        entry.flowingFluid = FLUIDS.register("flowing_" + name, () -> new IC2Fluid.Flowing(entry));
        entry.block = BLOCKS.register(name, () -> new LiquidBlock(entry.source.get(),
                BlockBehaviour.Properties.ofFullCopy(Blocks.WATER).setId(BLOCKS.key(name)).noLootTable()) {});
        if (bucket) {
            String id = name + "_bucket";
            entry.bucket = ITEMS.register(id, () -> new BucketItem(entry.source.get(),
                    new Item.Properties().setId(ITEMS.key(id)).craftRemainder(Items.BUCKET).stacksTo(1)));
        }
        if (cell) {
            String id = name + "_cell";
            entry.cell = ITEMS.register(id, () -> new Item(new Item.Properties().setId(ITEMS.key(id))));
        }
        ALL.add(entry);
        return entry;
    }

    public static final class Entry {
        private final String name;
        private RegistryObject<IC2Fluid.Source> source;
        private RegistryObject<IC2Fluid.Flowing> flowingFluid;
        private RegistryObject<LiquidBlock> block;
        private @Nullable RegistryObject<Item> bucket;
        private @Nullable RegistryObject<Item> cell;

        private Entry(String name) {
            this.name = name;
        }

        public String name() {
            return this.name;
        }

        /** O fluido parado (o que vai nos tanques). */
        public Fluid fluid() {
            return this.source.get();
        }

        public Fluid flowing() {
            return this.flowingFluid.get();
        }

        public RegistryObject<LiquidBlock> block() {
            return this.block;
        }

        public @Nullable RegistryObject<Item> bucket() {
            return this.bucket;
        }

        public @Nullable RegistryObject<Item> cell() {
            return this.cell;
        }
    }
}
