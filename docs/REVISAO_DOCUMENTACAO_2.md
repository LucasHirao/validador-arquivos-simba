# Revisão da Documentação, 2ª passada — Validador SIMBA 6.2.6

**Revisor:** revisor de documentação, independente dos autores. **Data:** 2026-10-17.
**Objeto:** `docs/DOCUMENTACAO_FUNCIONAL.md`, `docs/DOCUMENTACAO_TECNICA.md` e `docs/CATALOGO_REGRAS.md` v1.1, depois da correção dos problemas P-01…P-32 de `docs/REVISAO_DOCUMENTACAO.md`.
**Evidência primária:** código decompilado (`analise/decompilado/**`, `analise/decompilado-libs/spea-client/**`), lido sem alteração; `analise/05-testes.md`; `analise/testes/casos/*`; `implementacao/validador-independente/src/main/java/.../Schemas.java`.
**Restrições respeitadas:** nenhum documento foi corrigido e nenhuma regra foi criada. Nenhum binário do original foi executado e não houve acesso à rede. O único script executado foi um comparador de IDs e de confiança, escrito no scratchpad.
**Limite desta passada:** `docs/` não é versionado no git (`?? docs/`), então não há diff contra a versão anterior. O "texto antigo" foi reconstituído pelos trechos citados na 1ª revisão.

---

## 1. Resumo

| Item | Resultado |
|---|---|
| P-01…P-32 | **29 RESOLVIDOS · 3 PARCIAIS (P-10, P-28, P-30) · 0 NÃO RESOLVIDOS** |
| Cobertura de IDs | Os 103 IDs do catálogo estão nos dois documentos, sem ID estranho. Funcional: todos com linha em §5 e §8; o menos citado é R-CT-03 (3 menções). Técnico: todos na matriz §14; o menos citado é L-AG-03 (1 menção) |
| Confiança × catálogo | Igual nos 103 IDs, nos dois documentos, conferida por script. As diferenças são de grafia, mais uma omissão do qualificador PROV em L-OD-00 (funcional §8, Q-15). Nenhum documento atribui confiança acima da do catálogo |
| Estatística do catálogo §9 | Confere: EXEC 37 (35 + "EXEC (conteúdo)" + "EXEC (dado o receptor)"), sob remendo 10, EST 53, PROV 2, INALC 1, total 103 |
| Prefixos `D:`/`DX:` | `D:../` não aparece mais no catálogo. `DX:` está definido no catálogo §1.1 e no técnico §0.1 e é usado em K-01, K-02, K-08, E-01, E-08, E-11, E-14. No técnico, `D:../` só aparece de forma histórica (§0.1, RO-02) |
| Novos problemas (Q-nn) | **15**: CRÍTICO 0 · ALTO 1 · MÉDIO 4 · BAIXO 10 |
| Por responsável | funcional 6 · técnico 8 · catálogo 3 (Q-04 e Q-08 têm dois responsáveis) |
| Amostragem | 20 afirmações novas ou alteradas conferidas no código: 15 conferem, 5 com problema (§5) |

Todos os problemas CRÍTICOS da 1ª passada (E-07 em log, rótulo de E-04, `split` de campos finais, coluna dos limiares de OD, regra de rótulo do catálogo) estão resolvidos e coerentes entre os três documentos. O achado mais relevante desta passada é a correção de P-30. Ela trouxe um nome de campo errado para a mensagem `Campo <NOME> não é um número.` de ORIGEM_DESTINO (Q-01): o código usa `VALOR_OPERACAO` e `NUMERO_AGENCIA`, não `VALOR_TRANSACAO` e `NUMERO_AGENCIA_OD`.

---

## 2. Situação de P-01…P-32

Legenda: **R** = RESOLVIDO; **P** = PARCIAL; **N** = NÃO RESOLVIDO.

