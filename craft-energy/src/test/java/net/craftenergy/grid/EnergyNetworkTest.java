package net.craftenergy.grid;

import net.craftenergy.api.BufferedTransformer;
import net.craftenergy.api.EnergyUnits;
import net.craftenergy.api.Side;
import net.craftenergy.api.SimpleEnergyBuffer;
import net.craftenergy.grid.TestTopology.Cable;
import net.craftenergy.grid.TestTopology.Pos;
import net.craftenergy.grid.TestTopology.RecordingListener;
import net.craftenergy.grid.TestTopology.Sink;
import net.craftenergy.grid.TestTopology.Source;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnergyNetworkTest {
    private static final Cable COPPER = new Cable(220, 20.0, 0.0);

    /** Gerador em x=0, cabos em x=1..length, máquina em x=length+1 (tudo em y=0, z). */
    private static TestTopology line(TestTopology topology, int z, Source source, Cable cable, int length, Sink sink) {
        topology.place(0, 0, z, source);
        for (int x = 1; x <= length; x++) topology.place(x, 0, z, cable);
        topology.place(length + 1, 0, z, sink);
        return topology;
    }

    @Test
    void generatorPowersMachineThroughCable() {
        Source generator = new Source(220, 5000);
        Sink macerator = new Sink(220, 2000);
        List<EnergyNetwork<Pos>> networks = line(new TestTopology(), 0, generator, COPPER, 3, macerator).build();

        assertEquals(1, networks.size());
        EnergyNetwork<Pos> network = networks.get(0);
        NetworkTickReport report = network.tick(GridListener.none());

        assertEquals(220, report.voltage());
        assertEquals(2000, macerator.lastReceived);
        assertEquals(2000, generator.lastDrawn);
        // RA = CW / MV = 2000 / 220
        assertEquals(9.0909, network.conductorCurrent(new Pos(2, 0, 0)), 1e-3);
    }

    @Test
    void generatorNextToMachineWorksWithoutCable() {
        Source generator = new Source(220, 5000);
        Sink machine = new Sink(220, 1000);
        List<EnergyNetwork<Pos>> networks = new TestTopology().place(0, 0, 0, generator).place(1, 0, 0, machine).build();

        networks.get(0).tick(GridListener.none());

        assertEquals(1000, machine.lastReceived);
    }

    @Test
    void sharedStatelessCablesStillFormSeparateNetworks() {
        TestTopology topology = new TestTopology();
        line(topology, 0, new Source(220, 1000), COPPER, 3, new Sink(220, 100));
        line(topology, 5, new Source(220, 1000), COPPER, 3, new Sink(220, 100));

        List<EnergyNetwork<Pos>> networks = topology.build();

        assertEquals(2, networks.size());
        assertEquals(5, networks.get(0).vertices().size());
        assertEquals(5, networks.get(1).vertices().size());
    }

    @Test
    void shortageScalesDeliveryProportionally() {
        Source generator = new Source(220, 1000);
        Sink a = new Sink(220, 1000);
        Sink b = new Sink(220, 1000);
        TestTopology topology = new TestTopology()
                .place(0, 0, 0, a).place(1, 0, 0, generator).place(2, 0, 0, b);

        topology.build().get(0).tick(GridListener.none());

        assertEquals(500, a.lastReceived);
        assertEquals(500, b.lastReceived);
        assertEquals(1000, generator.lastDrawn);
    }

    @Test
    void resistiveLossesFollowCurrentSquaredTimesResistance() {
        Source generator = new Source(220, 5000);
        Sink machine = new Sink(220, 2200);
        Cable resistive = new Cable(220, 20.0, 0.1);
        EnergyNetwork<Pos> network = line(new TestTopology(), 0, generator, resistive, 10, machine).build().get(0);

        NetworkTickReport report = network.tick(GridListener.none());

        // RA = 2200 / 220 = 10; R = 10 × 0,1 = 1; perda = 10² × 1 = 100 CW
        assertEquals(2200, machine.lastReceived);
        assertEquals(100, report.losses());
        assertEquals(2300, generator.lastDrawn);
    }

    @Test
    void higherVoltageCarriesSamePowerWithLessCurrentAndLoss() {
        Cable line = new Cable(10_000, 1000.0, 0.1);
        Sink low = new Sink(220, 10_000);
        Sink high = new Sink(1000, 10_000);
        NetworkTickReport lowReport = line(new TestTopology(), 0, new Source(220, 100_000), line, 10, low).build().get(0).tick(GridListener.none());
        NetworkTickReport highReport = line(new TestTopology(), 0, new Source(1000, 100_000), line, 10, high).build().get(0).tick(GridListener.none());

        assertEquals(10_000, low.lastReceived);
        assertEquals(10_000, high.lastReceived);
        assertTrue(highReport.losses() < lowReport.losses() / 10, "1000 MV deveria perder bem menos que 220 MV");
    }

    @Test
    void overcurrentIsReportedOnEveryCableOfThePath() {
        Sink hungry = new Sink(220, 5000); // 22,7 RA > 20 RA do cobre
        EnergyNetwork<Pos> network = line(new TestTopology(), 0, new Source(220, 10_000), COPPER, 3, hungry).build().get(0);
        RecordingListener listener = new RecordingListener();

        network.tick(listener);

        assertEquals(3, listener.overcurrent.size());
        assertEquals(5000, hungry.lastReceived);
    }

    @Test
    void overvoltageMachineGetsNoPowerAndIsNotified() {
        Sink machine = new Sink(220, 1000);
        Cable hv = new Cable(10_000, 100.0, 0.0);
        EnergyNetwork<Pos> network = line(new TestTopology(), 0, new Source(2400, 10_000), hv, 2, machine).build().get(0);
        RecordingListener listener = new RecordingListener();

        network.tick(listener);

        assertEquals(0, machine.lastReceived);
        assertEquals(1, machine.overvoltages);
        assertEquals(1, listener.sinkOvervoltage.size());
    }

    @Test
    void cableBelowNetworkVoltageIsReported() {
        EnergyNetwork<Pos> network = line(new TestTopology(), 0, new Source(2400, 10_000), COPPER, 2, new Sink(2400, 100)).build().get(0);
        RecordingListener listener = new RecordingListener();

        network.tick(listener);

        assertEquals(2, listener.cableOvervoltage.size());
    }

    @Test
    void undervoltageMachineDoesNotRun() {
        Sink machine = new Sink(220, 1000);
        EnergyNetwork<Pos> network = line(new TestTopology(), 0, new Source(120, 10_000), COPPER, 2, machine).build().get(0);

        network.tick(GridListener.none());

        assertEquals(0, machine.lastReceived);
        assertEquals(0, machine.overvoltages);
    }

    @Test
    void batteryStoresSurplusAndCoversDeficit() {
        Source generator = new Source(220, 3000);
        Sink machine = new Sink(220, 1000);
        SimpleEnergyBuffer battery = new SimpleEnergyBuffer(220, EnergyUnits.fromCWh(10), 5000, 5000);
        EnergyNetwork<Pos> network = new TestTopology()
                .place(0, 0, 0, generator).place(1, 0, 0, COPPER).place(2, 0, 0, machine).place(1, 1, 0, battery)
                .build().get(0);

        NetworkTickReport charging = network.tick(GridListener.none());
        assertEquals(1000, machine.lastReceived);
        assertEquals(2000, battery.storedEnergy());
        assertEquals(2000, charging.toStorage());

        generator.available = 0;
        NetworkTickReport discharging = network.tick(GridListener.none());
        assertEquals(1000, machine.lastReceived);
        assertEquals(1000, battery.storedEnergy());
        assertEquals(1000, discharging.fromStorage());
    }

    @Test
    void transformerStepsDownVoltageKeepingPower() {
        // gerador 2400 MV → cabo → transformador → cabo → máquina 220 MV
        Source generator = new Source(2400, 22_000);
        Sink machine = new Sink(220, 22_000);
        BufferedTransformer transformer = new BufferedTransformer(2400, 220, 50_000, 1.0);
        Cable hvLine = new Cable(2400, 20.0, 0.0);
        Cable lvLine = new Cable(220, 200.0, 0.0);
        TestTopology topology = new TestTopology()
                .place(0, 0, 0, generator)
                .place(1, 0, 0, hvLine)
                .placeFace(2, 0, 0, Side.WEST, transformer.highSide())
                .placeFace(2, 0, 0, Side.EAST, transformer.lowSide())
                .place(3, 0, 0, lvLine)
                .place(4, 0, 0, machine);

        List<EnergyNetwork<Pos>> networks = topology.build();
        assertEquals(2, networks.size());

        for (int tick = 0; tick < 3; tick++) {
            for (EnergyNetwork<Pos> network : networks) network.tick(GridListener.none());
        }

        EnergyNetwork<Pos> high = TestTopology.networkAt(networks, 0, 0, 0);
        EnergyNetwork<Pos> low = TestTopology.networkAt(networks, 4, 0, 0);
        assertEquals(22_000, machine.lastReceived);
        assertEquals(2400, high.voltage());
        assertEquals(220, low.voltage());
        assertEquals(22_000.0 / 2400, high.conductorCurrent(new Pos(1, 0, 0)), 1e-3); // 9,17 RA
        assertEquals(100.0, low.conductorCurrent(new Pos(3, 0, 0)), 1e-3);           // 100 RA
    }

    @Test
    void nodeWithTwoRolesIsRejected() {
        class Confused extends SimpleEnergyBuffer implements net.craftenergy.api.EnergySource {
            Confused() { super(220, 1, 1, 1); }
            @Override public int outputVoltage() { return 220; }
            @Override public long availablePower() { return 0; }
            @Override public void drawPower(long power) {}
        }
        TestTopology topology = new TestTopology().place(0, 0, 0, new Confused());

        assertThrows(IllegalArgumentException.class, topology::build);
    }
}
