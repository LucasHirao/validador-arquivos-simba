# Mapa de Cobertura por Regra — nível 2 (original em sandbox)

**Gerado em:** 2026-10-17. **Fecha a lacuna LAC-09** do catálogo para as regras que emitem mensagem própria.

## 1. Método
1. Corpus: 16 casos golden + 17 dirigidos por cobertura (`fuzz_cobertura.py`) + 800 dirigidos a limiares (`fuzz_dirigido.py`, semente 5) + 2.100 aleatórios (`fuzz_cruzado.py`, 3 sementes × 700) + 7 casos de lacuna (`gerar_lacunas.py`) = **2.940 cenários**. Todos os dados são sintéticos.
2. Cada cenário roda no **aplicativo original** (pipeline real Spring + HSQLDB, `bwrap --unshare-net`, FS somente leitura; `diferencial_nivel2.py`) e na reimplementação em modo COMPAT.
3. Só contam cenários em que o original e a reimplementação produziram o **mesmo multiconjunto de linhas** (a ordem de R-OD-04 não é contratual). Resultado: **0 divergências reais** nos 2.940 cenários (28 com apenas ordem diferente, em R-OD-04).
4. O ID de cada violação vem da reimplementação (`MapaRegras.java`, campo `ruleId`); IDs internos (`L-NEG`, `L-NUM`, `L-UNK`) são traduzidos para IDs do catálogo pelo texto da mensagem (`cobertura_por_regra.py`). Três regras sem mensagem própria são atribuídas manualmente ao cenário que as exercita (L-AG-07, L-OD-00/E-13, E-07).
5. Para cada regra observada guarda-se um **caso mínimo** (menos linhas de erro; empate por prioridade do corpus) em `analise/testes/casos-por-regra/<ID>/`, com `esperado.txt` = saída do **original**. Reexecutado como corpus próprio: 71/71 idênticos.
6. Locale: o mesmo corpus (exceto dirigido) foi repetido com `en_US` (2.140 cenários): 0 divergências; saídas do original idênticas às de `pt_BR` em 700 cenários comparados diretamente.