| P | Resp. | Onde foi verificado | Situação | Observação / evidência |
|---|---|---|---|---|
| P-01 | func. | §4.2, §5.1, §7.1 e §8 (E-07) | **R** | O texto diz "só registrado em log" e "Nada novo no relatório". Confere com `D:carga/Carregar.java:82-83` (`catch (NegocioException e) { log.error(...) }`) |
| P-02 | cat. | E-07 Resultado | **R** | Idem |
| P-03 | func. | §0.2, §5.1, §7.1 e §8 (E-04 = L) | **R** | O cenário espera `Erro no arquivo: AGENCIAS…`. Confere com `AbstractCarregarArquivoCmd.java:296` (`getNomeArquivo()`) |
| P-04 | cat. | §1.3, tabela por mecanismo | **R** | Confere com as linhas 204-205 (`NegocioException` → `fileName`) e 403/416 (`getAbsolutePath`). Lacuna menor: G-01 não está na tabela (Q-08) |
| P-05 | téc. | §6.2 | **R** | O texto diz "campos vazios finais são **preservados**". Resta uma frase imprecisa (Q-10) |
| P-06 | func. | §3.5 (col. 10), §3.6 (col. 3), §5.8 | **R** | Os limiares estão em `EXTRATO.VALOR_LANCAMENTO` e R-OD-E1 não tem limiar. Confere com o SQL de `ValidarIdentificacaoTedDocRule`, `…TransferenciaMesmoBancoRule`, `…TransacaoEmEspecieRule`, `…TedDocTransfEleitoralRule` e `ValidarDepositoDinheiroChequeEleitoralRule` |
| P-07 | func. | §8 (coluna Rótulo) | **R** | L-CT-00, L-PE-00, L-TI-00, L-TI-02, L-TI-03, L-EX-00 e E-02 estão com F; L-TI-06 está com "F (parse) / L (início > fim)". Confere com `CarregarTabelaTitularidadeCmd.java:219-245` |
| P-08 | func. | §1 passo 4, §9 | **R** | "HSQLDB em arquivo do caso (`jdbc:hsqldb:file:`)" |
| P-09 | cat. | E-14 | **R** | Hash e cópia antes de E-02; contagem em `atualizaQuantidadeLinhaTabela`. Confere com `AbstractCarregarArquivoCmd.java:143` (ao fim do arquivo) |
| P-10 | func. | §1, §5.1, §7.1, §8 | **P** | Corrigido nessas seções, mas o diagrama da §4.1 ainda diz `E-14 (hash, linhas, cópia) → E-02` (Q-02) |
| P-11 | cat. | §5.0 (fonte), LAC-05 | **R** | `getCamposArquivo()`; os nomes exatos estão listados |
| P-12 | func. | §3.1, §3.6 l.18, §10 LAC-05, §11 item 1 | **R** | `OBSERVACÃO` com til. Confere com `CarregarArquivoOrigemDestinoCmd.java:45` e `N:Schemas.java:33` |
| P-13 | cat. | §1.1 (`DX:`), K-01, K-02, K-08, E-01, E-08, E-11, E-14 | **R** | Não resta nenhum `D:../` no catálogo |
| P-14 | func. | §5.3 L-AG-04, §6 D-03, §7.3 | **R** | Vazio dá 2 erros; não numérico dá 1; L-AG-05 e L-AG-06 não rodam. Confere com `CarregarArquivoAgenciasCmd.java:107-110,150`. O técnico e o catálogo mantêm a redação antiga (Q-04) |
| P-15 | func. | §6 D-16 | **R** | EST, com a nota "dedução estática" |
| P-16 | func. | §7.1 E-02 (2º cenário) | **R** | Com MOVIMENTACAO=2 e titular investigado, nenhuma de R-CT-03…10, R-TI-* ou R-EX-* dispara sem EXTRATO/OD |
| P-17 | téc. | §2 | **R** | 34 JARs (`ls original/extraido/app/lib \| grep -c .jar` = 34) |
| P-18 | téc. | §9 | **R** | Regra por mecanismo, igual à do catálogo §1.3. A RO-03(a) contradiz (Q-03) |
| P-19 | téc. | §4.2 | **R** | Existência → E-09 → `getComputador` → `GerarHashArquivosGabCmd.gerar` → `copiarGab` → G-01. Confere com `frmSelecionaDiretorioGab.java:748-781` e o campo da linha 98 |
| P-20 | cat. | R-OD-01/02/03/E2 | **R** | `EXTRATO.VALOR_LANCAMENTO` / `EXTRATO.DATA_LANCAMENTO` |
| P-21 | cat. | §8.2 | **R** | 6 sobrecargas. O técnico §11 ainda diz que o catálogo cita "7" (Q-13) |
| P-22 | cat. | E-05b | **R** | "primeiro campo de comprimento zero"; finais preservados |
| P-23 | cat. | L-CT-01 | **R** | Ramo inalcançável. Confere com `AgenciaLocalizerCmd.java:55-56` (`Integer.valueOf`) chamado em `CarregarArquivoContasCmd.convert:80` |
| P-24 | cat. | L-TI-00, L-TI-01 | **R** | 6ª mensagem (`CarregarTabelaTitularidadeCmd.java:126`) e TIPO_TITULAR omitido. O funcional §5.5 não propagou a 6ª mensagem (Q-06) |
| P-25 | cat. | E-09 | **R** | O texto confere com `frmSelecionaDiretorio.java:838-845` e o diálogo com `ViewUtil.showWarnDialog`. A evidência não aponta a tela (Q-07) |
| P-26 | téc. | §3.3, §2 | **R** | `carga` 30 e `certificados` 9 (conferidos com `ls`) |
| P-27 | téc. | RO-03(b) | **R** | Só `NUMERO_DOCUMENTO_TRANSACAO` e `OBSERVACÃO` divergem em OD |
| P-28 | téc. | §8 | **P** | "Vice-versa" foi retirado, mas a nova frase "L-TI-06 só falha com L-TI-02 aprovada quando a data de início está vazia" vale só para o *parse* de L-TI-06 (Q-09) |
| P-29 | téc. | §13.1 GABD | **R** | "por modo, 3.000 + 1.500 = 4.500". Confere com `analise/05-testes.md` l.10 e l.12 |
| P-30 | func. | §5.7 L-OD-00, §7.7 | **P** | As duas saídas estão listadas, mas os nomes de campo da mensagem estão errados (Q-01) |
| P-31 | func. | §6 D-12, §11 item 2 | **R** | D-12 cita a omissão de TIPO_TITULAR; o item 2 fica restrito a E-09 |
| P-32 | func. | §1, §5.1 E-11, §7.1 E-11 | **R** | O `finally` roda após o `return null` de E-01 e E-09 (`frmSelecionaDiretorio.java:829-845` e `:891-905`). O catálogo E-11 também foi atualizado |

