# Catálogo de Regras — Validador SIMBA 6.2.6

**Status:** referência compartilhada (versão 1.2, consolidada pelo orquestrador em 2026-10-17; v1.1 incorpora a revisão independente — ver §10).
**Fonte primária da consolidação:** `analise/01…07`, `analise/progresso.md`, `analise/testes/**`, código decompilado local e reimplementação `implementacao/validador-independente`.
**Regra de governança:** os identificadores deste catálogo são **estáveis**. Nenhum subagente pode renumerar, remover ou criar regras; novas descobertas entram apenas como *proposta* (§8) para consolidação pelo orquestrador.

---

## 1. Convenções

### 1.1 Prefixos de caminho usados nas evidências
| Prefixo | Caminho real |
|---|---|
| `D:` | `analise/decompilado/br/mp/mpf/spea/simba/validador/` (código do aplicativo, decompilado; **não versionado**, regenerável) |
| `DX:` | `analise/decompilado/` (raiz: contém `DAO/`, `telas/`, `META-INF/`, `validador-spring.xml`, `certificados/`, ao lado de `br/`) |
| `L:` | `analise/decompilado-libs/spea-client/br/mp/mpf/` (biblioteca `spea-client` 2.4.4, decompilada; **não versionada**) |
| `N:` | `implementacao/validador-independente/src/main/java/estudo/simba/validador/` (reimplementação de estudo) |
| `T:` | `analise/testes/` (modelo Python, casos sintéticos, harnesses diferenciais) |
| `A:` | `analise/` (relatórios 01–07, `progresso.md`) |

### 1.2 Escala de confiança (única para todo o catálogo)
| Código | Significado | Critério |
|---|---|---|
| **EXEC** | Confirmado por execução | Comportamento observado no aplicativo **original** executado em sandbox sem rede (funções puras, pipeline real, ou tela real), com evidência específica citada (caso golden, hipótese H*, harness) |
| **EST** | Confirmado estaticamente | Comportamento totalmente determinado pelo código decompilado, **sem** ocorrência observada no original durante a campanha diferencial (ver `docs/MAPA_COBERTURA_REGRAS.md`). Hoje: E-01, E-05, E-09, E-15 |
| **PROV** | Provável | Depende de locale, banco (NULL/ordem do HSQLDB), servidor ou dado externo |
| **INALC** | Inalcançável | Código existe, mas nenhuma entrada consegue acioná-lo (provado estaticamente) |
| **ND** | Não determinado | Não investigado ou inconclusivo |

### 1.3 Formato da mensagem (E-12) e rótulo de arquivo
`Erro no arquivo: <rótulo>, linha: <n>, erro: <mensagem>`. O rótulo depende do **mecanismo** que emite o erro (corrigido na v1.1; a regra anterior "estrutural/conversão = físico, campo/conteúdo = lógico" estava errada):
| Mecanismo | Rótulo |
|---|---|
| `report` direto nas regras de carga `validar()` (L-* que não lançam exceção) e nas regras de conteúdo R-* | **lógico** `AGENCIAS\|CONTAS\|TITULARES\|EXTRATO\|ORIGEM_DESTINO` |
| E-04 (`validarCampos`) | **lógico** |
| E-03, E-13 (catch genérico), G-02, E-02 e G-01 (`getAbsolutePath`) | **físico** (nome do arquivo `.txt`) |
| `NegocioException` capturada no laço de carga (L-CT-00, L-PE-00, L-TI-00, L-TI-02, L-TI-03, mensagens de parse de L-TI-06) | **físico** |
| `getInt`/`getLong` (L-EX-00 e numéricos de L-OD-00; `file.getAbsolutePath()` reduzido ao nome) | **físico** |
| Exceções declaradas | L-PE-02 ⇒ `EXTRATO`; R-CT-02 ⇒ `null`; R-G112-1 ⇒ `Investigado` |

Fonte: `D:carga/AbstractCarregarArquivoCmd` (linhas de `report`/`catch`), `L:pgr/client/error/ErroValidacao.getPrettyMessage`. Textos completos das mensagens: `analise/04-regras-validacao.md`; aqui aparecem prefixos.

### 1.4 Perfil
**Bancário comum** (padrão) vs. **eleitoral** (`NUMERO_CASO` começa com `001-MPE` ou `038-TSE`; ver E-10). Regras com ramo eleitoral indicam "Perfil" na condição.

---

## 2. Componentes e fluxos

### 2.1 Componentes
| Cód. | Componente | Papel | Caminho |
|---|---|---|---|
| K-01 | `Main` / `validador-spring.xml` | Ponto de entrada Swing + contexto Spring | `D:Main.java`, `DX:validador-spring.xml` (extraído do JAR) |
| K-02 | `frmSelecionaDiretorio` (tela) | Orquestra o fluxo ponta a ponta em `SwingWorker`; habilita "Continuar" | `DX:telas/frmSelecionaDiretorio.java` (`doInBackground`, `TarefaBackground`) |
| K-03 | `ControladorValidador` | Façade: existência/vazio de arquivos, versão | `D:ControladorValidador.java` |
| K-04 | `Carregar` + `AbstractCarregarArquivoCmd` | Leitura, split, E-03/E-04, ciclo convert→validar→inserir | `D:carga/Carregar.java`, `D:carga/AbstractCarregarArquivoCmd.java` |
| K-05 | `CarregarArquivo{Agencias,Contas,Titulares,Extrato,OrigemDestino}Cmd`, `CarregarTabela{Pessoa,Titularidade}Cmd` | Regras de carga por linha (L-*) | `D:carga/` |
| K-06 | `ValidarAtendimento` + `rules/*` | Ordem e execução das regras de conteúdo SQL (R-*) | `D:validacao/ValidarAtendimento.java`, `D:rules/` |
| K-07 | Loaders GAB (`CarregarArquivoGab*Cmd`, `GetCamposGabColunaFixaCmd`) e `ValidarAtendimentoGab` | Fluxo de corretoras | `D:carga/*Gab*`, `D:validacao/ValidarAtendimentoGab.java` |
| K-08 | `DAO/Mapa`, `MapaGab`, `BancoHSQLDB` | DDL e acesso HSQLDB 2.7.3 (`CACHED TABLE`) | `DX:DAO/` |
| K-09 | `spea-client` (`Context`, `ErrorReportCmd`/`ErroValidacao`, `CpfCnpjUtil`/`DigitoVerificador`, `StringUtil`, `asspa.client.Util`, `Arquivo`, `Controlador`) | Utilitários de MPF compartilhados | `L:pgr/…`, `L:asspa/client/…` |
| K-10 | `util/Util` | `tabSeparatedStringToArray` | `D:util/Util.java` |
| K-11 | `rules/CnabRule` | Tabela CNAB-240 | `D:rules/CnabRule.java` |
| K-12 | Módulo de comunicação (`cmd/TransmitirDocumentosCmd`, `PostToUrl`, `Conexao`) | Transmissão ao servidor — **fora do escopo de validação, não executado** | `D:cmd/` |
| R-01…R-n | Reimplementação | `Loader`, `ContentRules`, `Pipeline`, `CpfCnpj`, `Cnab`, `DateRules`, `TabSplitter`, `Text`, `Db`, `Limiares`, `Reports`, `Cli`, `gab/*` | `N:` |
| V-01 | Infra de verificação | `validador_ref.py` (modelo), `test_caracterizacao.py` (48 testes), `gerar_casos.py` (16 casos), `diferencial/*` (níveis 1–3, hash, GAB) | `T:` |

### 2.2 Fluxo bancário (CONFIRMADO; `A:03-arquitetura.md` §3)
```
pasta → E-01 → E-09 → [E-14 hash + cópia] → E-02
      → CARGA: AGENCIAS → CONTAS → TITULARES(PESSOA→TITULARIDADE) → EXTRATO → ORIGEM_DESTINO   (E-03/04/05/05b/06 por linha; E-07 fail-fast)
      → CONTEÚDO: AGENCIAS → CONTAS → TITULARES → EXTRATO → ORIGEM_DESTINO                      (E-08 fail-fast entre arquivos; todas as regras do arquivo rodam)
      → limpeza das tabelas → relatório → "Continuar" habilitado (E-11)
```
### 2.3 Fluxo de corretoras (GAB): `GAB109 → GAB112 → GAB800`, largura fixa, layout v1/v2 (D12). No 6.2.6, ver defeito **G-DEF**.

---

