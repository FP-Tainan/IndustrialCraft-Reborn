package net.craftenergy.api;

/**
 * Transformador: liga uma rede de tensão alta a uma de tensão baixa.
 *
 * <p>Cada lado é um nó diferente, então as duas redes continuam separadas. A potência
 * passa por um buffer interno de um tick: o lado de entrada é um {@link EnergySink} que
 * enche o buffer, o de saída é um {@link EnergySource} que o esvazia. A potência se
 * mantém (vezes a eficiência); só a tensão e a corrente mudam.
 *
 * <p>Ao trocar o {@link Mode}, os nós expostos em cada lado mudam: quem usa esta classe
 * precisa pedir para a rede ser reconstruída.
 */
public class BufferedTransformer {
    public enum Mode {
        /** Alta tensão entra, baixa tensão sai. */
        STEP_DOWN,
        /** Baixa tensão entra, alta tensão sai. */
        STEP_UP
    }

    private final int highVoltage;
    private final int lowVoltage;
    private final long maxPower;
    private final double efficiency;
    private final Input highInput = new Input(true);
    private final Input lowInput = new Input(false);
    private final Output highOutput = new Output(true);
    private final Output lowOutput = new Output(false);
    private Mode mode = Mode.STEP_DOWN;
    private long buffer;

    public BufferedTransformer(int highVoltage, int lowVoltage, long maxPower, double efficiency) {
        this.highVoltage = highVoltage;
        this.lowVoltage = lowVoltage;
        this.maxPower = maxPower;
        this.efficiency = efficiency;
    }

    /** Nó exposto nas faces de alta tensão. */
    public EnergyNode highSide() {
        return this.mode == Mode.STEP_DOWN ? this.highInput : this.highOutput;
    }

    /** Nó exposto nas faces de baixa tensão. */
    public EnergyNode lowSide() {
        return this.mode == Mode.STEP_DOWN ? this.lowOutput : this.lowInput;
    }

    public Mode mode() {
        return this.mode;
    }

    public void setMode(Mode mode) {
        if (this.mode != mode) {
            this.mode = mode;
            this.buffer = 0;
            onChanged();
        }
    }

    public int highVoltage() {
        return this.highVoltage;
    }

    public int lowVoltage() {
        return this.lowVoltage;
    }

    public long maxPower() {
        return this.maxPower;
    }

    public long bufferedPower() {
        return this.buffer;
    }

    public void setBufferedPower(long power) {
        this.buffer = Math.max(0, Math.min(this.maxPower, power));
    }

    /** Sobrescreva para marcar o block entity como alterado. */
    protected void onChanged() {
    }

    private final class Input implements EnergySink {
        private final boolean high;

        private Input(boolean high) {
            this.high = high;
        }

        @Override
        public int nominalVoltage() {
            return this.high ? highVoltage : lowVoltage;
        }

        @Override
        public long powerDemand() {
            return Math.max(0, maxPower - buffer);
        }

        @Override
        public void receivePower(long power, int voltage) {
            if (power > 0) {
                setBufferedPower(buffer + Math.round(power * efficiency));
                onChanged();
            }
        }
    }

    private final class Output implements EnergySource {
        private final boolean high;

        private Output(boolean high) {
            this.high = high;
        }

        @Override
        public int outputVoltage() {
            return this.high ? highVoltage : lowVoltage;
        }

        @Override
        public long availablePower() {
            return buffer;
        }

        @Override
        public void drawPower(long power) {
            if (power > 0) {
                setBufferedPower(buffer - power);
                onChanged();
            }
        }
    }
}
