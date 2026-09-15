# Tabela de energia do IC2 Reborn (Craft Energy)

> **Status: proposta revisada, aguardando aprovação final.**

## Princípios

1. **Só existe Craft Energy.** EU, EU/t, HU e KU do IC2 não existem no mod: nenhum texto,
   tooltip, GUI, config ou código usa essas unidades.
   - Eletricidade: **CW** (potência), **MV** (tensão), **RA** (corrente), **CWh** (energia).
   - Calor e movimento também são potência, então usam **CW térmicos** e **CW mecânicos**;
     a conversão entre eles e eletricidade tem eficiência (fica para a fase da cadeia de calor/cinética).
2. **Regras do modelo do Craft Energy:** CW = MV × RA, RA = CW / MV, perda = RA² × R,
   transformadores mudam MV mantendo a potência (menos a perda), 1 CWh = 1 CW por 1000 ticks.
3. **Âncoras do documento de design:** Gerador a combustão 5.000 CW a 220 MV; Triturador (Macerador)
   2.000 CW a 220 MV; Cabo de cobre 220 MV / 20 RA; transmissão 2.400 MV → transformador → 220 MV.
4. As proporções entre blocos seguem o IC2 original, ajustadas para caber nas âncoras.
5. **Tensão acima do limite (+10%) explode** máquinas, baterias e transformadores.
6. **Transformadores têm perda.**

## Níveis de tensão

| Nível | Tensão | Uso |
|---|---|---|
| Baixa tensão (BT) | **220 MV** | máquinas básicas, geradores iniciais, BatBox |
| Média tensão (MT) | **1.000 MV** | máquinas industriais, CESU |
| Alta tensão (AT) | **2.400 MV** | transmissão, MFE, fabricador de matéria |
| Extra-alta tensão (EAT) | **13.800 MV** | MFSU, scanner, replicador |
| Ultra-alta tensão (UAT) | **69.000 MV** | linhas de fibra de vidro |

220 / 1.000 / 2.400 MV vêm do modelo; 13.800 e 69.000 MV são valores reais de distribuição e transmissão.

## Cabos

O **material** define a corrente máxima e a resistência; o **isolamento** define a tensão máxima.
As camadas possíveis por material seguem o IC2.

| Isolamento | Tensão máx |
|---|---|
| sem isolamento | 220 MV |
| 1 camada | 1.000 MV |
| 2 camadas | 2.400 MV |
| 3 camadas | 13.800 MV |

| Cabo (bloco) | Tensão máx | Corrente máx | Resistência/bloco | Capacidade |
|---|---|---|---|---|
| Estanho (`cable_tin`) | 220 MV | 10 RA | 0,10 | 2,2 kCW |
| Estanho isolado (`cable_tin_insulated`) | 1.000 MV | 10 RA | 0,10 | 10 kCW |
| Cobre (`cable_copper`) | 220 MV | 20 RA | 0,05 | 4,4 kCW |
| Cobre isolado (`cable_copper_insulated`) | 1.000 MV | 20 RA | 0,05 | 20 kCW |
| Ouro (`cable_gold`) | 220 MV | 50 RA | 0,03 | 11 kCW |
| Ouro isolado (`cable_gold_insulated`) | 1.000 MV | 50 RA | 0,03 | 50 kCW |
| Ouro 2× isolado (`cable_gold_double_insulated`) | 2.400 MV | 50 RA | 0,03 | 120 kCW |
| Ferro (`cable_iron`) | 220 MV | 100 RA | 0,08 | 22 kCW |
| Ferro isolado (`cable_iron_insulated`) | 1.000 MV | 100 RA | 0,08 | 100 kCW |
| Ferro 2× isolado (`cable_iron_double_insulated`) | 2.400 MV | 100 RA | 0,08 | 240 kCW |
| Ferro 3× isolado (`cable_iron_triple_insulated`) | 13.800 MV | 100 RA | 0,08 | 1,38 MCW |
| Fibra de vidro (`glass_fibre_cable`) | 69.000 MV | 200 RA | 0,005 | 13,8 MCW |
| Detector / Divisor (`cable_detector`, `cable_splitter`) | 13.800 MV | 100 RA | 0,08 | 1,38 MCW |

Cabos sem isolamento vão dar choque em entidades (fase posterior).

**Exemplo do efeito da tensão**: 4.400 CW por 20 blocos de cobre (R = 1,0):
- cobre sem isolamento a 220 MV: 20 RA → perda 400 CW (**9,1%**);
- cobre isolado a 1.000 MV: 4,4 RA → perda 19 CW (**0,44%**).

## Geradores