## 3. Regras estruturais e de fluxo (E-*)
| ID | Nome | Descrição · Condições de aplicação | Resultado | Evidências | Conf. | Componentes |
|---|---|---|---|---|---|---|
| E-01 | Arquivos esperados | Cond.: sempre, no início. Em `<pasta>` (não recursiva) devem existir, comparação case-insensitive e só `*.txt`, `<NUMERO_CASO>_{AGENCIAS,CONTAS,TITULARES,EXTRATO,ORIGEM_DESTINO}.txt`. Só o **primeiro** ausente é reportado | `Arquivo esperado (<nome>) não encontrado.` e o fluxo para | `D:ControladorValidador.verificarExistenciaArquivosBancario`; `DX:DAO/Mapa.nomesTabelasA`; `N:Pipeline`; `T:`golden `00-valido` | EST | K-03; R `Pipeline` |
| E-02 | Arquivos obrigatoriamente não vazios | Cond.: após E-01. 0 bytes é erro (linha 0) para AGENCIAS, CONTAS, TITULARES. EXTRATO e ORIGEM_DESTINO podem ser vazios | `O arquivo X não pode ser vazio.` | `D:ControladorValidador.verificarArquivosVazios`; `N:Pipeline`; mapa de cobertura: 3 cenário(s), caso `casos-por-regra/E-02` | EXEC | K-03; R `Pipeline` |
| E-03 | Quantidade de colunas | Cond.: toda linha de arquivo bancário. Separação por TAB; exige N colunas: AGENCIAS 11, CONTAS 7, TITULARES 21, EXTRATO 14, ORIGEM_DESTINO 18 | `Quantidade de colunas inválida. Esperado: N Atual: M` (rótulo físico) | `D:carga/AbstractCarregarArquivoCmd.carregar`; `T:`casos `01-colunas-agencia`, `02-primeiro-campo-vazio` (nível 2, original); mapa de cobertura: 318 cenário(s), caso `casos-por-regra/E-03` | EXEC | K-04; R `Loader` |
| E-04 | Tamanho máximo do campo | Cond.: linha com N colunas certas. Comprimento (após `trim`) > máximo do campo (tabelas em §5.0; fonte: `getCamposArquivo()` de cada loader). Não há mínimo/obrigatoriedade aqui | `Tamanho do campo <NOME> excedido. Tamanho máximo permitido: <n>` (rótulo **lógico**; `<NOME>` é o nome do campo em `getCamposArquivo()`, ex.: `NUMERO_BANCO`, `OBSERVACÃO` com til) | `D:carga/AbstractCarregarArquivoCmd.validarCampos`, `getCamposArquivo()` de cada loader; `N:Schemas`; mapa de cobertura: 40 cenário(s), caso `casos-por-regra/E-04` | EXEC | K-04; R `Schemas`,`Loader` |
| E-05 | Codificação e quebra de linha | ISO-8859-1; `BufferedReader.readLine` (CR, LF ou CRLF); linha em branco ⇒ 1 coluna ⇒ E-03. Acentos/BOM **não** testados | — | `D:carga/AbstractCarregarArquivoCmd` | EST | K-04; R `Loader`,`Text` |
| E-05b | Separador de campos e descarte do 1º campo vazio | `split("(?=\t)")` + `trim()`: somente o **primeiro campo de comprimento zero** (linha iniciada por TAB) é descartado (`\tb\tc` ⇒ 2 colunas); campos vazios finais são preservados (o `trim` os torna `""`); um primeiro campo `" "` é mantido; `trim` remove todo caractere ≤ U+0020 | Coluna a menos ⇒ E-03 | `D:util/Util.tabSeparatedStringToArray`; `T:sondas/SplitProbe.java`; nível 1 (1.508 vetores split, H1) | EXEC | K-10; R `TabSplitter` |
| E-06 | Erro global bloqueia a validação semântica das linhas seguintes | Cond.: após o **primeiro** erro de qualquer arquivo (`ErrorReportCmd.hasErros()` é global). Nas linhas seguintes só rodam E-03/E-04; `convert()`/`validar()` não rodam e a linha não é inserida | Relatório = 1º erro semântico (todas as mensagens da linha) + erros estruturais das demais linhas | `D:carga/AbstractCarregarArquivoCmd.carregar` (`if (hasErros()) continue`); `L:pgr/client/error/ErrorReportCmd`; H3 (nível 2, `casos/03-uf-invalida` + 1.500 aleatórios) | EXEC | K-04, K-09; R `Loader` |
| E-07 | Fail-fast na carga | Ordem AGENCIAS→CONTAS→TITULARES→EXTRATO→ORIGEM_DESTINO; se um arquivo tem erro, os seguintes não são carregados. TITULARES: se PESSOA tem erro, TITULARIDADE não é carregada | Interrompe a carga dos arquivos seguintes. O texto `A carga foi interrompida devido a erros anteriores.` é lançado como `NegocioException`, capturado em `Carregar.carregar` e **somente registrado em log** — **não** aparece no relatório nem na tela (golden `03-uf-invalida`) | `D:carga/Carregar.temErroNaCarga`; `D:carga/CarregarArquivoTitularesCmd.carregar`; mapa de cobertura: 1 cenário(s), caso `casos-por-regra/E-07` | EXEC | K-04; R `Pipeline` |
| E-08 | Fail-fast no conteúdo | Cond.: só roda se **toda** a carga terminou sem erro. Regras SQL por arquivo, na ordem acima; o laço para no 1º arquivo reprovado; dentro do arquivo, todas as regras rodam | Mensagens R-* do 1º arquivo reprovado | `DX:telas/frmSelecionaDiretorio.doInBackground`; `D:validacao/ValidarAtendimento`; golden `09`…`14` (`#CONTEUDO`) | EXEC | K-02, K-06; R `Pipeline` |
| E-09 | Versão do validador | Versão do validador deve igualar a gravada no atendimento; senão aviso e interrompe | Diálogo de aviso (não entra no relatório): `A versão do sistema (<v>) é diferente da versão do cadastro de atendimento (<v>). Para solucionar este problema, clique no botão Voltar, selecione o caso, clique em Ver Detalhes e grave o caso novamente.` | `D:ControladorValidador.checkVersaoSistemaBanco` (compara versões); `DX:telas/frmSelecionaDiretorio` l.838-845 (texto e `ViewUtil.showWarnDialog`); `DX:telas/frmSelecionaDiretorioGab` l.760-767 | EST | K-03 |
| E-10 | Perfil eleitoral | `Context.isEleitoral()`: `NUMERO_CASO` começa com `001-MPE` ou `038-TSE`. Altera L-AG-01, L-AG-05, L-AG-06, L-TI-01 (sem efeito) e troca o conjunto de regras de ORIGEM_DESTINO (R-OD-E1/E2 no lugar de R-OD-01…03) | — | `L:pgr/client/Context.isEleitoral`; golden `13-eleitoral-deposito-sem-cpf` | EXEC | K-09; R `Config` |
| E-11 | Validação não bloqueia o prosseguimento | O botão "Continuar" é habilitado no `finally` do worker para todo caso ≠ `999-OUTROS-000001-88`, **mesmo com erros**. Só nesse caso exige ausência de erros. Se o servidor aceita o envio com erros: ND (D15) | Relatório de inconsistências gerado; interface não impede avanço. O `finally` também roda após os retornos antecipados de E-01 e E-09: "Continuar" fica habilitado mesmo sem validação alguma | `DX:telas/frmSelecionaDiretorio.TarefaBackground`; nível 3 (tela real, captura `gui.png`, 73 cenários) | EXEC | K-02 |
| E-12 | Formato da mensagem | `Erro no arquivo: <rótulo>, linha: <n>, erro: <msg>` (ver §1.3) | Linha de relatório | `L:pgr/client/error/ErroValidacao.getPrettyMessage`; todos os golden | EXEC | K-09; R `Reports` |
| E-13 | "Erro desconhecido" | Cond.: qualquer exceção não tratada na linha (ex.: A-06, `Integer.valueOf` de não numérico, NPE). Rótulo = nome físico | `Erro desconhecido. Consulte o log do sistema.` | `D:carga/AbstractCarregarArquivoCmd` (catch genérico); golden `04`, `07`; mapa de cobertura: 174 cenário(s), caso `casos-por-regra/E-13` | EXEC | K-04 |
| E-14 | Hash, contagem de linhas e cópia dos arquivos | Cond.: hash e cópia ocorrem **antes de E-02**: MD5 dos 5 arquivos gravado em `ATENDIMENTO.HASH_ARQUIVO_n` e arquivos copiados à pasta do caso. A **contagem de linhas** é gravada **durante a carga**, ao fim de cada arquivo (`atualizaQuantidadeLinhaTabela`), só para arquivos efetivamente carregados (E-07/E-02 impedem os demais) (formato do hash: ver A-07) | Valores gravados no atendimento | `DX:telas/frmSelecionaDiretorio`; nível 3: hash 73/73, linhas 73/73, cópia 73/73 | EXEC | K-02, K-12 (`cmd/`) |
| E-15 | Dependência de rede para validar | `getComputador(...)` → `Controlador.getComputadores()` faz HTTP GET `orgaos?codprog=…` (versão do receptor). O app real exige rede (ou servidor simulado); nos testes a lista foi injetada em memória | Afeta A-07 e L-OD-04 | `A:03-arquitetura.md` §7; `L:pgr/client/Controlador`; D2 resolvida (estático) | EST | K-02, K-09 |

---

