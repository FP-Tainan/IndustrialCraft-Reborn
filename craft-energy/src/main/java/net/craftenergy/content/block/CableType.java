package net.craftenergy.content.block;

import net.craftenergy.api.EnergyConductor;

/**
 * Tipos de cabo do Craft Energy (valores em docs/energia-conversao-ic2.md).
 *
 * <p>O <b>material</b> define a corrente máxima e a resistência; o <b>isolamento</b> define a
 * tensão máxima. Cada constante é o próprio nó condutor da rede: uma instância por tipo,
 * compartilhada por todos os blocos desse cabo.
 */
public enum CableType implements EnergyConductor {
    TIN(Material.TIN, 0),
    TIN_INSULATED(Material.TIN, 1),
    COPPER(Material.COPPER, 0),
    COPPER_INSULATED(Material.COPPER, 1),
    GOLD(Material.GOLD, 0),
    GOLD_INSULATED(Material.GOLD, 1),
    GOLD_DOUBLE_INSULATED(Material.GOLD, 2),
    IRON(Material.IRON, 0),
    IRON_INSULATED(Material.IRON, 1),
    IRON_DOUBLE_INSULATED(Material.IRON, 2),
    IRON_TRIPLE_INSULATED(Material.IRON, 3),
    GLASS_FIBRE(Material.GLASS_FIBRE, Material.GLASS_FIBRE_VOLTAGE),
    DETECTOR(Material.IRON, 3),
    SPLITTER(Material.IRON, 3);

    public enum Material {
        TIN(10.0, 0.10),
        COPPER(20.0, 0.05),
        GOLD(50.0, 0.03),
        IRON(100.0, 0.08),
        GLASS_FIBRE(200.0, 0.005);

        /** A fibra de vidro não tem camadas: já suporta ultra-alta tensão. */
        static final int GLASS_FIBRE_VOLTAGE = -69_000;

        public final double maxCurrent;
        public final double resistance;

        Material(double maxCurrent, double resistance) {
            this.maxCurrent = maxCurrent;
            this.resistance = resistance;
        }
    }

    private final Material material;
    private final int insulation;
    private final int maxVoltage;

    CableType(Material material, int insulationOrVoltage) {
        this.material = material;
        if (insulationOrVoltage < 0) {
            this.insulation = 0;
            this.maxVoltage = -insulationOrVoltage;
        } else {
            this.insulation = insulationOrVoltage;
            this.maxVoltage = insulationVoltage(insulationOrVoltage);
        }
    }

    /** Tensão máxima por camadas de isolamento: sem isolamento, 1, 2 e 3 camadas. */
    private static int insulationVoltage(int layers) {
        return switch (layers) {
            case 0 -> 220;
            case 1 -> 1_000;
            case 2 -> 2_400;
            default -> 13_800;
        };
    }

    public Material material() {
        return this.material;
    }

    /** Camadas de isolamento (0 a 3). */
    public int insulation() {
        return this.insulation;
    }

    @Override
    public int maxVoltage() {
        return this.maxVoltage;
    }

    @Override
    public double maxCurrent() {
        return this.material.maxCurrent;
    }

    @Override
    public double resistance() {
        return this.material.resistance;
    }
}