---

## 3. Contradições entre funcional, técnico e catálogo (pontos pedidos)

| Ponto | Catálogo v1.1 | Funcional | Técnico | Código | Situação |
|---|---|---|---|---|---|
| Rótulos (§1.3) | por mecanismo | §0.2 = catálogo; §8 coerente | §9 = catálogo; **RO-03(a) diverge** | `AbstractCarregarArquivoCmd` 204-216, 296, 403/416 | Q-03 (técnico). G-01 sem rótulo nos dois (Q-08) |
| E-04 | lógico | lógico | lógico (§6.3) | `getNomeArquivo()` | ✔ |
| E-05b | só o 1º campo de comprimento zero | igual | igual (§6.2), com uma frase residual | `Util.tabSeparatedStringToArray` | ✔ (Q-10 BAIXO) |
| E-07 | só log | só log | só log (§4.1, §14.1) | `Carregar.java:82-83` | ✔ |
| E-09 | diálogo com texto | diálogo com texto | `checkVersaoSistemaBanco` | `frmSelecionaDiretorio.java:837-846` | ✔ (Q-07: evidência) |
| E-11 | `finally`, inclusive após E-01/E-09 | igual | §4.1 em texto ✔; diagrama mermaid omite | `frmSelecionaDiretorio.java:891-905` | Q-14 |
| E-14 | hash/cópia antes de E-02; linhas na carga | §1, §5.1, §7.1, §8 ✔; **§4.1 ✖** | §7.1, §14.1 ✔ | linha 143 | Q-02 |
| Limiares R-OD | `EXTRATO.VALOR_LANCAMENTO`; E1 sem limiar | igual | remete ao catálogo e às `.properties` | SQL das 5 regras | ✔. Os cenários §7.8 estão incompletos (Q-05) |
| L-AG-04 | "vazio/não numérico ⇒ segundo erro" | vazio 2, não numérico 1 | "vazio/não numérico ⇒ 2º erro" | `CarregarArquivoAgenciasCmd:150` | Q-04 |