| Gerador | Tensão | Potência | Corrente |
|---|---|---|---|
| Gerador (combustão) | 220 MV | **5.000 CW** | 22,7 RA |
| Gerador geotérmico | 220 MV | 10.000 CW | 45,5 RA |
| Gerador semifluido | 220 MV | 8.000–16.000 CW (conforme o combustível) | 36–73 RA |
| Gerador solar | 220 MV | 500 CW (de dia) | 2,27 RA |
| Gerador de água | 220 MV | 500–1.000 CW | 2,3–4,5 RA |
| Gerador eólico | 220 MV | 0–5.000 CW (vento e altura) | 0–22,7 RA |

Combustíveis do semifluido (tanque de 10 baldes): biomassa 8.000 CW por 1.000 ticks/balde (8.000 CWh),
biogás 16.000 CW por 2.000 ticks/balde (32.000 CWh), creosoto 8.000 CW por 375 ticks/balde (3.000 CWh).
Geotérmico: lava, 10.000 CW por 500 ticks/balde (5.000 CWh).

Um gerador de 5.000 CW a plena carga já passa dos 20 RA do cobre sem isolamento:
precisa de ouro/ferro, de dois cabos de cobre, ou de subir a tensão.

## Máquinas

| Máquina | Tensão | Consumo | Corrente | Duração | Energia por operação |
|---|---|---|---|---|---|
| Macerador | 220 MV | **2.000 CW** | 9,09 RA | 300 ticks | 600 CWh |
| Compressor | 220 MV | 2.000 CW | 9,09 RA | 300 ticks | 600 CWh |
| Extrator | 220 MV | 2.000 CW | 9,09 RA | 300 ticks | 600 CWh |
| Fornalha elétrica | 220 MV | 3.000 CW | 13,6 RA | 100 ticks | 300 CWh |
| Reciclador | 220 MV | 1.000 CW | 4,55 RA | 45 ticks | 45 CWh |
| Enlatador sólido | 220 MV | 2.000 CW | 9,09 RA | 200 ticks | 400 CWh |
| Enlatador | 220 MV | 4.000 CW | 18,2 RA | 200 ticks | 800 CWh |
| Cortador de blocos | 220 MV | 4.000 CW | 18,2 RA | 450 ticks | 1.800 CWh |
| Conformador de metal | 220 MV | 4.000 CW | 18,2 RA | 200 ticks | 800 CWh |
| Minerador | 220 MV | 3.000 CW (perfurando) | 13,6 RA | — | — |
| Lavadora de minério | 1.000 MV | 16.000 CW | 16 RA | 500 ticks | 8.000 CWh |
| Centrífuga térmica | 1.000 MV | 48.000 CW | 48 RA | 500 ticks | 24.000 CWh |
| Forno de indução | 1.000 MV | 1.000 CW aquecendo / 15.000 CW processando | 1 / 15 RA | — | — |
| Fabricador de matéria | 2.400 MV | até 120.000 CW | até 50 RA | — | 500.000 CWh por UU-matéria |
| Scanner | 13.800 MV | 256.000 CW | 18,6 RA | — | — |
| Replicador | 13.800 MV | 512.000 CW | 37,1 RA | — | — |

Sem eletricidade: fornalha de ferro, alto-forno e fermentador (calor), distribuidores ponderados,
trade-o-mat, baú pessoal. Energy-o-mat e batch crafter: a definir.

Upgrades de transformador passam a subir a tensão nominal da máquina (220 → 1.000 → 2.400 MV).

## Máquinas avançadas (Advanced Machines)

Fim de jogo: todas em **13.800 MV** (EAT). O IC2 pedia 15/24/48 EU/t; aqui fica 2.000 CW por EU/t.
A velocidade vem do calor (0 a 10.000): cada tick trabalhando soma o calor ao progresso e a operação
sai a cada 120.000 pontos — 12 ticks com o calor no máximo (o reciclador compactador faz uma por tick).
O calor sobe 1/tick trabalhando (ou parado com redstone, pagando o consumo ocioso) e cai 2/tick parado.
Overclocker não faz efeito; transformador (até 69.000 MV) e armazenamento valem. Buffer: 640 ticks de consumo.

