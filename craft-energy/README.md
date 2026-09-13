# Craft Energy

Sistema de energia elétrica compartilhado pelos mods industriais do pack (começando pelo IC2 Reborn).
Inspirado em eletricidade real, com grandezas próprias do universo Minecraft.

## Grandezas

| Unidade | Nome | O que é |
|---|---|---|
| **CW** | Craft Watts | potência instantânea |
| **MV** | Mine Volts | tensão da rede |
| **RA** | Red Ampères | corrente |
| **CWh** | Craft Watt-hora | energia armazenada |

Relação fundamental: **CW = MV × RA**.

A "hora" é a hora do jogo: **1 CWh = 1 CW durante 1000 ticks**. Internamente a energia é um
inteiro em CW·tick (1 CWh = 1000), então não acumula erro de arredondamento.

## Papéis

Todo bloco que participa da rede expõe um `EnergyNode` com **um** papel:

- `EnergySource` (gerador): tensão de saída (MV) e potência disponível (CW).
- `EnergySink` (máquina): tensão nominal (MV) e consumo (CW). Corrente = CW / MV.
- `EnergyBuffer` (bateria): tensão, capacidade (CWh) e potência máxima de carga/descarga.
- `EnergyConductor` (cabo): tensão máxima (MV), corrente máxima (RA) e resistência.

Transformadores (`BufferedTransformer`) expõem um nó diferente em cada lado e por isso separam
duas redes de tensões diferentes. A potência se mantém; a corrente muda.

## Como a rede calcula cada tick

1. A tensão da rede é a maior entre geradores e baterias. Quem estiver fora de ±10% dela não participa.
2. Máquinas acima da faixa de tensão sofrem **sobretensão** (`onOvervoltage`, a máquina decide o que
   acontece) e não recebem energia; abaixo da faixa **não ligam**.
3. Cada máquina pede CW; a perda no caminho até o gerador mais próximo é **RA² × R**.
4. Geradores cobrem demanda + perdas; o que faltar sai das baterias; se ainda faltar, todas as
   máquinas recebem a mesma fração.
5. A sobra dos geradores carrega as baterias.
6. A corrente é somada nos cabos do caminho; cabos acima de RA ou MV máximos geram eventos de
   proteção (aquecer, queimar).

Por isso redes de tensão mais alta transportam a mesma potência com menos corrente e muito menos perda:

| Para 10.000 CW | Corrente |
|---|---|
| 120 MV | 83,3 RA |
| 220 MV | 45,45 RA |
| 1.000 MV | 10 RA |

## Estrutura do código

- `net.craftenergy.api`: unidades, papéis e aparelhos prontos (sem dependência do Minecraft).
- `net.craftenergy.grid`: montagem das redes e cálculo por tick (sem dependência do Minecraft,
  testado com JUnit em `src/test`).
- `net.craftenergy.fabric`: integração com o jogo (lookup de blocos, redes por dimensão, tick,
  aquecimento e queima de cabos).
- `net.craftenergy.content`: conteúdo compartilhado pelos mods do pack — cabos (`CableType`:
  material define corrente/resistência, isolamento define tensão), seringueira e borracha,
  minério/lingote/bloco de estanho, placas de cobre/estanho/ouro/ferro, torneira, alicate e martelo.
- `net.craftenergy.registry`: `DeferredRegister`/`RegistryObject`, usados também pelos outros mods.

Blocos de outros mods que devem desenhar conexão com os cabos antes de virarem nós de energia
entram na tag `craftenergy:connects_to_cables`.

Rodar os testes:

```bash
./gradlew :craft-energy:test
```