---

## 4. Problemas novos por severidade

**Critério** (o mesmo da 1ª passada): CRÍTICO = informação incorreta ou contraditória que leva a resultado esperado errado em escala; ALTO = afirmação relevante contrariada pelo código, com alcance limitado; MÉDIO = descrição incompleta ou contradição de baixo impacto; BAIXO = clareza, contagem ou organização.

### 4.1 CRÍTICO (0)
Nenhum.

### 4.2 ALTO (1)

**Q-01 · ALTO · funcional · §5.7 (L-OD-00) e §7.7 (2º cenário de L-OD-00). Correção de P-30**
- **Trecho:**
  - §5.7: "Demais numéricos (CODIGO_CHAVE_EXTRATO, VALOR_TRANSACAO, NUMERO_AGENCIA_OD, SITUACAO_IDENTIFICACAO) … `Campo <NOME> não é um número.`".
  - §7.7: "VALOR_TRANSACAO `ABC` | `Campo VALOR_TRANSACAO não é um número.`".
- **Evidência contrária:** `analise/decompilado/br/mp/mpf/spea/simba/validador/carga/CarregarArquivoOrigemDestinoCmd.java:81-99`. O nome que entra na mensagem é o 3º argumento de `getLong`/`getInt`: `"CODIGO_CHAVE_EXTRATO"`, **`"VALOR_OPERACAO"`**, **`"NUMERO_AGENCIA"`** e `"SITUACAO_IDENTIFICACAO"`. O texto esperado é `Campo VALOR_OPERACAO não é um número.`, não `VALOR_TRANSACAO`, que é o nome do layout (`getCamposArquivo`, l.29). Além disso, NUMERO_AGENCIA_OD só é convertido quando não está vazio (l.85).
- **Impacto:** um teste de texto exato derivado do §7.7 falharia contra o original. O "prev." do cenário cobre a confiança (PROV, A-05), não um nome que o código determina.
- **Correção sugerida:** citar os nomes da mensagem (`VALOR_OPERACAO`, `NUMERO_AGENCIA`) e anotar que diferem dos nomes do layout.

### 4.3 MÉDIO (4)

**Q-02 · MÉDIO · funcional · §4.1 (diagrama do fluxo bancário). P-10 parcial**
- **Trecho:** `pasta → E-01 (existem?) → E-09 (versão) → E-14 (hash, linhas, cópia) → E-02 (não vazios)`.
- **Evidência contrária:** a contagem é gravada ao fim de cada arquivo carregado (`AbstractCarregarArquivoCmd.java:143` → `atualizaQuantidadeLinhaTabela`). Antes de E-02 só ocorrem o hash e a cópia (`frmSelecionaDiretorio.java:850-856`). O texto contradiz o próprio §1 passo 2, §5.1 E-14 e §7.1, e também o catálogo §2.2.
- **Correção sugerida:** `E-14 (hash, cópia)` antes de E-02 e "linhas" dentro de CARGA.