## 2. Regras observadas no original (71)
| ID | Nome | Cenários | Caso mínimo | Erros no caso | Confiança no catálogo |
|---|---|---:|---|---:|---|
| E-02 | Arquivos obrigatoriamente não vazios | 3 | `casos-por-regra/E-02` | 1 | EXEC |
| E-03 | Quantidade de colunas | 318 | `casos-por-regra/E-03` | 1 | EXEC |
| E-04 | Tamanho máximo do campo | 40 | `casos-por-regra/E-04` | 1 | EXEC |
| E-07 | Fail-fast na carga | 1 | `casos-por-regra/E-07` | 1 | EXEC |
| E-13 | "Erro desconhecido" | 174 | `casos-por-regra/E-13` | 1 | EXEC |
| L-AG-01 | Campos obrigatórios da agência | 65 | `casos-por-regra/L-AG-01` | 1 | EXEC |
| L-AG-02 | Data de abertura | 43 | `casos-por-regra/L-AG-02` | 1 | EXEC |
| L-AG-03 | Data de fechamento | 41 | `casos-por-regra/L-AG-03` | 1 | EXEC |
| L-AG-04 | Banco igual ao do atendimento | 41 | `casos-por-regra/L-AG-04` | 1 | EXEC |
| L-AG-05 | Coerência de datas da agência | 22 | `casos-por-regra/L-AG-05` | 1 | EXEC |
| L-AG-06 | UF válida | 53 | `casos-por-regra/L-AG-06` | 1 | EXEC |
| L-AG-07 | Inserção da agência | 1 | `casos-por-regra/L-AG-07` | 1 | EXEC |
| L-CT-00 | Agência da conta existe | 76 | `casos-por-regra/L-CT-00` | 1 | EXEC |
| L-CT-01 | Obrigatórios da conta | 32 | `casos-por-regra/L-CT-01` | 2 | EXEC |
| L-CT-02 | Data de abertura da conta | 32 | `casos-por-regra/L-CT-02` | 1 | EXEC |
| L-CT-03 | Data de encerramento da conta | 36 | `casos-por-regra/L-CT-03` | 1 | EXEC |
| L-CT-04 | Tipo de conta | 52 | `casos-por-regra/L-CT-04` | 1 | EXEC |
| L-CT-05 | Movimentação da conta | 38 | `casos-por-regra/L-CT-05` | 1 | EXEC |
| L-EX-00 | Campos numéricos | 27 | `casos-por-regra/L-EX-00` | 1 | PROV (A-05) |
| L-EX-01 | Conta do lançamento existe | 52 | `casos-por-regra/L-EX-01` | 1 | EXEC |
| L-EX-02 | Obrigatórios do lançamento | 5 | `casos-por-regra/L-EX-02` | 1 | EXEC |
| L-EX-03 | Data do lançamento | 21 | `casos-por-regra/L-EX-03` | 1 | EXEC |
| L-EX-04 | Natureza do lançamento | 16 | `casos-por-regra/L-EX-04` | 1 | EXEC |
| L-EX-05 | Natureza do saldo | 22 | `casos-por-regra/L-EX-05` | 1 | EXEC |
| L-EX-06 | TIPO_LANCAMENTO × natureza (CNAB) | 90 | `casos-por-regra/L-EX-06` | 1 | EXEC |
| L-EX-07 | Valores não negativos | 4 | `casos-por-regra/L-EX-07` | 1 | EXEC |
| L-OD-00 | Conversão numérica | 26 | `casos-por-regra/L-OD-00` | 1 | EXEC (CODIGO_CHAVE_OD ⇒ E-13) / PROV (demais campos, A-05) |
| L-OD-01 | "Tipo de conta OD" valida o campo errado | 13 | `casos-por-regra/L-OD-01` | 2 | EXEC |
| L-OD-02 | Tipo de pessoa OD | 13 | `casos-por-regra/L-OD-02` | 2 | EXEC |
| L-OD-03 | Situação de identificação | 3 | `casos-por-regra/L-OD-03` | 1 | EXEC |
| L-PE-00 | Valor de renda numérico | 9 | `casos-por-regra/L-PE-00` | 1 | EXEC |
| L-PE-01 | Obrigatórios da pessoa | 23 | `casos-por-regra/L-PE-01` | 1 | EXEC |
| L-PE-02 | Data de atualização de renda | 11 | `casos-por-regra/L-PE-02` | 1 | EXEC |
| L-PE-03 | CPF/CNPJ do titular | 16 | `casos-por-regra/L-PE-03` | 1 | EXEC |
| L-PE-04 | Tipo de pessoa | 23 | `casos-por-regra/L-PE-04` | 1 | EXEC |
| L-TI-00 | Conta e pessoa existem | 65 | `casos-por-regra/L-TI-00` | 1 | EXEC |
| L-TI-01 | Obrigatórios da titularidade | 17 | `casos-por-regra/L-TI-01` | 1 | EXEC |
| L-TI-02 | Data de início (A-01 com exceção) | 13 | `casos-por-regra/L-TI-02` | 1 | EXEC |
| L-TI-03 | Data de fim (A-01 com exceção) | 14 | `casos-por-regra/L-TI-03` | 1 | EXEC |
| L-TI-04 | Tipo de titular | 1 | `casos-por-regra/L-TI-04` | 1 | EXEC |
| L-TI-05 | Pessoa investigada | 7 | `casos-por-regra/L-TI-05` | 1 | EXEC |
| L-TI-06 | Ordem das datas de relacionamento | 6 | `casos-por-regra/L-TI-06` | 1 | EXEC |
| R-AG-01 | Agência duplicada | 127 | `casos-por-regra/R-AG-01` | 1 | EXEC |
| R-CT-01 | Conta com separador | 1 | `casos-por-regra/R-CT-01` | 1 | EXEC |
| R-CT-02 | Conta duplicada | 1 | `casos-por-regra/R-CT-02` | 4 | EXEC |
| R-CT-03 | Conta sem titulares | 95 | `casos-por-regra/R-CT-03` | 1 | EXEC |
| R-CT-04 | Conta sem pessoa investigada | 5 | `casos-por-regra/R-CT-04` | 2 | EXEC |
| R-CT-05 | Atendimento sem movimentação | 55 | `casos-por-regra/R-CT-05` | 2 | EXEC |
| R-CT-06 | Movimentação deveria ser 3 | 3 | `casos-por-regra/R-CT-06` | 2 | EXEC |
| R-CT-07 | Mais de um titular T | 72 | `casos-por-regra/R-CT-07` | 1 | EXEC |
| R-CT-08 | Movimentação deveria ser 1 ou 2 | 55 | `casos-por-regra/R-CT-08` | 2 | EXEC |
| R-CT-09 | Movimentação 1 sem extrato | 2 | `casos-por-regra/R-CT-09` | 1 | EXEC |
| R-CT-10 | Movimentação 2 com extrato | 46 | `casos-por-regra/R-CT-10` | 1 | EXEC |
| R-EX-01 | Lançamento fora do período de afastamento | 124 | `casos-por-regra/R-EX-01` | 1 | EXEC |
| R-EX-02 | Lançamento fora do período da conta | 6 | `casos-por-regra/R-EX-02` | 1 | EXEC |
| R-EX-03 | Lançamento sem origem/destino | 1 | `casos-por-regra/R-EX-03` | 1 | EXEC |
| R-EX-04 | Extrato em conta não investigada | 1 | `casos-por-regra/R-EX-04` | 1 | EXEC |
| R-EX-05 | Valor do extrato × soma de OD | 83 | `casos-por-regra/R-EX-05` | 1 | EXEC |
| R-OD-01 | Identificação em TED/DOC | 72 | `casos-por-regra/R-OD-01` | 1 | EXEC |
| R-OD-02 | Identificação em transferência no mesmo banco | 73 | `casos-por-regra/R-OD-02` | 1 | EXEC |
| R-OD-03 | Identificação em espécie | 14 | `casos-por-regra/R-OD-03` | 1 | EXEC |
| R-OD-04 | CPF/CNPJ válido em transferências | 188 | `casos-por-regra/R-OD-04` | 1 | EXEC (conteúdo); ordem ND |
| R-OD-E1 | Identificação eleitoral em transferências | 78 | `casos-por-regra/R-OD-E1` | 1 | EXEC |
| R-OD-E2 | Depósito em cheque/dinheiro eleitoral | 77 | `casos-por-regra/R-OD-E2` | 1 | EXEC |
| R-TI-01 | Dados obrigatórios do titular investigado | 81 | `casos-por-regra/R-TI-01` | 1 | EXEC |
| R-TI-02 | Relacionamento anterior à abertura da conta | 5 | `casos-por-regra/R-TI-02` | 1 | EXEC |
| R-TI-03 | Relacionamento posterior ao encerramento | 4 | `casos-por-regra/R-TI-03` | 1 | EXEC |
| R-TI-04 | Investigado do atendimento ausente em TITULARES | 1 | `casos-por-regra/R-TI-04` | 1 | EXEC |
| R-TI-05 | Pessoa de investigado marcada 0 | 32 | `casos-por-regra/R-TI-05` | 1 | EXEC |
| R-TI-06 | PESSOA_INVESTIGADA=1 não cadastrada | 38 | `casos-por-regra/R-TI-06` | 1 | EXEC |
| R-TI-07 | Conta investigada sem titular T | 6 | `casos-por-regra/R-TI-07` | 1 | EXEC |

