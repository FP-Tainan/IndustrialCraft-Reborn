package net.craftenergy.api;

/**
 * Qualquer coisa que participa de uma rede elétrica.
 *
 * <p>Cada nó tem <b>exatamente um</b> papel: {@link EnergySource}, {@link EnergySink},
 * {@link EnergyBuffer} ou {@link EnergyConductor}. Aparelhos com dois lados diferentes
 * (como transformadores) expõem um nó por face.
 *
 * <p>Quem fornece nós para uma posição deve devolver <b>sempre a mesma instância</b>
 * para aquela face (um campo do block entity, ou um singleton para cabos sem estado):
 * a rede identifica os nós por posição + identidade do objeto.
 */
public interface EnergyNode {
}
