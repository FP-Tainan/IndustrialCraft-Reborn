package net.craftenergy.api;

import java.util.Locale;

/**
 * Grandezas do Craft Energy e a relação entre elas.
 *
 * <ul>
 *   <li><b>CW</b> (Craft Watts): potência instantânea;</li>
 *   <li><b>MV</b> (Mine Volts): tensão da rede;</li>
 *   <li><b>RA</b> (Red Ampères): corrente;</li>
 *   <li><b>CWh</b> (Craft Watt-hora): energia armazenada.</li>
 * </ul>
 * Relação fundamental: {@code CW = MV × RA}.
 *
 * <p>Energia é guardada como inteiro em <b>CW·tick</b>: um aparelho de 1 CW funcionando
 * por 1 tick gasta 1 unidade. A "hora" é a hora do jogo (1000 ticks), então
 * 1 CWh = 1000 CW·tick.
 */
public final class EnergyUnits {
    /** Ticks numa hora de jogo: 1 CWh = 1 CW mantido por 1000 ticks. */
    public static final int TICKS_PER_HOUR = 1000;

    /** Tolerância padrão de tensão (±10%) para geradores, baterias e máquinas. */
    public static final double DEFAULT_VOLTAGE_TOLERANCE = 0.10;

    private static final String[] PREFIXES = {"", "k", "M", "G", "T"};

    private EnergyUnits() {}

    /** RA = CW / MV. */
    public static double current(double powerCW, double voltageMV) {
        return voltageMV <= 0 ? 0.0 : powerCW / voltageMV;
    }

    /** CW = MV × RA. */
    public static double power(double voltageMV, double currentRA) {
        return voltageMV * currentRA;
    }

    /** Perda resistiva: CW = RA² × resistência. */
    public static double resistiveLoss(double currentRA, double resistance) {
        return currentRA * currentRA * resistance;
    }

    public static boolean withinTolerance(int voltage, int nominal, double tolerance) {
        if (nominal <= 0) return false;
        return voltage >= nominal * (1.0 - tolerance) && voltage <= nominal * (1.0 + tolerance);
    }

    /** CW·tick → CWh. */
    public static double toCWh(long energy) {
        return energy / (double) TICKS_PER_HOUR;
    }

    /** CWh → CW·tick. */
    public static long fromCWh(double cwh) {
        return Math.round(cwh * TICKS_PER_HOUR);
    }

    public static String formatPower(double cw) {
        return format(cw, "CW");
    }

    /** Tensão sem prefixo (220 MV, 13800 MV), para não virar "kMV". */
    public static String formatVoltage(double mv) {
        return String.format(Locale.ROOT, "%.0f MV", mv);
    }

    public static String formatCurrent(double ra) {
        return format(ra, "RA");
    }

    /** Formata energia guardada (em CW·tick) como CWh. */
    public static String formatEnergy(long energy) {
        return format(toCWh(energy), "CWh");
    }

    /** Formata com prefixo SI (k, M, G, T) e até 3 algarismos significativos. */
    public static String format(double value, String unit) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return "? " + unit;

        int prefix = 0;
        while (Math.abs(value) >= 1000 && prefix < PREFIXES.length - 1) {
            value /= 1000;
            prefix++;
        }

        double abs = Math.abs(value);
        String pattern = abs >= 100 ? "%.0f" : abs >= 10 ? "%.1f" : "%.2f";
        String number = String.format(Locale.ROOT, pattern, value);
        if (number.indexOf('.') >= 0) {
            number = number.replaceAll("0+$", "");
            if (number.endsWith(".")) number = number.substring(0, number.length() - 1);
        }
        return number + " " + PREFIXES[prefix] + unit;
    }
}
