# Revisão da Documentação — Validador SIMBA 6.2.6

**Revisor:** revisor de documentação, independente dos autores. **Data:** 2026-10-17.
**Objeto:** `docs/DOCUMENTACAO_FUNCIONAL.md` (analista funcional), `docs/DOCUMENTACAO_TECNICA.md` (arquiteto de software), e, de forma acessória, `docs/CATALOGO_REGRAS.md` v1.0 (referência).
**Evidência primária consultada:** código decompilado (`analise/decompilado/`, `analise/decompilado-libs/spea-client/`), `analise/01…07`, `analise/progresso.md`, `analise/testes/**` (incluindo os 16 `casos/*/esperado.txt`), `implementacao/validador-independente/src/**`, `implementacao/README.md`.
**Restrições respeitadas:** nenhum documento foi alterado e nenhuma regra foi criada. O aplicativo original não foi executado. Só se executou a sonda própria `analise/testes/sondas/SplitProbe.java` (código do projeto, JDK local), compilada no scratchpad.

---

## 1. Resumo

| Item | Resultado |
|---|---|
| IDs do catálogo | 103 (extraídos por script das linhas de tabela) |
| Cobertura de IDs: funcional | **103/103**, todos com linha de tabela própria (§5 e §8). Nenhum ID estranho ao catálogo |
| Cobertura de IDs: técnico | **103/103**, todos na matriz §14. IDs que não são do catálogo aparecem só como *ids internos* da reimplementação (`L-NEG`, `L-NUM`, `L-UNK`, `G-LARGURA`, `G-UNK`, `R-INFRA`, `L-OD-05`, `R-GAB109/800`), que o próprio autor declarou (RO-05) |
| Confiança citada × catálogo | Igual ao catálogo nos 103 IDs, nos dois documentos (comparação por script). Um excesso pontual de confiança fora das tabelas por ID: P-15 |
| Referências do técnico | 172 identificadores de classe, 264 pares `Classe.membro`, 66 referências de teste e 29 caminhos verificados. **0 referências inexistentes.** Há 3 contagens erradas (P-17, P-26) |
| Problemas | **32**: CRÍTICO 6 · ALTO 1 · MÉDIO 13 · BAIXO 12 |
| Por responsável | funcional 13 · técnico 8 · catálogo 11 |
| Registro dos autores | Técnico RO-01…RO-10: 8 confirmados, 1 parcialmente refutado (RO-03b), 1 informativo confirmado. Funcional itens 1–9: 5 confirmados, 2 parcialmente refutados (1 e 2), 2 sem objeção |

Os achados mais graves são três:
- O catálogo (E-07) e o documento funcional afirmam que a mensagem `A carga foi interrompida devido a erros anteriores.` aparece ao usuário, e ela **nunca** aparece.
- O documento funcional atribui rótulo **físico** a E-04, quando o rótulo é **lógico**, e com isso contradiz o técnico.
- O documento técnico afirma que o `split` **perde campos vazios finais**, quando os **preserva**, o que contradiz o próprio golden `00-valido`.

---

## 2. Metodologia

1. **Cobertura de IDs.** Um script Python extrai os 103 IDs das linhas de tabela do catálogo e busca cada um, com fronteira de palavra, nos dois documentos. Também procura IDs presentes nos documentos que não existem no catálogo.
2. **Confiança.** Um script cruza a última coluna (Conf.) das linhas de cada ID no catálogo, no funcional (§5 e §8) e no técnico (§14).
3. **Referências do técnico.** Um script lista todos os identificadores CamelCase, os pares `Classe.método` (com expansão de `{A,B}`) e as abreviações `JU:`/`PY:`. Para cada um, verifica a existência do `.java` (inclusive classes internas) e a presença do membro no arquivo. As abreviações `JU:` e `PY:` foram conferidas contra os nomes reais de `@Test` e `def test_`. Os caminhos foram conferidos com `ls`/`find`.
4. **Amostragem de afirmações.** Foram 30 afirmações do funcional e 30 do técnico, conferidas contra o código decompilado e os golden (lista no §6).
5. **Catálogo × código.** Foram 34 regras conferidas no decompilado (§5).
6. **Sonda.** `SplitProbe.java` foi compilado e executado no scratchpad para confirmar a semântica do `split("(?=\t)")`.

---

## 3. Tabela de cobertura de IDs por documento

| Grupo | IDs no catálogo | Funcional (citados / com linha própria) | Técnico (citados / na matriz §14) |
|---|---|---|---|
| E (E-01…E-15, E-05b) | 16 | 16 / 16 | 16 / 16 |
| A (A-01…A-07) | 7 | 7 / 7 | 7 / 7 |
| L (L-AG, L-CT, L-PE, L-TI, L-EX, L-OD) | 38 | 38 / 38 | 38 / 38 |
| R bancário | 29 | 29 / 29 | 29 / 29 |
| G + R-G | 13 | 13 / 13 | 13 / 13 |
| **Total** | **103** | **103 / 103** | **103 / 103** |

O ID menos citado tem 3 menções no funcional e 1 no técnico. Nenhum documento omite ID.

**Confiança:** nos 103 IDs, os valores do funcional (§5 e §8) e do técnico (§14) coincidem com o catálogo. As diferenças são só de grafia ("EXEC sob remendo" × "EXEC (sob remendo)"; "EXEC/PROV" × "EXEC (dado o receptor); … PROV").

---

## 4. Problemas por severidade

**Critério aplicado:**
- **CRÍTICO:** informação incorreta ou contraditória que leva a resultado esperado errado.
- **ALTO:** afirmação relevante sem evidência ou contrariada pelo código, com alcance limitado.
- **MÉDIO:** descrição incompleta, imprecisa ou com contradição de baixo impacto.
- **BAIXO:** clareza, contagem ou organização.

### 4.1 CRÍTICO (6)

**P-01 · CRÍTICO · funcional · §4.2, §5.1 (E-07), §7.1 (E-07), §8 (E-07)**
- **Trecho:** "`A carga foi interrompida devido a erros anteriores.` nos arquivos não carregados" (§4.2) e o cenário "CONTAS e seguintes não carregados e `A carga foi interrompida devido a erros anteriores.` (prev.)".
- **Evidência contrária:** `analise/decompilado/br/mp/mpf/spea/simba/validador/carga/Carregar.java`. `temErroNaCarga` lança `NegocioException` com esse texto, mas `carregar` captura a exceção (`catch (NegocioException e) { log.error("Erro de negócio na carga."); … }`) e não chama `report`. O golden `analise/testes/casos/03-uf-invalida/esperado.txt` (erro em AGENCIAS) traz só a mensagem de UF. A mensagem nunca chega ao relatório nem à tela.
- **Contradição:** o técnico §16 RO-01 e §14.1 (E-07) descrevem o comportamento correto.
- **Correção:** dizer que E-07 só interrompe a carga e que o texto fica apenas no log. Corrigir o cenário §7.1 para "apenas o erro de UF".
- **Responsável:** funcional.

