package net.ic2reborn.energy;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

/**
 * Simulação de vento do IC2 ({@code ic2.core.WindSim}), uma por dimensão.
 *
 * <p>A força do vento (5 a ~30) muda aos poucos a cada 128 ticks. O vento numa altura é
 * {@code força × curva(altura)}, onde a curva é um polinômio cúbico que vale 1 no meio do caminho
 * entre o nível do mar e o topo do mundo (o pico), com derivada zero ali, e 0 em 1,125 × altura
 * do mundo. Chuva aumenta o vento em 25% e tempestade em 50%.
 */
public final class WindSim {
    /** Vento máximo do IC2. */
    public static final double MAX_WIND = 108.0;

    private static final Map<ResourceKey<Level>, WindSim> SIMS = new HashMap<>();

    private final double[] coefficients;
    private int windStrength;
    private long lastStep;

    private WindSim(int worldHeight, int seaLevel, RandomSource random, long gameTime) {
        this.coefficients = coefficients(worldHeight, seaLevel);
        this.windStrength = 5 + random.nextInt(20);
        this.lastStep = gameTime;
    }

    public static WindSim get(ServerLevel level) {
        return SIMS.computeIfAbsent(level.dimension(),
                key -> new WindSim(level.getMaxY() + 1, level.getSeaLevel(), level.getRandom(), level.getGameTime()));
    }

    /** Vento na altura {@code height}, já com o efeito do tempo. */
    public double windAt(ServerLevel level, double height) {
        step(level);
        double wind = this.windStrength * heightMultiplier(height);
        if (level.isThundering()) {
            wind *= 1.5;
        } else if (level.isRaining()) {
            wind *= 1.25;
        }
        return wind;
    }

    public double heightMultiplier(double height) {
        double h = height;
        return Math.max(0.0, this.coefficients[0] * h + this.coefficients[1] * h * h + this.coefficients[2] * h * h * h);
    }

    private void step(ServerLevel level) {
        long now = level.getGameTime();
        for (int steps = 0; this.lastStep + 128 <= now && steps < 64; steps++) {
            this.lastStep += 128;
            randomWalk(level.getRandom());
        }
        if (this.lastStep + 128 <= now) this.lastStep = now;
    }

    private void randomWalk(RandomSource random) {
        int upChance = 10;
        int downChance = 10;
        if (this.windStrength > 20) {
            upChance -= this.windStrength - 20;
        } else if (this.windStrength < 10) {
            downChance -= 10 - this.windStrength;
        }
        if (random.nextInt(100) < upChance) {
            this.windStrength++;
        } else if (random.nextInt(100) < downChance) {
            this.windStrength--;
        }
    }

    /** Resolve f(h) = c0·h + c1·h² + c2·h³ com f(pico) = 1, f(1,125·altura) = 0 e f'(pico) = 0. */
    public static double[] coefficients(int worldHeight, int seaLevel) {
        int height = Math.max(1, worldHeight);
        int sea = Math.max(0, seaLevel);
        double base = sea < height ? sea : height * 0.5;
        double peak = base + (height - base) / 2.0;
        double zero = height * 1.125;

        double[][] a = {
                {peak, peak * peak, peak * peak * peak},
                {zero, zero * zero, zero * zero * zero},
                {1.0, 2.0 * peak, 3.0 * peak * peak}
        };
        double[] b = {1.0, 0.0, 0.0};
        return solve(a, b);
    }

    /** Peak height (where the multiplier is 1) for a world. */
    public static double peakHeight(int worldHeight, int seaLevel) {
        int height = Math.max(1, worldHeight);
        int sea = Math.max(0, seaLevel);
        double base = sea < height ? sea : height * 0.5;
        return base + (height - base) / 2.0;
    }

    private static double[] solve(double[][] a, double[] b) {
        double det = det(a);
        double[] x = new double[3];
        for (int column = 0; column < 3; column++) {
            double[][] m = new double[3][3];
            for (int row = 0; row < 3; row++) {
                for (int c = 0; c < 3; c++) m[row][c] = c == column ? b[row] : a[row][c];
            }
            x[column] = det(m) / det;
        }
        return x;
    }

    private static double det(double[][] m) {
        return m[0][0] * (m[1][1] * m[2][2] - m[1][2] * m[2][1])
                - m[0][1] * (m[1][0] * m[2][2] - m[1][2] * m[2][0])
                + m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0]);
    }
}