**Q-03 · MÉDIO · técnico · §16 RO-03(a) (marcado "[TRATADO]")**
- **Trecho:** "só E-03, G-02, E-13 e `getInt/getLong` usam o físico".
- **Evidência contrária:**
  - `AbstractCarregarArquivoCmd.java:204-205`: toda `NegocioException` é reportada com `fileName`, ou seja, físico.
  - `ControladorValidador.java:152-160`: E-02 usa `getAbsolutePath()`.
  - O próprio técnico §9 e o catálogo §1.3 dizem o contrário.
- **Impacto:** a regra de rótulo foi o tema central da 1ª passada (P-04, P-07, P-18). A linha diz "tratado", mas mantém a afirmação refutada.
- **Correção sugerida:** reescrever (a) remetendo ao §9, ou riscar o texto antigo.

**Q-04 · MÉDIO · técnico §7.2 (linha de `CarregarArquivoAgenciasCmd`) · catálogo L-AG-04 (redação)**
- **Trecho:**
  - Técnico: "`validarNumeroBanco` faz `Integer.valueOf(banco)` (vazio/não numérico ⇒ exceção ⇒ 2º erro, L-AG-04)".
  - Catálogo: "NUMERO_BANCO vazio/não numérico ⇒ exceção ⇒ **segundo** erro E-13 (dois erros no total quando vazio)".
- **Evidência contrária:** `CarregarArquivoAgenciasCmd.java:150`. Com valor preenchido não numérico (ex.: `AB1`), L-AG-01 não dispara e o E-13 é o **único** erro. É o que diz o funcional corrigido (P-14, §5.3 e §7.3). O técnico contradiz o funcional e a redação do catálogo é ambígua.
- **Correção sugerida:**
  - Técnico: "vazio ⇒ 2 erros (L-AG-01 + E-13); não numérico ⇒ 1 erro (E-13); L-AG-05 e L-AG-06 não rodam".
  - Catálogo: trocar "segundo erro" por "erro E-13 (o segundo quando vazio)".

**Q-05 · MÉDIO · funcional · §7.8 (cenários R-OD-02, R-OD-03, R-OD-04, R-OD-E1)**
- **Trecho:**
  - "Golden 14 com tipo 117 (e 213)".
  - "Lançamento tipo 123 … (ajustar também o OD …)".
  - "Tipo 120, data 15032020 …".
  - "Caso `001-MPE`, lançamento tipo 117 com EXTRATO.VALOR_LANCAMENTO 100, OD sem identificação".
- **Evidência:**
  - A base (§0.3) tem NATUREZA_LANCAMENTO `C` e OD com VALOR 50000.
  - Pelo CNAB (`D:rules/CnabRule`; catálogo A-04), 117, 120 e 123 são válidos só com `D`, e 213 só com `C`. O golden 14 usa `D` (`analise/testes/casos/14-ted-sem-identificacao/*_EXTRATO.txt`).
  - Sem trocar a natureza, a linha falha em L-EX-06 na carga e nenhuma R-* roda (E-08).
  - No cenário R-OD-E1, VALOR_LANCAMENTO 100 contra OD 50000 dispara R-EX-05 em EXTRATO, e o laço de conteúdo para antes de ORIGEM_DESTINO.
  - Os resultados previstos não se realizariam.
- **Correção sugerida:** declarar a natureza (`D` para 117, 120 e 123; `C` para 213) e igualar a soma do OD ao VALOR_LANCAMENTO em todos esses cenários.

### 4.4 BAIXO (10)

**Q-06 · BAIXO · funcional · §5.5 (L-TI-00) e §11 ("Pendências conhecidas")**
- **Trecho:** §5.5 lista 5 mensagens. O §11 afirma que "a §5.5 e D-12 já refletem a observação" sobre `Pessoa não localizada. Linha: <n>`.
- **Evidência:** a 6ª mensagem (`CarregarTabelaTitularidadeCmd.java:126`) está no catálogo L-TI-00, mas não na §5.5. A afirmação do §11 é falsa.
- **Correção sugerida:** acrescentar a mensagem à §5.5 (rótulo físico).