## 3. Regras sem observação por mensagem neste mapa (32)
| Grupo | IDs | Por quê / onde há evidência |
|---|---|---|
| Algoritmos | A-01…A-04, A-06 | Funções puras: nível 1 (20.103 vetores, 0 divergências) — EXEC |
| Algoritmo | A-05 | PROV: depende do locale; `pt_BR` e `en_US` sem diferença observada |
| Algoritmo | A-07 | Hash: `diferencial_hash.py` (401 arquivos × 3 versões) — EXEC |
| Estruturais sem mensagem própria | E-05b, E-06, E-08, E-10 | Efeitos de fluxo/parse comprovados por nível 1 (E-05b), H3 (E-06), `#CONTEUDO` dos golden (E-08) e caso eleitoral (E-10) |
| Estruturais de UI/servidor | E-01, E-09, E-11, E-12, E-14, E-15 | E-11/E-12/E-14: nível 3 (tela real, 73 cenários) — EXEC. E-01, E-09, E-15: **sem observação** (EST); a tela real não foi dirigida a esses ramos |
| Estrutural | E-05 | EST: CRLF e acentos ISO-8859-1 são exercidos em todos os cenários; LF/CR isolados e UTF-8/BOM não foram testados |
| Carga | L-OD-04 | INALC: inalcançável (E-04 limita NUMERO_BANCO_OD a 3 caracteres) |
| Corretoras | G-01…G-07, G-DEF, R-G109, R-G800, R-G112-1/2/3 | Campanha própria `diferencial_gab.py` (4.500 cenários por modo); G-03…G-07 e R-G* só sob remendo em memória, por causa de G-DEF |