## 4. Algoritmos de apoio (A-*)
| ID | Nome | Descrição · Condições | Resultado | Evidências | Conf. | Componentes |
|---|---|---|---|---|---|---|
| A-01 | `verificaData` (ddMMyyyy) | Exatamente 8 caracteres; ano ≥ 1900; parse **não leniente** (29/02 só em bissexto). Campo vazio não é validado aqui | `A <CAMPO> (<valor>) é inválida ou o ano é anterior a 1900.` | `L:asspa/client/Util.verificaData`; nível 1 (3.261 vetores, H2) | EXEC | K-09; R `DateRules` |
| A-02 | Dígito verificador de CPF | Completa com zeros à esquerda até 11; remove 2 últimos como DV; rejeita se a base tem todos dígitos iguais; pesos `len+1-i`; resto<2⇒0 senão 11−resto | CPF válido/inválido | `L:pgr/util/CpfCnpjUtil`, `DigitoVerificador`; nível 1 (6.006 vetores); 3.000 CPFs vs algoritmo oficial independente | EXEC | K-09; R `CpfCnpj` |
| A-03 | Dígito verificador de CNPJ alfanumérico | Completa com zeros até 14; base `[0-9A-Z]{7,}`, DV numérico; valor do caractere = `ord(c)−48`; pesos 5,4,3,2,9…2 e 6,5,4,3,2,9…2; base não pode ter todos os caracteres iguais | CNPJ válido/inválido | idem; nível 1 (6.005 vetores); 3.000 CNPJs alfanuméricos vs algoritmo oficial | EXEC | K-09; R `CpfCnpj` |
| A-04 | `CnabRule` (retorna **true = inválido**) | Crédito `C`: válido 201–223 ou 999. Débito `D`: 101–127 exceto 116, ou 999. `*`: 101–127 (exc. 116), 201–223 ou 999 | Inválido/válido | `D:rules/CnabRule`; nível 1 (3.306 vetores) | EXEC | K-11; R `Cnab` |
| A-05 | `isNumber` leniente dependente de locale | `NumberFormat.getInstance().parse(texto)` (`"12abc"`→número; `"1.000"`→1000 em pt_BR), depois `Long.valueOf` estrito ⇒ `NumberFormatException` ⇒ E-13. Usado por `getInt/getLong` | Varia com locale da máquina | `L:pgr/util/StringUtil.isNumber`; `D:carga/AbstractCarregarArquivoCmd.getLong/getInt`; testado só em `pt_BR` (D4, H8 parcial) | PROV | K-09, K-04; R `Text` |
| A-06 | `removerZerosEsquerda` lança exceção | `StringIndexOutOfBoundsException` se o texto for vazio ou só zeros | ⇒ E-13 (usado em L-PE-03, G-06) | `L:pgr/util/StringUtil.removerZerosEsquerda`; nível 1 | EXEC | K-09; R `Text` |
| A-07 | Hash MD5 de arquivo | MD5 hexadecimal; com zeros à esquerda (`%032x`) somente se receptor ≥ v4 (`possuiCorrecaoHash`), senão `BigInteger.toString(16)` sem padding. Versão vem do servidor | String do hash | `L:asspa/client/Arquivo.obterHashArquivo`; `T:diferencial/diferencial_hash.py` (401 arquivos × 3 versões; 28 com MD5 iniciado em zero) | EXEC (dado o receptor); versão real do servidor: PROV | K-09 |

---

## 5. Regras de carga por linha (L-*)
### 5.0 Máximos de campo (E-04) e ordem dos campos
- **AGENCIAS (11)**: NUMERO_BANCO 3, NUMERO_AGENCIA 4, NOME_AGENCIA 50, ENDERECO_LOGRADOURO 80, CIDADE 40, UF 2, PAIS 40, CEP 8, TELEFONE 20, DATA_ABERTURA 8, DATA_FECHAMENTO 8.
- **CONTAS (7)**: BANCO 3, AGENCIA 4, NUMERO_CONTA 20, TIPO_CONTA 1, DATA_ABERTURA 8, DATA_ENCERRAMENTO 8, MOVIMENTACAO 1.
- **TITULARES (21)**: BANCO 3, AGENCIA 4, CONTA 20, TIPO_CONTA 1, TIPO_TITULAR 1, PESSOA_INVESTIGADA 1, TIPO_PESSOA 1, CPF_CNPJ 14, NOME 80, NOME_DOC 50, NUMERO_DOC 20, LOGRADOURO 80, CIDADE 40, UF 2, PAIS 40, CEP 8, TELEFONE 20, VALOR_RENDA 14, DATA_ATUALIZACAO_RENDA 8, DATA_INICIO 8, DATA_FIM 8.
- **EXTRATO (14)**: CODIGO_CHAVE 18, BANCO 3, AGENCIA 4, CONTA 20, TIPO_CONTA 1, DATA_LANCAMENTO 8, NUMERO_DOCUMENTO 20, DESCRICAO 50, TIPO_LANCAMENTO 3, VALOR_LANCAMENTO 14, NATUREZA_LANCAMENTO 1, VALOR_SALDO 14, NATUREZA_SALDO 1, LOCAL_TRANSACAO 80.
- **ORIGEM_DESTINO (18)**: CODIGO_CHAVE_OD 18, CODIGO_CHAVE_EXTRATO 18, VALOR_TRANSACAO 14, NUMERO_DOCUMENTO 20, BANCO_OD 3, AGENCIA_OD 4, CONTA_OD 20, TIPO_CONTA_OD 1, TIPO_PESSOA_OD 1, CPF_CNPJ_OD 14, NOME_PESSOA_OD 80, NOME_DOC_IDENTIFICACAO_OD 50, NUMERO_DOC_IDENTIFICACAO_OD 20, CODIGO_DE_BARRAS 100, NOME_ENDOSSANTE_CHEQUE 80, DOC_ENDOSSANTE_CHEQUE 50, SITUACAO_IDENTIFICACAO 1, OBSERVACAO 250.

(Fonte: `getCamposArquivo()` de cada `CarregarArquivo*Cmd`/`CarregarTabela*Cmd` (`D:carga/`), reproduzido em `N:Schemas`; **não** existem enums de layout bancário (`D:enums/` só tem `CamposGab*`, `CamposAtendimento*`, `CamposInvestigado`). Os nomes acima são **abreviados**; os nomes exatos, que aparecem nas mensagens de E-04, estão em `getCamposArquivo()` (ex.: `TIPO_PESSOA_TITULAR`, `CPF_CNPJ_TITULAR`, `NOME_TITULAR`, `TELEFONE_PESSOA`, `NUMERO_DOCUMENTO_TRANSACAO`, `OBSERVACÃO`). Máximos conferidos em 71 campos pela revisão. Ver LAC-05.)

### 5.1 AGENCIAS — `CarregarArquivoAgenciasCmd.validar` (ordem de execução)
| ID | Nome | Descrição · Condições | Resultado | Evidências | Conf. | Componentes |
|---|---|---|---|---|---|---|
| L-AG-01 | Campos obrigatórios da agência | Cond.: toda linha sem erro global. "Vazio" = null ou `trim` vazio. Não eleitoral: BANCO, AGENCIA, NOME, LOGRADOURO, CIDADE, UF, PAIS, CEP, TELEFONE. Eleitoral: só BANCO, AGENCIA, NOME | `Um dos campos obrigatórios (<lista>) não está preenchido.` | `D:carga/CarregarArquivoAgenciasCmd`; golden `04-banco-vazio`; mapa de cobertura: 65 cenário(s), caso `casos-por-regra/L-AG-01` | EXEC | K-05; R `Loader` |
| L-AG-02 | Data de abertura | DATA_ABERTURA, se preenchida, passa A-01 | A-01 | idem; mapa de cobertura: 43 cenário(s), caso `casos-por-regra/L-AG-02` | EXEC | K-05 |
| L-AG-03 | Data de fechamento | DATA_FECHAMENTO, se preenchida, passa A-01 | A-01 | idem; mapa de cobertura: 41 cenário(s), caso `casos-por-regra/L-AG-03` | EXEC | K-05 |
| L-AG-04 | Banco igual ao do atendimento | `Integer.valueOf(NUMERO_BANCO)` = NUMERO_BANCO do atendimento. NUMERO_BANCO vazio ⇒ L-AG-01 **e** E-13 (dois erros); não numérico preenchido ⇒ só E-13 (um erro). Em ambos os casos a exceção interrompe `validar` e L-AG-05/L-AG-06 não rodam | `NUMERO_BANCO diferente do informado em atendimento.` | idem; H4 (golden `04-banco-vazio`, nível 2); mapa de cobertura: 41 cenário(s), caso `casos-por-regra/L-AG-04` | EXEC | K-05 |
| L-AG-05 | Coerência de datas da agência | Perfil não eleitoral: FECHAMENTO sem ABERTURA ⇒ erro; FECHAMENTO < ABERTURA ⇒ erro (parse **leniente** `SimpleDateFormat`) | `DATA_FECHAMENTO_AGENCIA informada, mas não há DATA_ABERTURA_AGENCIA.` / `DATA_ABERTURA_AGENCIA posterior à DATA_FECHAMENTO_AGENCIA.` / `Data inválida nos campos …` | idem; mapa de cobertura: 22 cenário(s), caso `casos-por-regra/L-AG-05` | EXEC | K-05 |
| L-AG-06 | UF válida | Perfil não eleitoral: UF ∈ 27 siglas (**case-sensitive**) ou `--` | `ENDERECO_UF:<v> não é uma sigla válida.` | idem; golden `03-uf-invalida`; mapa de cobertura: 53 cenário(s), caso `casos-por-regra/L-AG-06` | EXEC | K-05 |
| L-AG-07 | Inserção da agência | NUMERO_AGENCIA vira `Integer.valueOf` (não numérico ⇒ E-13) | — / E-13 | idem; mapa de cobertura: 1 cenário(s), caso `casos-por-regra/L-AG-07` | EXEC | K-05 |

