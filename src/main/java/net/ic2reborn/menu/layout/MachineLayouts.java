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
                    .gauge(56, 36, FUEL, MachineLayout.GaugeSource.HEAT)
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
                    .plainTank(56, 43, 16, 16)
                    .progress(80, 35, PROGRESS_ARROW)
                    .text(tr("heat", "Heat:"), 14, 25)
                    .gauge(15, 34, HEAT_CENTRIFUGE, MachineLayout.GaugeSource.HEAT)
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
                    .text(menu -> Component.literal(menu.getHeat() + " / " + menu.getMaxHeat() + " CCº"), 48, 66, 79, 13, HEAT_TEXT_COLOR)
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
            // forno de coque: saída e progresso; escotilha: entrada; grelha: tanque de creosoto e células
            case COKE_KILN -> MachineLayout.dynamic(176, 166)
                    .output(88, 34)
                    .gauge(70, 35, FUEL, MachineLayout.GaugeSource.PROGRESS)
                    .build();
            case COKE_KILN_HATCH -> MachineLayout.dynamic(176, 166)
                    .slot(78, 34)
                    .build();
            case COKE_KILN_GRATE -> MachineLayout.dynamic(176, 166)
                    .slot(52, 34).output(106, 34)
                    .image("common.png", 78, 34, 103, 7, 18, 18, 256, 256)
                    .plainTank(79, 35, 16, 16)
                    .build();
            // ── armazenamento e logística ─────────────────────────────────
            // caixas (guidef wooden/iron/steel/iridium_storage_box.xml)
            case WOODEN_STORAGE_BOX -> MachineLayout.dynamic(176, 166).grid(7, 16, 9, 3, false).build();
            case IRON_STORAGE_BOX -> MachineLayout.dynamic(176, 202).grid(7, 16, 9, 5, false).build();
            case STEEL_STORAGE_BOX -> MachineLayout.dynamic(176, 238).grid(7, 16, 9, 7, false).build();
            case IRIDIUM_STORAGE_BOX -> MachineLayout.dynamic(338, 238).grid(7, 16, 18, 7, false).inventory(88, 155).build();
            // tanque: recipiente 0 → 1 (esvazia ou enche) e upgrades 2–5
            case TANK -> MachineLayout.dynamic(176, 166)
                    .slot(52, 34).output(106, 34)
                    .upgrades(151, 7)
                    .tank(78, 14)
                    .build();
            // bomba (guidef pump.xml): recipiente 0 → 1, descarga 2, upgrades 3–6
            case PUMP -> MachineLayout.dynamic(176, 166)
                    .image("overlay/pump_arrow.png", 93, 36, 0, 0, 36, 13, 36, 13)
                    .slot(98, 16).output(131, 33).slot(7, 43)
                    .upgrades(151, 7)
                    .energy(12, 28)
                    .progress(36, 34, PROGRESS_DROP)
                    .tank(70, 16)
                    .build();
            // buffer: duas grades 4×6 e um upgrade para cada
            case ITEM_BUFFER -> MachineLayout.textured("guiitembuffer.png", 232)
                    .gridAt(8, 18, 4, 6, false).gridAt(98, 18, 4, 6, false)
                    .upgradeAt(35, 128).upgradeAt(125, 128)
                    .build();
            case SORTING_MACHINE -> sortingMachine();
            // envasadora: descarga 0, esvaziar 1, encher 2, saída 3, upgrades 4–7
            case FLUID_BOTTLER -> MachineLayout.textured("guibottler.png", 184)
                    .slotAt(8, 53).slotAt(44, 35).slotAt(44, 72).outputAt(117, 53)
                    .upgrades(151, 25)
                    .energy(12, 35)
                    .tank(78, 34)
                    .build();
            case FLUID_DISTRIBUTOR -> MachineLayout.textured("guifluiddistributor.png", 184)
                    .slotAt(9, 54).outputAt(9, 72)
                    .plainTank(29, 38, 55, 47)
                    .text(menu -> tr("fluid_distributor.mode", "Mode:"), 112, 47, 0, 0, HEAT_TEXT_COLOR)
                    .text(menu -> menu.getMachineMode() == 1 ? tr("fluid_distributor.distribute", "Distribute")
                            : tr("fluid_distributor.concentrate", "Concentrate"), 95, 71, 0, 0, HEAT_TEXT_COLOR)
                    .build();
            // regulador: descarga 0, recipiente 1 → 2; botões ±1/10/100/1000 mB e por segundo/tick
            case FLUID_REGULATOR -> MachineLayout.textured("guifluidregulator.png", 184)
                    .slotAt(8, 57).slotAt(58, 53).outputAt(58, 71)
                    .energy(12, 39)
                    .tank(78, 34)
                    .text(menu -> Component.literal((menu.getMachineMode() & 0xFFF) + " CL"), 105, 57, 0, 0, FLOW_TEXT_COLOR)
                    .text(menu -> (menu.getMachineMode() >> 12) != 0 ? tr("fluid_regulator.per_tick", "/t")
                            : tr("fluid_regulator.per_second", "/s"), 145, 57, 0, 0, FLOW_TEXT_COLOR)
                    .build();
            // condensador: descarga 0, célula 1 → 2, upgrade 3, ventoinhas 4–7
            case CONDENSER -> MachineLayout.textured("guicondenser.png", 184)
                    .slotAt(8, 44).slotAt(26, 73).outputAt(134, 73).upgradeAt(152, 73)
                    .slotAt(26, 26).slotAt(26, 44).slotAt(134, 26).slotAt(134, 44)
                    .energy(12, 26)
                    .plainTank(46, 27, 84, 33).plainTank(46, 74, 84, 15)
                    .progress(48, 64, PROGRESS_CONDENSER)
                    .build();
            // destilador solar: água 0 → 2, destilada 1 → 3, upgrades 4–5
            case SOLAR_DISTILLER -> MachineLayout.textured("guisolardestiller.png", 184)
                    .slotAt(17, 27).slotAt(136, 64).outputAt(17, 45).outputAt(136, 82)
                    .upgradeAt(152, 8).upgradeAt(152, 26)
                    .plainTank(37, 43, 53, 18).plainTank(115, 55, 17, 43)
                    .build();
            // ── cadeia de vapor ───────────────────────────────────────────
            // caldeira (GuiSteamGenerator): sem inventário, botões de água e da válvula de pressão
            case STEAM_GENERATOR -> MachineLayout.textured("guisteamgenerator.png", 220).noInventory()
                    .plainTank(10, 155, 75, 47)
                    .gauge(14, 71, HEAT_STEAM_GENERATOR, MachineLayout.GaugeSource.HEAT)
                    .gauge(156, 62, CALCIFICATION_STEAM_GENERATOR, MachineLayout.GaugeSource.PROGRESS)
                    .text(menu -> Component.literal((menu.getMachineMode() & 0x7FF) + " CL/t"), 91, 172, 59, 13, FLOW_TEXT_COLOR)
                    .text(menu -> tr("steam_generator.heat_input", "Heat input: %s CCº/t", menu.getPower()), 31, 133, 111, 13, FLOW_TEXT_COLOR)
                    .text(menu -> Component.literal(((menu.getMachineMode() >> 11) & 0x1FF) + " bar"), 22, 35, 42, 13, FLOW_TEXT_COLOR)
                    .text(menu -> Component.literal(menu.getEnergyCWh() + " CL/t"), 66, 25, 81, 13, FLOW_TEXT_COLOR)
                    .text(menu -> steamOutputName((menu.getMachineMode() >> 20) & 7), 66, 45, 100, 13, FLOW_TEXT_COLOR)
                    .build();
            case STEAM_REPRESSURIZER -> MachineLayout.dynamic(176, 166)
                    .image("guisteamrepressurizer.png", 0, 0, 0, 0, 176, 166, 256, 256)
                    .plainTank(15, 19, 38, 47).plainTank(123, 19, 38, 47)
                    .build();
            // trocador de calor líquido: fluido quente 0 → 1, refrigerante 2 → 3, upgrades 4–6, condutores 7–16
            case LIQUID_HEAT_EXCHANGER -> {
                MachineLayout.Builder builder = MachineLayout.textured("guiheatsourcefluid.png", 204)
                        .slotAt(8, 103).outputAt(26, 103).slotAt(134, 103).outputAt(152, 103)
                        .upgradeAt(62, 103).upgradeAt(80, 103).upgradeAt(98, 103);
                for (int i = 0; i < 10; i++) builder.slotAt(46 + (i % 5) * 17, i < 5 ? 50 : 72);
                yield builder.plainTank(19, 47, 12, 44).plainTank(145, 47, 12, 44)
                        .text(menu -> tr("heat_exchanger.emit", "Emitting %s / %s CCº/t", menu.getHeat(), menu.getMaxHeat()), 20, 28, 138, 13, HEAT_TEXT_COLOR)
                        .build();
            }
            // ── reator nuclear ────────────────────────────────────────────
            case NUCLEAR_REACTOR -> nuclearReactor();
            case REACTOR_CHAMBER, REACTOR_ACCESS_HATCH -> MachineLayout.dynamic(176, 166).build();
            case REACTOR_FLUID_PORT -> MachineLayout.dynamic(176, 166).upgrades(79, 42, 1).build();
            // injetor de refrigerante (guidef rci_rsh.xml): blocos 0–8, descarga 9, upgrades 10–13
            case REACTOR_COOLANT_INJECTOR -> MachineLayout.dynamic(176, 166)
                    .grid(61, 16, 3, 3, false)
                    .slot(7, 34)
                    .upgrades(151, 7)
                    .energy(7, 55)
                    .build();
            // ── UU-matter ─────────────────────────────────────────────────
            // fabricador (GuiMatter): amplificador 0, saída 1, célula 2, upgrades 3–6
            case MASS_FABRICATOR -> MachineLayout.textured("guimatter.png", 166)
                    .slotAt(72, 40).outputAt(125, 59).slotAt(125, 23)
                    .upgradeAt(152, 8).upgradeAt(152, 26).upgradeAt(152, 44).upgradeAt(152, 62)
                    .tank(96, 22)
                    .text(menu -> tr("mass_fabricator.progress", "Progress:"), 8, 22)
                    .text(menu -> Component.literal(Math.round(menu.getProgressRatio() * 100) + "%"), 18, 31)
                    .text(menu -> tr("mass_fabricator.amplifier", "Amplifier:"), 8, 46)
                    .text(menu -> Component.literal(String.valueOf(menu.getHeat())), 8, 58)
                    .build();
            // scanner (GuiScanner): descarga 0, item 1, memória de cristal 2
            case SCANNER -> MachineLayout.textured("guiscanner.png", 166)
                    .slotAt(8, 43).slotAt(55, 35).slotAt(152, 65)
                    .energy(12, 25)
                    .imageIf(menu -> menu.getMachineMode() == 2 || menu.getMachineMode() == 6, "guiscanner.png", 102, 49, 176, 57, 12, 12, 256, 256)
                    .imageIf(menu -> menu.getMachineMode() == 2 || menu.getMachineMode() == 6, "guiscanner.png", 143, 49, 176, 69, 24, 12, 256, 256)
                    .text(menu -> scannerState(menu.getMachineMode()), 10, 69, 0, 0, 0xEBEE20)
                    .text(menu -> menu.getMachineMode() == 1 ? Component.literal(Math.round(menu.getProgressRatio() * 100) + "%") : Component.empty(), 125, 69, 0, 0, FLOW_TEXT_COLOR)
                    .text(menu -> menu.getHeat() > 0 ? Component.literal(uuText(menu.getMaxHeat())) : Component.empty(), 105, 25, 0, 0, 0xFFFFFF)
                    .build();
            // replicador (GuiReplicator): descarga 0, saída 1, UU 2 → célula 3, upgrades 4–7
            case REPLICATOR -> MachineLayout.textured("guireplicator.png", 184)
                    .slotAt(152, 83).outputAt(90, 59).slotAt(8, 27).outputAt(8, 72)
                    .upgradeAt(152, 8).upgradeAt(152, 26).upgradeAt(152, 44).upgradeAt(152, 62)
                    .energy(136, 84)
                    .tank(27, 30)
                    .text(menu -> menu.getHeat() > 0 ? Component.literal(uuText(menu.getMaxHeat())) : tr("replicator.no_pattern", "No pattern"), 49, 36, 96, 16, FLOW_TEXT_COLOR)
                    .build();
            // armazenamento de moldes (GuiPatternStorage): memória de cristal 0
            case PATTERN_STORAGE -> MachineLayout.textured("guipatternstorage.png", 166)
                    .slotAt(18, 20)
                    .text(menu -> Component.literal(((menu.getMachineMode() >> 16) == 0 ? 0 : (menu.getMachineMode() & 0xFFFF) + 1) + " / " + (menu.getMachineMode() >> 16)), 0, 30, 176, 0, 0x404040)
                    .text(menu -> tr("pattern_storage.name", "Name:"), 10, 48, 0, 0, 0xFFFFFF)
                    .text(menu -> tr("pattern_storage.uu", "UU-Matter:"), 10, 59, 0, 0, 0xFFFFFF)
                    .text(menu -> patternName(menu.getHeat()), 80, 48, 0, 0, 0xFFFFFF)
                    .text(menu -> menu.getHeat() > 0 ? Component.literal(uuText(menu.getMaxHeat())) : Component.empty(), 80, 59, 0, 0, 0xFFFFFF)
                    .build();
            // ── utilidades ────────────────────────────────────────────────
            case TESLA_COIL, LUMINATOR -> MachineLayout.dynamic(176, 166).build();
            // carregador de chunks e magnetizador: descarga 0, upgrades 1–4
            case CHUNK_LOADER -> MachineLayout.dynamic(176, 166)
                    .slot(7, 43).upgrades(151, 7).energy(12, 28)
                    .text(tr("chunk_loader.info", "Keeps this chunk loaded"), 36, 36)
                    .build();
            case MAGNETIZER -> MachineLayout.dynamic(176, 166)
                    .slot(7, 43).upgrades(151, 7).energy(12, 28)
                    .text(tr("magnetizer.info", "Magnetizes iron fences above"), 36, 36)
                    .build();
            // eletrolisador (guidef electrolyzer.xml): água 0 → recipiente 1, upgrades 2–5
            case ELECTROLYZER -> MachineLayout.dynamic(176, 166)
                    .slot(53, 34).output(111, 34).upgrades(151, 7)
                    .energyBar(79, 38)
                    .build();
            // RTG (guidef rt_generator.xml): 6 pastilhas
            case RT_GENERATOR -> MachineLayout.dynamic(176, 166)
                    .grid(30, 25, 3, 2, false).limitOne(0, 6)
                    .energyBar(115, 39)
                    .build();
            // ── automação ─────────────────────────────────────────────────
            case TERRAFORMER -> MachineLayout.dynamic(176, 166).slot(79, 34).build();
            // bancada industrial: grade 0–8, estoque 9–26, martelo 27 e entrada 28, alicate 29 e entrada 30 (a tela é a IndustrialWorkbenchScreen)
            case INDUSTRIAL_WORKBENCH -> MachineLayout.textured("guiindustrialworkbench.png", 228)
                    .gridAt(30, 43, 3, 3, false).gridAt(8, 106, 9, 2, false)
                    .slotAt(7, 17).slotAt(25, 17).slotAt(91, 17).slotAt(109, 17)
                    .build();
            // minerador avançado (GuiAdvMiner): descarga 0, scanner 1, upgrades 2–5, filtros 6–20
            case ADVANCED_MINER -> {
                MachineLayout.Builder builder = MachineLayout.textured("guiadvminer.png", 203)
                        .slotAt(8, 80).slotAt(8, 26)
                        .upgradeAt(152, 26).upgradeAt(152, 44).upgradeAt(152, 62).upgradeAt(152, 80);
                for (int row = 0; row < 3; row++) {
                    for (int col = 0; col < 5; col++) builder.ghostAt(36 + col * 18, 44 + row * 18);
                }
                yield builder.energy(12, 55)
                        .text(menu -> (menu.getMachineMode() & 1) != 0 ? tr("advanced_miner.blacklist", "Blacklist") : tr("advanced_miner.whitelist", "Whitelist"),
                                40, 31, 0, 0, FLOW_TEXT_COLOR)
                        .text(menu -> menu.getHeat() == Integer.MIN_VALUE ? Component.empty() : tr("advanced_miner.layer", "Y: %s", menu.getHeat()),
                                10, 105, 0, 0, FLOW_TEXT_COLOR)
                        .build();
            }
            // fabricador em lote (GuiBatchCrafter): descarga 0, molde 1–9, saída 10, ingredientes 11–19, recipientes 20–28, upgrades 29–32
            case BATCH_CRAFTER -> {
                MachineLayout.Builder builder = MachineLayout.textured("guibatchcrafter.png", 206).slotAt(8, 62);
                for (int y = 0; y < 3; y++) {
                    for (int x = 0; x < 3; x++) builder.ghostAt(30 + x * 18, 17 + y * 18);
                }
                yield builder.outputAt(124, 35)
                        .gridAt(8, 84, 9, 1, false)
                        .gridAt(8, 102, 9, 1, true)
                        .upgradeAt(152, 8).upgradeAt(152, 26).upgradeAt(152, 44).upgradeAt(152, 62)
                        .energy(12, 45)
                        .progress(90, 35, PROGRESS_ARROW)
                        .build();
            }
            // Energy-O-Mat: pedido 0, upgrade 1, pagamento 2, carga 3
            case ENERGY_O_MAT -> MachineLayout.textured("guienergyomatopen.png", 166)
                    .ghostAt(24, 17).upgradeAt(24, 53).slotAt(60, 17).slotAt(60, 53)
                    .text(menu -> tr("omat.offer", "Offer:"), 100, 60)
                    .text(menu -> Component.literal(menu.getMachineMode() + " EU"), 100, 68)
                    .build();
            // Trade-O-Mat: pedido 0, oferta 1, pagamento 2, saída 3
            case TRADE_O_MAT -> MachineLayout.textured("guitradeomatopen.png", 166)
                    .ghostAt(50, 19).ghostAt(50, 53).slotAt(80, 19).outputAt(80, 53)
                    .text(menu -> tr("omat.want", "Want:"), 12, 23)
                    .text(menu -> tr("omat.offer", "Offer:"), 12, 57)
                    .text(menu -> tr("omat.trades", "Trades: %s", menu.getMachineMode()), 108, 28)
                    .text(menu -> tr("omat.stock", "Stock: %s", menu.getHeat()), 108, 44)
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
                    .text(menu -> Component.translatableWithFallback("gui.ic2reborn.heat_generator.emit", "Emit: %s CCº", menu.getHeat()), 96, 33, 0, 0, HEAT_TEXT_COLOR)
                    .text(menu -> Component.translatableWithFallback("gui.ic2reborn.heat_generator.max_emit", "Max: %s CCº", menu.getMaxHeat()), 96, 52, 0, 0, HEAT_TEXT_COLOR)
                    .build();

            case ELECTRIC_HEAT_GENERATOR -> MachineLayout.textured("guielectricheatgenerator.png", 166)
                    .gridAt(44, 27, 5, 2, false)
                    .slotAt(8, 62)
                    .energy(12, 44)
                    .text(menu -> Component.literal(menu.getHeat() + " / " + menu.getMaxHeat() + " CCº"), 34, 66, 109, 13, HEAT_TEXT_COLOR)
                    .build();

            case RT_HEAT_GENERATOR -> MachineLayout.textured("guirtheatgenerator.png", 166)
                    .gridAt(62, 27, 3, 2, false)
                    .text(menu -> Component.literal(menu.getHeat() + " / " + menu.getMaxHeat() + " CCº"), 49, 66, 79, 13, HEAT_TEXT_COLOR)
                    .build();

            case ELECTRIC_KINETIC_GENERATOR -> MachineLayout.textured("guielectrickineticgenerator.png", 166)
                    .gridAt(44, 27, 5, 2, false)
                    .slotAt(8, 62)
                    .energy(12, 44)
                    .text(menu -> Component.literal(menu.getProgress() + " CKGF·M · " + menu.getMaxProgress() + " CKGF·M/t"),
                            34, 66, 109, 13, HEAT_TEXT_COLOR)

                    .build();

            // turbina a vapor: upgrade 0, turbina 1
            case STEAM_KINETIC_GENERATOR -> MachineLayout.textured("guisteamkineticgenerator.png", 166)
                    .upgradeAt(152, 26).slotAt(80, 26)
                    .text(menu -> steamKineticStatus(menu.getMachineMode(), menu.getProgress()), 8, 50, 160, 13, HEAT_TEXT_COLOR)
                    .build();

            case STIRLING_KINETIC_GENERATOR -> MachineLayout.textured("guistirlingkineticgenerator.png", 204)
                    .slotAt(8, 103).outputAt(26, 103)
                    .slotAt(134, 103).outputAt(152, 103)
                    .upgradeAt(62, 103).upgradeAt(80, 103).upgradeAt(98, 103)
                    .plainTank(19, 47, 12, 44).plainTank(145, 47, 12, 44)
                    .text(menu -> tr("stirling_kinetic.buffer", "Stored: %s / %s CKGF·M", menu.getProgress(), menu.getMaxProgress()), 20, 28, 138, 13, HEAT_TEXT_COLOR)
                    .build();

            case WATER_KINETIC_GENERATOR -> MachineLayout.textured("guiwaterkineticgenerator.png", 166)
                    .slotAt(80, 26)
                    .text(menu -> switch (menu.getMachineMode()) {
                        case 0 -> tr("water_kinetic.wrong_biome", "Not in ocean or river");
                        case 1 -> tr("water_kinetic.rotor_missing", "No rotor");
                        case 2 -> tr("water_kinetic.rotor_space", "Rotor not under water");
                        default -> tr("kinetic.output", "Output: %s CKGF·M/t", menu.getProgress());
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
                        default -> tr("kinetic.output", "Output: %s CKGF·M/t", menu.getProgress());
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

            case CHARGEPAD, CHARGEPAD_CESU, CHARGEPAD_MFE, CHARGEPAD_MFSU -> MachineLayout.textured("guichargepadblock.png", 161)
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

    /** Triagem (GuiSortingMachine): descarga, 3 upgrades, buffer de 11 e 7 filtros por face (D, U, N, S, W, E). */
    private static MachineLayout sortingMachine() {
        MachineLayout.Builder builder = MachineLayout.textured("guisortingmachine.png", 212, 243)
                .slotAt(188, 219)
                .upgradeAt(188, 161).upgradeAt(188, 179).upgradeAt(188, 197)
                .gridAt(8, 141, 11, 1, false)
                .energy(174, 220);
        for (int side = 0; side < 6; side++) {
            for (int column = 0; column < 7; column++) {
                builder.ghostAt(80 + column * 18, 19 + side * 20);
            }
        }
        return builder.build();
    }
    private static Component steamOutputName(int output) {
        return switch (output) {
            case 3 -> fluidName(net.ic2reborn.fluid.IC2Fluids.STEAM.fluid());
            case 4 -> fluidName(net.ic2reborn.fluid.IC2Fluids.SUPERHEATED_STEAM.fluid());
            default -> Component.literal("-");
        };
    }

    private static Component fluidName(net.minecraft.world.level.material.Fluid fluid) {
        return net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes.getName(
                net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant.of(fluid));
    }

    /** Status do gerador cinético a vapor: bits 1 sem turbina, 2 soltando vapor, 4 freado, 8 bloqueado. */
    private static Component steamKineticStatus(int status, int ku) {
        if ((status & 8) != 0) return tr("steam_kinetic.blocked", "Turbine blocked by water");
        if ((status & 1) != 0) return tr("steam_kinetic.no_turbine", "No steam turbine");
        if ((status & 2) != 0) return tr("steam_kinetic.venting", "Venting steam! %s CKGF·M/t", ku);
        if ((status & 4) != 0) return tr("steam_kinetic.throttled", "Throttled by water: %s CKGF·M/t", ku);
        return tr("steam_kinetic.output", "Output: %s CKGF·M/t", ku);
    }
    /** Reator nuclear (GuiNuclearReactor): grade 9×6, slots de refrigerante e calor; colunas sem câmara ficam tampadas. */
    private static MachineLayout nuclearReactor() {
        MachineLayout.Builder builder = MachineLayout.textured("guinuclearreactor.png", 212, 243)
                .imageIf(menu -> (menu.getMachineMode() & 16) != 0, "guinuclearreactorfluid.png", 0, 0, 0, 0, 212, 243, 256, 256)
                .inventory(25, 160);
        for (int y = 0; y < 6; y++) {
            for (int x = 0; x < 9; x++) {
                int column = x;
                builder.slotAt(26 + 18 * x, 25 + 18 * y);
                builder.imageIf(menu -> (menu.getMachineMode() & 15) <= column, "guinuclearreactor.png",
                        26 + 18 * x, 25 + 18 * y, 213, 1, 16, 16, 256, 256);
            }
        }
        return builder.slotAt(8, 25).slotAt(188, 25).outputAt(8, 115).outputAt(188, 115)
                .limitOne(0, 54)
                .plainTank(10, 54, 12, 47).plainTank(190, 54, 12, 47)
                .gauge(7, 136, HEAT_NUCLEAR_REACTOR, MachineLayout.GaugeSource.HEAT)
                .text(menu -> (menu.getMachineMode() & 16) != 0
                        ? tr("reactor.heat_output", "Heat output: %s CCº/s", menu.getProgress())
                        : tr("reactor.output", "Output: %s · Fission: %s MMEV", EnergyUnits.formatPower(menu.getProgress()),
                                String.format(java.util.Locale.ROOT, "%.1f", menu.getMaxProgress() / 10.0)), 111, 139, 0, 0, HEAT_TEXT_COLOR)
                .build();
    }
    private static final String[] SCANNER_STATES = {"idle", "scanning", "completed", "failed", "no_storage", "no_energy", "transfer_error", "already_recorded"};
    private static final String[] SCANNER_STATE_FALLBACKS = {"Idle", "Scanning", "Scan complete", "Can't be scanned", "No pattern storage",
            "Not enough energy", "Transfer error", "Already recorded"};

    private static Component scannerState(int state) {
        int index = Math.floorMod(state, SCANNER_STATES.length);
        return tr("scanner." + SCANNER_STATES[index], SCANNER_STATE_FALLBACKS[index]);
    }

    /** Custo em unidades do IC2 (1 = pedregulho = 0,01 mB). */
    private static String uuText(int units) {
        return String.format(java.util.Locale.ROOT, "%.2f CL UU", units * net.ic2reborn.recipe.UuValues.MB_PER_UNIT);
    }

    private static Component patternName(int itemIdPlusOne) {
        if (itemIdPlusOne <= 0) return Component.empty();
        return new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.byId(itemIdPlusOne - 1)).getHoverName();
    }
    private static Component tr(String key, String fallback, Object... args) {
        return Component.translatableWithFallback("gui.ic2reborn." + key, fallback, args);
    }
}
