package net.ic2reborn.menu.layout;

import net.craftenergy.api.EnergyUnits;
import net.ic2reborn.menu.MachineGuiType;
import net.minecraft.network.chat.Component;

import static net.ic2reborn.menu.layout.GaugeStyle.*;
import static net.ic2reborn.menu.layout.MachineLayout.GaugeSource.NONE;

/**
 * GUI layouts of every machine, transcribed from IC2 Experimental.
 *
 * Dynamic layouts come from assets/ic2/guidef/*.xml (background coordinates);
 * textured layouts come from the Gui* / Container* classes (slot coordinates,
 * hence slotAt/outputAt). Slot order is the machine inventory index order.
 */
public final class MachineLayouts {
    private static final int HEAT_TEXT_COLOR = 0x57C4DA;
    private static final int FLOW_TEXT_COLOR = 0x20EB3E;

    private MachineLayouts() {}

    public static MachineLayout create(MachineGuiType type) {
        return switch (type) {
            case BASIC, ELECTRIC_FURNACE -> standard(PROGRESS_ARROW, 80, 35);
            case MACERATOR -> standard(PROGRESS_CRUSH, 80, 38);
            case COMPRESSOR -> standard(PROGRESS_TRIANGLE, 80, 35);
            case EXTRACTOR -> standard(PROGRESS_DROP, 80, 35);
            case RECYCLER -> standard(PROGRESS_RECYCLER, 80, 35);

            case IRON_FURNACE -> MachineLayout.dynamic(176, 166)
                    .slot(55, 16).largeOutput(111, 30).slot(55, 52)
                    .gauge(56, 36, FUEL, NONE)
                    .progress(80, 35, PROGRESS_ARROW)
                    .build();

            case METAL_FORMER -> MachineLayout.dynamic(176, 166)
                    .slot(16, 16).largeOutput(111, 30).slot(16, 52)
                    .upgrades(151, 7)
                    .energy(20, 37)
                    .progress(52, 40, PROGRESS_METAL_FORMER)
                    .build();

            case BLOCK_CUTTER -> MachineLayout.dynamic(176, 166)
                    .slot(26, 16).largeOutput(111, 34).slot(26, 52)
                    .plain(70, 34)
                    .upgrades(151, 7)
                    .energy(29, 37)
                    .progress(55, 33, PROGRESS_BLOCK_CUTTER)
                    // <only if="isBladeTooWeak">: aviso de lâmina fraca demais
                    .imageIf(menu -> menu.getMachineMode() == 1, "guiblockcutter.png", 63, 54, 176, 34, 30, 26, 256, 256)
                    .build();

            case INDUCTION_FURNACE -> MachineLayout.dynamic(176, 166)
                    .image("overlay/induction_furnace_input.png", 42, 16, 0, 0, 34, 18, 34, 18)
                    .plain(43, 17).plain(59, 17)
                    .image("overlay/induction_furnace_output.png", 110, 30, 0, 0, 38, 26, 38, 26)
                    .plainOutput(113, 35).plainOutput(129, 35)
                    .slot(50, 52)
                    .upgrades(151, 25, 2)
                    .energy(55, 37)
                    .progress(81, 35, PROGRESS_ARROW)
                    .text(tr("heat", "Heat:"), 10, 36)
                    .text(menu -> Component.literal((menu.getMaxHeat() <= 0 ? 0 : menu.getHeat() * 100 / menu.getMaxHeat()) + "%"), 10, 46)
                    .build();

            case BLAST_FURNACE -> MachineLayout.dynamic(176, 166)
                    .slot(55, 24)
                    .grid(111, 24, 1, 2, true)
                    .upgrades(151, 7, 2)
                    .slot(7, 52).output(25, 52)
                    // <fluidslot x="55" y="42"/>
                    .image("common.png", 55, 42, 103, 7, 18, 18, 256, 256)
                    .progress(80, 35, PROGRESS_ARROW)
                    .text(tr("heat", "Heat:"), 14, 25)
                    .gauge(15, 34, HEAT_CENTRIFUGE, NONE)
                    .build();

            case CENTRIFUGE -> MachineLayout.dynamic(176, 166)
                    .image("guitermalcentrifuge.png", 40, 18, 40, 18, 80, 60, 256, 256)
                    .slot(10, 17).slot(10, 53)
                    .grid(123, 17, 1, 3, true)
                    .upgrades(151, 7)
                    .progress(84, 25, PROGRESS_CENTRIFUGE)
                    .energy(15, 38)
                    .gauge(68, 67, HEAT_CENTRIFUGE, MachineLayout.GaugeSource.HEAT)
                    .build();

            case ORE_WASHING_PLANT -> MachineLayout.dynamic(176, 166)
                    .image("guiorewashingplant.png", 37, 16, 37, 16, 87, 63, 256, 256)
                    .slot(103, 16)
                    .grid(85, 61, 3, 1, true)
                    .upgrades(151, 7)
                    .slot(37, 16).output(37, 61)
                    .tank(60, 20)
                    .progress(103, 39, PROGRESS_ORE_WASHER)
                    .slot(10, 53)
                    .energy(15, 38)
                    .noInventoryTitle()
                    .build();

            case SOLID_CANNER -> MachineLayout.dynamic(176, 166)
                    .slot(36, 35).slot(66, 35).output(115, 35)
                    .slot(7, 61)
                    .energy(11, 46)
                    .upgrades(151, 7)
                    .image("overlay/canner_arrow.png", 54, 35, 0, 0, 12, 18, 16, 32)
                    .progress(89, 36, PROGRESS_ARROW)
                    .noInventoryTitle()
                    .build();

            case MASS_FABRICATOR -> MachineLayout.dynamic(176, 166)
                    .slot(114, 61).largeOutput(110, 18)
                    .upgrades(151, 7)
                    .energy(119, 46)
                    .text(menu -> tr("mass_fabricator.energy", "Energy: %s%%", Math.round(menu.getProgressRatio() * 100)), 18, 22)
                    .text(menu -> tr("mass_fabricator.scrap", "Scrap: %s%%", 0), 18, 34)
                    .build();

            case PERSONAL_CHEST -> MachineLayout.dynamic(176, 222)
                    .grid(7, 18, 9, 6, false)
                    .inventory(7, 139)
                    .build();

            case GENERATOR -> MachineLayout.dynamic(176, 166)
                    .slot(56, 16).slot(56, 52)
                    .energyBar(100, 39)
                    .progress(57, 36, FUEL)
                    .build();

            case GEO_GENERATOR, SEMIFLUID_GENERATOR -> MachineLayout.dynamic(176, 166)
                    .slot(26, 16).output(26, 52)
                    .slot(114, 48)
                    .energyBar(110, 30)
                    .tank(56, 16)
                    .build();

            case SOLAR_GENERATOR -> MachineLayout.dynamic(176, 166)
                    .slot(79, 25)
                    .image("overlay/solar_sun.png", 81, 45, 0, 0, 14, 14, 28, 14)
                    // <only if="sunlight">: o sol acende enquanto o painel produz
                    .imageIf(menu -> menu.getProgress() > 0, "overlay/solar_sun.png", 81, 45, 14, 0, 14, 14, 28, 14)
                    .build();

            case WATER_GENERATOR -> MachineLayout.dynamic(176, 166)
                    .slot(80, 53).slot(80, 17)
                    .progress(82, 36, BUCKET)
                    .build();

            case WIND_GENERATOR -> MachineLayout.dynamic(176, 166)
                    .slot(80, 26)
                    .progress(82, 45, PROGRESS_WIND)
                    .build();

            case KINETIC_GENERATOR, STIRLING_GENERATOR -> MachineLayout.dynamic(176, 166)
                    .text(menu -> tr("kinetic.bandwidth", "Bandwidth: %s", EnergyUnits.formatPower(menu.getMaxProgress())), 41, 33)
                    .text(menu -> tr("kinetic.production", "Production: %s", EnergyUnits.formatPower(menu.getProgress())), 41, 45)
                    .build();

            case SOLID_HEAT_GENERATOR -> MachineLayout.dynamic(176, 166)
                    .image("guisolidheatgenerator.png", 48, 45, 47, 44, 81, 36, 256, 256)
                    .slot(80, 45).output(113, 45)
                    .progress(81, 29, FUEL)
                    .text(menu -> Component.literal(menu.getHeat() + " / " + menu.getMaxHeat() + " HU"), 48, 66, 79, 13, HEAT_TEXT_COLOR)
                    .noInventoryTitle()
                    .build();

            // ── hand-written GUIs ────────────────────────────────────────────
            case CANNER -> MachineLayout.textured("guicanner.png", 184)
                    .slotAt(80, 44).outputAt(119, 17).slotAt(8, 80)
                    .gridAt(152, 26, 1, 4, false)
                    .slotAt(41, 17)
                    .energy(12, 62)
                    .tank(39, 42).tank(117, 42)
                    .progress(74, 22, PROGRESS_CANNER)
                    // GuiCanner: tampa as setas que o modo não usa e mostra a gota nos modos de recipiente
                    .imageIf(menu -> menu.getMachineMode() <= 1, "guicanner.png", 59, 53, 3, 4, 9, 18, 256, 256)
                    .imageIf(menu -> menu.getMachineMode() == 0 || menu.getMachineMode() == 2, "guicanner.png", 99, 53, 3, 4, 18, 23, 256, 256)
                    .imageIf(menu -> menu.getMachineMode() == 1 || menu.getMachineMode() == 2, "guicanner.png", 71, 43, 196, 0, 26, 18, 256, 256)
                    // botão de modo (63, 81): ícone do modo atual
                    .imageIf(menu -> menu.getMachineMode() == 0, "guicanner.png", 63, 81, 176, 18, 50, 14, 256, 256)
                    .imageIf(menu -> menu.getMachineMode() == 1, "guicanner.png", 63, 81, 176, 32, 50, 14, 256, 256)
                    .imageIf(menu -> menu.getMachineMode() == 2, "guicanner.png", 63, 81, 176, 46, 50, 14, 256, 256)
                    .imageIf(menu -> menu.getMachineMode() == 3, "guicanner.png", 63, 81, 176, 60, 50, 14, 256, 256)
                    .build();

            case FERMENTER -> MachineLayout.textured("guifermenter.png", 184)
                    .slotAt(14, 46).outputAt(14, 64)
                    .slotAt(148, 43).outputAt(148, 61)
                    .outputAt(86, 83)
                    .gridAt(125, 83, 2, 1, false)
                    .plainTank(38, 49, 48, 30).tank(125, 22)
                    .gauge(42, 41, HEAT_FERMENTER, MachineLayout.GaugeSource.HEAT)
                    .progress(38, 88, PROGRESS_FERMENTER)
                    .build();

            case MINER -> MachineLayout.textured("guiminer.png", 166)
                    .slotAt(152, 58)
                    .slotAt(8, 58).slotAt(8, 40).slotAt(8, 22)
                    .slotAt(152, 22)
                    .gridAt(44, 22, 5, 3, true)
                    .energy(155, 41)
                    .build();

            // descarga, 15 slots de colheita, 4 upgrades
            case CROP_HARVESTER -> MachineLayout.textured("guicropharvester.png", 166)
                    .slotAt(16, 53)
                    .gridAt(48, 17, 5, 3, true)
                    .upgrades(151, 7)
                    .energy(19, 37)
                    .build();

            // descarga, 7 fertilizantes, célula de herbicida (entra/sai), célula de água (entra/sai), 4 upgrades
            case CROPMATRON -> MachineLayout.textured("guicropmatron.png", 192)
                    .slotAt(134, 80)
                    .gridAt(8, 80, 7, 1, false)
                    .slotAt(49, 27).outputAt(67, 27)
                    .slotAt(57, 56).outputAt(75, 56)
                    .upgrades(151, 25)
                    .plainTank(11, 26, 24, 47).plainTank(105, 26, 24, 47)
                    .energy(138, 82)
                    .build();
            case SCANNER -> MachineLayout.textured("guiscanner.png", 166)
                    .slotAt(8, 43).slotAt(55, 35).slotAt(152, 65)
                    .energy(12, 25)
                    .build();

            case REPLICATOR -> MachineLayout.textured("guireplicator.png", 184)
                    .slotAt(152, 83).outputAt(90, 59)
                    .slotAt(8, 27).slotAt(8, 72)
                    .gridAt(152, 8, 1, 4, false)
                    .energy(136, 84)
                    .tank(27, 30)
                    .build();

            case BATCH_CRAFTER -> MachineLayout.textured("guibatchcrafter.png", 206)
                    .slotAt(8, 62)
                    .gridAt(30, 17, 3, 3, false)
                    .outputAt(124, 35)
                    .gridAt(8, 84, 9, 1, false)
                    .gridAt(8, 102, 9, 1, true)
                    .gridAt(152, 8, 1, 4, false)
                    .energy(12, 45)
                    .progress(90, 35, PROGRESS_ARROW)
                    .build();

            case ENERGY_O_MAT -> MachineLayout.textured("guienergyomatopen.png", 166)
                    .slotAt(24, 17).slotAt(24, 53).slotAt(60, 17).slotAt(60, 53)
                    .build();

            case TRADE_O_MAT -> MachineLayout.textured("guitradeomatopen.png", 166)
                    .slotAt(50, 19).slotAt(50, 53).slotAt(80, 19).outputAt(80, 53)
                    .build();

            case WEIGHTED_ITEM_DISTRIBUTOR -> MachineLayout.textured("guiweighteditemdistributor.png", 211)
                    .gridAt(8, 108, 9, 1, false)
                    .build();

            case WEIGHTED_FLUID_DISTRIBUTOR -> MachineLayout.textured("guiweightedfluiddistributor.png", 211)
                    .slotAt(8, 108).outputAt(152, 108)
                    .plainTank(33, 111, 110, 10)
                    .build();

            case FLUID_HEAT_GENERATOR -> MachineLayout.textured("guifluidheatgenerator.png", 166)
                    .slotAt(27, 21).outputAt(27, 54)
                    .tank(70, 20)
                    .text(menu -> Component.translatableWithFallback("gui.ic2reborn.heat_generator.emit", "Emit: %s HU", menu.getHeat()), 96, 33, 0, 0, HEAT_TEXT_COLOR)
                    .text(menu -> Component.translatableWithFallback("gui.ic2reborn.heat_generator.max_emit", "Max: %s HU", menu.getMaxHeat()), 96, 52, 0, 0, HEAT_TEXT_COLOR)
                    .build();

            case ELECTRIC_HEAT_GENERATOR -> MachineLayout.textured("guielectricheatgenerator.png", 166)
                    .gridAt(44, 27, 5, 2, false)
                    .slotAt(8, 62)
                    .energy(12, 44)
                    .text(menu -> Component.literal(menu.getHeat() + " / " + menu.getMaxHeat() + " HU"), 34, 66, 109, 13, HEAT_TEXT_COLOR)
                    .build();

            case RT_HEAT_GENERATOR -> MachineLayout.textured("guirtheatgenerator.png", 166)
                    .gridAt(62, 27, 3, 2, false)
                    .text(menu -> Component.literal(menu.getHeat() + " / " + menu.getMaxHeat() + " HU"), 49, 66, 79, 13, HEAT_TEXT_COLOR)
                    .build();

            case ELECTRIC_KINETIC_GENERATOR -> MachineLayout.textured("guielectrickineticgenerator.png", 166)
                    .gridAt(44, 27, 5, 2, false)
                    .slotAt(8, 62)
                    .energy(12, 44)
                    .text(menu -> Component.literal(menu.getProgress() + " KU · " + menu.getMaxProgress() + " KU/t"),
                            34, 66, 109, 13, HEAT_TEXT_COLOR)

                    .build();

            case STEAM_KINETIC_GENERATOR -> MachineLayout.textured("guisteamkineticgenerator.png", 166)
                    .slotAt(152, 26).slotAt(80, 26)
                    .build();

            case STIRLING_KINETIC_GENERATOR -> MachineLayout.textured("guistirlingkineticgenerator.png", 204)
                    .slotAt(8, 103).outputAt(26, 103)
                    .slotAt(134, 103).outputAt(152, 103)
                    .gridAt(62, 103, 3, 1, false)
                    .plainTank(19, 47, 12, 44).plainTank(145, 47, 12, 44)
                    .build();

            case WATER_KINETIC_GENERATOR -> MachineLayout.textured("guiwaterkineticgenerator.png", 166)
                    .slotAt(80, 26)
                    .text(menu -> switch (menu.getMachineMode()) {
                        case 0 -> tr("water_kinetic.wrong_biome", "Not in ocean or river");
                        case 1 -> tr("water_kinetic.rotor_missing", "No rotor");
                        case 2 -> tr("water_kinetic.rotor_space", "Rotor not under water");
                        default -> tr("kinetic.output", "Output: %s KU/t", menu.getProgress());
                    }, 17, 48, 143, 13, KINETIC_TEXT_COLOR)
                    .text(menu -> switch (menu.getMachineMode()) {
                        case 0 -> tr("water_kinetic.wrong_biome_hint", "Place it in water there");
                        case 1 -> tr("water_kinetic.rotor_missing_hint", "Wooden rotor won't fit");
                        case 2 -> tr("water_kinetic.rotor_space_hint", "%s blocks of water around", menu.getMaxHeat());
                        default -> tr("kinetic.rotor_health", "Rotor: %s%%", menu.getMaxProgress());
                    }, 17, 66, 143, 13, KINETIC_TEXT_COLOR)


                    .build();

            case WIND_KINETIC_GENERATOR -> MachineLayout.textured("guiwindkineticgenerator.png", 166)
                    .slotAt(80, 26)
                    .text(menu -> switch (menu.getMachineMode()) {
                        case 0 -> tr("wind_kinetic.rotor_missing", "No rotor");
                        case 1 -> tr("wind_kinetic.rotor_space", "No room for the rotor");
                        case 2 -> tr("wind_kinetic.wind_weak", "Wind too weak");
                        default -> tr("kinetic.output", "Output: %s KU/t", menu.getProgress());
                    }, 17, 48, 143, 13, KINETIC_TEXT_COLOR)
                    .text(menu -> switch (menu.getMachineMode()) {
                        case 1 -> tr("wind_kinetic.rotor_space_hint", "%s free blocks around", menu.getMaxHeat());
                        case 2 -> tr("wind_kinetic.wind_weak_hint", "Place it higher/open");
                        case 3 -> tr("kinetic.rotor_health", "Rotor: %s%%", menu.getMaxProgress());
                        default -> Component.empty();
                    }, 17, 66, 143, 13, KINETIC_TEXT_COLOR)


                    .build();

            // sem GUI: cada clique gira a manivela
            case MANUAL_KINETIC_GENERATOR -> MachineLayout.dynamic(176, 166).build();

            case BATBOX -> energyStorage(4_400);
            case CESU -> energyStorage(20_000);
            case MFE -> energyStorage(120_000);
            case MFSU -> energyStorage(1_000_000);

            case CHARGEPAD -> MachineLayout.textured("guichargepadblock.png", 161)
                    .slotAt(56, 17).slotAt(56, 53)
                    .energyBar(79, 38)
                    .text(tr("storage.level", "Power Level:"), 79, 25)
                    .text(menu -> Component.literal(" " + EnergyUnits.format(menu.getEnergyCWh(), "CWh")), 110, 35)
                    .text(menu -> Component.literal("/" + EnergyUnits.format(menu.getCapacityCWh(), "CWh")), 110, 45)
                    .build();

            case LV_TRANSFORMER -> transformer(220, 1_000, 20_000);
            case MV_TRANSFORMER -> transformer(1_000, 2_400, 120_000);
            case HV_TRANSFORMER -> transformer(2_400, 13_800, 1_000_000);
            case EV_TRANSFORMER -> transformer(13_800, 69_000, 5_000_000);
        };
    }

