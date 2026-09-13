package net.craftenergy.grid;

/** Divide um total inteiro proporcionalmente a pesos, sem perder nem sobrar unidades. */
final class Apportion {
    private Apportion() {}

    /**
     * Distribui {@code total} proporcionalmente a {@code weights} (método do maior resto).
     * Nenhuma parte passa do seu peso; se o total for maior que a soma dos pesos, cada
     * parte recebe o próprio peso.
     */
    static long[] distribute(long total, long[] weights) {
        long[] parts = new long[weights.length];
        long sum = 0;
        for (long weight : weights) sum += Math.max(0, weight);
        if (total <= 0 || sum <= 0) return parts;
        if (total >= sum) {
            for (int i = 0; i < weights.length; i++) parts[i] = Math.max(0, weights[i]);
            return parts;
        }

        double[] remainders = new double[weights.length];
        long assigned = 0;
        for (int i = 0; i < weights.length; i++) {
            if (weights[i] <= 0) continue;
            double exact = (double) total * weights[i] / sum;
            parts[i] = Math.min(weights[i], (long) Math.floor(exact));
            remainders[i] = exact - parts[i];
            assigned += parts[i];
        }

        for (long left = total - assigned; left > 0; left--) {
            int best = -1;
            for (int i = 0; i < weights.length; i++) {
                if (parts[i] < weights[i] && (best < 0 || remainders[i] > remainders[best])) best = i;
            }
            if (best < 0) break;
            parts[best]++;
            remainders[best] = -1;
        }
        return parts;
    }
}
