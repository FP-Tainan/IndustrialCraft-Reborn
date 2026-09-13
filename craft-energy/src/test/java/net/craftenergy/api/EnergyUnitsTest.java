package net.craftenergy.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnergyUnitsTest {
    @Test
    void ohmsLawMatchesTheDesignExamples() {
        assertEquals(22.727, EnergyUnits.current(5000, 220), 1e-3);  // gerador a combustão
        assertEquals(9.0909, EnergyUnits.current(2000, 220), 1e-3);  // triturador
        assertEquals(4400, EnergyUnits.power(220, 20), 1e-9);        // cabo de cobre
        assertEquals(83.33, EnergyUnits.current(10_000, 120), 1e-2);
        assertEquals(10.0, EnergyUnits.current(10_000, 1000), 1e-9);
        assertEquals(100.0, EnergyUnits.resistiveLoss(10, 1.0), 1e-9);
    }

    @Test
    void energyIsStoredAsCraftWattTicks() {
        assertEquals(1000, EnergyUnits.fromCWh(1));
        assertEquals(1.5, EnergyUnits.toCWh(1500), 1e-9);
    }

    @Test
    void toleranceIsTenPercentAroundNominal() {
        assertTrue(EnergyUnits.withinTolerance(230, 220, 0.10));
        assertFalse(EnergyUnits.withinTolerance(250, 220, 0.10));
        assertFalse(EnergyUnits.withinTolerance(190, 220, 0.10));
    }

    @Test
    void formatsWithSiPrefixes() {
        assertEquals("5 kCW", EnergyUnits.formatPower(5000));
        assertEquals("220 MV", EnergyUnits.formatVoltage(220));
        assertEquals("22.7 RA", EnergyUnits.formatCurrent(22.7272));
        assertEquals("9.09 RA", EnergyUnits.formatCurrent(9.0909));
        assertEquals("2400 MV", EnergyUnits.formatVoltage(2400));
        assertEquals("13800 MV", EnergyUnits.formatVoltage(13_800));
        assertEquals("1.5 CWh", EnergyUnits.formatEnergy(1500));
    }
}