**P-02 · CRÍTICO · catálogo · §3, E-07 (coluna Resultado)**
- **Trecho:** "`A carga foi interrompida devido a erros anteriores.` (nos arquivos não carregados)".
- **Evidência:** a mesma de P-01 (`D:carga/Carregar.java`, `carregar` e `temErroNaCarga`; golden `03`). O mesmo vale para `CarregarGab` e `CarregarGabVer2` (linha 98).
- **Correção:** "Resultado: interrompe a carga; a mensagem só é registrada em log". Confirma RO-01 do técnico.
- **Responsável:** catálogo.

**P-03 · CRÍTICO · funcional · §0.2, §5.1 (E-04), §7.1 (E-04), §8 (E-04, coluna Rótulo = F)**
- **Trecho:** "Rótulo físico (…): erros estruturais (E-03, E-04)…" e "`Tamanho do campo <NOME> excedido…` (rótulo físico…)".
- **Evidência contrária:** `D:carga/AbstractCarregarArquivoCmd.java`, `validarCampos`. O erro é criado com `new ErroValidacao(numeroLinha, this.getNomeArquivo(), mensagem)` e `getNomeArquivo()` devolve `NomeArquivo.X.name()` (por exemplo `CarregarArquivoAgenciasCmd.getNomeArquivo` → `AGENCIAS`). A reimplementação confirma: `N:Loader.java:67` usa `layout.name()`.
- **Contradição:** o técnico §6.3 e RO-03(a) dizem "nome lógico", corretamente.
- **Correção:** E-04 tem rótulo lógico. O cenário §7.1 deve esperar `Erro no arquivo: AGENCIAS, linha: 1, erro: Tamanho do campo NUMERO_BANCO excedido…`.
- **Responsável:** funcional.

**P-04 · CRÍTICO · catálogo · §1.3 (regra de rótulo)**
- **Trecho:** "Rótulo = nome físico (erros estruturais, de conversão e 'Erro desconhecido') ou nome lógico (erros de regra de campo e de conteúdo)".
- **Evidência:** a regra falha em três pontos:
  - E-04 é estrutural e usa rótulo lógico (P-03).
  - Toda `NegocioException` usa rótulo físico (`AbstractCarregarArquivoCmd.carregar`: `catch (NegocioException e) { report(new ErroValidacao(numeroLinha, fileName, …)) }`). Isso inclui regras de campo como L-TI-02 e L-TI-03 (`CarregarTabelaTitularidadeCmd.validarDataInicioRelacionamento` e `validarDataFimRelacionamento` lançam `NegocioException`), as mensagens de parse de L-TI-06 (`validarDatasRelacionamento`), L-TI-00, L-CT-00 (`CarregarArquivoContasCmd.convert`) e L-PE-00 (`CarregarTabelaPessoaCmd.convert`).
  - `getInt`/`getLong` (L-EX-00 e numéricos de L-OD-00) usam `file.getAbsolutePath()`, que `ErroValidacao.getPrettyMessage` reduz ao nome físico.
- **Impacto:** a regra genérica induziu P-03 e P-07.
- **Correção:** tabelar o rótulo por mecanismo:
  - `report` nas regras: lógico.
  - `NegocioException`, E-03, E-13, G-02, `getInt`/`getLong`, E-02: físico.
  - E-04: lógico.
  - Exceções declaradas: L-PE-02 `EXTRATO`, R-CT-02 `null`, R-G112-1 `Investigado`.
- **Responsável:** catálogo.

**P-05 · CRÍTICO · técnico · §6.2**
- **Trecho:** "campos vazios finais são perdidos pelo `split` do Java".
- **Evidência contrária:**
  - Com *lookahead*, cada campo final vazio vira o elemento `"\t"`, que não é string vazia e, por isso, não é descartado; o `trim` o converte depois em `""`.
  - A execução de `analise/testes/sondas/SplitProbe.java` dá `a<TAB>b<TAB> -> 3 [a, b, ]` e `a<TAB>b<TAB><TAB> -> 4 [a, b, , ]`.
  - O golden `analise/testes/casos/00-valido/001-TST-000001-00_AGENCIAS.txt` termina em `01012000\t\r\n` (DATA_FECHAMENTO vazia) e é aprovado com 11 colunas no original (níveis 2 e 3).
  - Se a afirmação fosse verdadeira, todo arquivo com o último campo opcional vazio falharia em E-03.
- **Correção:** "campos vazios finais são **preservados**; só o 1º campo de comprimento zero é descartado".
- **Responsável:** técnico.

**P-06 · CRÍTICO · funcional · §3.6, linha 3 (VALOR_TRANSACAO)**
- **Trecho:** "VALOR_TRANSACAO … limiares de R-OD-01…03, R-OD-E1/E2".
- **Evidência contrária:** as consultas comparam o limiar com **`EXTRATO.VALOR_LANCAMENTO`**, não com o valor do OD:
  - `D:rules/ValidarIdentificacaoTedDocRule.java` (`EXTRATO.VALOR_LANCAMENTO >= 1000000`);
  - `ValidarIdentificacaoTransferenciaMesmoBancoRule` e `ValidarIdentificacaoTransacaoEmEspecieRule` (`E.VALOR_LANCAMENTO`);
  - `ValidarDepositoDinheiroChequeEleitoralRule` (`E.VALOR_LANCAMENTO >= 10000`).
  
  R-OD-E1 (`ValidarIdentificacaoTedDocTransfEleitoralRule`) **não tem** limiar de valor. Um cenário de QA que variasse só o valor do OD não dispararia as regras.
- **Correção:** atribuir os limiares a EXTRATO.VALOR_LANCAMENTO (coluna 10 de §3.5) e retirar R-OD-E1 da lista.
- **Responsável:** funcional.

### 4.2 ALTO (1)

**P-07 · ALTO · funcional · §8 (coluna Rótulo) e §0.2**
- **Trecho:** a matriz marca **L** (lógico) para L-CT-00, L-PE-00, L-TI-00, L-TI-02, L-TI-03, L-TI-06 e L-EX-00.
- **Evidência contrária:**
  - L-CT-00, L-PE-00, L-TI-00, L-TI-02, L-TI-03 e as mensagens "é inválida: <v>" de L-TI-06 são `NegocioException`. O loop as reporta com `fileName` (físico): `D:carga/AbstractCarregarArquivoCmd.java`, bloco `catch (NegocioException e)`.
  - L-EX-00 sai com `this.file.getAbsolutePath()` (`getLong`/`getInt`), portanto físico.
  - O próprio §0.2 diz que "conversão" é físico, o que torna a matriz internamente incoerente.
  - E-02 está com rótulo "—", mas o código usa `getAbsolutePath()` (`ControladorValidador.verificarArquivosVazios`), ou seja, rótulo físico.