### 5.2 CONTAS — `CarregarArquivoContasCmd`
| ID | Nome | Descrição · Condições | Resultado | Evidências | Conf. | Componentes |
|---|---|---|---|---|---|---|
| L-CT-00 | Agência da conta existe | (convert) banco+agência numéricos (zeros à esquerda irrelevantes) devem existir em AGENCIAS. Vazio/não numérico ⇒ E-13 | `Agencia não localizada. Banco: <b> Agencia: <a>` | `D:carga/CarregarArquivoContasCmd`, `AgenciaLocalizerCmd`; mapa de cobertura: 76 cenário(s), caso `casos-por-regra/L-CT-00` | EXEC | K-05 |
| L-CT-01 | Obrigatórios da conta | NUMERO_BANCO, NUMERO_CONTA, TIPO_CONTA, MOVIMENTACAO_CONTA (a mensagem lista só os 3 últimos). O ramo NUMERO_BANCO é inalcançável: `convert` faz `Integer.valueOf` antes de `validar` e o vazio sai em L-CT-00/E-13 | `Um dos campos obrigatórios (NUMERO_CONTA, TIPO_CONTA, MOVIMENTACAO_CONTA) não está preenchido.` | idem; mapa de cobertura: 32 cenário(s), caso `casos-por-regra/L-CT-01` | EXEC | K-05 |
| L-CT-02 | Data de abertura da conta | Se preenchida, A-01. **Não há** validação ABERTURA ≤ ENCERRAMENTO na carga | A-01 | idem; mapa de cobertura: 32 cenário(s), caso `casos-por-regra/L-CT-02` | EXEC | K-05 |
| L-CT-03 | Data de encerramento da conta | Se preenchida, A-01 | A-01 | idem; mapa de cobertura: 36 cenário(s), caso `casos-por-regra/L-CT-03` | EXEC | K-05 |
| L-CT-04 | Tipo de conta | TIPO_CONTA ∈ {1,2,3,4} | `Tipo de conta inválido. Valores possíveis: 1, 2, 3 e 4.` | idem; golden `05-conta-tipo-invalido`; mapa de cobertura: 52 cenário(s), caso `casos-por-regra/L-CT-04` | EXEC | K-05 |
| L-CT-05 | Movimentação da conta | MOVIMENTACAO_CONTA ∈ {1,2,3} | `Movimentação de Conta inválido. Valores possíveis: 1, 2 e 3` | idem; mapa de cobertura: 38 cenário(s), caso `casos-por-regra/L-CT-05` | EXEC | K-05 |

### 5.3 TITULARES — duas passadas sobre o mesmo arquivo
**Passada PESSOA** (`CarregarTabelaPessoaCmd`)
| ID | Nome | Descrição · Condições | Resultado | Evidências | Conf. | Componentes |
|---|---|---|---|---|---|---|
| L-PE-00 | Valor de renda numérico | (convert) VALOR_RENDA, se preenchido, deve ser `Double.valueOf` válido | `Valor numérico inválido no campo Valor Renda: <v>` | `D:carga/CarregarTabelaPessoaCmd`; mapa de cobertura: 9 cenário(s), caso `casos-por-regra/L-PE-00` | EXEC | K-05 |
| L-PE-01 | Obrigatórios da pessoa | Testa TIPO_PESSOA, CPF_CNPJ, NOME; a mensagem tem **lista fixa** de 5 campos (inclui PESSOA_INVESTIGADA e DATA_INICIO_RELACIONAMENTO) | `Um dos campos obrigatórios (TIPO_PESSOA, CPF_CNPJ_PESSOA, NOME_PESSOA, PESSOA_INVESTIGADA, DATA_INICIO_RELACIONAMENTO) não está preenchido.` | idem; mapa de cobertura: 23 cenário(s), caso `casos-por-regra/L-PE-01` | EXEC | K-05 |
| L-PE-02 | Data de atualização de renda | DATA_ATUALIZACAO_RENDA, se preenchida, passa A-01. **O erro é rotulado `EXTRATO`** (defeito de rótulo no original) | A-01 com rótulo `EXTRATO` | idem; reimplementação reproduz em COMPAT; mapa de cobertura: 11 cenário(s), caso `casos-por-regra/L-PE-02` | EXEC | K-05; R `Loader` |
| L-PE-03 | CPF/CNPJ do titular | Tipo `1`: `validarCpf(removerZerosEsquerda(cpf))` — só zeros ⇒ A-06 ⇒ E-13 (em vez de "CPF inválido"). Tipo `2`: `validarCnpj(cpf)`. CPF_CNPJ convertido para maiúsculas | `O CPF_CNPJ_PESSOA: <v> está inválido.` | idem; H5 (golden `06-cpf-invalido`, `07-cpf-so-zeros`); mapa de cobertura: 16 cenário(s), caso `casos-por-regra/L-PE-03` | EXEC | K-05, K-09 |
| L-PE-04 | Tipo de pessoa | TIPO_PESSOA ∈ {1,2} | `Tipo de pessoa inválido. Valores possíveis: 1 e 2.` | idem; mapa de cobertura: 23 cenário(s), caso `casos-por-regra/L-PE-04` | EXEC | K-05 |

**Passada TITULARIDADE** (`CarregarTabelaTitularidadeCmd`) — só roda se PESSOA não teve erro (E-07)
| ID | Nome | Descrição · Condições | Resultado | Evidências | Conf. | Componentes |
|---|---|---|---|---|---|---|
| L-TI-00 | Conta e pessoa existem | (convert) BANCO/AGENCIA/CONTA/TIPO_CONTA não vazios; conta (banco+agência+conta+tipo) deve existir em CONTAS; a pessoa da linha deve existir | `O código COMPE da instituição financeira não foi informado` / `O número da agência não foi informado` / `O número da conta não foi informado` / `O tipo de conta não foi informado` / `Conta não localizada. Banco: … Agencia: … Conta: … TipoConta: …` / `Pessoa não localizada. Linha: <n>` (rótulo físico, `NegocioException`) | `D:carga/CarregarTabelaTitularidadeCmd`, `ContaLocalizerCmd`, `PessoaLocalizerCmd`; mapa de cobertura: 65 cenário(s), caso `casos-por-regra/L-TI-00` | EXEC | K-05 |
| L-TI-01 | Obrigatórios da titularidade | TIPO_TITULAR, DATA_INICIO_RELACIONAMENTO, PESSOA_INVESTIGADA. Ramos eleitoral e não eleitoral têm a **mesma** condição e mensagem; a mensagem **não cita TIPO_TITULAR**, embora seja testado | lista fixa de L-PE-01 | idem; mapa de cobertura: 17 cenário(s), caso `casos-por-regra/L-TI-01` | EXEC | K-05 |
| L-TI-02 | Data de início (A-01 com exceção) | DATA_INICIO passa A-01, mas aqui o erro **lança `NegocioException`** (interrompe a linha) | `A DATA_INICIO_RELACIONAMENTO (<v>) é inválida ou o ano é anterior a 1900.` | idem; mapa de cobertura: 13 cenário(s), caso `casos-por-regra/L-TI-02` | EXEC | K-05 |
| L-TI-03 | Data de fim (A-01 com exceção) | idem para DATA_FIM | `A DATA_FIM_RELACIONAMENTO (<v>) é inválida …` | idem; mapa de cobertura: 14 cenário(s), caso `casos-por-regra/L-TI-03` | EXEC | K-05 |
| L-TI-04 | Tipo de titular | TIPO_TITULAR casa `[T1-9RLPO]` (1 char) | `Tipo de titular inválido. Valores possíveis: T, 1 até 9, R, L, P e O.` | idem; mapa de cobertura: 1 cenário(s), caso `casos-por-regra/L-TI-04` | EXEC | K-05 |
| L-TI-05 | Pessoa investigada | PESSOA_INVESTIGADA casa `[01]` | `Tipo de conta investigada inválido. Valores possíveis: 0 e 1.` (texto diz "conta" mas refere-se ao campo PESSOA_INVESTIGADA) | idem; mapa de cobertura: 7 cenário(s), caso `casos-por-regra/L-TI-05` | EXEC | K-05 |
| L-TI-06 | Ordem das datas de relacionamento | DATA_INICIO parseável (vazio ⇒ erro); DATA_FIM, se houver, parseável e ≥ início | `A DATA_INICIO_RELACIONAMENTO é inválida: <v>` / `A DATA_FIM_RELACIONAMENTO é inválida: <v>` / `A DATA_INICIO_RELACIONAMENTO (<i>) é posterior a DATA_FIM_RELACIONAMENTO (<f>).` | idem; mapa de cobertura: 6 cenário(s), caso `casos-por-regra/L-TI-06` | EXEC | K-05 |

### 5.4 EXTRATO — `CarregarArquivoExtratoCmd`
| ID | Nome | Descrição · Condições | Resultado | Evidências | Conf. | Componentes |
|---|---|---|---|---|---|---|
| L-EX-00 | Campos numéricos | (convert) CODIGO_CHAVE, TIPO_LANCAMENTO, VALOR_LANCAMENTO, VALOR_SALDO via `getInt/getLong` (A-05); não numérico ⇒ mensagem e valor 0 | `Campo <NOME> não é um número.` | `D:carga/CarregarArquivoExtratoCmd`; mapa de cobertura: 27 cenário(s), caso `casos-por-regra/L-EX-00` (pt_BR e en_US idênticos; segue PROV por A-05) | PROV (A-05) | K-05 |
| L-EX-01 | Conta do lançamento existe | conta (banco+agência+conta+tipo) deve existir em CONTAS | `Conta não localizada no arquivo de CONTAS.` | idem; mapa de cobertura: 52 cenário(s), caso `casos-por-regra/L-EX-01` | EXEC | K-05 |
| L-EX-02 | Obrigatórios do lançamento | DATA_LANCAMENTO, NUMERO_DOCUMENTO, DESCRICAO_LANCAMENTO (lista fixa de 8 campos na mensagem). A checagem de NATUREZA nunca falha por `null`: vazios caem em L-EX-04/05 | `Um dos campos obrigatórios (…) não está preenchido.` | idem; mapa de cobertura: 5 cenário(s), caso `casos-por-regra/L-EX-02` | EXEC | K-05 |
| L-EX-03 | Data do lançamento | DATA_LANCAMENTO passa A-01 | A-01 | idem; mapa de cobertura: 21 cenário(s), caso `casos-por-regra/L-EX-03` | EXEC | K-05 |
| L-EX-04 | Natureza do lançamento | NATUREZA_LANCAMENTO casa `[CD*]` | `Natureza de Lancamento inválida. Valores possíveis: C, D e *(asterisco).` | idem; mapa de cobertura: 16 cenário(s), caso `casos-por-regra/L-EX-04` | EXEC | K-05 |
| L-EX-05 | Natureza do saldo | NATUREZA_SALDO casa `[CD]` | `Natureza de saldo inválida. Valores possíveis: C e D.` | idem; mapa de cobertura: 22 cenário(s), caso `casos-por-regra/L-EX-05` | EXEC | K-05 |
| L-EX-06 | TIPO_LANCAMENTO × natureza (CNAB) | TIPO_LANCAMENTO coerente com a natureza via A-04 | `TIPO_LANCAMENTO inválido para NATUREZA_LANCAMENTO='C'\|'D'\|'*'. Veja tabela CNAB-240.` | idem; golden `08-extrato-cnab-invalido`; mapa de cobertura: 90 cenário(s), caso `casos-por-regra/L-EX-06` | EXEC | K-05, K-11 |
| L-EX-07 | Valores não negativos | VALOR_LANCAMENTO e VALOR_SALDO ≥ 0 | `VALOR_LANCAMENTO e VALOR_SALDO não podem ser negativos.` | idem; mapa de cobertura: 4 cenário(s), caso `casos-por-regra/L-EX-07` | EXEC | K-05 |

