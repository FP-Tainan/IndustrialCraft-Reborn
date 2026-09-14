package net.ic2reborn.menu;

import io.netty.buffer.ByteBuf;
import net.ic2reborn.menu.layout.MachineLayout;
import net.ic2reborn.menu.layout.MachineLayouts;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * GUI profile of each IC2 machine.
 *
 * Each value lists the block ids that use it; its layout (slots, gauges, background)
 * is defined in {@link MachineLayouts}, copied from IC2 Experimental's guidef XML
 * files and hand-written Gui/Container classes.
 */
public enum MachineGuiType {
    /** Fallback for machines without a dedicated layout yet. */
    BASIC,

    // ── processing machines (guidef) ─────────────────────────────────────
    MACERATOR("macerator"),
    COMPRESSOR("compressor"),
    EXTRACTOR("extractor"),
    RECYCLER("recycler"),
    ELECTRIC_FURNACE("electric_furnace"),
    IRON_FURNACE("iron_furnace"),
    METAL_FORMER("metal_former"),
    BLOCK_CUTTER("block_cutter"),
    INDUCTION_FURNACE("induction_furnace"),
    BLAST_FURNACE("blast_furnace"),
    CENTRIFUGE("centrifuge", "thermal_centrifuge"),
    ORE_WASHING_PLANT("ore_washing_plant"),
    SOLID_CANNER("solid_canner"),
    MASS_FABRICATOR("matter_generator", "mass_fabricator"),
    PERSONAL_CHEST("personal_chest"),

    // ── processing machines (hand-written GUIs) ──────────────────────────
    CANNER("canner"),
    FERMENTER("fermenter"),
    MINER("miner"),
    CROP_HARVESTER("crop_harvester"),
    CROPMATRON("cropmatron"),
    COKE_KILN("coke_kiln"),
    COKE_KILN_HATCH("coke_kiln_hatch"),
    COKE_KILN_GRATE("coke_kiln_grate"),
    SCANNER("scanner"),
    REPLICATOR("replicator"),
    PATTERN_STORAGE("pattern_storage"),

    // ── utilidades ───────────────────────────────────────────────────────
    TESLA_COIL("tesla_coil"),
    CHUNK_LOADER("chunk_loader"),
    ELECTROLYZER("electrolyzer"),
    MAGNETIZER("magnetizer"),
    LUMINATOR("luminator_flat"),
    RT_GENERATOR("rt_generator"),
    TERRAFORMER("terraformer"),
    ADVANCED_MINER("advanced_miner"),
    INDUSTRIAL_WORKBENCH("industrial_workbench"),
    BATCH_CRAFTER("batch_crafter"),
    ENERGY_O_MAT("energy_o_mat"),
    TRADE_O_MAT("trade_o_mat"),
    WEIGHTED_ITEM_DISTRIBUTOR("weighted_item_distributor"),
    WEIGHTED_FLUID_DISTRIBUTOR("weighted_fluid_distributor"),

    // ── armazenamento e logística ────────────────────────────────────────
    WOODEN_STORAGE_BOX("wooden_storage_box"),
    IRON_STORAGE_BOX("iron_storage_box", "bronze_storage_box"),
    STEEL_STORAGE_BOX("steel_storage_box"),
    IRIDIUM_STORAGE_BOX("iridium_storage_box"),
    TANK("tank", "bronze_tank", "iron_tank", "steel_tank", "iridium_tank"),
    PUMP("pump"),
    ITEM_BUFFER("item_buffer", "item_buffer_2"),
    SORTING_MACHINE("sorting_machine"),
    FLUID_BOTTLER("fluid_bottler"),
    FLUID_DISTRIBUTOR("fluid_distributor"),
    FLUID_REGULATOR("fluid_regulator"),
    CONDENSER("condenser"),
    SOLAR_DISTILLER("solar_distiller"),

    // ── reator nuclear ───────────────────────────────────────────────────
    NUCLEAR_REACTOR("nuclear_reactor"),
    REACTOR_CHAMBER("reactor_chamber"),
    REACTOR_ACCESS_HATCH("reactor_access_hatch"),
    REACTOR_FLUID_PORT("reactor_fluid_port"),
    REACTOR_COOLANT_INJECTOR("rci_rsh", "rci_lzh"),

    // ── generators ───────────────────────────────────────────────────────
    GENERATOR("generator"),
    GEO_GENERATOR("geo_generator"),
    SEMIFLUID_GENERATOR("semifluid_generator"),
    SOLAR_GENERATOR("solar_generator"),
    WATER_GENERATOR("water_generator"),
    WIND_GENERATOR("wind_generator"),
    KINETIC_GENERATOR("kinetic_generator"),
    STIRLING_GENERATOR("stirling_generator"),
    MANUAL_KINETIC_GENERATOR("manual_kinetic_generator"),
    SOLID_HEAT_GENERATOR("solid_heat_generator"),
    FLUID_HEAT_GENERATOR("fluid_heat_generator"),
    ELECTRIC_HEAT_GENERATOR("electric_heat_generator"),
    RT_HEAT_GENERATOR("rt_heat_generator"),
    ELECTRIC_KINETIC_GENERATOR("electric_kinetic_generator"),
    STEAM_KINETIC_GENERATOR("steam_kinetic_generator"),
    STIRLING_KINETIC_GENERATOR("stirling_kinetic_generator"),
    STEAM_GENERATOR("steam_generator"),
    STEAM_REPRESSURIZER("steam_repressurizer"),
    LIQUID_HEAT_EXCHANGER("liquid_heat_exchanger"),
    WATER_KINETIC_GENERATOR("water_kinetic_generator"),
    WIND_KINETIC_GENERATOR("wind_kinetic_generator"),

    // ── energy storage / wiring ──────────────────────────────────────────
    BATBOX("batbox"),
    CESU("cesu"),
    MFE("mfe"),
    MFSU("mfsu"),
    CHARGEPAD("chargepad_batbox"),
    CHARGEPAD_CESU("chargepad_cesu"),
    CHARGEPAD_MFE("chargepad_mfe"),
    CHARGEPAD_MFSU("chargepad_mfsu"),
    LV_TRANSFORMER("lv_transformer"),
    MV_TRANSFORMER("mv_transformer"),
    HV_TRANSFORMER("hv_transformer"),
    EV_TRANSFORMER("ev_transformer");

    private static final MachineGuiType[] VALUES = values();

    public static final StreamCodec<ByteBuf, MachineGuiType> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(index -> VALUES[index], MachineGuiType::ordinal);

    private final String[] blockIds;
    private MachineLayout layout;

    MachineGuiType(String... blockIds) {
        this.blockIds = blockIds;
    }

    public MachineLayout layout() {
        if (this.layout == null) {
            this.layout = MachineLayouts.create(this);
        }
        return this.layout;
    }

    /**
     * Maps the registry path of a block (e.g. {@code "macerator"}) to its GUI type.
     * Falls back to {@link #BASIC} for anything unknown.
     */
    public static MachineGuiType fromBlockId(String blockId) {
        if (blockId == null) return BASIC;
        return ByBlockId.MAP.getOrDefault(blockId.toLowerCase(Locale.ROOT), BASIC);
    }

    private static final class ByBlockId {
        private static final Map<String, MachineGuiType> MAP = new HashMap<>();

        static {
            for (MachineGuiType type : VALUES) {
                for (String id : type.blockIds) {
                    MAP.put(id, type);
                }
            }
        }
    }
}