- **Correção:** marcar F nessas linhas (L-TI-06: "F para parse; L para início > fim") e E-02 = F.
- **Responsável:** funcional.

### 4.3 MÉDIO (13)

**P-08 · MÉDIO · funcional · §1 (passo 4) e §9 (Carga)**
- **Trecho:** "gravando em um banco em memória (HSQLDB)" e "gravados no banco em memória".
- **Evidência:** `L:pgr/client/jdbc/JdbcConnectionProvider.java:21` usa `"jdbc:hsqldb:file:" + …getArquivoCaso()`. O DDL está em `analise/decompilado/DAO/Mapa.java`, com `CREATE CACHED TABLE`.
- **Contradição:** o técnico §5.1 descreve corretamente um banco em arquivo.
- **Correção:** "banco HSQLDB em arquivo do caso".
- **Responsável:** funcional.

**P-09 · MÉDIO · catálogo · §3, E-14 (Condição)**
- **Trecho:** "Cond.: antes de E-02. MD5 … ; contagens de linhas gravadas; arquivos copiados".
- **Evidência:**
  - Antes de E-02 só ocorrem o hash e a cópia (`telas/frmSelecionaDiretorio.java:851-856`).
  - A contagem de linhas é gravada **ao fim da carga de cada arquivo**, em `AbstractCarregarArquivoCmd.carregar` → `atualizaQuantidadeLinhaTabela`, com *overrides* em `CarregarArquivoAgenciasCmd:68`, `CarregarArquivoContasCmd:66` e `CarregarTabelaTitularidadeCmd:86`.
  - Por causa de E-07 e E-02, arquivos não carregados não têm contagem gravada.
- **Correção:** separar "hash + cópia (antes de E-02)" de "contagem de linhas (durante a carga, só para os arquivos carregados)".
- **Responsável:** catálogo.

**P-10 · MÉDIO · funcional · §1 (passo 2), §5.1 (E-14), §7.1 (E-14)**
- **Trecho:** "Calcula o hash MD5 e a contagem de linhas … (E-14)" antes de E-02, e "Antes de E-02: grava MD5 dos 5 arquivos, contagem de linhas…".
- **Evidência:** a mesma de P-09.
- **Observação:** o técnico §7.1 atribui corretamente a contagem a `atualizaQuantidadeLinhaTabela`.
- **Correção:** como em P-09.
- **Responsável:** funcional.

**P-11 · MÉDIO · catálogo · §5.0 (fonte e nomes) e LAC-05**
- **Trecho:** "(Fonte: `D:enums/Campos*.java` …)" e "consultar `D:enums/`".
- **Evidência:**
  - `analise/decompilado/br/mp/mpf/spea/simba/validador/enums/` não contém enum de layout bancário. Só existem `CamposGab*`, `CamposAtendimento*` e `CamposInvestigado`.
  - Os layouts (nome e máximo) estão em `getCamposArquivo()` de cada loader, por exemplo `CarregarArquivoOrigemDestinoCmd.getCamposArquivo`.
  - Os nomes abreviados do §5.0 (BANCO, CPF_CNPJ, NOME, CODIGO_CHAVE, NUMERO_DOCUMENTO de OD, OBSERVACAO) diferem dos que aparecem na mensagem E-04 (`NUMERO_BANCO`, `CPF_CNPJ_TITULAR`, `NOME_TITULAR`, `CODIGO_CHAVE_EXTRATO`, `NUMERO_DOCUMENTO_TRANSACAO`, `OBSERVACÃO`).
  - Os máximos conferem nos 71 campos.
- **Correção:** apontar `getCamposArquivo()` como fonte e usar os nomes exatos. Confirma RO-03(b) do técnico e o item 1 do funcional.
- **Responsável:** catálogo.

**P-12 · MÉDIO · funcional · §3.1, §3.6 (linha 18), §10 (LAC-05), §11 item 1**
- **Trecho:**
  - "fonte: `Schemas.java` da reimplementação, que reproduz os enums `Campos*`";
  - "OBSERVACAO | 250";
  - "conferir com `enums/Campos*.java`";
  - "o catálogo e as mensagens usam `OBSERVACAO`".
- **Evidência:** não há enums `Campos*` bancários (P-11). `N:Schemas.java:33` reproduz `getCamposArquivo()` e grafa `OBSERVACÃO`, igual ao original, que é o nome exibido na mensagem E-04. A §3.6 diverge da própria fonte declarada.
- **Correção:** citar `getCamposArquivo()`, grafar `OBSERVACÃO` com nota de erro de digitação do original e retirar "as mensagens usam OBSERVACAO".
- **Responsável:** funcional.

**P-13 · MÉDIO · catálogo · §1.1 e caminhos `D:../DAO`, `D:../telas`, `app/DAO/` (K-01, K-02, K-08, E-01, E-08, E-11, E-14)**
- **Evidência:**
  - `analise/decompilado/br/mp/mpf/spea/simba/DAO` e `…/simba/telas` não existem (`ls`). Os caminhos reais são `analise/decompilado/DAO/` e `analise/decompilado/telas/`.
  - `original/extraido/app/` contém apenas `jre`, `lib`, `simba-validador.exe` e `simba-validador.jar`. Não há `app/DAO/` nem `app/validador-spring.xml`: ambos estão dentro do JAR (extraídos em `analise/decompilado/`).
- **Correção:** criar prefixos próprios ou usar caminhos relativos a `analise/decompilado/`. Confirma RO-02.
- **Responsável:** catálogo.

**P-14 · MÉDIO · funcional · §5.3 (L-AG-04)**
- **Trecho:** "Se vazio ou não numérico, lança exceção: **dois erros** na linha".
- **Evidência:** `D:carga/CarregarArquivoAgenciasCmd.java` (`validarCamposObrigatorios` e depois `validarNumeroBanco` com `Integer.valueOf`). Só há dois erros quando o campo está **vazio**, porque L-AG-01 dispara e depois vem o E-13. Com um valor não numérico preenchido (ex.: `AB1`), L-AG-01 não dispara e há **um** erro (E-13). Em ambos os casos L-AG-05 e L-AG-06 deixam de rodar na linha, porque a exceção interrompe o `validar`. O catálogo L-AG-04 já diz "dois erros no total quando vazio".
- **Correção:** restringir "dois erros" ao caso vazio e citar a interrupção das validações seguintes.
- **Responsável:** funcional.