### 5.5 ORIGEM_DESTINO — `CarregarArquivoOrigemDestinoCmd`
| ID | Nome | Descrição · Condições | Resultado | Evidências | Conf. | Componentes |
|---|---|---|---|---|---|---|
| L-OD-00 | Conversão numérica | (convert) `Long.valueOf(CODIGO_CHAVE_OD)` direto: não numérico ⇒ E-13. Demais numéricos via `getInt/getLong` (A-05) | E-13 | `D:carga/CarregarArquivoOrigemDestinoCmd`; mapa de cobertura: 26 cenário(s), caso `casos-por-regra/L-OD-00` | EXEC (CODIGO_CHAVE_OD ⇒ E-13) / PROV (demais campos, A-05) | K-05 |
| L-OD-01 | "Tipo de conta OD" valida o campo errado | **Defeito:** `validarTipoConta` testa TIPO_**PESSOA** (`[12]`) com a mensagem de TIPO_CONTA. TIPO_CONTA_OD nunca é validado como intervalo; não numérico falha só na inserção (`Integer.valueOf` ⇒ E-13) | `TIPO_CONTA_OD inválido. Valores possíveis: 1, 2, 3 ou 4.` | idem; H6 (golden `15-od-tipo-pessoa-invalido`); mapa de cobertura: 13 cenário(s), caso `casos-por-regra/L-OD-01` | EXEC | K-05; R `Loader` |
| L-OD-02 | Tipo de pessoa OD | TIPO_PESSOA_OD, se preenchido, ∈ {1,2}. Valor inválido gera L-OD-01 **e** L-OD-02 | `TIPO_PESSOA_OD inválido. Valores possíveis: 1 ou 2.` | idem; golden `15`; mapa de cobertura: 13 cenário(s), caso `casos-por-regra/L-OD-02` | EXEC | K-05 |
| L-OD-03 | Situação de identificação | SITUACAO_IDENTIFICACAO deve ser 0 | `SITUACAO_IDENTIFICACAO inválido. Valor possível: 0.` | idem; mapa de cobertura: 3 cenário(s), caso `casos-por-regra/L-OD-03` | EXEC | K-05 |
| L-OD-04 | NUMERO_BANCO_OD com mais de 3 caracteres | Sem suporte do receptor ⇒ erro; com suporte (`Versao.suportaCNPJComoNumeroBanco`: major ≥ 4, ou major ≥ 3 e minor ≥ 3) exige 14 dígitos. **Inalcançável**: o layout limita NUMERO_BANCO_OD a 3 caracteres (E-04) e a linha com erro estrutural não chega a `validar()`. Versão do receptor vem do servidor (E-15) | `Número Banco inválido. O mesmo deve ser composto de apenas 3 dígitos (COMPE).` / `… (COMPE) ou 14 dígitos(CNPJ).` | idem; teste na reimplementação | INALC | K-05 |

---

## 6. Regras de conteúdo (R-*) — consultas SQL após a carga completa
Ordem = `ValidarAtendimento`. Cada linha retornada vira **um** erro (JOINs podem duplicar). Mensagem completa no código; abaixo, prefixo. Unidade de VALOR_* (centavos?) é **ND** (D3). Semântica de NULL/`NOT IN`/`REPEAT`/`UCASE` do HSQLDB: PROV quanto a detalhes.

### 6.1 AGENCIAS
| ID | Nome | Descrição · Condições | Resultado | Evidências | Conf. | Componentes |
|---|---|---|---|---|---|---|
| R-AG-01 | Agência duplicada | `GROUP BY NUMERO_BANCO, NUMERO_AGENCIA HAVING COUNT(*)>1`; linha = MAX(LINHA) | `Agência duplicada: NUMERO_BANCO: bbb, NUMERO_AGENCIA: aaaa` | `D:rules/ValidarAgenciaRepetidaRule`; golden `12-agencia-duplicada`; mapa de cobertura: 127 cenário(s), caso `casos-por-regra/R-AG-01` | EXEC | K-06; R `ContentRules` |