**Q-07 · BAIXO · catálogo · E-09 (coluna Evidências)**
- **Evidência:** o texto incorporado vem de `DX:telas/frmSelecionaDiretorio.java:838-845` (e `frmSelecionaDiretorioGab.java:760-767`), mas a evidência cita só `D:ControladorValidador.checkVersaoSistemaBanco`, que apenas compara versões.
- **Correção sugerida:** acrescentar `DX:telas/frmSelecionaDiretorio` (montagem do texto e `ViewUtil.showWarnDialog`).

**Q-08 · BAIXO · catálogo §1.3 · funcional §8 (G-01)**
- **Evidência:** `ControladorValidador.java:134-145` reporta G-01 com `getAbsolutePath()`, portanto com rótulo **físico** (como E-02). O catálogo §1.3 não inclui G-01 na tabela por mecanismo, e o funcional §8 marca G-01 com "—". O caso é análogo ao de E-02 em P-07.
- **Correção sugerida:** incluir G-01 na linha "físico" do §1.3 e marcar F no funcional §8.

**Q-09 · BAIXO · técnico · §8 ("Datas leniente × estrita"). P-28 parcial**
- **Trecho:** "L-TI-06 só falha com L-TI-02 aprovada quando a data de início está vazia".
- **Evidência:** `CarregarTabelaTitularidadeCmd.java:234-245`. O ramo "início posterior ao fim" de L-TI-06 falha com datas válidas, e portanto com L-TI-02 aprovada (cenário do funcional §7.5). A frase só vale para as mensagens de *parse*. A redação veio da sugestão da 1ª revisão, que era imprecisa.
- **Correção sugerida:** "a parte de *parse* de L-TI-06 só falha …".

**Q-10 · BAIXO · técnico · §6.2 e §12.3**
- **Trecho:** §6.2: "um **primeiro campo vazio é descartado**" (a mesma frase depois diz "só o primeiro campo de comprimento zero"). §12.3: "1º campo vazio (E-05b) | descartado".
- **Evidência:** é a imprecisão de P-22. Um primeiro campo `" "` é mantido (goldens `02` × `04`).
- **Correção sugerida:** usar "de comprimento zero" nas duas ocorrências.

**Q-11 · BAIXO · técnico · cabeçalho (l.3)**
- **Trecho:** "versão 1.0 … a partir do catálogo `docs/CATALOGO_REGRAS.md` v1.0".
- **Evidência:** o documento remete ao catálogo v1.1 (§0.1, §9, §14.1, §16) e incorpora P-05…P-29.
- **Correção sugerida:** atualizar a versão e o catálogo de referência, como fez o funcional.

**Q-12 · BAIXO · técnico · §16, linhas RO-01…RO-04**
- **Evidência:** o cabeçalho tem 5 colunas (`# | Tipo | Descrição | Evidência | Sugestão`), mas essas linhas têm uma 6ª célula com "**[TRATADO]**". Em Markdown (GFM), as células excedentes são descartadas, e a marcação de tratamento não aparece no documento renderizado.
- **Correção sugerida:** mover a marcação para dentro da coluna "Sugestão" ou criar a coluna "Situação".

**Q-13 · BAIXO · técnico · §11 (1ª linha)**
- **Trecho:** "(catálogo/`A:03` citam "7"; ver §16 RO-04)".
- **Evidência:** o catálogo v1.1 §8.2 já diz 6.
- **Correção sugerida:** "`A:03` cita 7; catálogo v1.1 corrigido".