**P-15 · MÉDIO · funcional · §6 (D-16)**
- **Trecho:** "D-16 | NPE quando DATA_ABERTURA_CONTA nula e lançamento após o encerramento | R-EX-02 | **EXEC**".
- **Evidência:** a NPE é dedução estática de `D:rules/ValidarExtratoForaPeriodoContaAbertaRule.java` (`sdf.format(dataAberturaConta)` sem teste de nulo). Nem o catálogo nem `analise/04` (l.160), `05` ou `progresso` citam execução desse cenário no original; o EXEC de R-EX-02 vem do golden `10`, que não exercita a NPE.
- **Correção:** marcar D-16 como EST.
- **Responsável:** funcional.

**P-16 · MÉDIO · funcional · §7.1 (E-02, 2º cenário)**
- **Trecho:** "usar MOVIMENTACAO=3 e investigados sem relacionamento para isolar".
- **Evidência:**
  - Com todas as contas em MOVIMENTACAO=3, `ValidarContaSemMovimentacao` (R-CT-05) reporta "Atendimento sem nenhuma movimentação de conta." (`D:rules/ValidarContaSemMovimentacao.java:20-23`).
  - Com o titular-base investigado (PESSOA_INVESTIGADA=1) em conta MOV=3, R-CT-08 também dispara.
  - A previsão de "sem erro" não se sustenta.
- **Correção:** usar MOVIMENTACAO=2 (R-CT-10 não dispara sem EXTRATO), mantendo o titular investigado.
- **Responsável:** funcional.

**P-17 · MÉDIO · técnico · §2 ("`lib/` com 40 JARs"; "Verificação realizada… `ls original/extraido/app/lib` (40 JARs…)")**
- **Evidência:** `ls original/extraido/app/lib` lista **34** arquivos `.jar`, e o `Class-Path` do `analise/decompilado/META-INF/MANIFEST.MF` tem 34 entradas. O valor 40 vem de `analise/01-inventario.md:15`. O texto declara uma verificação por `ls` que daria 34.
- **Correção:** 34 JARs.
- **Responsável:** técnico.

**P-18 · MÉDIO · técnico · §9 (Rótulos) e §7 (mecânica comum)**
- **Trecho:** "nome físico (E-03, E-13, G-02, `getInt/getLong`, erros de vazio) ou lógico (`NomeArquivo.X.toString()` nas regras de campo, E-04 e R-\*)".
- **Evidência:** as `NegocioException` (L-CT-00, L-PE-00, L-TI-00, L-TI-02, L-TI-03, parse de L-TI-06) são reportadas com `fileName` físico (P-04). A afirmação "regras de campo → lógico" é falsa para L-TI-02 e L-TI-03.
- **Correção:** incluir "mensagens de `NegocioException` → físico".
- **Responsável:** técnico.

**P-19 · MÉDIO · técnico · §4.2 (fluxo GAB)**
- **Trecho:** "`frmSelecionaDiretorioGab` → `verificarExistenciaArquivosGab` → `verificarArquivosGabVazios` (G-01) → `CarregarGab.carregarGab`…".
- **Evidência:** `analise/decompilado/telas/frmSelecionaDiretorioGab.java:750-789` mostra que, entre existência e vazios, ocorrem também `checkVersaoSistemaBanco` (E-09, l.759), `getComputador` (E-15, l.773), `GerarHashArquivosGabCmd.gerar` (E-14, l.774) e `copiarGab` (l.777). O fluxo do documento omite esses passos, que existem no fluxo bancário §4.1.
- **Correção:** completar o fluxo.
- **Responsável:** técnico.

**P-20 · MÉDIO · catálogo · §6.5 (R-OD-01, R-OD-02, R-OD-03, R-OD-E2)**
- **Trecho:** "`VALOR ≥ 1.000.000`", "`DATA≥… ∧ VALOR≥…`", "`VALOR ≥ 10.000`".
- **Evidência:** a coluna é `EXTRATO.VALOR_LANCAMENTO` (consultas citadas em P-06). A forma ambígua induziu o erro P-06 do funcional.
- **Correção:** escrever `EXTRATO.VALOR_LANCAMENTO` e `EXTRATO.DATA_LANCAMENTO`.
- **Responsável:** catálogo.

### 4.4 BAIXO (12)

**P-21 · BAIXO · catálogo · §8.2**
- **Trecho:** "Sobrecargas `validar(ExtratoOrigemDestino)` em 7 regras de OD".
- **Evidência:** `grep -ln "validar(ExtratoOrigemDestino" D:rules/*.java` devolve **6** arquivos. O 7º arquivo que contém "ExtratoOrigemDestino" é `ValidarValorExtratoOrigemDestinoRule`, que só o tem no nome da classe e expõe apenas `validar()`. Nenhum chamador usa a sobrecarga (`grep "\.validar([a-zA-Z]"` não encontra nada).
- **Correção:** 6. Confirma RO-04.
- **Responsável:** catálogo.

**P-22 · BAIXO · catálogo · E-05b**
- **Trecho:** "o **primeiro campo vazio é descartado**".
- **Evidência:** só é descartado o campo de **comprimento zero**. Um primeiro campo `" "` é mantido e o `trim` o torna vazio depois (SplitProbe; goldens `02` → 10 colunas e `04` → 11 colunas).
- **Correção:** "primeiro campo de comprimento zero (linha iniciada por TAB)". Confirma o item 4 do funcional.
- **Responsável:** catálogo.

**P-23 · BAIXO · catálogo · L-CT-01**
- **Trecho:** "NUMERO_BANCO, NUMERO_CONTA, TIPO_CONTA, MOVIMENTACAO_CONTA".
- **Evidência:** `CarregarArquivoContasCmd.convert` chama `AgenciaLocalizerCmd.findIdAgencia(String,String)`, que faz `Integer.valueOf(numeroBanco)` antes de `validar`. Com NUMERO_BANCO vazio a linha sai em E-13 (como diz L-CT-00), e o ramo NUMERO_BANCO de L-CT-01 é inalcançável. Isso também enfraquece a observação D-12 do funcional sobre essa mensagem.
- **Correção:** nota de inalcançabilidade desse ramo.
- **Responsável:** catálogo.

**P-24 · BAIXO · catálogo · L-TI-00 e L-TI-01**
- **Evidência:**
  - `CarregarTabelaTitularidadeCmd.convert` tem uma 6ª mensagem, `Pessoa não localizada. Linha: <n>`, que o catálogo não lista.
  - A mensagem de L-TI-01 não cita TIPO_TITULAR, que é testado.
- **Correção:** acrescentar a mensagem e a observação.
- **Responsável:** catálogo.

**P-25 · BAIXO · catálogo · E-09**
- **Trecho:** "Aviso de versão".
- **Evidência:** o texto existe em `analise/decompilado/telas/frmSelecionaDiretorio.java:838-843`: "A versão do sistema (<v>) é diferente da versão do cadastro de atendimento (<v>). Para solucionar este problema, clique no botão Voltar, selecione o caso, clique em Ver Detalhes e grave o caso novamente.", exibido em diálogo (`ViewUtil.showWarnDialog`).
- **Correção:** catalogar o texto. Confirma a parte E-09 do item 2 do funcional.
- **Responsável:** catálogo.

