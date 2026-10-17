# 07 — Avaliação de implementação independente (Fase 6)

**STATUS: IMPLEMENTADO (versão 0.1.0) após sua autorização** — ver `implementacao/validador-independente/` e `implementacao/README.md`. Escolhida a opção **C (compat + estrito por flag)**. Este documento descreve a proposta original; o que foi de fato construído está resumido em §8.

## 1. Viabilidade — resumo
**Alta** para o fluxo bancário: o conjunto de regras é pequeno, determinístico e legível (≈ 84 itens catalogados em 04: 11 estruturais/de fluxo, 6 algoritmos de apoio, 38 de carga por linha, 29 de conteúdo). Não há criptografia/ofuscação no caminho de validação; o JAR não está ofuscado (nomes legíveis, fonte decompila sem falhas).
**Média** para paridade total: depende de (a) confirmação por execução diferencial (D1), (b) dados externos do receptor (D2), (c) fluxo GAB não catalogado (D5), (d) sentido de negócio dos limiares (D3). **Não verificado:** termos de uso (D11).
Estimativa de esforço (baixa confiança, 1 engenheiro): núcleo bancário + testes 2-4 semanas; paridade validada por diferencial +1-2; fluxo GAB +1-2.

## 2. Decisão-chave: o que o novo validador promete?
| Opção | Descrição | Prós | Contras |
|---|---|---|---|
| **A. Paridade bug-a-bug** | Reproduz E-06, E-05b, L-OD-01, rótulos errados etc. | Resultado idêntico ao oficial; útil p/ pré-checagem | Replica defeitos; mensagens menos úteis |
| **B. Modo estrito** | Corrige as peculiaridades (todos os erros por linha; 1º campo vazio detectado; etc.) | Relatório melhor | Pode **aprovar/reprovar diferente** do oficial |
| **C. Os dois por flag** (recomendado) | `--modo=compat|estrito` | Mede-se o desvio; migração gradual | Mais código/testes |

## 3. Arquitetura proposta (Java 21+; você já domina Java; o original roda em Java 25)
```
validador-core        (biblioteca pura, sem I/O de UI nem rede)
 ├─ model       Violation{ruleId,file,line,message,severity}, FileKind, Profile{BANCARIO, ELEITORAL}
 ├─ schema      FileSchema/FieldSpec(name,maxLen,type) declarativo (YAML ou código) — fonte única p/ colunas e tamanhos
 ├─ parse       LineSource(ISO-8859-1, CR/LF/CRLF) → TabSplitter{compat|estrito} | FixedWidthSplitter(GAB)
 ├─ rules.row   RowRule<T>: funções puras (obrigatórios, datas, domínios, CPF/CNPJ, CNAB) + registro por arquivo
 ├─ rules.cross CrossRule: sobre índices em memória (ver §4), 1 classe por regra R-*, mesma ordem de ValidarAtendimento
 ├─ util        Cpf/Cnpj (alfanumérico), DateRules(verificaData), Cnab
 ├─ engine      Pipeline: existência → vazios → carga(fail-fast) → conteúdo(fail-fast por arquivo); ErrorPolicy{compat|estrito}
 └─ report      Sinks: texto idêntico ao original ("Erro no arquivo: …"), JSON, CSV
validador-cli         comando: `validador --caso 001-TST-000001-00 --pasta ./dados --modo compat --saida json`
validador-testes      suíte de caracterização (golden dos casos/ + esperados) + harness diferencial (opcional)
```
Separação **parsing × validação**: o parse produz registros tipados + erros estruturais (E-03/E-04); as regras recebem registros e nunca tocam arquivo. Regras configuráveis: limiares e datas (hoje *hard-coded* em SQL: 2006-01-01, 1.000.000, 2013-07-01, 5.000.000, 2012-07-01, 10.000.000, 10.000) vão para `regras.yaml` versionado com a vigência — o original exige novo release a cada mudança.

## 4. Estratégia para regras de conteúdo (R-*)
| Abordagem | Quando |
|---|---|
| **Índices em memória** (HashMap por chave normalizada banco/agência/conta; agregados por extrato) — **recomendado** | Volumes até dezenas de milhões de linhas com EXTRATO/OD em *streaming* (só guarda mapa extrato→soma OD e conjunto de contas com lançamento) |
| **HSQLDB/H2 embutido** executando as mesmas SQL | Máxima paridade de semântica NULL/NOT IN com o original; custo de memória/tempo (o original usa `CACHED TABLE`, batch de 500, commit a cada 250.000 linhas) |
Sugestão: implementar em memória e validar contra uma execução SQL de referência (H2/HSQLDB) nos casos sintéticos, para provar que NULLs foram tratados igual.