## 4. Lacunas reais que o mapa revelou (e foram fechadas)
O nível 2 anterior **nunca fez o original emitir** R-EX-03, E-02, L-AG-07, a parte E-13 de L-OD-00 e o fail-fast E-07 (efeito por ausência). Foram criados `analise/testes/gerar_lacunas.py` (7 cenários) e o original confirmou todos: R-EX-03 (`Lançamento em EXTRATO sem correspondente em ORIGEM_DESTINO…`), E-02 (rótulo **físico**, linha 0), L-AG-07 (NUMERO_AGENCIA `AB` ⇒ `Erro desconhecido`), L-OD-00 (CODIGO_CHAVE_OD `X` ⇒ `Erro desconhecido`), E-07 (erro de UF em AGENCIAS + CONTAS com colunas faltando ⇒ só o erro de UF, sem E-03).

## 5. Limitações
- "Observada" significa que o original **emitiu a mensagem** em ≥ 1 cenário e coincidiu com a reimplementação; não prova a ausência de falso positivo/negativo em todas as combinações.
- Regras que dependem de ausência (fail-fast, E-06/E-07/E-08) só são provadas por cenários desenhados para isso.
- Os golden promovidos usam a **saída do original** como esperado; as mensagens do modelo Python (`esperado.txt` dos 16 casos originais) coincidem com o original nesses casos.
- GAB, GUI (E-01/E-09/E-15) e dados reais continuam fora desta campanha.

## 6. Como reproduzir
```
mvn -q -f implementacao/validador-independente package            # classes da reimplementação
mkdir -p $OUT/cls $CORPORA && javac -d $OUT/cls -cp implementacao/validador-independente/target/classes analise/testes/diferencial/MapaRegras.java
# corpora em $CORPORA/<nome>/ : golden (analise/testes/casos), cobertura (fuzz_cobertura.py), dirigido (fuzz_dirigido.py 800 5),
# cruz1..3 (fuzz_cruzado.py 700 <semente>), lacunas (gerar_lacunas.py)
OUT=$OUT CORPORA=$CORPORA analise/testes/diferencial/rodar_corpus.sh <nome>          # para cada corpus
python3 analise/testes/diferencial/cobertura_por_regra.py $OUT $CORPORA analise/testes/casos-por-regra mapa.json
```
Requer o instalador original extraído em `original/extraido/` (ver `analise/01-inventario.md`) e `bwrap`.