**P-26 · BAIXO · técnico · §3.3 e §2**
- **Trecho:** "`carga` (31 arquivos)" e "`certificados/` (3 arquivos)".
- **Evidência:** `ls …/validador/carga` lista 30 arquivos; `ls analise/decompilado/certificados` lista 9 (os 3 citados em §10 mais GlobalSign, icpbrasil v2/v5/v10, isrg_root e sectigo).
- **Correção:** 30 e 9.
- **Responsável:** técnico.

**P-27 · BAIXO · técnico · §16 RO-03(b)**
- **Trecho:** "OD: `VALOR_TRANSACAO`, `NUMERO_DOCUMENTO_TRANSACAO`, `CPF_CNPJ_OD`, e `OBSERVACÃO`" como nomes divergentes do catálogo.
- **Evidência:** o catálogo §5.0 já usa `VALOR_TRANSACAO` e `CPF_CNPJ_OD`. Só `NUMERO_DOCUMENTO_TRANSACAO` e `OBSERVACÃO` divergem.
- **Correção:** retirar os dois falsos positivos.
- **Responsável:** técnico.

**P-28 · BAIXO · técnico · §8 ("Datas leniente × estrita")**
- **Trecho:** "explica casos em que L-TI-02 passa e L-TI-06 ainda falha (ou vice-versa)".
- **Evidência:** se L-TI-02 falha, ela **lança** `NegocioException` e L-TI-06 não roda (`CarregarTabelaTitularidadeCmd.validar`), então "vice-versa" é impossível. L-TI-06 só falha com L-TI-02 aprovada quando a data está vazia. A coexistência relevante é a de L-AG-02/03 (estrita, reporta) com L-AG-05 (leniente).
- **Correção:** reescrever o exemplo.
- **Responsável:** técnico.

**P-29 · BAIXO · técnico · §13.1 (GABD)**
- **Trecho:** "4.500 cenários × 2 modos (3.000 com o defeito + 3.000 sob remendo em memória, v1 e v2)".
- **Evidência:** pelo `analise/05-testes.md`, há 3.000 cenários com defeito e 3.000 sob remendo, mais 1.500 × 2 do GAB v2, o que dá 4.500 por modo. O parêntese soma 6.000 e contradiz "4.500 × 2".
- **Correção:** "por modo: 3.000 (v1/v2) + 1.500 (v2 completo)".
- **Responsável:** técnico.

**P-30 · BAIXO · funcional · §5.7 (L-OD-00)**
- **Trecho:** "O usuário vê: `Erro desconhecido…` (E-13)".
- **Evidência:** os demais numéricos de OD (CODIGO_CHAVE_EXTRATO, VALOR_OPERACAO, NUMERO_AGENCIA, SITUACAO_IDENTIFICACAO) passam por `getLong`/`getInt` e geram `Campo <NOME> não é um número.` com rótulo físico (`CarregarArquivoOrigemDestinoCmd.convert`).
- **Correção:** listar as duas saídas.
- **Responsável:** funcional.

**P-31 · BAIXO · funcional · §6 D-12 e §11 item 2**
- **Evidência:**
  - D-12 não registra que a mensagem de L-TI-01 omite TIPO_TITULAR, que é testado (P-24).
  - O §11 item 2 diz que "texto exato de E-04 … não aparece no catálogo", mas o catálogo E-04 traz `Tamanho do campo <NOME> excedido. Tamanho máximo permitido: <n>`.
- **Correção:** ajustar D-12 e restringir o item 2 a E-09.
- **Responsável:** funcional.

**P-32 · BAIXO · funcional · §5.1 (E-11) e §7.1 (E-11) — lacuna**
- **Evidência:** `frmSelecionaDiretorio.java:891-905`. O `finally` também roda após os `return null` de E-01 (arquivo ausente) e de E-09 (versão), e então "Continuar" é habilitado mesmo sem validação nenhuma. O funcional diz apenas "após a validação mesmo com erros". O técnico §4.1 está correto ("`finally` … habilita").
- **Correção:** explicitar o caso.
- **Responsável:** funcional.

---

## 5. Verificação do catálogo contra o código (34 regras)

Legenda: ✔ = confere; ✖ = erro registrado (P-nn); ≈ = confere com ressalva menor.