**Q-14 · BAIXO · técnico · §4.1 (diagrama mermaid)**
- **Trecho:** `B -- ausente --> Z[mensagem na tela e fim]` e `C -- diferente --> W[aviso modal e fim]`.
- **Evidência:** o `return null` está dentro do `try`, então o `finally` roda: apaga as tabelas e habilita "Continuar" (`frmSelecionaDiretorio.java:829-831`, `845`, `891-905`). O próprio catálogo E-11 v1.1 registra isso. O diagrama não liga Z e W ao nó `I` (`finally`).
- **Correção sugerida:** ligar Z e W a I.

**Q-15 · BAIXO · funcional · §8 (L-OD-00, coluna Conf.)**
- **Trecho:** "EST".
- **Evidência:** o catálogo diz "EST / A-05 PROV", e o próprio funcional §5.7 diz "EST (A-05 PROV)". Na matriz falta o qualificador, o que pode sugerir confiança maior para os numéricos via `getLong`/`getInt`.
- **Correção sugerida:** "EST / A-05 PROV".

---

## 5. Amostragem de afirmações novas ou alteradas (20)

| # | Afirmação (doc · local) | Verificação no código | Veredito |
|---|---|---|---|
| 1 | Cat. E-07 / Func. §4.2: mensagem de E-07 só em log | `Carregar.java:82-83` | ✔ |
| 2 | Cat. §1.3 / Téc. §9: `NegocioException` → físico | `AbstractCarregarArquivoCmd.java:204-205` | ✔ |
| 3 | Cat. §1.3: `getInt`/`getLong` → físico | `:403`, `:416` (`file.getAbsolutePath()`) | ✔ |
| 4 | Func. §8: E-02 = F | `ControladorValidador.java:152-160` | ✔ |
| 5 | Func. §5.5: L-TI-06 "F (parse) / L (início > fim)" | `CarregarTabelaTitularidadeCmd.java:221,231` (throw) e `:236-240` (`report` com `NomeArquivo.TITULARES`) | ✔ |
| 6 | Cat. L-TI-00: 6ª mensagem "Pessoa não localizada" | `CarregarTabelaTitularidadeCmd.java:126` | ✔ (funcional não propagou: Q-06) |
| 7 | Cat. L-TI-01 / Func. D-12: mensagem omite TIPO_TITULAR, que é testado | `:156-163` | ✔ |
| 8 | Cat. L-CT-01: ramo NUMERO_BANCO inalcançável | `CarregarArquivoContasCmd.java:80`; `AgenciaLocalizerCmd.java:55-56` | ✔ |
| 9 | Cat. E-09 / Func. §5.1: texto e diálogo, fora do relatório | `frmSelecionaDiretorio.java:837-846` (`ViewUtil.showWarnDialog`, sem `report`) | ✔ |
| 10 | Cat. E-11 / Func. §1: "Continuar" habilitado após E-01/E-09 | `:829-831`, `:845`, `:900-902` | ✔ |
| 11 | Cat. E-14 / Téc. §14.1: contagem ao fim de cada arquivo carregado | `AbstractCarregarArquivoCmd.java:143` | ✔ |
| 12 | Cat. R-OD-01/02/03/E2 e Func. §5.8: limiares em `EXTRATO.VALOR_LANCAMENTO` | SQL das 4 regras | ✔ |
| 13 | Cat./Func.: R-OD-E1 sem limiar de data nem de valor | SQL de `ValidarIdentificacaoTedDocTransfEleitoralRule` | ✔ |
| 14 | Func. §5.7 / §7.7: `Campo VALOR_TRANSACAO não é um número.` | `CarregarArquivoOrigemDestinoCmd.java:82` (`"VALOR_OPERACAO"`), `:86` (`"NUMERO_AGENCIA"`) | ✖ Q-01 |
| 15 | Func. §3.6 / Téc. §6.3 / Cat. §5.0: `OBSERVACÃO` com til | `CarregarArquivoOrigemDestinoCmd.java:45`; `N:Schemas.java:33` | ✔ |
| 16 | Téc. §4.2: fluxo GAB com E-09, E-15, E-14 e `copiarGab` antes de G-01 | `frmSelecionaDiretorioGab.java:748-781`; campo `GerarHashArquivosGabCmd` (l.98) | ✔ |
| 17 | Téc. §2/§3.3: 34 JARs; `carga` 30; `certificados` 9 | `ls` | ✔ |
| 18 | Func. §5.3: L-AG-04 não numérico = 1 erro; L-AG-05/06 não rodam | `CarregarArquivoAgenciasCmd.java:103-110,150` | ✔ (técnico e catálogo divergem: Q-04) |
| 19 | Téc. §8: "L-TI-06 só falha com L-TI-02 aprovada quando o início está vazio" | `CarregarTabelaTitularidadeCmd.java:234-245` | ✖ parcial, Q-09 |
| 20 | Téc. §16 RO-03(a): "só E-03, G-02, E-13 e getInt/getLong usam o físico" | `:204-205`; `ControladorValidador:152-160` | ✖ Q-03 |

