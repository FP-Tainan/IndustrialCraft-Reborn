package net.craftenergy.grid;

/**
 * Resumo de um tick de uma rede (todos os valores de potência em CW).
 *
 * @param voltage    tensão da rede em MV
 * @param demand     potência pedida pelas máquinas que podiam funcionar
 * @param delivered  potência entregue às máquinas
 * @param losses     perdas resistivas nos cabos (RA² × resistência)
 * @param generated  potência tirada dos geradores
 * @param fromStorage potência tirada das baterias
 * @param toStorage  potência guardada nas baterias
 */
public record NetworkTickReport(int voltage, long demand, long delivered, long losses,
                                long generated, long fromStorage, long toStorage) {
    public static final NetworkTickReport EMPTY = new NetworkTickReport(0, 0, 0, 0, 0, 0, 0);
}