| ID | Verificação no decompilado | Resultado |
|---|---|---|
| E-01 | `ControladorValidador.verificarExistenciaArquivosBancario`: `exists()` ou `findFile` com `SuffixFileFilter(".txt", INSENSITIVE)`, sem recursão, índice 1 em diante de `Mapa.nomesTabelasA`, retorna no 1º ausente | ✔ (caminho `D:../DAO` ✖ P-13) |
| E-02 | `verificarArquivosVazios`: 3 `if` independentes, linha 0 | ✔ |
| E-03 | `AbstractCarregarArquivoCmd.carregar` l.168-178, rótulo `fileName` | ✔ |
| E-04 | `validarCampos` l.289-299, `length()` após trim, rótulo `getNomeArquivo()` | ✔ condição; rótulo ✖ P-04 |
| E-05 | ISO-8859-1, `BufferedReader` 10 MiB, `readLine` | ✔ |
| E-05b | `Util.tabSeparatedStringToArray` = `split("(?=\t)")` + `trim`; SplitProbe | ≈ P-22 |
| E-06 | `if (hasErros()) continue` após `validarCampos` e após `validar`; `hasErros` global | ✔ |
| E-07 | `Carregar.temErroNaCarga` + `catch (NegocioException)` só com log | ✖ P-02 |
| E-08 | laço `NomeArquivo.values()` com `break` (`frmSelecionaDiretorio:871-878`); `validarArquivo*` retorna `!hasErros()` | ✔ |
| E-09 | `checkVersaoSistemaBanco` = `configurador.getVersao().equals(SISTEMA.VERSAO)` | ✔ (texto ausente, P-25) |
| E-10 | `Context.isEleitoral`: `startsWith("001-MPE") \|\| startsWith("038-TSE")`; consumidores: Agencias, Titularidade, ValidarAtendimento | ✔ |
| E-11 | `finally`: `!"999-OUTROS-000001-88".equals(...)` ⇒ `setEnabled(true)` | ✔ |
| E-12 | `ErroValidacao.getPrettyMessage` com `FilenameUtils.getName` | ✔ |
| E-13 | `catch (Exception)` ⇒ "Erro desconhecido. Consulte o log do sistema." com `fileName` | ✔ |
| E-14 | hash e cópia antes de E-02; contagem de linhas na carga | ✖ P-09 |
| E-15 | `Controlador.getComputadores` com `orgaos?codprog=`; chamada em `frmSelecionaDiretorio:851` | ✔ |
| A-01 | `asspa/client/Util.verificaData`: tamanho 8, ano ≥ 1900, `setLenient(false)` | ✔ |
| A-02/A-03 | `CpfCnpjUtil` + `DigitoVerificador` (`[0-9]{6,}`, `[0-9A-Z]{7,}`, `sameChar`) | ✔ |
| A-05 | `StringUtil.isNumber` = `NumberFormat.getInstance().parse` | ✔ |
| A-06 | `removerZerosEsquerda`: `while (charAt(index)=='0')` sem limite | ✔ |
| A-07 | `Arquivo.java:446` (`%032x` se `possuiCorrecaoHash`); `Versao.versao4OuSuperior` | ✔ |
| L-AG-01…07 | `CarregarArquivoAgenciasCmd.validar` (ordem, listas de campos, eleitoral, `UF.get` com `equals` case-sensitive, `"--"`) | ✔ |
| L-CT-00…05 | `CarregarArquivoContasCmd` (convert com `NegocioException`, ordem de `validar`, faixas 1–4 e 1–3) | ✔ (L-CT-01 ≈ P-23) |
| L-PE-00…04 | `CarregarTabelaPessoaCmd` (`Double.valueOf`, lista fixa de 5, rótulo `EXTRATO`, `removerZerosEsquerda`) | ✔ |
| L-TI-00…06 | `CarregarTabelaTitularidadeCmd` (4 `"".equals`, `NegocioException` em 02/03/06-parse, regex `[T1-9RLPO]{1}` e `[01]{1}`) | ✔ (≈ P-24) |
| L-EX-00…07 | `CarregarArquivoExtratoCmd` (`getLong`/`getInt`, `== null` nunca verdadeiro, `[CD\*]`, `[CD]`, `CnabRule`) | ✔ |
| L-OD-00…04 | `CarregarArquivoOrigemDestinoCmd` (`Long.valueOf(dados[0])`, `validarTipoConta` testa `getTipoPessoa()`, `suportaCNPJComoNumeroBanco`) | ✔ |
| §5.0 máximos | 71 campos de `getCamposArquivo()` × catálogo | ✔ máximos; ✖ fonte e nomes (P-11) |
| R-AG-01 | `GROUP BY … HAVING COUNT(*)>1`, `MAX(LINHA_ARQUIVO)` | ✔ |
| R-CT-01…07 | SQL e ordem em `ValidarAtendimento.validarArquivoConta`; R-CT-02 `extends AbstractRule` (rótulo `null`); R-CT-05 `if (!rs.next())` | ✔ |
| R-TI-01, R-TI-04 | SQL de dados obrigatórios; `ErroValidacao(-1L, …)` | ✔ |
| R-EX-01, 02, 04 | `WITH INV …`; `sdf.format(dataAberturaConta)` (NPE possível); `MOVIMENTACAO_CONTA = 3` | ✔ |
| R-OD-01…04, E1, E2 | SQL com limiares em `EXTRATO.VALOR_LANCAMENTO`; R-OD-04 Java com `completarZerosEsquerda(…,14)` e `substring(3)` | ✔ condição; ≈ P-20 |
| G-01, G-03, G-04, G-05, G-DEF, R-G112-1 | `verificarArquivosGabVazios`; `validarObrigatoriedade` (GAB109 7, GAB800 10, GAB112 20 com DOCUMENTO e COMPLEMENTO_DO_CEP duplicados); `validarData` em else-if no GAB109; `erroNumber`; `getCamposArquivo()` = `null` nos **6** loaders; rótulo `"Investigado"` | ✔ |
| §8.2 (sobrecargas) | 6 arquivos | ✖ P-21 |

---

## 6. Amostragem de afirmações

### 6.1 Funcional (30 amostras): 20 conferem, 10 com problema

| # | Afirmação (local) | Veredito |
|---|---|---|
| 1 | §0.2 E-04 com rótulo físico | ✖ P-03 |
| 2 | §0.2 R-G112-1 com rótulo `Investigado` | ✔ |
| 3 | §1 "banco em memória" | ✖ P-08 |
| 4 | §1 / §5.1 contagem de linhas antes de E-02 | ✖ P-10 |
| 5 | §2 efeitos do perfil eleitoral (L-AG-01/05/06, L-TI-01, OD) | ✔ |
| 6 | §3.1 E-05b: só o campo de comprimento zero é descartado (golden 02 × 04) | ✔ |
| 7 | §3.1 `Schemas` reproduz enums `Campos*` | ✖ P-12 |
| 8 | §3.2 máximos de AGENCIAS | ✔ |
| 9 | §3.6 VALOR_TRANSACAO base dos limiares | ✖ P-06 |
| 10 | §4.2 mensagem de E-07 | ✖ P-01 |
| 11 | §4.2 E-02 com 3 checagens independentes | ✔ |
| 12 | §5.3 L-AG-04 "vazio ou não numérico ⇒ dois erros" | ✖ P-14 |
| 13 | §5.3 L-AG-06 case-sensitive + `--` | ✔ |
| 14 | §5.4 L-CT-01 mensagem com 3 campos | ✔ |
| 15 | §5.5 L-PE-01 lista fixa de 5 | ✔ |
| 16 | §5.5 L-PE-02 rótulo `EXTRATO` | ✔ |
| 17 | §5.5 L-TI-02 interrompe a linha | ✔ |
| 18 | §5.6 L-EX-02 com 8 campos na mensagem | ✔ |
| 19 | §5.7 L-OD-01 testa TIPO_PESSOA | ✔ |
| 20 | §5.8 R-CT-05 na linha 0 | ✔ |
| 21 | §5.8 R-AG-01 com a maior linha | ✔ |
| 22 | §5.8 R-OD-01 exceção NAO-CORRENTISTA com `UCASE` | ✔ |
| 23 | §5.8 R-OD-E2 com TIPO 201 e ≥ 10.000 | ✔ |
| 24 | §5.9 G-01: textos das 3 mensagens | ✔ |
| 25 | §5.9 G-DEF sem filtro de E-06 | ✔ |
| 26 | §8 rótulos L de L-CT-00, L-PE-00, L-TI-00/02/03/06 e L-EX-00 | ✖ P-07 |
| 27 | §7.1 dica de isolamento de E-02 | ✖ P-16 |
| 28 | §6 D-16 EXEC | ✖ P-15 |
| 29 | §7.8 textos dos golden 09, 10, 11, 12, 13, 14 (comparados com `esperado.txt`) | ✔ |
| 30 | §7.7 L-OD-04 bloqueado por E-04 com o nome `NUMERO_BANCO_OD` | ✔ |

### 6.2 Técnico (30 amostras): 23 conferem, 7 com problema