## 5. Testabilidade, observabilidade, erros, desempenho, manutenção
- **Testes:** `testes/casos/` + `esperado.json` viram *golden files*; property-based para CPF/CNPJ (já há diferencial vs algoritmo oficial); teste diferencial opcional contra o original (05).
- **Observabilidade:** contadores por arquivo/regra, linhas/s, tempo por regra (o original já mede tempo por regra em EXTRATO/ORIGEM_DESTINO via `ValidarAtendimento.time`), log estruturado; relatório com ID de regra estável (R-AG-01 …) — o original só tem texto.
- **Erros:** distinguir *violação de regra* (dado) de *falha de infraestrutura* (IO). No original ambos acabam em "Erro desconhecido" (E-06/L-PE-03).
- **Desempenho:** streaming em `BufferedReader`; ≈ O(n) tempo e O(chaves) memória; paralelizar regras R-* independentes (todas só leem os índices).
- **Manutenibilidade:** 1 regra = 1 classe/teste; catálogo 04 como especificação viva; ADR para cada decisão compat×estrito.

## 6. Riscos e limites
1. Um validador independente **não substitui** o aceite oficial (o servidor/receptor pode validar mais; D2, D10).
2. **Deriva de regras**: o oficial evolui (6.2.6 hoje; limiares em código). Exige processo de reengenharia a cada release (a cadeia de ferramentas desta investigação é reutilizável: `analise/ferramentas/`).
3. **Legal/licenciamento (D11) não verificado.** Antes de publicar/distribuir qualquer código derivado, consulte os termos do MPF/SIMBA e orientação jurídica; este material é para estudo.
4. Sem documentação oficial do layout, a interpretação de negócio (D3) fica por conta de quem especifica.
5. Dados reais: o novo validador processará dados sigilosos; exige tratamento (sem telemetria, logs sem PII) — o original grava mensagens com CPF/CNPJ em log (`ErrorReportCmd.report` → `log.info`).

## 7. Próximos passos recomendados (sem implementar ainda)
1. Autorizar o **nível 1** da execução diferencial (funções puras) para elevar A-01…A-06 de "estático" a "confirmado".
2. Fechar D2, D5, D6 por leitura estática (baixo custo).
3. Decidir A/B/C (§2) e, só então, pedir a implementação.

## 8. O que foi construído (v0.2.0)
- Projeto Maven `implementacao/validador-independente` (Java 21+, sem dependências em produção; JUnit 5 nos testes). Pacotes `estudo.simba.validador` (bancário) e `estudo.simba.validador.gab` (corretoras).
- Núcleo bancário: `Loader` (L-*), `ContentRules` (R-*, índices em memória), `Pipeline` (E-01/E-02/E-07/E-08), `CpfCnpj` (alfanumérico), `Cnab`, `DateRules`, `TabSplitter`, `Reports` (texto idêntico ao original, JSON, CSV), `Cli`.
- **Corretoras (GAB):** `GabLayouts` (v1/v2), `GabLoader`, `GabRules`, `GabPipeline`. Modo COMPAT reproduz o **defeito do original** (G-DEF: toda linha com a largura certa vira "Erro desconhecido"); ESTRITO aplica a lógica pretendida (obrigatórios, datas, numéricos, domínios, CPF/CNPJ, regras de conteúdo). Flag `--defeito-gab true|false` permite combinar E-06 com a lógica pretendida (usado no diferencial).
- **Limiares configuráveis:** `Limiares` + `implementacao/limiares-6.2.6.properties` (datas de corte, valores mínimos, códigos CNAB por regra, faixas de espécie). O arquivo versionado é testado como idêntico ao padrão interno. `--limiares arquivo` na CLI.
- Modos: **COMPAT** reproduz E-05b, E-06, L-AG-04 (dois erros), L-PE-02 (rótulo EXTRATO), L-PE-03, L-OD-01, NPE de R-EX-02, `isNumber` leniente, PK/FK do HSQLDB. **ESTRITO** corrige.
- **Verificação:** 31 testes JUnit; contra o aplicativo original, em sandbox sem rede: nível 1 (20.103 vetores), hash (401 arquivos × 3 versões), nível 2 bancário (2.316 + 18 dirigidos pela cobertura, incl. volume de 250k linhas), GAB (4.500 × 2 modos) e **nível 3 (tela real, 73 cenários)**: **0 divergências reais** após corrigir as 2 que a cobertura achou. Cobertura do original: 82,9% das linhas de validação.
- **Desempenho:** `Db` usa índices (agências, contas, PK/FK) em vez de varreduras lineares; arquivo de 250.005 linhas processado sem problema.
- **Não coberto:** PDF/relatório, transmissão, cadastro de atendimento (entrada via parâmetros), locale `en_US`, unidade dos valores (D3), L-OD-04 (inalcançável), dados reais.