Fora da amostra, mas também conferidos:
- Func. §7.1, cenário E-02 com MOVIMENTACAO=2 (P-16): ✔ pela lógica de R-CT-03…10.
- Téc. §13.1 GABD (P-29): ✔ contra `analise/05-testes.md`.
- Cenários §7.8 de OD: ✖ (Q-05).
- Rótulo de G-01: ✖ omisso (Q-08).

Resultado: 15 conferem, 5 com problema (14, 18 com ressalva, 19, 20 e o 6 com ressalva). Problemas registrados: Q-01, Q-03, Q-04, Q-06 e Q-09.

---

## 6. Verificações de regressão (resíduos procurados por `grep`)

| Padrão buscado | Resultado |
|---|---|
| `em memória` como descrição do banco do original | Não resta. Só aparece para "remendo em memória", a lista de órgãos injetada e a reimplementação |
| `D:../` | Não aparece no catálogo. No técnico só aparece de forma histórica (§0.1, RO-02) |
| `enums/Campos` / "enums Campos* bancários" | Não resta afirmação de enum bancário. As menções restantes dizem que esses enums não existem ou se referem a GAB |
| `40 JARs`, `31 arquivos`, `perdid` (split) | Ausentes |
| "7" sobrecargas | Só no técnico §11 (Q-13) |
| "hash, linhas, cópia" antes de E-02 | Funcional §4.1 (Q-02) |
| "não numérico ⇒ 2º erro" | Técnico §7.2 e catálogo L-AG-04 (Q-04) |
| `v1.0` como base | Cabeçalho do técnico (Q-11) |

---

## 7. Conclusão

- **Correções da 1ª passada:** 29 de 32 estão resolvidas e coerentes com o código. As 3 parciais (P-10, P-28, P-30) deixaram resíduos pontuais. Nenhuma ficou sem tratamento.
- **Consistência entre documentos:** a regra de rótulo, E-04, E-05b, E-07, E-09, E-11, E-14 e os limiares de R-OD estão alinhados nos três documentos. Há três exceções: a RO-03(a) do técnico (Q-03), o L-AG-04 no técnico e no catálogo (Q-04) e o diagrama da §4.1 do funcional (Q-02).
- **Rastreabilidade:** 103/103 IDs nos dois documentos. A confiança é idêntica à do catálogo; o único ponto é a omissão do qualificador PROV em Q-15. A estatística do catálogo §9 confere.
- **Prioridade antes de uso por QA:**
  - Q-01: nome de campo na mensagem de L-OD-00.
  - Q-05: cenários de OD que não chegam à regra-alvo.
  - Q-02 e Q-04: contradições residuais.
- **Contagem final:** CRÍTICO 0 · ALTO 1 · MÉDIO 4 · BAIXO 10 (total 15). Por responsável: funcional 6, técnico 8, catálogo 3 (Q-04 e Q-08 têm dois responsáveis).
