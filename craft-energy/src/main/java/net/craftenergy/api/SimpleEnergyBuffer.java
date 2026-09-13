package net.craftenergy.api;

/** Implementação pronta de bateria, para block entities que só precisam guardar energia. */
public class SimpleEnergyBuffer implements EnergyBuffer {
    private final int voltage;
    private final long capacity;
    private final long maxChargePower;
    private final long maxDischargePower;
    private long stored;

    public SimpleEnergyBuffer(int voltage, long capacity, long maxChargePower, long maxDischargePower) {
        this.voltage = voltage;
        this.capacity = capacity;
        this.maxChargePower = maxChargePower;
        this.maxDischargePower = maxDischargePower;
    }

    @Override
    public int voltage() {
        return this.voltage;
    }

    @Override
    public long maxChargePower() {
        return this.maxChargePower;
    }

    @Override
    public long maxDischargePower() {
        return this.maxDischargePower;
    }

    @Override
    public long storedEnergy() {
        return this.stored;
    }

    @Override
    public long energyCapacity() {
        return this.capacity;
    }

    public void setStoredEnergy(long energy) {
        this.stored = Math.max(0, Math.min(this.capacity, energy));
        onChanged();
    }

    @Override
    public void charge(long power) {
        setStoredEnergy(this.stored + Math.max(0, power));
    }

    @Override
    public void discharge(long power) {
        setStoredEnergy(this.stored - Math.max(0, power));
    }

    /** Sobrescreva para marcar o block entity como alterado. */
    protected void onChanged() {
    }
}