### 6.2 CONTAS (10, nesta ordem)
| ID | Nome | Descrição · Condições | Resultado | Evidências | Conf. | Componentes |
|---|---|---|---|---|---|---|
| R-CT-01 | Conta com separador | NUMERO_CONTA contém `.`, `/`, `\` ou `-` | `Conta com separador: <conta>` | `D:rules/ValidarContaSeparadorRule`; mapa de cobertura: 1 cenário(s), caso `casos-por-regra/R-CT-01` | EXEC | K-06 |
| R-CT-02 | Conta duplicada | (agência, NUMERO_CONTA, TIPO_CONTA) repetido. **Peculiaridade:** herda de `AbstractRule`, não de `AbstractContaRule` ⇒ rótulo `Erro no arquivo: null` | `Conta duplicada - Linha: …` | `D:rules/ValidarContaRepetidaRule`; achado da cobertura JaCoCo (`A:05`); mapa de cobertura: 1 cenário(s), caso `casos-por-regra/R-CT-02` | EXEC | K-06 |
| R-CT-03 | Conta sem titulares | MOVIMENTACAO ∈ {1,2,3} e a conta não está em TITULARIDADE | `Conta sem registro correspondente em Titulares. NUMERO_BANCO: …` | `D:rules/ValidarContaSemTitularesRule`; mapa de cobertura: 95 cenário(s), caso `casos-por-regra/R-CT-03` | EXEC | K-06 |
| R-CT-04 | Conta sem pessoa investigada | MOVIMENTACAO ∈ {1,2} e nenhuma titularidade com PESSOA_INVESTIGADA=1 | `Conta sem pessoa investigada. …` | `D:rules/ValidarContaSemTitularInvestigado`; mapa de cobertura: 5 cenário(s), caso `casos-por-regra/R-CT-04` | EXEC | K-06 |
| R-CT-05 | Atendimento sem movimentação | Nenhuma conta com MOVIMENTACAO ∈ {1,2} (erro na linha 0) | `Atendimento sem nenhuma movimentação de conta.` | `D:rules/ValidarContaSemMovimentacao`; mapa de cobertura: 55 cenário(s), caso `casos-por-regra/R-CT-05` | EXEC | K-06 |
| R-CT-06 | Movimentação deveria ser 3 | MOVIMENTACAO ≠ 3 e a conta não tem investigado (um erro por titularidade, por JOIN) | `Conta com atributo MOVIMENTACAO_CONTA incorreto. Deve ser 3, pois não é conta de investigado. …` | `D:rules/ValidarMovimentacaoContaInvestigadoNaoRule`; mapa de cobertura: 3 cenário(s), caso `casos-por-regra/R-CT-06` | EXEC | K-06 |
| R-CT-07 | Mais de um titular T | Mais de um titular `T` na conta | `Conta com mais de um Titular tipo T. …` | `D:rules/ValidarTitularUnicoRule`; mapa de cobertura: 72 cenário(s), caso `casos-por-regra/R-CT-07` | EXEC | K-06 |
| R-CT-08 | Movimentação deveria ser 1 ou 2 | Titular investigado (=1) em conta com MOVIMENTACAO ∉ {1,2} (duplica por JOIN) | `… Deve ser 1 ou 2, pois é um investigado. …` | `D:rules/ValidarMovimentacaoContaInvestigadoSimRule`; mapa de cobertura: 55 cenário(s), caso `casos-por-regra/R-CT-08` | EXEC | K-06 |
| R-CT-09 | Movimentação 1 sem extrato | MOVIMENTACAO=1 e conta sem lançamentos em EXTRATO | `Conta definida com movimentação (MOVIMENTACAO_CONTA = 1), porém não possui lançamentos em EXTRATO. …` | `D:rules/ValidarContaInvestigadoComMovimentacaoSemExtratoRule`; mapa de cobertura: 2 cenário(s), caso `casos-por-regra/R-CT-09` | EXEC | K-06 |
| R-CT-10 | Movimentação 2 com extrato | MOVIMENTACAO=2 e conta com lançamentos em EXTRATO | `Conta definida sem movimentação (MOVIMENTACAO_CONTA = 2), porém possui lançamentos em EXTRATO. …` | `D:rules/ValidarContaInvestigadaSemMovimentacaoComExtratoRule`; golden `09-conta-mov2-com-extrato`; mapa de cobertura: 46 cenário(s), caso `casos-por-regra/R-CT-10` | EXEC | K-06 |

### 6.3 TITULARES (7)
| ID | Nome | Descrição · Condições | Resultado | Evidências | Conf. | Componentes |
|---|---|---|---|---|---|---|
| R-TI-01 | Dados obrigatórios do titular investigado | Titular `T` investigado com qualquer de NOME_DOC, NUMERO_DOC, LOGRADOURO, CIDADE, UF, PAIS, CEP, TELEFONE nulo | `O titular de conta investigada deve ter os seguintes dados preenchidos: …` | `D:rules/ValidarDadosObrigatoriosTitularRule`; mapa de cobertura: 81 cenário(s), caso `casos-por-regra/R-TI-01` | EXEC | K-06 |
| R-TI-02 | Relacionamento anterior à abertura da conta | `INICIO < ABERTURA_CONTA` ou `FIM < ABERTURA_CONTA` | `A DATA_INICIO_RELACIONAMENTO ou DATA_FIM_RELACIONAMENTO está menor que a DATA_ABERTURA_CONTA (…` | `D:rules/ValidarDataAberturaRelacionamentoTitularidadeRule`; mapa de cobertura: 5 cenário(s), caso `casos-por-regra/R-TI-02` | EXEC | K-06 |
| R-TI-03 | Relacionamento posterior ao encerramento | `INICIO > ENCERRAMENTO_CONTA` ou `FIM > ENCERRAMENTO_CONTA` | `… está maior que a DATA_ENCERRAMENTO_CONTA (…` | `D:rules/ValidarDataEncerramentoRelacionamentoTitularidadeRule`; mapa de cobertura: 4 cenário(s), caso `casos-por-regra/R-TI-03` | EXEC | K-06 |
| R-TI-04 | Investigado do atendimento ausente em TITULARES | Investigado (TEVE_RELACIONAMENTO=1 e TEVE_CONTA=1) cujo CPF/CNPJ (14 dígitos com zeros) não aparece em TITULARES como investigado; **reportado com linha −1** | `O CPF_CNPJ … cadastrado no atendimento como investigado, não foi encontrado no arquivo TITULARES como PESSOA_INVESTIGADA = 1` | `D:rules/ValidarInvestigadoCadastroSimArquivoNaoRule`; achado da cobertura JaCoCo; mapa de cobertura: 1 cenário(s), caso `casos-por-regra/R-TI-04` | EXEC | K-06 |
| R-TI-05 | Pessoa de investigado marcada 0 | Pessoa ligada a investigado (rel=1, conta=1) com PESSOA_INVESTIGADA=0 | `… deve possuir o atributo PESSOA_INVESTIGADA = 1` | `D:rules/ValidarInvestigadoPessoaInvestigada0Rule`; mapa de cobertura: 32 cenário(s), caso `casos-por-regra/R-TI-05` | EXEC | K-06 |
| R-TI-06 | PESSOA_INVESTIGADA=1 não cadastrada | CPF/CNPJ com PESSOA_INVESTIGADA=1 que não está entre os investigados (rel=1, conta=1) | `… informado como PESSOA_INVESTIGADA = 1 não foi cadastrado no atendimento.` | `D:rules/ValidarInvestigadoCadastroNaoArquivoSimRule`; mapa de cobertura: 38 cenário(s), caso `casos-por-regra/R-TI-06` | EXEC | K-06 |
| R-TI-07 | Conta investigada sem titular T | Conta com investigado sem titular tipo `T` | `Conta investigada sem Titular do tipo T. …` | `D:rules/ValidarContaInvestigadaSemTitularTRule`; mapa de cobertura: 6 cenário(s), caso `casos-por-regra/R-TI-07` | EXEC | K-06 |

### 6.4 EXTRATO (5)
| ID | Nome | Descrição · Condições | Resultado | Evidências | Conf. | Componentes |
|---|---|---|---|---|---|---|
| R-EX-01 | Lançamento fora do período de afastamento | Lançamento em conta de investigado com `DATA_LANCAMENTO < INICIO_AFASTAMENTO` ou `> FIM_AFASTAMENTO` (datas do **investigado**) | `Lançamento em dd/MM/yyyy fora do período de afastamento (…) do investigado: CPF_CNPJ (…)` | `D:rules/ValidarExtratoForaPeriodoAfastamentoRule`; D6 resolvida; mapa de cobertura: 124 cenário(s), caso `casos-por-regra/R-EX-01` | EXEC | K-06; R `ContentRules` |
| R-EX-02 | Lançamento fora do período da conta | `DATA_LANCAMENTO < ABERTURA_CONTA` ou `> ENCERRAMENTO_CONTA` (NULL não dispara). NPE do original quando `DATA_ABERTURA_CONTA` é nula e há lançamento após encerramento: reproduzida como falha de infraestrutura em COMPAT | `A DATA_LANCAMENTO (dd/MM/yyyy) está fora do período de DATA_ABERTURA_CONTA (…` | `D:rules/ValidarExtratoForaPeriodoContaAbertaRule`; golden `10-extrato-fora-periodo`; mapa de cobertura: 6 cenário(s), caso `casos-por-regra/R-EX-02` | EXEC | K-06 |
| R-EX-03 | Lançamento sem origem/destino | Lançamento sem registro em ORIGEM_DESTINO | `Lançamento em EXTRATO sem correspondente em ORIGEM_DESTINO. CODIGO_CHAVE_EXTRATO: n` | `D:rules/ValidarExtratoSemOrigemDestinoRule`; mapa de cobertura: 1 cenário(s), caso `casos-por-regra/R-EX-03` | EXEC | K-06 |
| R-EX-04 | Extrato em conta não investigada | Lançamento em conta com MOVIMENTACAO=3 | `No arquivo CONTAS, se o campo MOVIMENTAÇÃO DA CONTA estiver com o parâmetro "3"(conta não investigada) não pode haver nenhum registro para essa conta no arquivo EXTRATO` | `D:rules/ValidarExtratoContaNaoInvestigadaRule`; mapa de cobertura: 1 cenário(s), caso `casos-por-regra/R-EX-04` | EXEC | K-06 |
| R-EX-05 | Valor do extrato × soma de OD | `VALOR_LANCAMENTO ≠ SUM(VALOR_OPERACAO)` dos OD do lançamento | `O Valor do lancamento em EXTRATO (CODIGO_CHAVE: n) não é igual ao somatório dos registros correspondentes em ORIGEM_DESTINO.` | `D:rules/ValidarValorExtratoOrigemDestinoRule`; golden `11-valor-extrato-vs-od`; mapa de cobertura: 83 cenário(s), caso `casos-por-regra/R-EX-05` | EXEC | K-06 |

### 6.5 ORIGEM_DESTINO
Limiares/datas **hard-coded** em SQL (versionados na reimplementação em `implementacao/limiares-6.2.6.properties`).
| ID | Nome | Descrição · Condições | Resultado | Evidências | Conf. | Componentes |
|---|---|---|---|---|---|---|
| R-OD-01 | Identificação em TED/DOC | Perfil não eleitoral. `TIPO_LANCAMENTO IN (120,209)`, `EXTRATO.DATA_LANCAMENTO ≥ 2006-01-01`, `EXTRATO.VALOR_LANCAMENTO ≥ 1.000.000` (**valor do extrato**, não do OD), identificação ausente (CPF_CNPJ_OD, NOME, BANCO, AGENCIA ou CONTA nulos; BANCO=0; AGENCIA=0; CONTA só zeros) **e** NÃO (AGENCIA=9999 ∧ CONTA='99999999999999999999' ∧ `UCASE(OBS)='NAO-CORRENTISTA'`) | `É OBRIGATÓRIA a identicação do depositante/favorecido de Transferências Interbancária (TED, DOC) …` | `D:rules/ValidarIdentificacaoTedDocRule`; golden `14-ted-sem-identificacao`; 800 cenários dirigidos a limiares; mapa de cobertura: 72 cenário(s), caso `casos-por-regra/R-OD-01` | EXEC | K-06 |
| R-OD-02 | Identificação em transferência no mesmo banco | Perfil não eleitoral. `TIPO IN (117,213)`, mesma `EXTRATO.DATA_LANCAMENTO`/`EXTRATO.VALOR_LANCAMENTO` de R-OD-01, identificação ausente (sem exceção NAO-CORRENTISTA) | `… de Transferências entre Contas …` | `D:rules/ValidarIdentificacaoTransferenciaMesmoBancoRule`; cenários dirigidos; mapa de cobertura: 73 cenário(s), caso `casos-por-regra/R-OD-02` | EXEC | K-06 |
| R-OD-03 | Identificação em espécie | Perfil não eleitoral. `TIPO IN (123,220)` e [`DATA_LANCAMENTO≥2013-07-01 ∧ VALOR_LANCAMENTO≥1.000.000` ∨ `≥2013-01-02 ∧ ≥5.000.000` ∨ `≥2012-07-01 ∧ ≥10.000.000`] (todas em `EXTRATO`) e `NOME_PESSOA_OD` nulo e `UCASE(OBS)≠'099 - NEGATIVA DE IDENTIFICACAO DO PORTADOR'` | `É obrigatória a identificação do depositante/favorecido de transações em espécie …` | `D:rules/ValidarIdentificacaoTransacaoEmEspecieRule`; cenários dirigidos; mapa de cobertura: 14 cenário(s), caso `casos-por-regra/R-OD-03` | EXEC | K-06 |
| R-OD-04 | CPF/CNPJ válido em transferências | Ambos os perfis. `TIPO IN (117,120,209,213)`, `DATA ≥ 2006-01-01`, CPF_CNPJ_OD preenchido: tipo `1` ⇒ CPF; `2` ⇒ CNPJ (14); outro/vazio ⇒ CNPJ **ou** CPF (últimos 11 de 14). Ordem das linhas de erro não é contratual (D13) | `O campo CPF_CNPJ_PESSOA_OD com o valor <v> é inválido [para TIPO_PESSOA_OD = …]` | `D:rules/ValidarCpfCnpjTransferenciaTedDocOrigemDestinoRule`; mapa de cobertura: 188 cenário(s), caso `casos-por-regra/R-OD-04` | EXEC (conteúdo); ordem ND | K-06 |
| R-OD-E1 | Identificação eleitoral em transferências | Perfil **eleitoral** (substitui R-OD-01…03). `TIPO IN (117,120,209,213)` e (CPF_CNPJ_OD, BANCO, AGENCIA ou CONTA nulos; BANCO=0; AGENCIA=0; CONTA só zeros) — **sem** limite de data/valor (sem limiar algum) | `É OBRIGATÓRIA a identicação … de transferências no mesmo banco ou transferências interbancárias (TED/DOC) …` | `D:rules/ValidarIdentificacaoTedDocTransfEleitoralRule`; mapa de cobertura: 78 cenário(s), caso `casos-por-regra/R-OD-E1` | EXEC | K-06 |
| R-OD-E2 | Depósito em cheque/dinheiro eleitoral | Perfil **eleitoral**. `TIPO=201` e `EXTRATO.VALOR_LANCAMENTO ≥ 10.000` e CPF_CNPJ_OD nulo | `É OBRIGATÓRIA a identicação … de depósito em cheque e/ou dinheiro no campo CPF_CNPJ_OD.` | `D:rules/ValidarDepositoDinheiroChequeEleitoralRule`; golden `13-eleitoral-deposito-sem-cpf`; mapa de cobertura: 77 cenário(s), caso `casos-por-regra/R-OD-E2` | EXEC | K-06 |

---

## 7. Fluxo de corretoras (GAB) — CONFIRMADO por execução (`A:04` §G)
Arquivos `<caso>_GAB109.txt`, `_GAB112.txt`, `_GAB800.txt` (ISO-8859-1, largura fixa; layout v1/v2). Larguras de linha: GAB109 127/333, GAB112 307/726, GAB800 298/320 (v1/v2). Campos por `substring`+`trim`.
| ID | Nome | Descrição · Condições | Resultado | Evidências | Conf. | Componentes |
|---|---|---|---|---|---|---|
| G-01 | Arquivos GAB vazios | `verificarArquivosGabVazios`, 3 checagens independentes: GAB112 não pode ser vazio; GAB800 vazio com GAB109 com dados; GAB109 vazio com GAB800 com dados | `O arquivo GAB-112 não pode ser vazio.` / `O arquivo GAB-800 não pode ser vazio quando o arquivo GAB-109 possui dados.` / `O arquivo GAB-109 não pode ser vazio quando o arquivo GAB-800 possui dados.` | `D:ControladorValidador.verificarArquivosGabVazios`; `T:diferencial/diferencial_gab.py` | EXEC | K-03, K-07; R `gab/GabPipeline` |
| G-02 | Largura da linha GAB | Largura deve ser exatamente a soma dos campos (v1/v2) | `Certifique-se de completar com espaços os campos que não atingirem o tamanho predeterminado e Não use tab para separar os campos.` (rótulo = nome físico) | `D:carga/GetCamposGabColunaFixaCmd`; `T:diferencial/gab_layouts.json` | EXEC | K-07; R `gab/GabLayouts` |
| G-DEF | **Defeito: nenhum GAB não vazio é aprovado** | `getCamposArquivo()` devolve `null` nos 6 carregadores GAB; `validarCampos` faz `camposArquivo[i]` ⇒ NPE ⇒ **toda linha com a largura certa** gera E-13, uma por linha (sem o filtro E-06). Logo G-03…G-07 e R-G* nunca rodam no app real. Pilha: `Cannot load from object array because "this.camposArquivo" is null at …validarCampos(AbstractCarregarArquivoCmd.java:299)` | `Erro desconhecido. Consulte o log do sistema.` por linha | `D:carga/CarregarArquivoGab*Cmd`, `AbstractCarregarArquivoCmd`; 3.000 cenários idênticos (modo com defeito) | EXEC | K-07; R `gab/*` (modo COMPAT) |
| G-03 | Obrigatórios GAB | Vazio = null/trim vazio. GAB109: CODIGO_DO_CLIENTE, DESCRICAO_DO_LANCAMENTO, VALOR_DO_LANCAMENTO, NATUREZA_LANCAMENTO, DATA_DE_LIQUIDACAO, DATA_DO_MOVIMENTO, DATA_DE_REFERENCIA. GAB800: CODIGO_DO_CLIENTE, DESCRICAO_DO_LANCAMENTO, VALOR_DO_LANCAMENTO, NOME_DO_CLIENTE, DATA_DE_LIQUIDACAO, ENDERECO, DATA_DE_REFERENCIA, SALDO_INICIAL, NATUREZA_DO_SALDO_INICIAL, NATUREZA_DO_LANCAMENTO. GAB112: 20 verificações (DOCUMENTO_DO_CLIENTE e COMPLEMENTO_DO_CEP verificados **duas vezes** ⇒ mensagem duplicada) | `O campo <CAMPO> é obrigatório.` | `D:carga/CarregarArquivoGab*Cmd`; execução com remendo **em memória** (campo nulo preenchido por reflexão), 3.000 cenários idênticos à lógica pretendida | EXEC (só sob remendo; inatingível no app real, ver G-DEF) | K-07 |
| G-04 | Datas GAB | `yyyyMMdd` (parse leniente + conferência de ida e volta). GAB109: só o 1º erro (cadeia else-if: movimento, referência, liquidação). GAB112/800: um erro por campo. Não parseável ⇒ E-13 | GAB109: `O campo X está incorreto. A data deve estar no formato AAAAMMDD e válida.`; GAB112/800: `A data deve estar no formato AAAAMMDD e válida.  Campo: X` (dois espaços) | idem | EXEC (sob remendo) | K-07 |
| G-05 | Numéricos GAB | `Long.parseLong`/`BigInteger`: GAB109 CODIGO_DO_CLIENTE, VALOR_DO_LANCAMENTO; GAB112 CODIGO_DO_CLIENTE, DOCUMENTO_DO_CLIENTE, CODIGO_DO_CLIENTE_MERCADO, CODIGO_DA_ATIVIDADE; GAB800 CODIGO_DO_CLIENTE, VALOR_DO_LANCAMENTO, SALDO_INICIAL | `O dado informado não é um número. Campo: CODIGO_DO_CLIENTE` (1º campo) / `… não é um número.  Campo X` (demais) | idem | EXEC (sob remendo) | K-07 |
| G-06 | Domínios GAB | Naturezas ∈ {C,D}; GAB112: CONSTANTE_0000A=`0000A`; TIPO_DE_PESSOA ∈ {F,J}; MERCADO ∈ {BOVESPA,BMF,SWAP}; ESTADO sigla de UF (27, maiúsculas); DOCUMENTO_DO_CLIENTE com 15 caracteres; CPF (F) ou CNPJ (J) válido após remover zeros à esquerda (só zeros ⇒ A-06 ⇒ E-13) | `Informe C para crédito ou D para débito. Campo: X`; `Valor esperado para o campo CONSTANTE_0000A: 0000A.`; `Informe F para pessoa física ou J para pessoa jurídica. Campo: TIPO_DE_PESSOA`; `Informe BOVESPA para mercado à vista, BMF para derivativos ou SWAP para balcão. Campo: MERCADO`; `A sigla do campo estado está incorreta`; `O campo DOCUMENTO_DO_CLIENTE é obrigatório conter 15posições. Devendo ser completementado com zeros à esquerda.`; `CPF inválido para o tipo de pessoa: F. Campo: DOCUMENTO_DO_CLIENTE` / `CNPJ inválido …` | idem | EXEC (sob remendo) | K-07 |
| G-07 | Gravação GAB layout v2 | Colunas NUMERIC (CHAVE_OD, BANCO/AGENCIA/CONTA de origem e destino em GAB109; MEMBRO_COMPENSACAO, AGENTE_CUSTODIA, CODIGO_CORRETORA em GAB112) rejeitam texto não numérico; **DATA_SITUACAO (GAB112 v2) usa `getDateGabSql` sem validação**: vazia/inválida ⇒ NPE ⇒ E-13 | E-13 | idem | EXEC (sob remendo) | K-07 |
| R-G109 | Cliente do GAB109 existe no GAB112 | CODIGO_DO_CLIENTE (comparação **numérica**) do GAB109 deve existir no GAB112 | `O cliente com o código N não foi informado no arquivo gab112.` | `D:rules/ValidarGab112NaoGab109SimRule` | EXEC (sob remendo) | K-06, K-07 |
| R-G800 | Cliente do GAB800 existe no GAB112 | idem para GAB800 | idem | `D:rules/ValidarGab112NaoGab800SimRule` | EXEC (sob remendo) | K-06, K-07 |
| R-G112-1 | Nenhum investigado com relacionamento | Todos os investigados com TEVE_RELACIONAMENTO≠1 (linha 0, rótulo `Investigado`); com INVESTIGADO vazio a consulta não devolve linha e o original lança exceção de infraestrutura | `Não foi encontrado relacionamento no arquivo investigado` | `D:rules/InvestigadoGabNenhumRelacionamentoRule` | EXEC (sob remendo) | K-06 |
| R-G112-2 | Documento do GAB112 sem relacionamento | DOCUMENTO_DO_CLIENTE (sem zeros à esquerda) não consta dos investigados | `O CPF/CNPJ <doc> não teve relacionamento com esta instituição no período do afastamento de sigilo.` | `D:rules/ValidarGab112InvestigadoSemRelacionamentoRule` | EXEC (sob remendo) | K-06 |
| R-G112-3 | Investigado ausente do GAB112 | Investigado com relacionamento ausente do GAB112 (linha 0) | `O investigado com CPF/CNPJ <cpf> não foi encontrado no arquivo cadastral(gab112).` | `D:rules/ValidarGab112InvestigadoNaoInformadoRule` | EXEC (sob remendo) | K-06 |

Rótulo de arquivo: mensagens de campo `Gab109`/`Gab112`/`Gab800` (+`Ver2`); conteúdo `GAB109`/`GAB112`/`GAB800`. **Código morto:** `ValidarGab112SimGab109NaoRule`, `ValidarGab112SimGab800NaoRule` (nunca injetadas).

---

## 8. Itens não catalogados como regra, propostas e lacunas

### 8.1 Propostas de novas regras
*Nenhuma pendente.* Os IDs E-12…E-15 foram **criados na consolidação** (origem: `A:03` §3/§7, `A:04` §B e `A:05` nível 3) para dar identificador a comportamentos já documentados porém sem ID em `04-regras-validacao.md`; todos os demais IDs preservam os de `04`. Subagentes devem registrar aqui, em seção própria do seu retorno, qualquer proposta (campos: título, evidência, impacto).

### 8.2 Código morto (não são regras; não executam)
Sobrecargas `validar(ExtratoOrigemDestino)` em **6** regras de OD (`ValidarValorExtratoOrigemDestinoRule` só tem o termo no nome) (condições diferentes das consultas SQL; ex.: `dataLimite20120701 = "01-07-2013"`); `ValidarGab112Sim{Gab109,Gab800}NaoRule`; `InvestigadoLocalizerCmd`; `ValidarCasoPossuiInvestigadoComRelacionamentoRule` (usada só por `frmIdentificaAtendimento`). Fonte: `A:03` §8.

### 8.3 Peculiaridades do original (todas dentro de regras acima)
E-06, E-05b, L-AG-04 (dois erros), L-PE-02 (rótulo EXTRATO), L-OD-01 (campo errado), L-PE-03 (CPF só zeros ⇒ E-13), L-EX-02 (`== null` nunca verdadeiro), A-05 (leniente/locale), R-CT-06/R-CT-08 (duplicação por JOIN), R-CT-02 (rótulo `null`), R-TI-04 (linha −1), G-DEF.

### 8.4 Informações desconhecidas / lacunas conhecidas
| Cód. | Lacuna | Fonte |
|---|---|---|
| LAC-01 | Unidade de VALOR_* (centavos?) e sentido de negócio dos limiares 1.000.000/5.000.000/10.000/10.000.000 | D3 |
| LAC-02 | Locale `en_US`: **testado** em 2.140 cenários (0 divergências original × reimplementação; saídas do original idênticas às de `pt_BR` em 700 cenários comparados diretamente). A-05 segue PROV por depender do locale da máquina | D4/H8 |
| LAC-03 | Termos de uso/licença para engenharia reversa e reimplementação **não verificados juridicamente**. Verificação estática: o instalador (`LicenseText` vazio) e os JARs do MPF (`simba-validador.jar`, `spea-client-2.4.4.jar`) **não contêm EULA, termos ou arquivo de licença**; só há avisos de copyright do JRE e de bibliotecas abertas. Requer consulta ao MPF/jurídico | D11 |
| LAC-04 | Se o servidor aceita envio com erros de validação local | D15, E-11 |
| LAC-05 | Ordem exata das colunas e nomes exatos dos campos bancários não estão tabulados neste catálogo; fonte: `getCamposArquivo()` / `N:Schemas` | consolidação, revisão P-11 |
| LAC-06 | Telas de cadastro de atendimento/investigados (`frmDadosAtendimento`, `frmIdentificaAtendimento`), relatório PDF, empacotamento e transmissão: lidos só por amostragem; origem de ATENDIMENTO/INVESTIGADO | D7, D10 |
| LAC-07 | Papel de `simba-validador.exe` (PE32 GUI, 73.728 B) e 2 file entries anômalas do instalador | D8, D9 |
| LAC-08 | Codificação (acentos/BOM), dados reais, volume > 250.000 linhas só testado sintéticamente | `A:05` |
| LAC-09 | **Fechada na v1.2** para regras com mensagem própria (71 regras observadas no original; mapa em `docs/MAPA_COBERTURA_REGRAS.md`). Restam sem observação direta: E-01, E-05, E-09, E-15 (nível 3/GUI ou estáticas) e L-OD-04 (inalcançável). Cobertura de linhas do original: 82,9% em `rules+carga+validacao` | `A:05`, mapa |
| LAC-10 | `ValidarGab112SimGab800NaoRule` e 6 `Abstract*Rule` lidas parcialmente (inferidas por tamanho idêntico) | `A:04` §F |

### 8.5 Discrepâncias internas da documentação de origem (tratadas na consolidação)
1. `04-regras-validacao.md` e `03-arquitetura.md` ainda afirmam "o aplicativo nunca foi executado / nada confirmado por execução"; `05-testes.md` e `progresso.md` (posteriores) registram execução diferencial nos níveis 1–3. **Este catálogo adota 05/progresso** e usa a escala §1.2. Também em `03` §3 a leitura sobre o botão "Continuar" foi corrigida (E-11).
2. `07` §1 estima "≈ 84 itens (11 estruturais, 6 algoritmos, 38 de carga, 29 de conteúdo)"; a contagem real deste catálogo está em §9 e difere dessa estimativa.
3. `05` cita "2.316 cenários" (nível 2) e `README`/`progresso` "2.334" (inclui 18 dirigidos pela cobertura). Aqui: 2.316 + 18 = 2.334.
4. `05` cita 31 testes JUnit (`07` §8 e `implementacao/README.md`) e 48 testes Python; ambos citados como declarados, não reexecutados nesta consolidação.

---

## 9. Estatísticas do catálogo
| Grupo | Qtde | IDs |
|---|---|---|
| Estruturais/fluxo (E) | 16 | E-01…E-15 + E-05b |
| Algoritmos (A) | 7 | A-01…A-07 |
| Carga por linha (L) | 38 | L-AG 7, L-CT 6, L-PE 5, L-TI 7, L-EX 8, L-OD 5 |
| Conteúdo SQL bancário (R) | 29 | R-AG 1, R-CT 10, R-TI 7, R-EX 5, R-OD 6 |
| Corretoras (G + R-G) | 13 | G-01…G-07, G-DEF, R-G109, R-G800, R-G112-1/2/3 |
| **Total** | **103** | 99 herdados de `04` + 4 criados (E-12…E-15) |

Herança: `04` lista 94 IDs em linhas de tabela; 3 linhas agrupadas (`L-AG-02/03`, `L-CT-02/03`, `L-TI-02/03`) foram **desdobradas** em 6 IDs (+3, sem renumerar os demais) e `R-G109 / R-G800` (linha combinada) em 2 IDs (+2): 94 + 3 + 2 = 99. Conferência por script: 103 linhas, 103 IDs únicos, todas com 7 colunas.

| Confiança | Qtde | IDs |
|---|---|---|
| EXEC (plena) | 86 | inclui 49 promovidas de EST pelo mapa de cobertura (v1.2) |
| EXEC sob remendo em memória | 10 | G-03…G-07, R-G109/G800/G112-1/2/3 (inatingíveis no app real por G-DEF) |
| EST | 4 | E-01, E-05, E-09, E-15 |
| PROV | 2 | A-05, L-EX-00 (locale: `pt_BR` e `en_US` sem diferença observada, mas A-05 depende da máquina) |
| INALC | 1 | L-OD-04 |
| **Total** | **103** | |

Critério da promoção a EXEC na v1.2: a mensagem da regra foi **emitida pelo aplicativo original** (sandbox sem rede, pipeline real em HSQLDB) com saída idêntica à da reimplementação em COMPAT, em ≥ 1 cenário do corpus de 2.933 + 7 cenários (`docs/MAPA_COBERTURA_REGRAS.md`). Cada regra observada tem um caso mínimo em `analise/testes/casos-por-regra/<ID>/`.

---

## 10. Histórico de revisões
| Versão | Mudanças |
|---|---|
| 1.0 | Consolidação inicial (103 regras). |
| 1.1 | Correções da revisão independente (`docs/REVISAO_DOCUMENTACAO.md`), **sem renumerar ou criar IDs**: P-02 (E-07), P-04 (§1.3 regra de rótulo por mecanismo; E-04 lógico), P-09 (E-14), P-11 (§5.0/LAC-05), P-13 (prefixo `DX:`), P-20 (R-OD: coluna `EXTRATO.VALOR_LANCAMENTO`), P-21 (6 sobrecargas), P-22 (E-05b), P-23 (L-CT-01), P-24 (L-TI-00/01), P-25 (E-09), P-32 (E-11). |
| 1.2 | Correções da 2ª passada (`docs/REVISAO_DOCUMENTACAO_2.md`): Q-04 (L-AG-04), Q-07 (E-09 evidência), Q-08 (G-01 rótulo físico em §1.3). Confiança por regra revisada a partir do mapa de cobertura (ver §11). |