| # | Afirmação (local) | Veredito |
|---|---|---|
| 1 | §1 Java 25 (`Build-Jdk-Spec: 25`) | ✔ |
| 2 | §1 159 `.java` no app + 83 na biblioteca | ✔ (`find`) |
| 3 | §2 `lib/` com 40 JARs, "verificado por `ls`" | ✖ P-17 |
| 4 | §3.3 `carga` com 31 arquivos; `certificados` com 3 | ✖ P-26 |
| 5 | §3.3 rules 46, enums 22, domain 20, to 4, cmd 15 | ✔ |
| 6 | §3.1 beans e escopos do `validador-spring.xml` | ✔ |
| 7 | §4.1 ordem E-01 → E-09 → getComputador → hash → cópia → E-02 → carga → conteúdo | ✔ (`frmSelecionaDiretorio:827-881`) |
| 8 | §4.1 / RO-01: mensagem de E-07 só em log | ✔ |
| 9 | §4.1 `finally` | ✔ |
| 10 | §4.2 fluxo GAB | ✖ P-19 |
| 11 | §5.1 `enforce_strict_size`, `jdbc:hsqldb:file:` | ✔ |
| 12 | §5.2 DDL de AGENCIA, ATENDIMENTO, INVESTIGADO, TITULARIDADE (PK composta) | ✔ |
| 13 | §6.1 buffer 10 MiB, lote 500, commit a cada 250.000 linhas | ✔ |
| 14 | §6.2 campos vazios finais perdidos | ✖ P-05 |
| 15 | §6.3 E-04 com rótulo lógico; `tipo` nunca lido; `OBSERVACÃO` | ✔ |
| 16 | §7.1 E-01 (`SuffixFileFilter`, índice 1, `findFile`) | ✔ |
| 17 | §7.2 ordem de `validar` dos 6 loaders | ✔ |
| 18 | §7.3 ordem das 29 regras SQL e ramos eleitorais | ✔ |
| 19 | §7.3 R-EX-02 com NPE quando a abertura é nula | ✔ (estático) |
| 20 | §7.5 G-DEF (`getCamposArquivo()` = `null`) | ✔ (nos 6) |
| 21 | §8 A-01, A-02/03, A-05, A-06, A-07 (linha ~446) | ✔ |
| 22 | §8 leniente × estrita "vice-versa" | ✖ P-28 |
| 23 | §9 `report` com `log.info` | ✔ |
| 24 | §9 regra de rótulos | ✖ P-18 |
| 25 | §10 URLs (`TestaConexao`, `Configurador`) e `PostToUrl.post` com sobrecargas | ✔ |
| 26 | §11 código morto (Mapa: 6 métodos sem uso; `InvestigadoLocalizerCmd`; `ValidarGab112Sim*`) | ✔ |
| 27 | §12 POM (0.2.0, release 21, JUnit 5.11.4, surefire 3.5.2, jar 3.4.2, `mainClass`) | ✔ |
| 28 | §12 `GabConfig.defeito` padrão em COMPAT; `R-INFRA` com rótulo `EXTRATO` | ✔ |
| 29 | §13.1: 48 testes Python (4/5/3/6/16/14), 31 `@Test` (7/8/6/6/3/1), modelo com 774 linhas | ✔ |
| 30 | §13.1 números GABD | ✖ P-29 |

---

## 7. Verificação das referências do documento técnico

| Tipo | Quantidade | Inexistentes | Observação |
|---|---|---|---|
| Identificadores de classe (CamelCase, com `{A,B}` expandido) | 172 | **0** | 136 resolvem para `.java` do projeto ou classe interna. Os 36 restantes não são classes do projeto e foram conferidos um a um: JDK ou terceiros (`BufferedReader`, `SimpleDateFormat`, `FilenameUtils`, `SuffixFileFilter`, `SwingWorker`, …), chaves do instalador (`AppName`, `DefaultDirName`, `EncryptionUse`, …), classes Python de teste (`CargaEstrutural`, `CargaPorArquivo`, `PrimitivasJava`, `RegrasDeConteudo`, que existem em `test_caracterizacao.py`) e prefixos de curinga (`CarregarArquivoGab*`, `VerificarHashArquivos*`, `...NaturalKey` → `AgenciaNaturalKey`, `ContaNaturalKey`, `PessoaNaturalKey`) |
| Pares `Classe.membro` | 264 | **0** | Todos os membros de classes do projeto foram encontrados no arquivo da classe. Os não resolvidos são JDK (`Integer.valueOf`, `Files.readAllLines`, …) ou curingas (`validarArquivo*`) |
| Métodos citados sem classe (`montarDadosAtendimento`, `gerarArquivoZip`, `salvarComprovante`, `getTamanhoLinha`, `getNomesList`, `getCampos`, `getGab109/112/800`, `countLines`, `startThread`, `getArquivoSpring`, …) | 18 | **0** | Encontrados por `grep` |
| Testes `JU:` (6 classes, 30 métodos) | 30 | **0** | `CasoMinimoTest`, `ModosTest`, `PrimitivasTest`, `GabTest`, `LimiaresTest`, `GoldenCasosTest` |
| Testes `PY:` (36 referências, algumas por prefixo `…`) | 36 | **0** | Todas casam com um `def test_` da classe indicada |
| Caminhos de arquivo/diretório | 29 | **0** | Inclui `analise/decompilado/DAO/*.java`, `telas/`, `validador-spring.xml`, `analise/ferramentas/inno65*.py`, `carve_zip.py`, `T:diferencial/*`, `T:sondas/SplitProbe.java`, `implementacao/limiares-6.2.6.properties`, `original/extraido/app/{jre,lib,simba-validador.exe,simba-validador.jar}` |
| Contagens declaradas como verificadas | — | 3 erradas | `lib` 40 → 34 (P-17); `carga` 31 → 30 e `certificados` 3 → 9 (P-26) |

O documento técnico também resolve corretamente o defeito de prefixo do catálogo (§0.1 explica que `DAO` e `telas` ficam ao lado de `br/`).

---

## 8. Comparação funcional × técnico (mesmas regras)

| Aspecto | Funcional | Técnico | Código | Problema |
|---|---|---|---|---|
| Mensagem de E-07 | aparece ao usuário | só em log | só em log | P-01 |
| Rótulo de E-04 | físico | lógico | lógico | P-03 |
| Banco | "em memória" | arquivo `jdbc:hsqldb:file:` | arquivo | P-08 |
| Contagem de linhas (E-14) | antes de E-02 | na carga (`atualizaQuantidadeLinhaTabela`) | na carga | P-10 |
| Nome do campo 18 de OD | `OBSERVACAO` | `OBSERVACÃO` | `OBSERVACÃO` | P-12 |
| Fonte dos layouts | "enums `Campos*`" | `getCamposArquivo()` / `Mapa.camposTabelasA` | `getCamposArquivo()` | P-12 |
| Ordem das regras R-\* (AG 1, CT 10, TI 7, EX 5, OD 3+1 / 2+1) | igual | igual | igual | — |
| Ordem de `validar` dos loaders | igual (§5.3–5.7) | igual (§7.2) | igual | — |
| Condições e mensagens de L-\*, R-\*, G-\* | iguais ao catálogo | remetem ao catálogo | conferidas (§5) | — |
| Confiança por ID | = catálogo | = catálogo | — | — |