| Máquina | Tensão | Consumo | Corrente | Ocioso (redstone) | Buffer | Base das receitas |
|---|---|---|---|---|---|---|
| Triturador rotativo | 13.800 MV | 30.000 CW | 2,17 RA | 2.000 CW | 5.333 CWh | macerador |
| Compressor de singularidade | 13.800 MV | 30.000 CW | 2,17 RA | 2.000 CW | 5.333 CWh | compressor |
| Extrator centrífugo | 13.800 MV | 30.000 CW | 2,17 RA | 2.000 CW | 5.333 CWh | extrator |
| Reciclador compactador | 13.800 MV | 30.000 CW | 2,17 RA | 2.000 CW | 5.333 CWh | reciclador (+ 9 sucatas → caixa) |
| Extrusora liquescente | 13.800 MV | 48.000 CW | 3,48 RA | 2.000 CW | 8.533 CWh | conformador (extrudar) |
| Laminador de impulsores | 13.800 MV | 48.000 CW | 3,48 RA | 2.000 CW | 8.533 CWh | conformador (laminar) |
| Cortador a jato d'água | 13.800 MV | 48.000 CW | 3,48 RA | 2.000 CW | 8.533 CWh | conformador (cortar) + 500 CL de água |
| Enlatadora a vácuo | 13.800 MV | 48.000 CW | 3,48 RA | 2.000 CW | 8.533 CWh | enlatadora (4 modos, 2 tanques) |
| Lavadora térmica | 13.800 MV | 96.000 CW | 6,96 RA | 12.000 CW | 17.067 CWh | lavadora de minério + 500 CL de água |

## Painéis solares avançados (Advanced Solar Panels)

Tensões altas; IC2 EU/t × 500 = CW e EU × 0,5 = CWh (como o painel solar e as armaduras).
Precisam de céu aberto acima (olham a cada 128 ticks); noite ou chuva de dia rendem a produção noturna; sem céu, nada.

| Bloco | Tensão | Dia | Noite | Capacidade |
|---|---|---|---|---|
| Painel solar avançado | 1.000 MV | 4.000 CW | 500 CW | 16.000 CWh |
| Painel solar híbrido | 2.400 MV | 32.000 CW | 4.000 CW | 50.000 CWh |
| Painel solar híbrido supremo | 13.800 MV | 256.000 CW | 32.000 CW | 500.000 CWh |
| Painel solar quântico | 69.000 MV | 2.048.000 CW | 1.024.000 CW | 5.000.000 CWh |
| Gerador quântico (criativo) | 220 a 69.000 MV (botões) | 256.000 CW padrão, ajustável | — | infinita |

Transformador molecular: aceita qualquer tensão (nominal 69.000 MV), sem buffer; puxa da rede só o que falta da
receita (campo `energy`, em CWh, em `machine_recipe`). Ex.: terra → argila 25.000 CWh; lingote de ferro → minério
de irídio 4.500.000 CWh; crânio de esqueleto wither → estrela do Nether 125.000.000 CWh.

Capacetes solares: avançado 4.000/500 CW (2.400 MV, 500.000 CWh), híbrido 32.000/4.000 CW e supremo
256.000/32.000 CW (13.800 MV, 5.000.000 CWh); o híbrido e o supremo gastam 500 CWh para repor o ar.

## Armazenamento

| Bloco | Tensão | Carga/descarga máx | Capacidade |
|---|---|---|---|
| BatBox | 220 MV | 4.400 CW | 20.000 CWh (20 kCWh) |
| CESU | 1.000 MV | 20.000 CW | 150.000 CWh (150 kCWh) |
| MFE | 2.400 MV | 120.000 CW | 2.000.000 CWh (2 MCWh) |
| MFSU | 13.800 MV | 1.000.000 CW | 20.000.000 CWh (20 MCWh) |

Chargepads usam os valores do armazenamento correspondente.

## Transformadores (com perda)

| Bloco atual | Nome | Lados | Potência máx | Eficiência |
|---|---|---|---|---|
| `lv_transformer` | Transformador 220/1.000 MV | 220 ↔ 1.000 MV | 20 kCW | 97% |
| `mv_transformer` | Transformador 1.000/2.400 MV | 1.000 ↔ 2.400 MV | 120 kCW | 97,5% |
| `hv_transformer` | Transformador 2.400/13.800 MV | 2.400 ↔ 13.800 MV | 1 MCW | 98% |
| `ev_transformer` | Transformador 13.800/69.000 MV | 13.800 ↔ 69.000 MV | 5 MCW | 98,5% |

Modo abaixador (padrão) ou elevador.

## Sobretensão: explosão

Máquina, bateria ou transformador ligado numa rede acima de +10% da sua tensão **explode**.
Força proposta (TNT = 4): BT 2, MT 3, AT 4, EAT 5. Configurável.

## O que muda no código para o EU sumir

- `MachineScreen`: tooltip de energia em "EU" → CWh / CW / MV / RA.
- `MachineLayouts` e traduções: textos "EU/t" da BatBox, chargepad, transformador e geradores cinéticos.
- Craft Energy: baterias e transformadores também recebem aviso de sobretensão (hoje só máquinas).