    /** macerator/compressor/extractor/recycler/electric_furnace guidef. */
    private static MachineLayout standard(GaugeStyle progress, int progressX, int progressY) {
        return MachineLayout.dynamic(176, 166)
                .slot(55, 16).largeOutput(111, 30).slot(55, 52)
                .upgrades(151, 7)
                .energy(59, 37)
                .progress(progressX, progressY, progress)
                .build();
    }

    /** GuiElectricBlock + ContainerElectricBlock (BatBox, CESU, MFE, MFSU). */
    private static MachineLayout energyStorage(long power) {
        return MachineLayout.textured("guielectricblock.png", 196)
                .slotAt(56, 17).slotAt(56, 53)
                .energyBar(79, 38)
                .text(tr("storage.armor", "Armor"), 8, 196 - 126 + 3)
                .text(tr("storage.level", "Power Level:"), 79, 25)
                .text(menu -> Component.literal(" " + EnergyUnits.format(menu.getEnergyCWh(), "CWh")), 110, 35)
                .text(menu -> Component.literal("/" + EnergyUnits.format(menu.getCapacityCWh(), "CWh")), 110, 45)
                .text(tr("storage.output", "Out: %s", EnergyUnits.formatPower(power)), 85, 60)
                .build();
    }

    /** GuiTransformer (no slots; mode buttons not implemented yet). */
    private static MachineLayout transformer(int lowVoltage, int highVoltage, long power) {
        return MachineLayout.textured("guitransfomer.png", 219)
                .text(tr("transformer.output", "Output:"), 6, 30)
                .text(tr("transformer.input", "Input:"), 6, 43)
                .text(menu -> Component.literal(EnergyUnits.formatVoltage(lowVoltage) + " · " + EnergyUnits.formatPower(power)), 52, 30, 0, 0, FLOW_TEXT_COLOR)
                .text(menu -> Component.literal(EnergyUnits.formatVoltage(highVoltage)), 52, 45, 0, 0, FLOW_TEXT_COLOR)
                .build();
    }

    /** Cor dos textos das GUIs cinéticas do IC2 (2157374). */
    private static final int KINETIC_TEXT_COLOR = 0x20EB3E;

    private static Component tr(String key, String fallback, Object... args) {
        return Component.translatableWithFallback("gui.ic2reborn." + key, fallback, args);
    }
}