---

## 9. Divergências registradas pelos autores: confirmação ou refutação

### 9.1 Técnico (§16)

| # | Veredito | Evidência |
|---|---|---|
| RO-01 (E-07 só em log) | **Confirmado** | `Carregar.carregar`, `catch (NegocioException)`; golden `03` (P-02) |
| RO-02 (prefixos `D:../DAO`, `D:../telas`) | **Confirmado** | `ls` (P-13) |
| RO-03(a) (rótulo de E-04 lógico) | **Confirmado** | `validarCampos` → `getNomeArquivo()` (P-03, P-04) |
| RO-03(b) (nomes de E-04 divergentes de §5.0) | **Parcialmente refutado** | Divergem de fato `TIPO_PESSOA_TITULAR`, `CPF_CNPJ_TITULAR`, `NOME_TITULAR`, `TELEFONE_PESSOA`, `NUMERO_DOCUMENTO_TRANSACAO` e `OBSERVACÃO`; `VALOR_TRANSACAO` e `CPF_CNPJ_OD` já estão no catálogo (P-27) |
| RO-04 (6, não 7 sobrecargas) | **Confirmado** | `grep -ln` → 6 (P-21) |
| RO-05 (ids internos não catalogados) | **Confirmado** | `N:Loader`, `N:Pipeline:47`, `N:gab/GabPipeline:27-32` (`E-01`/`E-02` para G-01), `N:gab/GabRules:37` (`"R-"+arquivo` ⇒ `R-GAB109/800`) |
| RO-06 (código morto adicional) | **Confirmado** | Nenhum uso de `getCamposNaoNulos`, `getCamposNaoZeros`, `colAparaColB`, `getNomesOficios`, `getCodigoArquivoA` ou `getCamposTabelasA` fora de `Mapa.java`; `CampoArquivo.getTipo()` sem consumidor |
| RO-07 (lacuna de teste de E-02) | **Confirmado** | `CasoMinimoTest.arquivoAusenteEVazio` só afirma a mensagem de E-01 |
| RO-08 (golden = saída do modelo) | **Confirmado** | Docstring de `T:gerar_casos.py` ("esperado.json = saída do MODELO") e de `NT:GoldenCasosTest` |
| RO-09 (contadores de progresso) | **Confirmado** | `ValidarAtendimento`: EXTRATO `isEleitoral()?6:5`, OD `isEleitoral()?3:4` |
| RO-10 (ordens conferidas; G-DEF) | **Confirmado** | G-DEF vale nos **6** loaders (`return null` em Gab109/112/800 e nas versões Ver2) |

### 9.2 Funcional (§11)

| # | Veredito | Evidência |
|---|---|---|
| 1 (nomes de campos) | **Parcialmente confirmado** | A divergência entre catálogo e nomes reais existe (P-11). Refutado: "as mensagens usam `OBSERVACAO`" (a mensagem E-04 usa `OBSERVACÃO`) e a premissa de enums `Campos*` bancários (não existem) (P-12) |
| 2 (textos de E-04 e E-09 fora do catálogo) | **Parcialmente refutado** | O texto de E-04 está no catálogo. O de E-09 falta e foi localizado no código (P-25, P-31) |
| 3 (cobertura golden insuficiente) | **Confirmado** | 16 casos; 53 regras EST (catálogo §9) |
| 4 (precisão de E-05b) | **Confirmado** | SplitProbe; goldens 02 e 04 (P-22) |
| 5 (significado de MOVIMENTACAO) | **Confirmado** como não documentado | Nenhum enum ou comentário define 1/2/3; a dedução a partir das regras é coerente |
| 6 (L-AG-04: dois rótulos no golden 04) | **Confirmado** | `casos/04-banco-vazio/esperado.txt` |
| 7 (L-PE-02 sem golden) | **Confirmado** | Nenhum `esperado.txt` traz o rótulo `EXTRATO` em erro de TITULARES; só testes do modelo e da reimplementação (`PY:…data_renda…`, `JU:Modos.lPe02…`) |
| 8 (maiúscula em "Não use tab"; dois espaços em G-04/G-05) | **Confirmado** | `AbstractCarregarArquivoCmd:158`; `erroNumber + " Campo "` em Gab109 e Gab112 |
| 9 (código morto) | Sem objeção | Contagem: P-21 |

---

## 10. Lacunas observadas (sem regra nova)

1. Nenhum documento informa que as mensagens de E-01 e E-09 vão **só para a tela** (`addMessage` ou diálogo), sem entrar no `ErrorReportCmd` nem no relatório HTML. O funcional §4.2 descreve o efeito visível, mas não a ausência no relatório.
2. A L-TI-00 tem a mensagem "Pessoa não localizada. Linha: n" e L-CT-01 tem um ramo inalcançável (P-23, P-24).
3. Os documentos não deixam claro que o filtro global E-06 é, na prática, intra-arquivo: um erro em qualquer arquivo interrompe a carga dos seguintes (E-07). Os dois documentos apenas repetem o catálogo, sem erro.

---

## 11. Conclusão

- **Cobertura e rastreabilidade:** completas nos dois documentos (103/103 IDs, confiança idêntica à do catálogo). O técnico tem 0 referências de classe, método, teste ou arquivo inexistentes; só três contagens estão erradas.
- **Correção exigida antes de uso por QA:**
  - **Funcional:** P-01 (mensagem de E-07), P-03 e P-07 (rótulos), P-06 (coluna dos limiares de OD).
  - **Técnico:** P-05 (semântica do `split` para campos finais).
  - **Catálogo:** P-02 e P-04, que originam P-01, P-03 e P-07 no funcional; recomenda-se corrigir o catálogo primeiro.
- **Qualidade geral:**
  - O documento técnico é o mais fiel ao código. Seu registro RO-01…RO-10 está quase todo confirmado (só RO-03b é parcialmente refutado).
  - O funcional herdou do catálogo três imprecisões (E-07, regra de rótulo, temporização de E-14) e acrescentou duas próprias (VALOR_TRANSACAO, banco em memória).
- **Contagem final:** CRÍTICO 6 · ALTO 1 · MÉDIO 13 · BAIXO 12 (total 32; funcional 13, técnico 8, catálogo 11).
