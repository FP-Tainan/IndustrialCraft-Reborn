package net.craftenergy.content.block;

import net.craftenergy.api.EnergyUnits;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Confere os cabos contra a tabela aprovada em docs/energia-conversao-ic2.md. */
class CableTypeTest {
    @Test
    void insulationDefinesVoltage() {
        assertEquals(220, CableType.COPPER.maxVoltage());
        assertEquals(1_000, CableType.COPPER_INSULATED.maxVoltage());
        assertEquals(2_400, CableType.GOLD_DOUBLE_INSULATED.maxVoltage());
        assertEquals(13_800, CableType.IRON_TRIPLE_INSULATED.maxVoltage());
        assertEquals(69_000, CableType.GLASS_FIBRE.maxVoltage());
        assertEquals(3, CableType.IRON_TRIPLE_INSULATED.insulation());
        assertEquals(0, CableType.GLASS_FIBRE.insulation());
    }

    @Test
    void materialDefinesCurrentAndResistance() {
        assertEquals(10.0, CableType.TIN.maxCurrent());
        assertEquals(0.10, CableType.TIN_INSULATED.resistance());
        assertEquals(20.0, CableType.COPPER.maxCurrent());
        assertEquals(50.0, CableType.GOLD_INSULATED.maxCurrent());
        assertEquals(100.0, CableType.IRON.maxCurrent());
        assertEquals(200.0, CableType.GLASS_FIBRE.maxCurrent());
        assertEquals(0.005, CableType.GLASS_FIBRE.resistance());
        assertEquals(13_800, CableType.DETECTOR.maxVoltage());
        assertEquals(100.0, CableType.SPLITTER.maxCurrent());
    }

    @Test
    void copperMatchesTheDesignDocument() {
        // Cabo de cobre: 220 MV × 20 RA = 4.400 CW
        assertEquals(4_400.0, EnergyUnits.power(CableType.COPPER.maxVoltage(), CableType.COPPER.maxCurrent()), 1e-9);

        // 4.400 CW por 20 blocos de cobre (R = 1,0)
        double resistance = 20 * CableType.COPPER.resistance();
        double lossAt220 = EnergyUnits.resistiveLoss(EnergyUnits.current(4_400, 220), resistance);
        double lossAt1000 = EnergyUnits.resistiveLoss(EnergyUnits.current(4_400, 1_000), resistance);
        assertEquals(400.0, lossAt220, 1e-6);
        assertEquals(19.36, lossAt1000, 1e-6);
    }
}
