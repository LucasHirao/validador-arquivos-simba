# Documentação Técnica — Validador Bancário SIMBA 6.2.6 e reimplementação independente

**Status:** versão 1.2 (redigida pelo arquiteto de software e revisada; sincronizada com o catálogo `docs/CATALOGO_REGRAS.md` v1.2 e dos artefatos de engenharia reversa).
**Escopo:** descreve **como o sistema é implementado** (apenas o que foi comprovado). O "o que valida", em linguagem de negócio, fica no documento funcional; aqui as regras aparecem pelo **ID do catálogo** e pelo componente que as implementa.
**Fontes consultadas:** `docs/CATALOGO_REGRAS.md`; `analise/01…07`, `analise/progresso.md`, `analise/catalogo-instalador.json`; código decompilado (somente leitura) em `analise/decompilado/` e `analise/decompilado-libs/spea-client/`; `implementacao/validador-independente/` (src/main, src/test, pom.xml), `implementacao/limiares-6.2.6.properties`, `implementacao/README.md`; `analise/testes/**`.
**Código de terceiros:** o código decompilado não é reproduzido; citam-se caminho/classe/método e, no máximo, fragmentos mínimos. Toda classe e método citados neste documento foram verificados por `ls`/`grep`/leitura no repositório.

---

## 0. Convenções deste documento

### 0.1 Prefixos de caminho (iguais ao catálogo §1.1)
| Prefixo | Caminho real |
|---|---|
| `D:` | `analise/decompilado/br/mp/mpf/spea/simba/validador/` (app decompilado) |
| `DX:` | `analise/decompilado/` (raiz; prefixo do catálogo v1.1 para os pacotes `DAO` e `telas`, irmãos de `br/`: `DX:DAO/Mapa.java`, `DX:telas/frmSelecionaDiretorio.java`). Corrige o antigo `D:../DAO` (RO-02, resolvido na v1.1) |
| `L:` | `analise/decompilado-libs/spea-client/br/mp/mpf/` (biblioteca `spea-client` 2.4.4) |
| `N:` | `implementacao/validador-independente/src/main/java/estudo/simba/validador/` |
| `NT:` | `implementacao/validador-independente/src/test/java/estudo/simba/validador/` |
| `T:` | `analise/testes/` |
| `A:` | `analise/` |

### 0.2 Confiança
Mesmos códigos do catálogo §1.2: **EXEC** (observado no original executado em sandbox sem rede), **EST** (determinado estaticamente), **PROV**, **INALC**, **ND**. Na matriz (§14) a coluna "Conf." repete o valor do catálogo; **este documento não altera confianças** (catálogo v1.2: EXEC 86, EXEC sob remendo 10, EST 4 [E-01, E-05, E-09, E-15], PROV 2 [A-05, L-EX-00], INALC 1 [L-OD-04]; L-OD-00 = EXEC para CODIGO_CHAVE_OD ⇒ E-13 e PROV para os demais campos, A-05). Quando algo aqui é inferência do arquiteto (e não fato do código ou do catálogo), vem marcado **[HIP]**.

### 0.3 Abreviações de testes (usadas na matriz)
- `JU:Casos`, `JU:Modos`, `JU:Prim`, `JU:Gab`, `JU:Lim`, `JU:Gold` = classes `CasoMinimoTest`, `ModosTest`, `PrimitivasTest`, `GabTest`, `LimiaresTest`, `GoldenCasosTest` em `NT:`.
- `PY:<Classe>.<teste>` = `T:test_caracterizacao.py` (48 testes; classes `PrimitivasJava`, `CpfCnpj`, `Cnab`, `CargaEstrutural`, `CargaPorArquivo`, `RegrasDeConteudo`).
- `casos-por-regra/<ID>` = `T:casos-por-regra/<ID>/` (caso mínimo observado no original; `JU:Gold.casosPorRegraObservadosNoOriginal` roda os 71).
- `C-nn` = cenário golden (caso) `T:casos/nn-*` (00 a 15); o prefixo `C-` evita confusão com os IDs `G-*` de corretoras. **O `esperado.txt` de cada cenário é saída do modelo Python (`T:validador_ref.py`), não do aplicativo**; a fidelidade ao original vem de o mesmo cenário ter sido executado no original (nível 2/3) e ter dado resultado idêntico.
- `N1`/`N2`/`N3` = execução diferencial nível 1 (funções puras), 2 (pipeline real em HSQLDB, sem tela) e 3 (tela real `frmSelecionaDiretorio`); `HASH` = `T:diferencial/diferencial_hash.py`; `GABD` = `T:diferencial/diferencial_gab.py` + `fuzz_gab.py`.
- `FZD` = `T:fuzz_dirigido.py` (cenários dirigidos a regras de OD, R-CT-07, R-TI-01); `FZC` = `T:fuzz_cobertura.py` (cenários dirigidos pela cobertura JaCoCo); `FZX` = `T:fuzz_cruzado.py` (modelo × Java).

---

## 1. Visão geral e stack

O Validador SIMBA 6.2.6 (MPF) é uma aplicação **desktop Swing** em **Java 25** (`Build-Jdk-Spec: 25`; JRE 25 embutido) que:

1. cadastra o *atendimento* (caso) e seus *investigados* num banco **HSQLDB 2.7.3** em arquivo, local;
2. lê cinco arquivos `.txt` do caso (bancário: AGENCIAS, CONTAS, TITULARES, EXTRATO, ORIGEM_DESTINO; separados por TAB, ISO-8859-1), ou três de largura fixa (corretoras: GAB109/GAB112/GAB800);
3. carrega-os linha a linha em tabelas HSQLDB, validando cada linha (regras **L-\***, **E-03…E-07**);
4. roda **regras de conteúdo como consultas SQL** sobre as tabelas (regras **R-\***);
5. grava hash MD5 dos arquivos, copia-os para a pasta do caso, gera relatório HTML de inconsistências e permite prosseguir ao envio (módulo de rede, §10, **não executado nesta investigação**).

| Camada | Tecnologia (comprovada em `META-INF/MANIFEST.MF`, `META-INF/maven/.../pom.xml`, `lib/`) |
|---|---|
| Linguagem/Runtime | Java 25 (classfile 69), JRE embutido em `app/jre/` |
| Build | Maven (`Created-By: Maven JAR Plugin 3.4.2`); POM com pai `spring-boot-starter-parent 3.5.14` |
| Injeção de dependência | Spring Framework 6.2.18 (`context/beans/aop/core/expression`), anotações `@Component`, `@Scope("prototype")`, `@Inject` (`jakarta.inject`); **não** é Spring Boot em execução (contexto XML + component-scan) |
| UI | Swing (`telas/*`, `ui/*`), `SwingWorker` para a validação |
| Banco | HSQLDB 2.7.3, `jdbc:hsqldb:file:<arquivo do caso>`, usuário `sa`, tabelas `CACHED` |
| Utilitários | Guava 33.4.8, commons-io 2.4 / codec 1.18.0 / lang3 3.17.0, gson 2.13.2, zip4j 2.6.1 |
| Rede (fora do núcleo) | httpclient5 5.5.2 / httpcore5 5.3.6 |
| Relatórios/PDF | OpenPDF 3.0.0 + openpdf-html + neko-htmlunit 4.14.0 + Velocity 2.4.1 |
| Log | logback 1.5.32 / slf4j 2.0.17, micrometer 1.15.11 |
| Biblioteca MPF | `spea-client-2.4.4.jar` (83 classes decompiladas): `Context`, `Configurador`, `Controlador`, `ErrorReportCmd`, `CpfCnpjUtil`, `DigitoVerificador`, `StringUtil`, `asspa.client.Util`, `Arquivo`, `http.*`, `jdbc.*` |

**Não ofuscado:** nomes de pacotes, classes e métodos são legíveis; o JAR decompila sem falhas (Vineflower 1.12.0; 159 `.java` do app + 83 da biblioteca — contagem conferida por `find`).

**Reimplementação de estudo** (`implementacao/validador-independente`, v0.2.0): Java 21+ (`maven.compiler.release=21`), **sem dependências de produção**, JUnit 5.11.4 nos testes (§12).

---

## 2. Empacotamento e instalador

**Artefato:** `original/Setup-SIMBA-Validador-6.2.6.exe`, 50.824.043 B, SHA-256 `67c44d7e…8e8db6c2` (`analise/01-inventario.md`).

- **Instalador:** PE32 (Windows GUI, i386), **Inno Setup 6.5.0** (stub Delphi 36.0); não é .NET. Cabeçalho (`analise/catalogo-instalador.json`, chave `header`): `AppName`/título "SIMBA Validador", versão `6.2.6`, `DefaultDirName = {userdocs}\Programas SIMBA\Validador`, grupo de menu "Programas SIMBA\Validador", `UninstallDisplayIcon`-like `{app}\unins000.exe`; contadores: 217 entradas `[Files]`, 216 `FileLocation`, 2 `[Icons]`, 12 `CustomMessage`, 1 `Language`.
- **Container:** `TSetupLdrOffsetTable` v2 em 878.144 (setup-0 = cabeçalhos, offset 49.793.011; setup-1 = dados, offset 882.176). **Sem criptografia** (`EncryptionUse=0`). Dados: 1 chunk sólido LZMA2 (48,9 MB → 71,2 MB); 41 executáveis/DLLs com filtro de chamadas x86. Todos os CRCs conferem e os 216 arquivos únicos têm SHA-256 igual ao registrado.
- **Extração:** `innoextract` 1.9 não suporta 6.5.0; foram usados scripts próprios: `A:ferramentas/inno65.py`, `inno65_catalog.py`, `inno65_extract.py`, `carve_zip.py`. Resultado em `original/extraido/` (215 arquivos; **binários de terceiros, não executar**).
- **Conteúdo instalado (`original/extraido/app/`):**
  - `simba-validador.jar` (540.746 B, 249 classes, `Implementation-Version: 6.2.6`, `Main-Class: br.mp.mpf.spea.simba.validador.Main`); recursos internos `validador-spring.xml`, `certificados/` (9 arquivos; os 3 citados em §10 mais GlobalSign, icpbrasil e outros), `templates/`, `logback.xml`, `icone.png`, `META-INF/maven/br.gov.mpf.pgr.asspa/simba-validador/`.
  - `lib/` com 34 JARs (os 34 do `Class-Path` do manifesto, §1).
  - `jre/` (JRE 25 próprio: java.base, java.desktop, java.sql, java.xml, java.naming, jdk.jfr, …).
  - `simba-validador.exe` (PE32 GUI i386, 73.728 B): **função não determinada** (hipótese [HIP]: *launcher* nativo do JRE) — lacuna LAC-07.
- **Anomalias:** 2 *file entries* do catálogo do instalador têm destino `{app}`/vazio apontando à *location* 0 (natureza ND; provável artefato da varredura heurística de `inno65_catalog.py`; não afetam a verificação por hash) — LAC-07.

Verificação realizada para este documento: `ls original/extraido/app` (jre, lib, simba-validador.exe, simba-validador.jar) e `ls original/extraido/app/lib` (34 JARs, incluindo `hsqldb-2.7.3.jar`, `spea-client-2.4.4.jar`, `spring-*-6.2.18.jar`) — nada foi executado.

---

## 3. Arquitetura, pacotes e classes

### 3.1 Contexto Spring (`validador-spring.xml`, extraído do JAR em `analise/decompilado/validador-spring.xml`)
`default-lazy-init="true"`; `component-scan` de `br.mp.mpf, DAO, negocio, telas`; beans explícitos: `connectionProvider` → `br.mp.mpf.pgr.client.jdbc.JdbcConnectionProvider`; `configurador` → `ConfiguradorValidador`; `controlador` → `ControladorValidador`; `bancoHSQLDB` → `DAO.BancoHSQLDB` (prototype); `frmSelecionaDiretorio`, `frmSelecionaDiretorioGab`, `frmDadosAtendimento`, `frmTrataEnvio` (prototype); `criarTabelasCmd` → `CriarTabelasValidador` (prototype). `Carregar` e `ValidarAtendimento` são `@Component @Scope("prototype")`.

### 3.2 Ponto de entrada
`D:Main` (`extends BaseMain`, de `L:asspa/client/BaseMain`): `main(String[])` chama `init(args)`, define `nextForm = frmIdentificaAtendimento` e `startThread`; `getArquivoSpring()` devolve `"validador-spring.xml"`. A primeira tela é o cadastro/identificação do atendimento (fora do núcleo de validação; LAC-06). A tela de validação é `telas/frmSelecionaDiretorio` (bancário) ou `telas/frmSelecionaDiretorioGab` (corretoras).

### 3.3 Mapa de pacotes do app (`D:`; contagens de `.java` por `ls`)
| Pacote | Conteúdo relevante (verificado) |
|---|---|
| (raiz) | `Main`, `ControladorValidador` (façade), `ConfiguradorValidador` (`getVersao()`), `CriarTabelasValidador` (DDL, §5) |
| `carga` (30 arquivos) | `AbstractCarregarArquivoCmd<D>` (laço de leitura/validação/inserção), `Carregar` (orquestra o bancário), `CarregarGab`/`CarregarGabVer2`, `CarregarArquivo{Agencias,Contas,Titulares,Extrato,OrigemDestino}Cmd`, `CarregarTabelaPessoaCmd`, `CarregarTabelaTitularidadeCmd`, `CarregarArquivoGab{109,112,800}Cmd` (+`Ver2`), `GetCamposGabColunaFixaCmd`, localizadores com cache Guava (`AgenciaLocalizerCmd`, `ContaLocalizerCmd`, `PessoaLocalizerCmd`, `InvestigadoLocalizerCmd`), `ApagarTabelasCmd`, `GetInvestigadosHashTableCmd`, `GetAtendimento{Bancario,Gab}Cmd`, `CampoArquivo`, `TipoCampoArquivo`, `CargaInfo`, `HasCargaInfo` |
| `validacao` (2) | `ValidarAtendimento` (ordem das regras SQL bancárias), `ValidarAtendimentoGab` |
| `rules` (46) | 8 classes `Abstract*Rule` (agência, conta, extrato, titulares, origem-destino, gab109/112/800), `CnabRule` e 37 regras `Validar*`/`InvestigadoGab*` (R-\*, R-G\*) |
| `enums` (22) | `NomeArquivo` (ATENDIMENTO, AGENCIAS, CONTAS, TITULARES, EXTRATO, ORIGEM_DESTINO), `NomeArquivoGab`, `Campos*` (layouts GAB, atendimento, investigado), `UF`, `TipoConta`, `TipoLancamento`, `TipoPessoa*`, `MercadoGab`, `SimNao`, `NomesTabela{A,B}Gab` |
| `domain` (20), `to` (4) | POJOs de linha (`Agencia`, `Conta`, `Pessoa`, `Titularidade`, `Extrato`, `OrigemDestino`, `Gab*`, `Atendimento*`, `Arquivos`, `ArquivosGab`, `Investigado`) e TOs de transmissão (`ExtratoOrigemDestino`, `TransmissaoDocumentoRequestTO`, …) |
| `cmd` (15) | `GerarHashArquivosCmd`/`GerarHashArquivosGabCmd`, `CopiarArquivoParaPastaTxtCmd`, `InserirAtendimentoCmd`, `GetInvestigadosCmd`, `Gerar*Cmd`, `CompactarArquivosParaEnvioCmd`, `VerificarHashArquivos*Cmd`, `TransmitirDocumentosCmd` |
| `util`, `desempenho`, `ui` | `util/Util` (split TAB, datas, `toCsv`), `desempenho/Desempenho` (progresso/linhas por s), `ui/*` (painéis Swing) |
| `DAO` (em `analise/decompilado/DAO/`) | `Mapa`, `MapaGab`, `BancoHSQLDB` |
| `telas` (em `analise/decompilado/telas/`) | `frmSelecionaDiretorio`, `frmSelecionaDiretorioGab`, `frmIdentificaAtendimento`, `frmDadosAtendimento`, `frmInvestigado`, `frmTrataEnvio` |

### 3.4 Componentes do catálogo (K-01…K-12) e classe real
K-01 `Main` + `validador-spring.xml` · K-02 `telas/frmSelecionaDiretorio` (`TarefaBackground.doInBackground`) · K-03 `ControladorValidador` (`verificarExistenciaArquivosBancario`, `verificarExistenciaArquivosGab`, `verificarArquivosVazios`, `verificarArquivosGabVazios`, `checkVersaoSistemaBanco`, `validarConteudo`, `validarConteudoGab`, `gerarHashArquivo`, `gravarHashArquivo`) · K-04 `Carregar` + `AbstractCarregarArquivoCmd` · K-05 loaders · K-06 `ValidarAtendimento` + `rules/*` · K-07 loaders GAB + `GetCamposGabColunaFixaCmd` + `ValidarAtendimentoGab` · K-08 `DAO.Mapa`/`MapaGab`/`BancoHSQLDB` · K-09 `spea-client` · K-10 `util/Util` · K-11 `rules/CnabRule` · K-12 `cmd/TransmitirDocumentosCmd` + `L:pgr/client/http/*`.

### 3.5 Classes de apoio da biblioteca (`L:`, verificadas)
`pgr/client/Context` (`isEleitoral()`, `isEleitoral(String)`), `pgr/client/Configurador` (`getVersaoLayoutGab`/`setVersaoLayoutGab`, URL base), `pgr/client/Controlador` (`getComputadores`, `getComputador`), `pgr/client/error/ErrorReportCmd`, `ErroValidacao`, `pgr/util/list/FileBackedList`, `pgr/client/validacao/AbstractRule`, `pgr/client/jdbc/{JdbcConnectionProvider,ConnectionManager,SharedConnection,CriarTabelasCmd}`, `pgr/client/to/{Versao,Computador,Computadores}`, `pgr/util/{StringUtil,CpfCnpjUtil,DigitoVerificador}`, `asspa/client/{Util,Arquivo}`.

---

## 4. Fluxo de execução

### 4.1 Bancário (orquestração em `frmSelecionaDiretorio.TarefaBackground.doInBackground`, `SwingWorker`)
```mermaid
flowchart TD
  A[iniciarValidacao(pasta)] --> B{E-01 verificarExistenciaArquivosBancario}
  B -- ausente --> Z[mensagem na tela e fim] --> I
  B --> C{E-09 checkVersaoSistemaBanco}
  C -- diferente --> W[aviso modal e fim] --> I
  C --> D[E-14 GerarHashArquivosCmd.gerar -> ATENDIMENTO.HASH_ARQUIVO_n]
  D --> E[CopiarArquivoParaPastaTxtCmd.copiar]
  E --> F{E-02 verificarArquivosVazios}
  F -- erro --> R[finishWithError: relatorio]
  F --> G["Carregar.carregar (CARGA)"]
  G -- erro --> R
  G --> H["laco i=0..5: ControladorValidador.validarConteudo(i)"]
  H -- 1o arquivo reprovado: break --> R
  H --> I[finally: ApagarTabelasCmd.apagar; Continuar habilitado]
  R --> I
```
Pontos verificados no código:
- **Ordem real:** existência (E-01) → versão (E-09) → `getComputador(...)` (rede/injeção, E-15) → hash (E-14) → cópia → vazios (E-02) → carga → conteúdo. A cópia dos arquivos acontece **antes** de qualquer erro de vazio/carga.
- **Carga** (`D:carga/Carregar.carregar`): `ApagarTabelasCmd.apagar()` (TRUNCATE de TITULARIDADE, PESSOA, ORIGEM_DESTINO, EXTRATO, CONTA, AGENCIA; **não** toca ATENDIMENTO/INVESTIGADO); `GetInvestigadosHashTableCmd.get()` monta o mapa CPF/CNPJ→código do investigado; carrega AGENCIAS → CONTAS → TITULARES (`CarregarArquivoTitularesCmd.carregar`: passada PESSOA e, **só se não houve erro**, passada TITULARIDADE) → EXTRATO → ORIGEM_DESTINO. Após cada arquivo, `temErroNaCarga` lança `NegocioException("A carga foi interrompida devido a erros anteriores.")` se `CargaInfo.isTemErro()`; essa exceção é **capturada em `Carregar.carregar` e apenas registrada em log** (ver RO-01 em §16).
- **Conteúdo:** o laço percorre `NomeArquivo.values()` (índice 0 = ATENDIMENTO ⇒ `default: return true`); para i=1..5 chama `validarConteudo(i, view)` → `ValidarAtendimento.validarArquivoAgencia/Conta/Titularidade/Extrato/OrigemDestino`. O laço dá `break` no primeiro arquivo que devolve `false`; **todas** as regras do arquivo corrente são executadas (não há `return` antecipado dentro de `validarArquivo*`).
- **`finally`:** limpa tabelas; habilita "Continuar" para todo caso ≠ `999-OUTROS-000001-88` (E-11); depois, se `!hasErros() && arquivosExistem`, habilita também; `errorReportCmd.reset()`.
- **Qualquer `Exception` não tratada** no worker: mensagem "Ocorreu um erro do sistema durante a validação…" na tela e `arquivosExistem=false`.

### 4.2 Corretoras (GAB)
`frmSelecionaDiretorioGab` → `ControladorValidador.verificarExistenciaArquivosGab` (ordem `NomeArquivoGab.getNomesList()`: GAB109, GAB112, GAB800) → `checkVersaoSistemaBanco` (E-09) → `getComputador` (E-15) → `GerarHashArquivosGabCmd.gerar` (E-14) → `CopiarArquivoParaPastaTxtCmd.copiarGab` → `verificarArquivosGabVazios` (G-01) → `CarregarGab.carregarGab` / `CarregarGabVer2` (versão do layout escolhida na tela: `Configurador.setVersaoLayoutGab`, chamado em `frmIdentificaAtendimento`) → laço `validarConteudoGab` sobre `NomeArquivoGab.values()` (GAB109, GAB112, GAB800). Largura de linha: `NomeArquivoGab.getTamanhoLinha`. No 6.2.6 a carga GAB falha em toda linha de largura correta (**G-DEF**, §7.5).

### 4.3 Concorrência e estado
A validação roda em um único `SwingWorker` por vez. O estado de erros é **um único `ErrorReportCmd` compartilhado** (lista persistida em `FileBackedList`), por isso `hasErros()` é global entre arquivos — base de E-06 e E-08.

---

## 5. Modelo de dados (HSQLDB)

### 5.1 Conexão e bootstrap
- `L:pgr/client/jdbc/JdbcConnectionProvider.get()`: `jdbc:hsqldb:file:<Context.getArquivoCaso().getCanonicalPath()>`, usuário `sa`, senha vazia; chama `CriarTabelasCmd.criar(con)`, que só executa o DDL se `SELECT * FROM ATENDIMENTO` falhar.
- `D:CriarTabelasValidador.executeStatements()`: primeiro `SET PROPERTY "sql.enforce_strict_size" true` (**tamanhos de coluna passam a ser impostos pelo banco**: inserir valor maior que `numeric(n)`/`varchar(n)` falha — origem de várias exceções que viram E-13); cria, nesta ordem, SISTEMA (+ `INSERT` da versão), ATENDIMENTO, INVESTIGADO, AGENCIA, CONTA, EXTRATO, PESSOA, TITULARIDADE, ORIGEM_DESTINO, GAB109, GAB112, GAB800, GAB109VER2, GAB112VER2, GAB800VER2 e os índices abaixo.
- `DX:DAO/BancoHSQLDB`: `getVersaoSistema` (`SELECT VERSAO FROM SISTEMA`), `executaQuery`, `executaUpdate`, `gravarHashArquivo(int,String)` (`UPDATE ATENDIMENTO SET HASH_ARQUIVO_n`), `gravarHashArquivoGab`, `atualizaQtdLinhasArquivo`.

### 5.2 DDL do bancário (`analise/decompilado/DAO/Mapa.java`, constantes `sqlCriaTabela*`)
Todas `CREATE CACHED TABLE`. Notação: `PK`, `FK→`, `NN` = NOT NULL.
| Tabela | Colunas (tipo) |
|---|---|
| `SISTEMA` | `VERSAO varchar(10) NN` |
| `ATENDIMENTO` | `CODIGO_CHAVE_ATENDIMENTO integer identity PK`; `NUMERO_CASO varchar(30)`; `CNPJ varchar(14)`; `NUMERO_BANCO smallint`; `NOME_BANCO varchar(200)`; `NOME_BANCO_ATENDIMENTO varchar(200)`; `DATA_GERACAO_ARQUIVO date`; `NOME_FUNC_RESPONSAVEL varchar(80)`; `TELEFONE_FUNC_RESPONSAVEL varchar(20)`; `EMAIL_FUNC_RESPONSAVEL varchar(100)`; `DATA_INICIO_AFASTAMENTO date`; `DATA_FIM_AFASTAMENTO date`; `NUMERO_CORREIO_BACEN varchar(20)`; `NUMERO_OFICIO varchar(20)`; `DATA_OFICIO date`; `NUMERO_PROCESSO varchar(100)`; `NOME_TRIBUNAL varchar(80)`; `NUMERO_VARA varchar(10)`; `NOME_MAGISTRADO varchar(80)`; `CARGO_MAGISTRADO varchar(80)`; `DESCRICAO_CONTEUDO varchar(500)`; `HASH_ARQUIVO_1..5 varchar(32)`; `LINHAS_ARQUIVO_1..5 integer`; `HASH_ARQUIVO_GAB109/112/800 varchar(32)`; `LINHAS_ARQUIVO_GAB109/112/800 integer`; `VERSAO_VALIDADOR varchar(10)` |
| `INVESTIGADO` | `CODIGO_INVESTIGADO integer identity PK`; `TIPO_PESSOA tinyint NN`; `CPF_CNPJ_PESSOA varchar(14) NN`; `NOME_PESSOA varchar(80) NN`; `TEVE_RELACIONAMENTO tinyint default 0`; `TEVE_CONTA tinyint default 0`; `TEVE_BDV tinyint default 0`; `OBSERVACAO varchar(500)`; `DATA_INICIO_AFASTAMENTO date`; `DATA_FIM_AFASTAMENTO date` |
| `AGENCIA` | `CODIGO_CHAVE_AGENCIA integer identity PK`; `NUMERO_BANCO numeric(3)`; `NUMERO_AGENCIA numeric(4)`; `NOME_AGENCIA varchar(50)`; `ENDERECO_LOGRADOURO varchar(80)`; `ENDERECO_CIDADE varchar(40)`; `ENDERECO_UF varchar(2)`; `ENDERECO_PAIS varchar(40)`; `ENDERECO_CEP varchar(8)`; `TELEFONE_AGENCIA varchar(20)`; `DATA_ABERTURA_AGENCIA date`; `DATA_FECHAMENTO_AGENCIA date`; `LINHA_ARQUIVO integer NN` |
| `CONTA` | `CODIGO_CHAVE_CONTA integer identity PK`; `CODIGO_CHAVE_AGENCIA integer FK→AGENCIA (ON DELETE/UPDATE CASCADE)`; `NUMERO_CONTA varchar(20)`; `TIPO_CONTA numeric(1)`; `DATA_ABERTURA_CONTA date`; `DATA_ENCERRAMENTO_CONTA date`; `MOVIMENTACAO_CONTA numeric(1)`; `LINHA_ARQUIVO integer NN` |
| `EXTRATO` | `CODIGO_CHAVE_EXTRATO numeric(18) PK` (**chave vem do arquivo**; duplicada ⇒ violação de PK ⇒ E-13); `CODIGO_CHAVE_CONTA integer NN FK→CONTA`; `DATA_LANCAMENTO date`; `NUMERO_DOCUMENTO varchar(20)`; `DESCRICAO_LANCAMENTO varchar(50)`; `TIPO_LANCAMENTO numeric(3)`; `VALOR_LANCAMENTO numeric(14)`; `NATUREZA_LANCAMENTO varchar(1)`; `VALOR_SALDO numeric(14)`; `NATUREZA_SALDO varchar(1)`; `LOCAL_TRANSACAO varchar(80)`; `LINHA_ARQUIVO integer NN` |
| `PESSOA` | `CODIGO_CHAVE_PESSOA integer identity PK`; `CODIGO_INVESTIGADO integer FK→INVESTIGADO`; `TIPO_PESSOA tinyint`; `CPF_CNPJ_PESSOA varchar(14)`; `NOME_PESSOA varchar(80)`; `NOME_DOC_IDENTIFICACAO varchar(50)`; `NUMERO_DOC_IDENTIFICACAO varchar(20)`; `ENDERECO_LOGRADOURO varchar(80)`; `ENDERECO_CIDADE varchar(40)`; `ENDERECO_UF varchar(2)`; `ENDERECO_PAIS varchar(40)`; `ENDERECO_CEP varchar(8)`; `TELEFONE_PESSOA varchar(20)`; `VALOR_RENDA numeric(14)`; `DATA_ATUALIZACAO_RENDA date`; `LINHA_ARQUIVO integer NN` — **uma linha de PESSOA por linha do arquivo TITULARES** (não há deduplicação; `PessoaLocalizerCmd.findIdPessoa(int numeroLinha)` busca por `LINHA_ARQUIVO`, índice `IDX_PESSOA_LINHA`) |
| `TITULARIDADE` | `CODIGO_CHAVE_PESSOA integer NN FK→PESSOA`; `CODIGO_CHAVE_CONTA integer NN FK→CONTA`; `TIPO_TITULAR varchar(1)`; `PESSOA_INVESTIGADA numeric(1) default 0`; `DATA_INICIO_RELACIONAMENTO date NN`; `DATA_FIM_RELACIONAMENTO date`; `LINHA_ARQUIVO integer NN`; `PRIMARY KEY (CODIGO_CHAVE_PESSOA, CODIGO_CHAVE_CONTA)` |
| `ORIGEM_DESTINO` | `CODIGO_CHAVE_OD numeric(18) PK`; `CODIGO_CHAVE_EXTRATO numeric(18) NN FK→EXTRATO` (**OD sem extrato ⇒ violação de FK na inserção ⇒ E-13**; por isso R-EX-03 só "vê" extrato sem OD, nunca o contrário); `VALOR_OPERACAO numeric(14) NN`; `NUMERO_DOCUMENTO varchar(20)`; `NUMERO_BANCO_OD numeric(3)`; `NUMERO_AGENCIA_OD numeric(4)`; `NUMERO_CONTA_OD varchar(20)`; `TIPO_CONTA_OD numeric(1)`; `TIPO_PESSOA_OD numeric(1)`; `CPF_CNPJ_PESSOA_OD varchar(14)`; `NOME_PESSOA_OD varchar(80)`; `NOME_DOC_IDENTIFICACAO_OD varchar(50)`; `NUMERO_DOC_IDENTIFICACAO_OD varchar(20)`; `CODIGO_DE_BARRAS varchar(100)`; `NOME_ENDOSSANTE_CHEQUE varchar(80)`; `DOC_ENDOSSANTE_CHEQUE varchar(50)`; `SITUACAO_IDENTIFICACAO numeric(1)`; `OBSERVACAO_OD varchar(250)`; `LINHA_ARQUIVO integer NN` |

**Índices** (`CriarTabelasValidador`): `IDX_ATENDIMENTO` (único, `NUMERO_CASO`); `IDX_AGENCIA (NUMERO_BANCO, NUMERO_AGENCIA)`; `IDX_PESSOA (TIPO_PESSOA, CPF_CNPJ_PESSOA, NOME_PESSOA, NOME_DOC_IDENTIFICACAO, NUMERO_DOC_IDENTIFICACAO)`; `IDX_PESSOA_LINHA (LINHA_ARQUIVO)`; `IDX_CONTA (CODIGO_CHAVE_AGENCIA, NUMERO_CONTA, TIPO_CONTA)`; `IDX_EXTRATO_1 (TIPO_LANCAMENTO, VALOR_LANCAMENTO)`; `IDX_EXTRATO_2 (TIPO_LANCAMENTO, DATA_LANCAMENTO)`; `IDX_EXTRATO_3 (DATA_LANCAMENTO)`. **Não há** índice único em AGENCIA nem em CONTA (a unicidade é verificada por R-AG-01/R-CT-02, e a primeira ocorrência "vence" nos localizadores).

**Observações de semântica que alimentam regras:** AGENCIA.NUMERO_BANCO/NUMERO_AGENCIA são **numéricos** (zeros à esquerda irrelevantes: "001"≡"1"); CONTA.NUMERO_CONTA é texto (zeros à esquerda relevantes); `numeric(1)` rejeita 2 dígitos sob `enforce_strict_size`.

### 5.3 Tabelas de metadados em `Mapa` (estrutura auxiliar)
`Mapa.nomesTabelasA` = {ATENDIMENTO, AGENCIAS, CONTAS, TITULARES, EXTRATO, ORIGEM_DESTINO} (nomes lógicos dos arquivos; usado por E-01 e por `atualizaQuantidadeLinhaTabela`); `nomesTabelasB` = {ATENDIMENTO, AGENCIA, CONTA, PESSOA, EXTRATO, ORIGEM_DESTINO, TITULARIDADE, INVESTIGADO}; `camposTabelasA` (ordem de colunas dos arquivos, com marcadores `:f:`/`:a:`) e `camposTabelasB` (colunas das tabelas). A **ordem de colunas dos 5 arquivos** — lacuna LAC-05 do catálogo — está em `Mapa.camposTabelasA` e, com nomes/tamanhos, nos métodos `getCamposArquivo()` dos loaders (§6.3).

### 5.4 DDL das corretoras (`analise/decompilado/DAO/MapaGab.java`)
Seis tabelas `create cached table`, geradas por concatenação a partir dos enums `Campos*` (nome da coluna = nome do enum; `numeric(n)`/`varchar(n)` com `n = getTamanhoCampo()`; datas `date`; todas com `codigo_chave_<TABELA> bigint generated by default as identity primary key` e `LINHA_ARQUIVO integer`). Colunas (verificadas por varredura do código-fonte):
- `GAB109` (8): CODIGO_DO_CLIENTE, DATA_DO_MOVIMENTO, DATA_DE_REFERENCIA, DATA_DE_LIQUIDACAO, DESCRICAO_DO_LANCAMENTO, VALOR_DO_LANCAMENTO, NATUREZA_LANCAMENTO, LINHA_ARQUIVO. `GAB109VER2` (18) acrescenta CHAVE_OD, BANCO/AGENCIA/CONTA_ORIGEM, CPF_CNPJ_ORIGEM, BANCO/AGENCIA/CONTA_DESTINO, CPF_CNPJ_DESTINO, OBSERVACAO.
- `GAB112` (22): CODIGO_DO_CLIENTE, DOCUMENTO_DO_CLIENTE, DATA_DA_CRIACAO, DATA_DA_ATUALIZACAO, DATA_NASCIMENTO_FUNDACAO, TIPO_DE_PESSOA, NOME_DO_CLIENTE, DOCUMENTO_DE_IDENTIFICACAO, NOME_DO_CONJUGE, CODIGO_DO_CLIENTE_MERCADO, ENDERECO, NUMERO_E_COMPLEMENTO, BAIRRO, CIDADE, ESTADO, CEP, COMPLEMENTO_DO_CEP, CONSTANTE_0000A, CODIGO_DA_ATIVIDADE, TELEFONE, MERCADO, LINHA_ARQUIVO. `GAB112VER2` (33) acrescenta DESCRICAO_DA_ATIVIDADE, EMAIL, CELULAR, MEMBRO_COMPENSACAO, DESCRICAO_MEMBRO_COMPENSACAO, AGENTE_CUSTODIA, DESCRICAO_AGENTE_CUSTODIA, CODIGO_CORRETORA, DESCRICAO_CODIGO_CORRETORA, SITUACAO_CLIENTE, DATA_SITUACAO.
- `GAB800` (12): CODIGO_DO_CLIENTE, NOME_DO_CLIENTE, ENDERECO, ENDERECO_COMPLEMENTO, DATA_DE_REFERENCIA, DATA_DE_LIQUIDACAO, DESCRICAO_DO_LANCAMENTO, SALDO_INICIAL, NATUREZA_DO_SALDO_INICIAL, VALOR_DO_LANCAMENTO, NATUREZA_DO_LANCAMENTO, LINHA_ARQUIVO. `GAB800VER2` (13) acrescenta CHAVE_OD.

---

## 6. Parsing e leitura

### 6.1 Leitura (E-05)
`D:carga/AbstractCarregarArquivoCmd.carregar`: `FileInputStream` → `Channels.newReader(..., StandardCharsets.ISO_8859_1)` → `BufferedReader` com buffer de 10 MiB → `readLine()` (aceita CR, LF e CRLF; última linha sem terminador é lida). Contagem prévia de linhas por `L:asspa/client/Util.countLines` (apenas para progresso). Inserção em lote de **500** (`getBatchSize()`) quando `useBatch`; `commit()` ao fim do arquivo e **a cada 250.000 linhas** (`numeroLinha % 250000`). Linha em branco gera 1 coluna ⇒ E-03.

### 6.2 Separação em colunas (E-05b, E-03)
`D:util/Util.tabSeparatedStringToArray(String)`: `text.split("(?=\t)")` seguido de `trim()` por campo. Consequências (comprovadas por N1, 1.508 vetores, e pela sonda `T:sondas/SplitProbe.java`): o *lookahead* mantém o TAB no início de cada campo (e o `trim` o remove); um **primeiro campo de comprimento zero é descartado** (linha `\tb\tc` ⇒ 2 colunas); campos vazios finais são **preservados** (cada um vira o elemento `"\t"`, que o `trim` converte em `""`; confirmado pela sonda `SplitProbe` e pelo golden `00-valido`, cuja última coluna de AGENCIAS é vazia); só o **primeiro campo de comprimento zero** é descartado. `trim()` remove todo caractere ≤ U+0020. Contagem de colunas esperada via `getQuantidadeColunas()` de cada loader (AGENCIAS 11, CONTAS 7, TITULARES 21, EXTRATO 14, ORIGEM_DESTINO 18); divergência ⇒ E-03 com rótulo = **nome físico** do arquivo.

### 6.3 Tamanho máximo (E-04) e layouts
`AbstractCarregarArquivoCmd.validarCampos(String[], int)` percorre `dados[i]` contra `camposArquivo[i]` (`CampoArquivo.getNome()/getMaxLength()`); o campo `tipo` de `CampoArquivo` (`TipoCampoArquivo`) **nunca é lido** (verificado por `grep`). O rótulo do erro E-04 é `getNomeArquivo()` (nome **lógico**, p.ex. `AGENCIAS`; para as duas passadas de TITULARES, `TITULARES`). Os nomes de campo usados na mensagem E-04 são os dos `getCamposArquivo()` — por exemplo, em TITULARES: `NUMERO_BANCO, NUMERO_AGENCIA, NUMERO_CONTA, TIPO_CONTA, TIPO_TITULAR, PESSOA_INVESTIGADA, TIPO_PESSOA_TITULAR, CPF_CNPJ_TITULAR, NOME_TITULAR, NOME_DOC_IDENTIFICACAO, NUMERO_DOC_IDENTIFICACAO, ENDERECO_LOGRADOURO, ENDERECO_CIDADE, ENDERECO_UF, ENDERECO_PAIS, ENDERECO_CEP, TELEFONE_PESSOA, VALOR_RENDA, DATA_ATUALIZACAO_RENDA, DATA_INICIO_RELACIONAMENTO, DATA_FIM_RELACIONAMENTO`; em ORIGEM_DESTINO o 4º é `NUMERO_DOCUMENTO_TRANSACAO`, o 3º `VALOR_TRANSACAO`, o 10º `CPF_CNPJ_OD` e o 18º é literalmente **`OBSERVACÃO`** (com til; erro de digitação do original, reproduzido em `N:Schemas`). Os tamanhos coincidem com o catálogo §5.0. Nomes divergentes dos abreviados do catálogo → §16, item RO-03.

### 6.4 Datas e números
- `ddMMyyyy` bancário: `L:asspa/client/Util.verificaData` (A-01, não leniente) e `SimpleDateFormat("ddMMyyyy")` **leniente** em `AbstractCarregarArquivoCmd.sdf` (usado por L-AG-05 e L-TI-06) e em `util/Util.getDate` (gravação em `date`).
- `yyyyMMdd` GAB: `util/Util.getDateGab` (parse leniente + conferência de ida e volta; devolve `null` se vazio/inconsistente) e `getDateGabSql` (NPE se `null`).
- Numéricos: `getInt/getLong` usam `StringUtil.isNumber` (A-05), depois `Integer.valueOf`/`Long.valueOf` estritos.

### 6.5 Largura fixa (GAB)
`NomeArquivoGab.getTamanhoLinha(arquivo, versao)` (soma dos tamanhos de `CamposGab*`/`CamposGab*Ver2`): GAB109 127/333, GAB112 307/726, GAB800 298/320 (v1/v2). Linha de largura diferente ⇒ G-02 (rótulo = nome físico). Campos extraídos por `GetCamposGabColunaFixaCmd.getCampos/getGab109/getGab112/getGab800` (`substring` + `trim`).

---

## 7. Implementação das regras

Mecânica comum: cada loader implementa `convert` (montagem do domínio e *lookups* que podem lançar `NegocioException`), `validar` (chamadas `validar*` em ordem fixa, cada uma reportando via `errorReportCmd.report(new ErroValidacao(linha, rótulo, msg))`) e `setParametros` (inserção). `NegocioException` ⇒ mensagem vai ao relatório e a linha é abandonada; qualquer outra `Exception` ⇒ E-13. A matriz completa por ID está em §14; aqui estão os mecanismos.

### 7.1 Regras estruturais e de fluxo (E-\*)
- **E-01** `ControladorValidador.verificarExistenciaArquivosBancario(File, String, StringBuilder)`: lista `*.txt` (não recursivo, `SuffixFileFilter(".txt", INSENSITIVE)`), percorre `Mapa.getNomesTabelasA()` a partir do índice 1 e para no primeiro ausente (`findFile` ignora caixa). **E-02** `verificarArquivosVazios(Arquivos, ErrorReportCmd)`: três `if` independentes (AGENCIAS, CONTAS, TITULARES; linha 0; rótulo = caminho do arquivo reduzido a nome por `FilenameUtils.getName` em `ErroValidacao.getPrettyMessage`).
- **E-03/E-04/E-05/E-05b/E-06/E-13**: `AbstractCarregarArquivoCmd.carregar` e `validarCampos` (§6). Fluxo por linha: split → E-03 (`continue`) → `validarCampos` (E-04) → **`if hasErros() continue`** (E-06; global) → `convert` → `validar` → `if hasErros() continue` → `setParametros` + `executeUpdate`/`addBatch`. Captura: `NegocioException` ⇒ report da mensagem; `Exception` ⇒ `"Erro desconhecido. Consulte o log do sistema."` (E-13) com `log.error` da pilha. Uma `Exception` do laço externo vira `InfraestruturaException` (aborta o worker).
- **E-07** `Carregar.carregar` + `Carregar.temErroNaCarga` (§4.1) e `CarregarArquivoTitularesCmd.carregar` (`getCargaInfo().isTemErro()` entre PESSOA e TITULARIDADE).
- **E-08** `frmSelecionaDiretorio.TarefaBackground.doInBackground` (laço + `break`), `ControladorValidador.validarConteudo`, `ValidarAtendimento.validarArquivo{Agencia,Conta,Titularidade,Extrato,OrigemDestino}` — cada um devolve `!errorReportCmd.hasErros()` (global). Efeito: se a carga passou sem erro, o conteúdo começa com `hasErros()==false`, então "false" no primeiro arquivo significa erro naquele arquivo.
- **E-09** `ControladorValidador.checkVersaoSistemaBanco()` compara `ConfiguradorValidador.getVersao()` com `BancoHSQLDB.getVersaoSistema()` (via `getVersaoSistemaBanco()`).
- **E-10** `L:pgr/client/Context.isEleitoral()` ⇒ `isEleitoral(numeroCaso)`: `startsWith("001-MPE") || startsWith("038-TSE")`. Consumido em `CarregarArquivoAgenciasCmd` (L-AG-01/05/06), `CarregarTabelaTitularidadeCmd` (L-TI-01), `ValidarAtendimento.validarArquivoOrigemDestino` e `validarArquivoExtrato` (apenas contadores de progresso).
- **E-11/E-14/E-15**: `TarefaBackground.doInBackground` (§4.1); hash por `cmd/GerarHashArquivosCmd.gerar(controlador, computador, a)` (códigos 1–5) → `ControladorValidador.gerarHashArquivo` → `L:asspa/client/Arquivo.obterHashArquivo(Computador)`; gravação em `ATENDIMENTO.HASH_ARQUIVO_n` (`BancoHSQLDB.gravarHashArquivo`); contagem de linhas em `AbstractCarregarArquivoCmd.atualizaQuantidadeLinhaTabela` (`UPDATE ATENDIMENTO SET LINHAS_ARQUIVO_i`); cópia em `cmd/CopiarArquivoParaPastaTxtCmd.copiar`. E-15: `Controlador.getComputador(int)` → `getComputadores()` (§10).
- **E-12** `L:pgr/client/error/ErroValidacao.getPrettyMessage()`: `"Erro no arquivo: " + FilenameUtils.getName(arquivo) + ", linha: " + linha + ", erro: " + msg`. Como aplica `getName`, rótulos que são caminhos absolutos (p.ex. `getInt/getLong` usam `file.getAbsolutePath()`) saem como nome físico.

### 7.2 Regras de carga (L-\*) — classe e ordem de `validar`
| Loader (`D:carga/`) | Ordem em `validar(...)` | Notas |
|---|---|---|
| `CarregarArquivoAgenciasCmd` | `validarCamposObrigatorios` (ou `...Eleitoral`) → `validarDataAberturaAgencia` → `validarDataFechamentoAgencia` → `validarNumeroBanco` → `validarDatas` → `validarUf` | `convert` não valida; `validarNumeroBanco` faz `Integer.valueOf(banco)` (vazio ⇒ 2 erros (L-AG-01 + E-13); não numérico preenchido ⇒ 1 erro (E-13); L-AG-05/L-AG-06 não rodam, L-AG-04); `UF.get` + `"--"` em `validarUf`; L-AG-07 é o `setParametros` |
| `CarregarArquivoContasCmd` | `convert`: `AgenciaLocalizerCmd.findIdAgencia` (L-CT-00, `-1` ⇒ `NegocioException`). `validar`: `validarCamposObrigatorios` → `validarDataAberturaConta` → `validarDataEncerramentoConta` → `validarTipoConta` → `validarMovimentacaoConta` | sem checagem ABERTURA ≤ ENCERRAMENTO |
| `CarregarTabelaPessoaCmd` (1ª passada de TITULARES) | `convert`: VALOR_RENDA via `Double.valueOf` (L-PE-00), CPF_CNPJ em maiúsculas, vínculo ao investigado por `completarZerosEsquerda(cpf,14)`. `validar`: `validarCamposObrigatorios` → `validarDataAtualizacaoRenda` (rótulo `NomeArquivo.EXTRATO`) → `validarCpfCnpj` → `validarTipoPessoa` | lista fixa de 5 campos na mensagem de L-PE-01 |
| `CarregarTabelaTitularidadeCmd` (2ª passada) | `convert`: 4 `"".equals(...)` ⇒ `NegocioException`, `ContaLocalizerCmd.findIdConta`, `PessoaLocalizerCmd.findIdPessoa(numeroLinha)` (L-TI-00). `validar`: `validarCamposObrigatorios`/`...Eleitoral` → `validarDataInicioRelacionamento` → `validarDataFimRelacionamento` → `validarTipoTitular` → `validarPessoaInvestigada` → `validarDatasRelacionamento` | as datas L-TI-02/03 lançam `NegocioException`; `validarDatasRelacionamento` usa parse leniente e lança `NegocioException` p/ data não parseável |
| `CarregarArquivoExtratoCmd` | `convert` (L-EX-00: `getLong/getInt`; `ContaLocalizerCmd.findIdConta`). `validar`: `validarIdConta` → `validarCamposObrigatorios` → `validarDataLancamento` → `validarNaturezaLancamento` (`[CD\*]`) → `validarNaturezaSaldo` (`[CD]`) → `validarTipoLancamentoCredito/Debito/Asterisco` (→ `CnabRule`) → `validarLancamentoSaldoNegativo` | `validarCamposObrigatorios` testa `getNaturezaLancamento() == null` (nunca verdadeiro; ver catálogo L-EX-02) |
| `CarregarArquivoOrigemDestinoCmd` | `convert` (`Long.valueOf(dados[0])` direto; demais via `getLong/getInt`). `validar`: `validarTipoConta` → `validarTipoPessoa` → `validarSituacaoIdentificao` → `validarNumeroBanco` | `validarTipoConta` testa `getTipoPessoa()` (defeito L-OD-01); `validarNumeroBanco` consulta `Controlador.getComputador(...)` (L-OD-04, INALC) |

### 7.3 Regras de conteúdo SQL (R-\*)
Cada regra é um `@Component` em `D:rules/` que estende `Abstract*Rule` (define `nomeArquivo` = rótulo) e implementa `public void validar()`: obtém `SharedConnection`, executa **uma** consulta SQL (`Statement.executeQuery`) e chama `errorReportCmd.report(...)` **por linha retornada** (JOINs podem duplicar erros: R-CT-06/R-CT-08). `SQLException` ⇒ `InfraestruturaException`. Ordem em `ValidarAtendimento`:
- **AGENCIAS (1):** `ValidarAgenciaRepetidaRule` (`GROUP BY NUMERO_BANCO, NUMERO_AGENCIA HAVING COUNT(*)>1`, `MAX(LINHA_ARQUIVO)`, formata com `RIGHT(CONCAT('000',…),3)` e `…'0000'…,4)`).
- **CONTAS (10), ordem de `validarArquivoConta`:** `ValidarContaSeparadorRule` (R-CT-01), `ValidarContaRepetidaRule` (R-CT-02; **herda `AbstractRule`**, logo `nomeArquivo` fica `null` ⇒ rótulo `null`), `ValidarContaSemTitularesRule` (R-CT-03), `ValidarContaSemTitularInvestigado` (R-CT-04), `ValidarContaSemMovimentacao` (R-CT-05), `ValidarMovimentacaoContaInvestigadoNaoRule` (R-CT-06), `ValidarTitularUnicoRule` (R-CT-07), `ValidarMovimentacaoContaInvestigadoSimRule` (R-CT-08), `ValidarContaInvestigadoComMovimentacaoSemExtratoRule` (R-CT-09), `ValidarContaInvestigadaSemMovimentacaoComExtratoRule` (R-CT-10).
- **TITULARES (7), ordem de `validarArquivoTitularidade`:** `ValidarDadosObrigatoriosTitularRule` (R-TI-01), `ValidarDataAberturaRelacionamentoTitularidadeRule` (R-TI-02), `ValidarDataEncerramentoRelacionamentoTitularidadeRule` (R-TI-03), `ValidarInvestigadoCadastroSimArquivoNaoRule` (R-TI-04; linha −1), `ValidarInvestigadoPessoaInvestigada0Rule` (R-TI-05), `ValidarInvestigadoCadastroNaoArquivoSimRule` (R-TI-06), `ValidarContaInvestigadaSemTitularTRule` (R-TI-07). Comparações de CPF/CNPJ por `RIGHT(CONCAT('00000000000000', CPF_CNPJ_PESSOA), 14)`.
- **EXTRATO (5), ordem de `validarArquivoExtrato`:** `ValidarExtratoForaPeriodoAfastamentoRule` (R-EX-01), `ValidarExtratoForaPeriodoContaAbertaRule` (R-EX-02; `sdf.format(dataAberturaConta)` ⇒ **NPE** se a abertura é `NULL` e o lançamento é posterior ao encerramento), `ValidarExtratoSemOrigemDestinoRule` (R-EX-03), `ValidarExtratoContaNaoInvestigadaRule` (R-EX-04), `ValidarValorExtratoOrigemDestinoRule` (R-EX-05).
- **ORIGEM_DESTINO, `validarArquivoOrigemDestino`:** não eleitoral: `ValidarIdentificacaoTedDocRule` (R-OD-01), `ValidarIdentificacaoTransferenciaMesmoBancoRule` (R-OD-02), `ValidarIdentificacaoTransacaoEmEspecieRule` (R-OD-03); eleitoral: `ValidarIdentificacaoTedDocTransfEleitoralRule` (R-OD-E1), `ValidarDepositoDinheiroChequeEleitoralRule` (R-OD-E2); em ambos, por fim `ValidarCpfCnpjTransferenciaTedDocOrigemDestinoRule` (R-OD-04, que valida em Java linha a linha o resultado da consulta usando `CpfCnpjUtil`). Limiares e datas estão **dentro das strings SQL** (hard-coded) — veja o catálogo §6.5 e `implementacao/limiares-6.2.6.properties` para os valores.

### 7.4 Localizadores (cache)
`AgenciaLocalizerCmd.findIdAgencia(String,String)` / `(int,int)` e `ContaLocalizerCmd.findIdConta(String,String,String,String)` usam cache Guava com chave natural (`...NaturalKey`) e consulta SQL em caso de *miss*; devolvem `-1` quando não existe (a primeira correspondência em `IDX_AGENCIA`/`IDX_CONTA` — não há unicidade). `PessoaLocalizerCmd.findIdPessoa(int)` busca por `LINHA_ARQUIVO`.

### 7.5 Corretoras (G-\*, R-G\*) e o defeito G-DEF
- Loaders `CarregarArquivoGab109Cmd`, `...Gab112Cmd`, `...Gab800Cmd` (e `Ver2`): `validar` → `validarNumero`, `validarObrigatoriedade`, `validarData`, `validarDadoEsperado` (GAB112 também `validarCpfCnpj`); `erroObrigatoriedade/erroData` montam mensagens.
- **G-DEF (EXEC):** `getCamposArquivo()` retorna `null` nos seis carregadores GAB (confirmado em `CarregarArquivoGab109Cmd`: o método devolve literalmente `null`). Em `AbstractCarregarArquivoCmd.validarCampos`, `camposArquivo[i]` ⇒ NPE ⇒ E-13 por linha de largura correta (o ramo de largura incorreta, G-02, sai antes, com `continue`). Logo `validar*` GAB e as regras `R-G*` **não executam** no app real; o que foi observado por execução com remendo em memória (campo preenchido por reflexão) vale para a "lógica pretendida" (confiança "EXEC sob remendo" do catálogo).
- Regras de conteúdo GAB (`ValidarAtendimentoGab`): `validarArquivoGab112` → `InvestigadoGabNenhumRelacionamentoRule` (R-G112-1), `ValidarGab112InvestigadoSemRelacionamentoRule` (R-G112-2), `ValidarGab112InvestigadoNaoInformadoRule` (R-G112-3); `validarArquivoGab109` → `ValidarGab112NaoGab109SimRule` (R-G109); `validarArquivoGab800` → `ValidarGab112NaoGab800SimRule` (R-G800). **Observação de ordem:** a carga é GAB109→GAB112→GAB800 (`CarregarGab.carregarGab`), e o laço de conteúdo também percorre `NomeArquivoGab.values()` (GAB109, GAB112, GAB800), de modo que R-G109 roda antes de R-G112-\*.

---

## 8. Algoritmos

- **A-01 `verificaData`** (`L:asspa/client/Util.verificaData`): `length()==8`; `Integer.valueOf(substring(4,8)) >= 1900`; `SimpleDateFormat("ddMMyyyy")` com `setLenient(false)`; qualquer exceção ⇒ `false`. Reimplementação: `N:DateRules.verificaData`.
- **A-02/A-03 dígitos verificadores** (`L:pgr/util/CpfCnpjUtil` + `DigitoVerificador`): `validarCpf/validarCnpj` completam com zeros à esquerda (`StringUtil.completarZerosEsquerda`: 11 / 14) e delegam a `validarDvCpf/validarDvCnpj(numero)`, que separam os 2 últimos caracteres como DV; `validarDvCpf(numero, dv)` rejeita base vazia, base de caracteres todos iguais (`sameChar`) e exige `[0-9]{6,}` + DV `[0-9]{2}`; CNPJ aceita base `[0-9A-Z]{7,}` (**alfanumérico**; valor do caractere = `ord(c)−48`) com DV numérico; módulo 11 (`resto<2 → 0`, senão `11−resto`), pesos `len+1−i` (CPF) e 5,4,3,2,9…2 / 6,5,4,3,2,9…2 (CNPJ). Verificado contra algoritmo oficial independente em 3.000 CPFs e 3.000 CNPJs alfanuméricos (catálogo A-02/A-03). Reimplementação: `N:CpfCnpj` (`dvCpf`, `dvCnpj`, `validarCpf`, `validarCnpj`).
- **A-04 CNAB** (`D:rules/CnabRule`): três métodos que retornam **true = inválido** (`validarCnabCredito`, `validarCnabDebito`, `validarCnabCreditoDebito`); constantes 201/223 (crédito), 101/127 e 116 (débito), 999 (curinga). Reimplementação: `N:Cnab.creditoInvalido/debitoInvalido/creditoDebitoInvalido`.
- **A-05 `isNumber`** (`L:pgr/util/StringUtil.isNumber`): `NumberFormat.getInstance().parse(texto)` — leniente (aceita prefixo numérico) e **dependente de locale**; seguido de `Long.valueOf`/`Integer.valueOf` estritos em `getInt/getLong`. Testado só em `pt_BR` (LAC-02). Reimplementação: padrão `^-?\d` no COMPAT (`N:Loader.getLong`).
- **A-06 `removerZerosEsquerda`** (`StringUtil`): laço `charAt(index)=='0'` sem teste de limite ⇒ `StringIndexOutOfBoundsException` para vazio/só zeros. Reimplementação: `N:Text.removeLeadingZeros`.
- **A-07 hash** (`L:asspa/client/Arquivo.obterHashArquivo(Computador)`, linha ~432–446): MD5 hexadecimal; `String.format("%032x", bigInt)` se `Versao.possuiCorrecaoHash` (major ≥ 4), senão `bigInt.toString(16)` (sem zeros à esquerda). Não reimplementado.
- **Datas leniente × estrita:** `SimpleDateFormat` padrão (leniente) em L-AG-05 e L-TI-06; `verificaData` (estrita) nos demais. Exemplo relevante: L-AG-02/03 (estrita, só reportam) coexistem com L-AG-05 (leniente) na mesma linha. Em TITULARIDADE, L-TI-02/03 lançam `NegocioException` e interrompem a linha, então a parte de *parse* de L-TI-06 só falha com L-TI-02 aprovada quando a data de início está vazia; o ramo "início posterior ao fim" de L-TI-06 falha com datas válidas.
- **Semântica SQL relevante (PROV):** `NOT IN` com `NULL`, `UCASE`, `REPEAT`/`CONCAT/RIGHT` e ordem de linhas sem `ORDER BY` (D13, R-OD-04).

---

## 9. Erros e relatório

- **Modelo:** `ErroValidacao(long linha, String arquivo, String message)`; `ErrorReportCmd` guarda os erros em `FileBackedList<ErroValidacao>` (persistência em arquivo para volumes grandes), `report()` acrescenta, **registra em `log.info(prettyMessage)`** e notifica `MessageConsumer` (texto na tela); `hasErros()` = lista não vazia (**global**); `reset()`/`close()`.
- **Rótulos (`arquivo`)** — regra por mecanismo (catálogo v1.1 §1.3): **físico** em E-03, E-13, G-02, E-02, `getInt/getLong` e em toda `NegocioException` capturada no laço de carga (L-CT-00, L-PE-00, L-TI-00, L-TI-02, L-TI-03, parse de L-TI-06); **lógico** (`NomeArquivo.X.toString()`) em `report` direto dos `validar()` de L-\*, em E-04 e em R-\*; exceções: L-PE-02 ⇒ `EXTRATO`, R-CT-02 ⇒ `null`, R-G112-1 ⇒ `Investigado`. `ErroValidacao.getPrettyMessage` reduz qualquer caminho a nome com `FilenameUtils.getName`. R-CT-02 sai com rótulo `null`; R-TI-04 com linha −1; L-PE-02 com rótulo `EXTRATO`.
- **Relatório:** `TarefaBackground.finishWithError` apaga a pasta `envio` do caso, chama `gerarRelatorio(errorReportCmd)` (HTML `relatorio.html` na pasta do caso, a partir de `templates/`), exibe "Relatório de inconsistências gerado." e habilita "Ver relatório". Não há ID de regra no relatório do original (só texto).
- **Falhas de infraestrutura:** `InfraestruturaException` (SQL, E/S) propaga ao `catch (Exception)` do worker ⇒ mensagem genérica na tela; **não** entra na lista de erros de validação. Na reimplementação: `Pipeline.run` converte `IllegalStateException` em uma violação `R-INFRA` (rótulo `EXTRATO`), equivalente documental da falha de infraestrutura (NPE de R-EX-02, PK/FK, INVESTIGADO vazio em R-G112-1).
- **Privacidade:** como `report()` registra a mensagem com CPF/CNPJ em `log.info`, os logs do original contêm dados pessoais (observado também em `A:07` §6).

---

## 10. Módulo de rede (descrito; **nunca executado**)

Fora do escopo de validação; incluído apenas para delimitar riscos. Nada em `cmd/` ou `http/` foi executado.
- **Transmissão:** `D:cmd/TransmitirDocumentosCmd.transmitir(TransmissaoDocumentoRequestTO, BiConsumer<Integer,Integer>)`: monta dados do atendimento e dos investigados (`montarDadosAtendimento`, `montarDadosInvestigado`), valida o código de autenticação (`validarCodigoAutenticacao`), gera arquivos de atendimento/hash/ZIP (`gerarArquivoAtendimento`, `gerarArquivoHash`, `gerarArquivoZip`), envia por `L:pgr/client/http/PostToUrl.post(Computador, String, List<String[]>, List<String>[, …])` e salva comprovante (`salvarComprovante`). UI: `ui/TransmissaoDocumentosFrame`, `telas/frmTrataEnvio`.
- **Conexão:** `L:pgr/client/http/{Conexao,TestaConexao,GetUrl,GetString,DownloadFile,GetVersaoNova,IsVersaoAtual}`. Literais de URL observados: `https://simba.mpf.mp.br/vamp/` (produção), `https://bender.pgr.mpf.mp.br/vamp/` (aparente desenvolvimento), `https://localhost:8443/vamp/`; `Configurador` retorna `https://simba.mpf.mp.br` como URL base. Certificados MPF em `certificados/` (AC_Equipamentos_MPF.crt, asspadev.cer, Autoridade_Certificadora_Raiz_Interna_MPF_v2.pem).
- **Rede durante a validação (E-15):** `frmSelecionaDiretorio.doInBackground` chama `controlador.getComputador(context.getNumeroComputador())` antes do hash; `L:pgr/client/Controlador.getComputadores()` faz GET `<urlVamp>orgaos?codprog=<código>[&T=1]` e mantém o resultado em memória; a versão do receptor (`Computador.getVersaoReceptor()` → `Versao`) governa **A-07** (`possuiCorrecaoHash`: major ≥ 4) e **L-OD-04** (`suportaCNPJComoNumeroBanco`: major ≥ 4, ou major ≥ 3 e minor ≥ 3). Ou seja, o aplicativo real **exige rede (ou servidor simulado) mesmo só para validar**. Nos testes diferenciais a lista de órgãos foi **injetada em memória** nos beans `Controlador` e a execução ocorreu em `bwrap --unshare-net` (nenhuma conexão aberta).
- **Pendências (LAC-04, LAC-06):** se o servidor aceita um envio com erros locais (D15); formato XML `<computador>` e conteúdo exato do comprovante.

---

## 11. Código morto e partes não alcançáveis

| Item | Evidência (verificada) | Situação |
|---|---|---|
| Sobrecarga `validar(ExtratoOrigemDestino)` em **6** regras: `ValidarCpfCnpjTransferenciaTedDocOrigemDestinoRule`, `ValidarIdentificacaoTedDocTransfEleitoralRule`, `ValidarDepositoDinheiroChequeEleitoralRule`, `ValidarIdentificacaoTransferenciaMesmoBancoRule`, `ValidarIdentificacaoTedDocRule`, `ValidarIdentificacaoTransacaoEmEspecieRule` | `grep -ln "validar(ExtratoOrigemDestino" rules/*.java` retorna esses 6 (`A:03` cita 7; catálogo v1.1 já corrigido para 6; RO-04) | Só a consulta SQL executa; as condições Java diferem (p.ex. `dataLimite20120701="01-07-2013"`) |
| `ValidarGab112SimGab109NaoRule`, `ValidarGab112SimGab800NaoRule` | existem em `rules/`; só referenciadas nos próprios arquivos | Nunca injetadas em `ValidarAtendimentoGab` |
| `carga/InvestigadoLocalizerCmd` | só referenciada em seu arquivo | Morto |
| `rules/ValidarCasoPossuiInvestigadoComRelacionamentoRule` | usada apenas por `telas/frmIdentificaAtendimento` | Não faz parte da validação de arquivos |
| `DAO.Mapa.getCamposNaoNulos`, `getCamposNaoZeros`, `colAparaColB`, `getNomesOficios`, `getCodigoArquivoA`, `getCamposTabelasA` | `grep` sem usos fora de `Mapa.java` (apenas `getCamposTabelasB` é usado: `cmd/GetInvestigadosCmd`, `telas/frmDadosAtendimento`) | Auxiliares sem uso |
| `carga/CampoArquivo.tipo` / `TipoCampoArquivo` | nenhum `getTipo()` consumido | Metadado inerte |
| L-OD-04 | `NUMERO_BANCO_OD` tem máx. 3 (E-04) e linha com erro estrutural não chega a `validar()` | **INALC** (catálogo); teste `JU:Casos.lOd04_inalcancavelPorCausaDeE04` |
| R-G\* e G-03…G-07 | dependem de `getCamposArquivo()` ≠ null | Inatingíveis no app real (G-DEF) |

---

## 12. Reimplementação independente (`implementacao/validador-independente`)

### 12.1 Projeto
Maven, `groupId estudo.simba`, `artifactId validador-independente`, versão **0.2.0**, `maven.compiler.release=21`, **sem dependências de produção**; testes JUnit Jupiter 5.11.4; plugins `maven-surefire-plugin 3.5.2` e `maven-jar-plugin 3.4.2` (`mainClass estudo.simba.validador.Cli`). Natureza: **estudo**; não substitui o aceite oficial; termos de uso do original **não verificados** (LAC-03/D11).

### 12.2 Pacotes e classes (`N:` — verificadas)
**`estudo.simba.validador` (bancário):**
| Classe | Papel | Regras |
|---|---|---|
| `Pipeline` (`run(Path, Config, List<Db.Inv>)` → `Result{violacoes, conteudo, falhaCarga}`) | orquestra existência → vazios → carga fail-fast → conteúdo fail-fast | E-01, E-02, E-07, E-08 |
| `Loader` (`carregarArquivo`, `carregar`, `agencia`, `conta`, `pessoa`, `titularidade`, `extrato`, `od`, `getLong`, `toInt`) | leitura ISO-8859-1, split, E-03/E-04, E-06 (COMPAT), regras L-\*, inserção em `Db` | E-03…E-06, E-13, L-\* |
| `ContentRules` (`validar`, `agencias`, `contas`, `titulares`, `extrato`, `origemDestino`) | regras R-\* em memória, na ordem de `ValidarAtendimento`, parando no 1º arquivo reprovado | E-08, R-\* |
| `Db` | "banco" em memória (listas + índices `idxAgencia`, `idxConta`, conjuntos de PK de EXTRATO/OD; `addExtrato`/`addOd` lançam `IllegalStateException` p/ PK/FK como o HSQLDB) | modelo §5 |
| `Schemas` | layout (nome, máx.) das 5 tabelas — fonte única de E-03/E-04 | E-03, E-04 |
| `TabSplitter` | `split(linha, Mode)`: COMPAT = `split("(?=\t)")`+`trim`; ESTRITO = `split("\t",-1)`+`trim` | E-05b |
| `DateRules` | `verificaData`, `parseLenient`, `of`, `br` | A-01 |
| `CpfCnpj`, `Cnab`, `Text` | A-02/A-03, A-04, A-06 e utilitários (`isEmpty`, `padLeft`, `removeLeadingZeros`, `intOrNull`) | A-\* |
| `Limiares` (`padrao()`, `carregar(Path)`) | limiares/códigos de OD; `record` imutável | R-OD-\* |
| `Config` (`of`, `comLimiares`, `receptorSuportaCnpjComoBanco`) | caso, banco, perfil eleitoral (E-10), modo, receptor (L-OD-04), limiares | E-10, E-15 (parcial) |
| `Mode` | `COMPAT`, `ESTRITO` | — |
| `FileKind` | AGENCIAS, CONTAS, TITULARES, EXTRATO, ORIGEM_DESTINO | — |
| `Violation`, `Report`, `Reports` | modelo de violação com **ruleId**, coletor, saídas texto/JSON/CSV/"comparável" | E-12 |
| `Cli` | linha de comando e modo lote | — |

**`estudo.simba.validador.gab` (corretoras):** `GabKind` (GAB109, GAB112, GAB800), `GabLayouts` (`of`, `numericas`, `datas`, `tamanhoLinha`), `GabConfig` (`record`; `defeito` ligado por padrão em COMPAT), `GabLoader` (`carregar`; `v109/v112/v800`; `gravar` emula NUMERIC/DATE), `GabRules` (`validar`, `clientesAusentesNoGab112`, `gab112`), `GabPipeline` (`run`), `GabDb`.

### 12.3 Modos
| Aspecto | COMPAT | ESTRITO |
|---|---|---|
| Linhas após o 1º erro (E-06) | só E-03/E-04 | validação completa |
| 1º campo de comprimento zero (E-05b) | descartado | preservado |
| `NUMERO_BANCO` vazio (L-AG-04) | 2 erros (L-AG-01 + E-13; não numérico = só E-13) | 1 erro (L-AG-01) |
| Rótulo da data de renda (L-PE-02) | `EXTRATO` | `TITULARES` |
| CPF só de zeros (L-PE-03) | E-13 | "CPF inválido" |
| `TIPO_CONTA_OD` (L-OD-01) | valida TIPO_PESSOA (defeito) | valida `[1-4]` |
| NPE de R-EX-02 | reproduzido (`IllegalStateException` → `R-INFRA`) | mensagem com "sem data" |
| GAB (G-DEF) | `defeito=true`: toda linha da largura certa ⇒ E-13 | lógica pretendida |
`--defeito-gab true|false` desacopla G-DEF do modo (usado no diferencial).

### 12.4 Limiares (`Limiares` + `implementacao/limiares-6.2.6.properties`)
Chaves: `versao`; `ident.data_corte` (2006-01-01) e `ident.valor_minimo` (1000000) — R-OD-01/02/04; `ted_doc.tipos` (120,209); `mesmo_banco.tipos` (117,213); `cpf_cnpj_od.tipos` (117,120,209,213); `especie.tipos` (123,220) e `especie.faixas` (`data:valor_minimo;…`: 2013-07-01:1000000; 2013-01-02:5000000; 2012-07-01:10000000) — R-OD-03; `eleitoral.transferencia.tipos`, `eleitoral.deposito.tipo` (201), `eleitoral.deposito.valor_minimo` (10000) — R-OD-E1/E2. O arquivo versionado é **idêntico** ao padrão interno (`JU:Lim.arquivoVersionadoEquivaleAoPadraoInterno`); formato inválido ⇒ `IllegalArgumentException` (`JU:Lim.arquivoInvalidoEhRejeitado`). Unidade dos valores **ND** (LAC-01).

### 12.5 CLI (`Cli`)
```
java -jar target/validador-independente-0.2.0.jar --pasta DIR --caso CASO [--banco N] [--modo compat|estrito]
     [--investigados F.tsv] [--saida texto|json|csv] [--receptor major.minor] [--limiares ARQ.properties]
     [--tipo bancario|corretora --layout 1|2 --defeito-gab true|false]
java -jar ... --lote DIR_DE_CASOS [--modo ...]   # grava saida-<modo>[...].txt por subpasta (usado pelo diferencial)
```
Código de saída: 0 sem violações, 1 com violações, 2 uso incorreto. `investigados.tsv` (TAB): `TIPO_PESSOA, CPF_CNPJ, NOME, TEVE_RELACIONAMENTO, TEVE_CONTA, INICIO_AFASTAMENTO, FIM_AFASTAMENTO` (ddMMyyyy) — substitui as telas de cadastro (LAC-06). `--receptor` habilita L-OD-04 (inalcançável de qualquer forma).

### 12.6 O que **não** está na reimplementação
E-09 (versão do banco), E-11 (UI), E-14/A-07 (hash, contagem, cópia), E-15 (rede; só `Config.receptor`), geração de relatório HTML/PDF, cadastro de atendimento, transmissão, locale `en_US`. Decisão de arquitetura: índices em memória em vez de HSQLDB (`A:07` §4); a equivalência de NULL/`NOT IN` com o HSQLDB é **modelada** e verificada pelo diferencial, não por reuso das SQL.

---

## 13. Estratégia de verificação e cobertura

### 13.1 Camadas
1. **Modelo de referência Python** `T:validador_ref.py` (774 linhas), escrito a partir da leitura do decompilado; não importa nem executa o app. **48 testes** em `T:test_caracterizacao.py` (4 primitivas Java, 5 CPF/CNPJ, 3 CNAB, 6 carga estrutural, 16 carga por arquivo, 14 conteúdo) — contagem conferida por `grep -c "def test_"` por classe.
2. **Casos sintéticos** `T:gerar_casos.py` → `T:casos/00…15` (ISO-8859-1/CRLF; `esperado.json`/`esperado.txt` = saída do **modelo**); todos fictícios (CPF `52998224725`, CNPJ `11222333000181` são vetores públicos).
3. **JUnit da reimplementação (32 `@Test`, contados por `grep`):** `PrimitivasTest` 7, `CasoMinimoTest` 8, `ModosTest` 6, `GabTest` 6, `LimiaresTest` 3, `GoldenCasosTest` 2 (`casosSinteticos`: compara o COMPAT com `esperado.txt` do modelo em `T:casos`, exige ≥16; `casosPorRegraObservadosNoOriginal`: roda os 71 casos de `T:casos-por-regra`, cujo `esperado.txt` é a saída do **original**).
4. **Teste cruzado** `T:fuzz_cruzado.py` (modelo × Java COMPAT; 2.100 cenários, 0 divergências): mede **consistência interna** (mesmo autor, mesma leitura), não fidelidade ao original.
5. **Execução diferencial contra o aplicativo original** (somente `bwrap --unshare-net`, FS somente leitura, diretório descartável; `T:diferencial/`):
   - **N1** `diferencial_nivel1.py` + `Harness.java`: funções puras (`Util.tabSeparatedStringToArray`, `verificaData`, `CpfCnpjUtil`, `CnabRule`, `StringUtil`): **20.103 vetores, 0 divergências**.
   - **HASH** `diferencial_hash.py` + `HashHarness.java`: 401 arquivos × receptor 4.0.0/3.9.0/1.0.0; 0 divergências (28 com MD5 iniciando em zero).
   - **N2** `diferencial_nivel2.py` + `Nivel2.java`: sobe o contexto Spring real, cria o HSQLDB do caso e chama `Carregar`, `ControladorValidador.validarConteudo`, `ErrorReportCmd`; **2.316 cenários** (16 + 1.500 aleatórios + 800 dirigidos a limiares) **+ 18** dirigidos pela cobertura (inclui 1.200 e 250.005 linhas) = 2.334; 0 divergências reais. Equivalência = mesmo multiconjunto de mensagens + mesmo desfecho por arquivo (ordem de R-OD-04 não é contratual — D13).
   - **GABD** `Nivel2Gab.java`, `diferencial_gab.py`, `fuzz_gab.py`, `gab_gen.py`, `gab_layouts.json`: por modo, 3.000 cenários (v1/v2) + 1.500 (GAB v2 completo) = 4.500; modos: original com o defeito × Java e original sob remendo em memória × Java com a lógica pretendida; 0 divergências.
   - **N3** `Nivel3.java` + `diferencial_gui.py`: tela real `frmSelecionaDiretorio` na sessão gráfica; **73 cenários**; mensagens/ícones, hash gravado, linhas e cópia (73/73 cada); 0 divergências.
   - **Cobertura JaCoCo 0.8.14** do original (`rules`+`carga`+`validacao`): 63,0% → **82,9%** das linhas; revelou 2 divergências reais (R-CT-02 rótulo `null`; R-TI-04 linha −1), corrigidas no modelo, no Java e no catálogo.
6. **Mapa de cobertura por regra (catálogo v1.2; `docs/MAPA_COBERTURA_REGRAS.md`)** — fecha LAC-09 para as regras que emitem mensagem própria. Infraestrutura (verificada no disco): `T:diferencial/MapaRegras.java` (atribui `ruleId` por violação), `T:diferencial/rodar_corpus.sh`, `T:diferencial/cobertura_por_regra.py` (traduz ids internos e escolhe o caso mínimo), `T:gerar_lacunas.py` (7 cenários de lacuna) e `T:casos-por-regra/<ID>/` (71 casos mínimos, um por regra observada; cada pasta tem os 5 arquivos, `investigados.tsv`, `ORIGEM.md` e `esperado.txt` = saída do original). Corpus: 16 golden + 17 dirigidos por cobertura + 800 dirigidos a limiares + 2.100 aleatórios + 7 de lacuna = **2.940 cenários**, 0 divergências reais (28 com só ordem diferente em R-OD-04); repetido em `en_US` em 2.140 cenários, 0 divergências (**LAC-02 fechada**). Regras sem observação direta: E-01, E-05, E-09, E-15 e L-OD-04 (INALC). Na matriz §14 a evidência `casos-por-regra/<ID>` é citada para as 71 regras observadas.
7. **Sondas:** `T:sondas/SplitProbe.java` (código nosso, JDK 25) valida o `split` sem usar o app.

### 13.2 Alcance e limites
- A cobertura por regra individual **passou a ser rastreada** (item 6 acima; LAC-09 fechada na v1.2). A coluna "Teste/evidência" distingue teste unitário dedicado de observação no original (`casos-por-regra/<ID>`).
- Não cobertos: acentos/BOM (LAC-08), volume > 250.000 linhas além do sintético, dados reais, unidade de valor (LAC-01), L-OD-04 (INALC), E-09.
- `JU:Casos.arquivoAusenteEVazio` só afirma E-01 (a asserção de "vazio" não existe apesar do nome): **E-02 bancário não tem teste unitário dedicado** (registrado em §16 RO-07).

---

## 14. Matriz de rastreabilidade (103 IDs)

Legenda das colunas: **Original** = classe.método em `D:` (ou `L:` quando indicado); **Reimplementação** = classe.método em `N:` (— = não reimplementado); **Teste/evidência** = abreviações de §0.3; **Conf.** = confiança do catálogo.

### 14.1 Estruturais e de fluxo (16)
| ID | Original | Reimplementação | Teste/evidência | Conf. |
|---|---|---|---|---|
| E-01 | `ControladorValidador.verificarExistenciaArquivosBancario`; `DAO/Mapa.nomesTabelasA` | `Pipeline.run` (laço E-01) | `JU:Casos.arquivoAusenteEVazio`; C-00 | EST |
| E-02 | `ControladorValidador.verificarArquivosVazios` | `Pipeline.run` (bloco E-02) | sem teste unitário dedicado (observada no original: mapa de cobertura); N2/N3 agregados; `casos-por-regra/E-02` | EXEC |
| E-03 | `AbstractCarregarArquivoCmd.carregar` | `Loader.carregar` | `JU:Casos.colunasETamanho`; `PY:CargaEstrutural.test_quantidade_de_colunas`; C-01, C-02; N2; `casos-por-regra/E-03` | EXEC |
| E-04 | `AbstractCarregarArquivoCmd.validarCampos` + `getCamposArquivo()` | `Loader.carregar` + `Schemas.LAYOUT` | `JU:Casos.colunasETamanho`, `lOd04_inalcancavel…`; `PY:CargaEstrutural.test_tamanho_maximo_do_campo`; `casos-por-regra/E-04` | EXEC |
| E-05 | `AbstractCarregarArquivoCmd.carregar` (`Channels.newReader` ISO-8859-1, `readLine`) | `Loader.carregarArquivo` (`Files.readAllLines`, ISO-8859-1) | fixtures em CRLF; sem teste de acentos/BOM (LAC-08) | EST |
| E-05b | `util/Util.tabSeparatedStringToArray` (só o 1º campo de comprimento zero é descartado; finais vazios preservados — catálogo v1.1) | `TabSplitter.split` | `JU:Prim.splitCompatDescartaCampoVazioInicial`, `splitEstritoPreservaVazios`; `JU:Modos.e05b_primeiroCampoVazio`; `PY:PrimitivasJava.test_split_tab_equivale_a_sonda_java`; sonda `SplitProbe`; C-02; N1 (1.508) | EXEC |
| E-06 | `AbstractCarregarArquivoCmd.carregar` (`hasErros()`); `L:error/ErrorReportCmd.hasErros` | `Loader.carregar` (ramo `compat() && !rep.isEmpty()`); `Report` | `JU:Modos.e06_erroGlobalSoNoCompat`; `PY:CargaEstrutural.test_apos_primeiro_erro_…`; C-03; N2 (H3) | EXEC |
| E-07 | `Carregar.carregar`/`temErroNaCarga`; `CarregarArquivoTitularesCmd.carregar` | `Pipeline.run` (laço do `Loader`); `Loader.carregarArquivo` (TITULARES: PESSOA ∥ TITULARIDADE) | sem teste unitário dedicado (observada no original: mapa de cobertura); N2 agregado. A mensagem só vai ao log (catálogo v1.1; RO-01 resolvido); `casos-por-regra/E-07` | EXEC |
| E-08 | `frmSelecionaDiretorio.TarefaBackground.doInBackground`; `ControladorValidador.validarConteudo`; `ValidarAtendimento.validarArquivo*` | `ContentRules.validar` | `JU:Casos.regrasDeConteudo`; C-09…C-14 (`#CONTEUDO`); N2 | EXEC |
| E-09 | `ControladorValidador.checkVersaoSistemaBanco`/`getVersaoSistemaBanco`; `BancoHSQLDB.getVersaoSistema` | — | — (sem atendimento persistido) | EST |
| E-10 | `L:Context.isEleitoral` | `Config.of` (`eleitoral`) | `JU:Casos.eleitoralTrocaRegrasDeOd`; `PY:CargaPorArquivo.test_agencia_eleitoral_exige_so_tres_campos`; `PY:RegrasDeConteudo.test_eleitoral_usa_outras_regras_de_od`; C-13 | EXEC |
| E-11 | `frmSelecionaDiretorio.TarefaBackground.doInBackground` (`finally`, `continuarButton`) | — (UI; `Cli` devolve 0/1/2) | N3 (`gui.png`, 73 cenários) | EXEC |
| E-12 | `L:error/ErroValidacao.getPrettyMessage` | `Violation.pretty`; `Reports.texto` | todos os `esperado.txt` (`JU:Gold`) | EXEC |
| E-13 | `AbstractCarregarArquivoCmd.carregar` (`catch (Exception)`) | `Loader.carregar` (`catch RuntimeException` → `L-UNK`) | `JU:Modos.lAg04_…`, `lPe03_cpfSoZeros`; `PY:CargaPorArquivo.test_pessoa_cpf_so_zeros_…`; C-04, C-07; `casos-por-regra/E-13` | EXEC |
| E-14 | `cmd/GerarHashArquivosCmd.gerar`; `ControladorValidador.gerarHashArquivo`/`gravarHashArquivo`; `BancoHSQLDB.gravarHashArquivo`; `AbstractCarregarArquivoCmd.atualizaQuantidadeLinhaTabela` (contagem gravada durante a carga, ao fim de cada arquivo carregado); `cmd/CopiarArquivoParaPastaTxtCmd.copiar` | — | HASH; N3 (hash/linhas/cópia 73/73); hash e cópia antes de E-02 | EXEC |
| E-15 | `L:Controlador.getComputadores`/`getComputador`; `frmSelecionaDiretorio.doInBackground` | `Config.receptor` (parcial, só L-OD-04) | `JU:Casos.lOd04_inalcancavel…`; N3 (órgãos injetados) | EST |

### 14.2 Algoritmos (7)
| ID | Original | Reimplementação | Teste/evidência | Conf. |
|---|---|---|---|---|
| A-01 | `L:asspa/client/Util.verificaData` | `DateRules.verificaData` | `JU:Prim.verificaData`; `PY:PrimitivasJava.test_verifica_data`; N1 (3.261) | EXEC |
| A-02 | `L:pgr/util/CpfCnpjUtil.validarCpf`; `DigitoVerificador.validarDvCpf`/`dvCpf` | `CpfCnpj.validarCpf`/`dvCpf` | `JU:Prim.cpfCnpjVetores`; `PY:CpfCnpj.test_*`; N1 (6.006); 3.000 CPFs × algoritmo oficial | EXEC |
| A-03 | `CpfCnpjUtil.validarCnpj`; `DigitoVerificador.validarDvCnpj`/`dvCnpj` | `CpfCnpj.validarCnpj`/`dvCnpj` | `JU:Prim.cnpjAlfanumericoContraAlgoritmoOficial`; `PY:CpfCnpj.test_diferencial_…_cnpj_alfanumerico`; N1 (6.005) | EXEC |
| A-04 | `D:rules/CnabRule.validarCnabCredito`/`Debito`/`CreditoDebito` | `Cnab.creditoInvalido`/`debitoInvalido`/`creditoDebitoInvalido` | `JU:Prim.cnabFronteiras`; `PY:Cnab.test_credito/debito/credito_debito`; N1 (3.306) | EXEC |
| A-05 | `L:pgr/util/StringUtil.isNumber`; `AbstractCarregarArquivoCmd.getInt`/`getLong` | `Loader.getLong`, `Loader.toInt`, `Text.intOrNull` | `PY:CargaPorArquivo.test_extrato_valor_nao_numerico`; N2 só `pt_BR` | PROV |
| A-06 | `L:StringUtil.removerZerosEsquerda` | `Text.removeLeadingZeros` | `JU:Prim.removerZerosEsquerdaFalhaParaSoZeros`; `PY:PrimitivasJava.test_remover_zeros_esquerda_…`; N1 | EXEC |
| A-07 | `L:asspa/client/Arquivo.obterHashArquivo`; `L:to/Versao.possuiCorrecaoHash` | — | HASH (401 × 3 versões) | EXEC (dado o receptor) |

### 14.3 Carga por linha (38)
| ID | Original (`D:carga/…`) | Reimplementação | Teste/evidência | Conf. |
|---|---|---|---|---|
| L-AG-01 | `CarregarArquivoAgenciasCmd.validarCamposObrigatorios`/`validarCamposObrigatoriosEleitoral` | `Loader.agencia` | `PY:CargaPorArquivo.test_agencia_eleitoral_exige_so_tres_campos`; `JU:Modos.e05b…`; C-04; `casos-por-regra/L-AG-01` | EXEC |
| L-AG-02 | `CarregarArquivoAgenciasCmd.validarDataAberturaAgencia` | `Loader.agencia` → `dataValida` | sem teste unitário dedicado (observada no original: mapa de cobertura); `casos-por-regra/L-AG-02` | EXEC |
| L-AG-03 | `CarregarArquivoAgenciasCmd.validarDataFechamentoAgencia` | `Loader.agencia` → `dataValida` | sem teste unitário dedicado (observada no original: mapa de cobertura); `casos-por-regra/L-AG-03` | EXEC |
| L-AG-04 | `CarregarArquivoAgenciasCmd.validarNumeroBanco` | `Loader.agencia` (`toInt`) | `JU:Modos.lAg04_bancoVazioGeraDoisErrosNoCompat`; `PY:CargaEstrutural.test_numero_banco_vazio_gera_dois_erros`, `PY:CargaPorArquivo.test_agencia_banco_diferente_do_atendimento`; C-04; `casos-por-regra/L-AG-04` | EXEC |
| L-AG-05 | `CarregarArquivoAgenciasCmd.validarDatas` (`sdf` leniente) | `Loader.agencia` + `DateRules.parseLenient` | `PY:CargaPorArquivo.test_agencia_fechamento_antes_da_abertura`; `casos-por-regra/L-AG-05` | EXEC |
| L-AG-06 | `CarregarArquivoAgenciasCmd.validarUf`; `enums/UF` | `Loader.agencia` (`UFS`) | `JU:Casos.ufCaseSensitive`; `PY:CargaPorArquivo.test_agencia_uf_case_sensitive_…`; C-03; `casos-por-regra/L-AG-06` | EXEC |
| L-AG-07 | `CarregarArquivoAgenciasCmd.setParametros` (`Integer.valueOf`) | `Loader.agencia` (bloco final) | sem teste unitário dedicado (observada no original: mapa de cobertura); `casos-por-regra/L-AG-07` | EXEC |
| L-CT-00 | `CarregarArquivoContasCmd.convert`; `AgenciaLocalizerCmd.findIdAgencia` | `Loader.conta`; `Db.findAgencia` | `PY:CargaPorArquivo.test_conta_agencia_inexistente`, `…_zeros_a_esquerda_equivale`; `casos-por-regra/L-CT-00` | EXEC |
| L-CT-01 | `CarregarArquivoContasCmd.validarCamposObrigatorios` | `Loader.conta` | sem teste unitário dedicado (observada no original: mapa de cobertura); `casos-por-regra/L-CT-01` | EXEC |
| L-CT-02 | `CarregarArquivoContasCmd.validarDataAberturaConta` | `Loader.conta` → `dataValida` | sem teste unitário dedicado (observada no original: mapa de cobertura); `casos-por-regra/L-CT-02` | EXEC |
| L-CT-03 | `CarregarArquivoContasCmd.validarDataEncerramentoConta` | `Loader.conta` → `dataValida` | sem teste unitário dedicado (observada no original: mapa de cobertura); `casos-por-regra/L-CT-03` | EXEC |
| L-CT-04 | `CarregarArquivoContasCmd.validarTipoConta` | `Loader.conta` | `PY:CargaPorArquivo.test_conta_tipo_e_movimentacao_invalidos`; C-05; `casos-por-regra/L-CT-04` | EXEC |
| L-CT-05 | `CarregarArquivoContasCmd.validarMovimentacaoConta` | `Loader.conta` | `PY:CargaPorArquivo.test_conta_tipo_e_movimentacao_invalidos`; `casos-por-regra/L-CT-05` | EXEC |
| L-PE-00 | `CarregarTabelaPessoaCmd.convert` (`Double.valueOf`) | `Loader.pessoa` | sem teste unitário dedicado (observada no original: mapa de cobertura); `casos-por-regra/L-PE-00` | EXEC |
| L-PE-01 | `CarregarTabelaPessoaCmd.validarCamposObrigatorios` | `Loader.pessoa` (`OBRIG_TITULAR`) | sem teste unitário dedicado (observada no original: mapa de cobertura); `casos-por-regra/L-PE-01` | EXEC |
| L-PE-02 | `CarregarTabelaPessoaCmd.validarDataAtualizacaoRenda` | `Loader.pessoa` (rótulo por modo) | `JU:Modos.lPe02_rotuloEExtratoSoNoCompat`; `PY:CargaPorArquivo.test_pessoa_data_renda_invalida_…`; `casos-por-regra/L-PE-02` | EXEC |
| L-PE-03 | `CarregarTabelaPessoaCmd.validarCpfCnpj`; `L:CpfCnpjUtil`; `StringUtil.removerZerosEsquerda` | `Loader.pessoa`; `CpfCnpj`; `Text.removeLeadingZeros` | `JU:Modos.lPe03_cpfSoZeros`; `PY:CargaPorArquivo.test_pessoa_cpf_invalido_e_tipo`, `…_cpf_so_zeros_…`; C-06, C-07; `casos-por-regra/L-PE-03` | EXEC |
| L-PE-04 | `CarregarTabelaPessoaCmd.validarTipoPessoa` | `Loader.pessoa` | `PY:CargaPorArquivo.test_pessoa_cpf_invalido_e_tipo`; `casos-por-regra/L-PE-04` | EXEC |
| L-TI-00 | `CarregarTabelaTitularidadeCmd.convert`; `ContaLocalizerCmd.findIdConta`; `PessoaLocalizerCmd.findIdPessoa` | `Loader.titularidade`; `Db.findConta` | `PY:CargaPorArquivo.test_titularidade_conta_inexistente_e_datas`; `casos-por-regra/L-TI-00` | EXEC |
| L-TI-01 | `CarregarTabelaTitularidadeCmd.validarCamposObrigatorios`/`…Eleitoral` | `Loader.titularidade` | sem teste unitário dedicado (observada no original: mapa de cobertura); `casos-por-regra/L-TI-01` | EXEC |
| L-TI-02 | `CarregarTabelaTitularidadeCmd.validarDataInicioRelacionamento` | `Loader.titularidade` (`Negocio`) | sem teste unitário dedicado (observada no original: mapa de cobertura); `casos-por-regra/L-TI-02` | EXEC |
| L-TI-03 | `CarregarTabelaTitularidadeCmd.validarDataFimRelacionamento` | `Loader.titularidade` (`Negocio`) | sem teste unitário dedicado (observada no original: mapa de cobertura); `casos-por-regra/L-TI-03` | EXEC |
| L-TI-04 | `CarregarTabelaTitularidadeCmd.validarTipoTitular` | `Loader.titularidade` | `PY:CargaPorArquivo.test_titularidade_tipo_e_investigada`; `casos-por-regra/L-TI-04` | EXEC |
| L-TI-05 | `CarregarTabelaTitularidadeCmd.validarPessoaInvestigada` | `Loader.titularidade` | `PY:CargaPorArquivo.test_titularidade_tipo_e_investigada`; `casos-por-regra/L-TI-05` | EXEC |
| L-TI-06 | `CarregarTabelaTitularidadeCmd.validarDatasRelacionamento` | `Loader.titularidade` + `DateRules.parseLenient` | `PY:CargaPorArquivo.test_titularidade_conta_inexistente_e_datas`; `casos-por-regra/L-TI-06` | EXEC |
| L-EX-00 | `CarregarArquivoExtratoCmd.convert` (`getLong`/`getInt`) | `Loader.extrato`; `Loader.getLong` | `PY:CargaPorArquivo.test_extrato_valor_nao_numerico`; `casos-por-regra/L-EX-00` | PROV |
| L-EX-01 | `CarregarArquivoExtratoCmd.validarIdConta` | `Loader.extrato`; `Db.findConta` | `PY:CargaPorArquivo.test_extrato_valor_negativo_e_conta_nao_localizada`; `casos-por-regra/L-EX-01` | EXEC |
| L-EX-02 | `CarregarArquivoExtratoCmd.validarCamposObrigatorios` | `Loader.extrato` | sem teste unitário dedicado (observada no original: mapa de cobertura); `casos-por-regra/L-EX-02` | EXEC |
| L-EX-03 | `CarregarArquivoExtratoCmd.validarDataLancamento` | `Loader.extrato` → `dataValida` | sem teste unitário dedicado (observada no original: mapa de cobertura); `casos-por-regra/L-EX-03` | EXEC |
| L-EX-04 | `CarregarArquivoExtratoCmd.validarNaturezaLancamento` | `Loader.extrato` | `PY:CargaPorArquivo.test_extrato_naturezas_e_cnab`; `casos-por-regra/L-EX-04` | EXEC |
| L-EX-05 | `CarregarArquivoExtratoCmd.validarNaturezaSaldo` | `Loader.extrato` | sem teste unitário dedicado (observada no original: mapa de cobertura); `casos-por-regra/L-EX-05` | EXEC |
| L-EX-06 | `CarregarArquivoExtratoCmd.validarTipoLancamentoCredito`/`Debito`/`Asterisco`; `rules/CnabRule` | `Loader.extrato`; `Cnab` | `PY:CargaPorArquivo.test_extrato_naturezas_e_cnab`; C-08; `casos-por-regra/L-EX-06` | EXEC |
| L-EX-07 | `CarregarArquivoExtratoCmd.validarLancamentoSaldoNegativo` | `Loader.extrato` | `PY:CargaPorArquivo.test_extrato_valor_negativo_e_conta_nao_localizada`; `casos-por-regra/L-EX-07` | EXEC |
| L-OD-00 | `CarregarArquivoOrigemDestinoCmd.convert` | `Loader.od` (`Long.valueOf` no COMPAT) | `PY:CargaPorArquivo.test_origem_destino_quirks` (CODIGO_CHAVE_OD "ABC"); `casos-por-regra/L-OD-00` | EXEC (CODIGO_CHAVE_OD ⇒ E-13) / PROV (demais campos, A-05) |
| L-OD-01 | `CarregarArquivoOrigemDestinoCmd.validarTipoConta` (testa `getTipoPessoa()`) | `Loader.od` (ramo por modo) | `JU:Modos.lOd01_tipoContaOd`; `PY:CargaPorArquivo.test_origem_destino_quirks`; C-15; `casos-por-regra/L-OD-01` | EXEC |
| L-OD-02 | `CarregarArquivoOrigemDestinoCmd.validarTipoPessoa` | `Loader.od` | idem; C-15; `casos-por-regra/L-OD-02` | EXEC |
| L-OD-03 | `CarregarArquivoOrigemDestinoCmd.validarSituacaoIdentificao` | `Loader.od` | `PY:CargaPorArquivo.test_origem_destino_quirks`; `casos-por-regra/L-OD-03` | EXEC |
| L-OD-04 | `CarregarArquivoOrigemDestinoCmd.validarNumeroBanco`; `L:Versao.suportaCNPJComoNumeroBanco` | `Loader.od` (com `Config.receptor`) | `JU:Casos.lOd04_inalcancavelPorCausaDeE04` | INALC |

### 14.4 Conteúdo SQL bancário (29)
| ID | Original (`D:rules/…`, método `validar()`) | Reimplementação | Teste/evidência | Conf. |
|---|---|---|---|---|
| R-AG-01 | `ValidarAgenciaRepetidaRule` | `ContentRules.agencias` | `JU:Casos.regrasDeConteudo`; `PY:RegrasDeConteudo.test_agencia_duplicada`; C-12; `casos-por-regra/R-AG-01` | EXEC |
| R-CT-01 | `ValidarContaSeparadorRule` | `ContentRules.contas` (bloco 1) | `PY:RegrasDeConteudo.test_conta_com_separador`; `casos-por-regra/R-CT-01` | EXEC |
| R-CT-02 | `ValidarContaRepetidaRule` (herda `AbstractRule`) | `ContentRules.contas` (rótulo `"null"`) | FZC (achado JaCoCo); sem teste unitário dedicado; `casos-por-regra/R-CT-02` | EXEC |
| R-CT-03 | `ValidarContaSemTitularesRule` | `ContentRules.contas` | sem teste unitário dedicado (observada no original: mapa de cobertura); `casos-por-regra/R-CT-03` | EXEC |
| R-CT-04 | `ValidarContaSemTitularInvestigado` | `ContentRules.contas` | sem teste unitário dedicado (observada no original: mapa de cobertura); `casos-por-regra/R-CT-04` | EXEC |
| R-CT-05 | `ValidarContaSemMovimentacao` | `ContentRules.contas` | `PY:RegrasDeConteudo.test_atendimento_sem_movimentacao_alguma`; `casos-por-regra/R-CT-05` | EXEC |
| R-CT-06 | `ValidarMovimentacaoContaInvestigadoNaoRule` | `ContentRules.contas` | sem teste unitário dedicado (observada no original: mapa de cobertura) (FZD agregado); `casos-por-regra/R-CT-06` | EXEC |
| R-CT-07 | `ValidarTitularUnicoRule` | `ContentRules.contas` | FZD (2º titular T); `casos-por-regra/R-CT-07` | EXEC |
| R-CT-08 | `ValidarMovimentacaoContaInvestigadoSimRule` | `ContentRules.contas` | FZD agregado; `casos-por-regra/R-CT-08` | EXEC |
| R-CT-09 | `ValidarContaInvestigadoComMovimentacaoSemExtratoRule` | `ContentRules.contas` | `PY:RegrasDeConteudo.test_conta_mov1_sem_extrato`; `casos-por-regra/R-CT-09` | EXEC |
| R-CT-10 | `ValidarContaInvestigadaSemMovimentacaoComExtratoRule` | `ContentRules.contas` | `JU:Casos.regrasDeConteudo`; `PY:RegrasDeConteudo.test_conta_mov2_com_extrato`; C-09; `casos-por-regra/R-CT-10` | EXEC |
| R-TI-01 | `ValidarDadosObrigatoriosTitularRule` | `ContentRules.titulares` | FZD (titular com dados faltando); `casos-por-regra/R-TI-01` | EXEC |
| R-TI-02 | `ValidarDataAberturaRelacionamentoTitularidadeRule` | `ContentRules.titulares` | sem teste unitário dedicado (observada no original: mapa de cobertura); `casos-por-regra/R-TI-02` | EXEC |
| R-TI-03 | `ValidarDataEncerramentoRelacionamentoTitularidadeRule` | `ContentRules.titulares` | sem teste unitário dedicado (observada no original: mapa de cobertura); `casos-por-regra/R-TI-03` | EXEC |
| R-TI-04 | `ValidarInvestigadoCadastroSimArquivoNaoRule` | `ContentRules.titulares` (linha −1) | FZC (achado JaCoCo); `casos-por-regra/R-TI-04` | EXEC |
| R-TI-05 | `ValidarInvestigadoPessoaInvestigada0Rule` | `ContentRules.titulares` | sem teste unitário dedicado (observada no original: mapa de cobertura); `casos-por-regra/R-TI-05` | EXEC |
| R-TI-06 | `ValidarInvestigadoCadastroNaoArquivoSimRule` | `ContentRules.titulares` | sem teste unitário dedicado (observada no original: mapa de cobertura); `casos-por-regra/R-TI-06` | EXEC |
| R-TI-07 | `ValidarContaInvestigadaSemTitularTRule` | `ContentRules.titulares` | sem teste unitário dedicado (observada no original: mapa de cobertura); `casos-por-regra/R-TI-07` | EXEC |
| R-EX-01 | `ValidarExtratoForaPeriodoAfastamentoRule` | `ContentRules.extrato` (bloco 1) | sem teste unitário dedicado (observada no original: mapa de cobertura); `casos-por-regra/R-EX-01` | EXEC |
| R-EX-02 | `ValidarExtratoForaPeriodoContaAbertaRule` | `ContentRules.extrato` (NPE ⇒ `IllegalStateException`) | `JU:Casos.regrasDeConteudo`; `PY:RegrasDeConteudo.test_extrato_fora_do_periodo_da_conta`; C-10; `casos-por-regra/R-EX-02` | EXEC |
| R-EX-03 | `ValidarExtratoSemOrigemDestinoRule` | `ContentRules.extrato` | `PY:RegrasDeConteudo.test_extrato_sem_origem_destino`; `casos-por-regra/R-EX-03` | EXEC |
| R-EX-04 | `ValidarExtratoContaNaoInvestigadaRule` | `ContentRules.extrato` | sem teste unitário dedicado (observada no original: mapa de cobertura); `casos-por-regra/R-EX-04` | EXEC |
| R-EX-05 | `ValidarValorExtratoOrigemDestinoRule` | `ContentRules.extrato` | `PY:RegrasDeConteudo.test_valor_extrato_diferente_da_soma_od`; C-11; `casos-por-regra/R-EX-05` | EXEC |
| R-OD-01 | `ValidarIdentificacaoTedDocRule` | `ContentRules.origemDestino` (`passe`, 1ª consulta) | `JU:Casos.tedAcimaDoLimiteSemIdentificacao`; `JU:Lim.limiarCustomizadoMudaOResultado`; `PY:RegrasDeConteudo.test_ted_doc_*`; C-14; FZD; `casos-por-regra/R-OD-01` | EXEC |
| R-OD-02 | `ValidarIdentificacaoTransferenciaMesmoBancoRule` | `ContentRules.origemDestino` | FZD / 800 dirigidos (N2); `casos-por-regra/R-OD-02` | EXEC |
| R-OD-03 | `ValidarIdentificacaoTransacaoEmEspecieRule` | `ContentRules.origemDestino` | FZD / 800 dirigidos (N2); `casos-por-regra/R-OD-03` | EXEC |
| R-OD-04 | `ValidarCpfCnpjTransferenciaTedDocOrigemDestinoRule` | `ContentRules.origemDestino` (laço final) | `PY:RegrasDeConteudo.test_cpf_od_invalido_em_transferencia`; FZD; `casos-por-regra/R-OD-04` | EXEC (ordem ND) |
| R-OD-E1 | `ValidarIdentificacaoTedDocTransfEleitoralRule` | `ContentRules.origemDestino` (ramo eleitoral) | FZD (30% eleitoral); `casos-por-regra/R-OD-E1` | EXEC |
| R-OD-E2 | `ValidarDepositoDinheiroChequeEleitoralRule` | `ContentRules.origemDestino` (ramo eleitoral) | `JU:Casos.eleitoralTrocaRegrasDeOd`; `PY:RegrasDeConteudo.test_eleitoral_usa_outras_regras_de_od`; C-13; `casos-por-regra/R-OD-E2` | EXEC |

### 14.5 Corretoras (13)
| ID | Original | Reimplementação | Teste/evidência | Conf. |
|---|---|---|---|---|
| G-01 | `ControladorValidador.verificarArquivosGabVazios` | `GabPipeline.run` (ids internos `E-02`; rótulo físico, como E-02) | `JU:Gab.arquivosVazios`; GABD | EXEC |
| G-02 | `AbstractCarregarArquivoCmd.carregar` (ramo `isCorretora`); `NomeArquivoGab.getTamanhoLinha`; `GetCamposGabColunaFixaCmd` | `GabLoader.carregar` (id interno `G-LARGURA`); `GabLayouts.tamanhoLinha` | `JU:Gab.tamanhosDeLinha`, `compatReproduzOdefeitoDoOriginal`; `T:diferencial/gab_layouts.json`; GABD | EXEC |
| G-DEF | `CarregarArquivoGab{109,112,800}Cmd[Ver2].getCamposArquivo` (= `null`); `AbstractCarregarArquivoCmd.validarCampos` | `GabLoader.carregar` (`cfg.defeito()`, id interno `G-UNK`); `GabConfig.defeito` | `JU:Gab.compatReproduzOdefeitoDoOriginal`; GABD (3.000 idênticos) | EXEC |
| G-03 | `CarregarArquivoGab{109,112,800}Cmd.validarObrigatoriedade`/`erroObrigatoriedade` | `GabLoader.obrig` (`v109`/`v112`/`v800`) | `JU:Gab.validacoesDeCampo`; GABD (remendo em memória) | EXEC (sob remendo) |
| G-04 | `CarregarArquivoGab*Cmd.validarData`/`erroData`; `util/Util.getDateGab` | `GabLoader.dataOk`, `v109`/`v112`/`v800` | `JU:Gab.validacoesDeCampo`; GABD | EXEC (sob remendo) |
| G-05 | `CarregarArquivoGab*Cmd.validarNumero` | `GabLoader.numero`, `isNumber` | `JU:Gab.validacoesDeCampo`; GABD | EXEC (sob remendo) |
| G-06 | `CarregarArquivoGab*Cmd.validarDadoEsperado`; `CarregarArquivoGab112Cmd.validarCpfCnpj`; `enums/MercadoGab`, `UF` | `GabLoader.cd`, `v112`, `UFS` | `JU:Gab.validacoesDeCampo` (CPF inválido, sigla de estado); GABD | EXEC (sob remendo) |
| G-07 | `CarregarArquivoGab*Ver2Cmd.setParametros`; `util/Util.getDateGabSql` | `GabLoader.gravar` | `JU:Gab.validacoesDeCampo` (v2, `DATA_SITUACAO` vazia ⇒ E-13); GABD | EXEC (sob remendo) |
| R-G109 | `ValidarGab112NaoGab109SimRule` (via `ValidarAtendimentoGab.validarArquivoGab109`) | `GabRules.clientesAusentesNoGab112("GAB109", …)` (id interno `R-GAB109`) | `JU:Gab.regrasDeConteudo`; GABD | EXEC (sob remendo) |
| R-G800 | `ValidarGab112NaoGab800SimRule` (via `validarArquivoGab800`) | `GabRules.clientesAusentesNoGab112("GAB800", …)` (id interno `R-GAB800`) | GABD | EXEC (sob remendo) |
| R-G112-1 | `InvestigadoGabNenhumRelacionamentoRule` | `GabRules.gab112` | GABD (INVESTIGADO vazio ⇒ `IllegalStateException`) | EXEC (sob remendo) |
| R-G112-2 | `ValidarGab112InvestigadoSemRelacionamentoRule` | `GabRules.gab112` | GABD | EXEC (sob remendo) |
| R-G112-3 | `ValidarGab112InvestigadoNaoInformadoRule` | `GabRules.gab112` | `JU:Gab.regrasDeConteudo`; GABD | EXEC (sob remendo) |

**Totais conferidos:** E 16 + A 7 + L 38 + R 29 + G/R-G 13 = **103 IDs** (todos presentes nas tabelas acima, sem renumeração nem IDs novos).

---

## 15. Riscos, limitações e lacunas

### 15.1 Riscos técnicos
1. **Equivalência de banco:** a reimplementação substitui HSQLDB por índices em memória. Semântica de `NULL`, `NOT IN`, `UCASE`, `CONCAT/RIGHT` e **ordem de linhas sem `ORDER BY`** (D13) é modelada; verificada por execução diferencial, mas qualquer regra nova que dependa dessas nuances precisa de novo cenário dirigido.
2. **Dependência de locale (A-05/L-EX-00/L-OD-00):** `NumberFormat.getInstance()` varia por máquina; `pt_BR` e `en_US` testados sem diferença (LAC-02 fechada), mas A-05 permanece PROV. Também `SimpleDateFormat` leniente e `String.toUpperCase` dependem de locale/JDK **[HIP]**.
3. **Dependência de servidor:** o app real precisa da lista de órgãos (E-15); a versão do receptor afeta hash (A-07) e L-OD-04. A reimplementação aceita `--receptor` só para L-OD-04.
4. **Deriva de versão:** limiares e datas ficam em SQL no original; qualquer release novo exige nova engenharia reversa (cadeia reutilizável em `A:ferramentas/` e `T:diferencial/`).
5. **Dados pessoais em logs** do original (`ErrorReportCmd.report` → `log.info`) e processamento de dados sigilosos pela reimplementação: usar apenas dados sintéticos; não há telemetria na reimplementação.
6. **Isolamento da execução do original:** todas as execuções foram em `bwrap --unshare-net`; qualquer repetição deve manter esse isolamento (módulo de rede nunca deve ser exercitado).

### 15.2 Limitações da reimplementação
Sem UI, cadastro de atendimento, hash/cópia, relatório HTML/PDF, transmissão, verificação de versão do banco; investigados entram por `investigados.tsv`. Modo ESTRITO **pode divergir** do aceite oficial (por desenho). Ids internos de regra não catalogados (§16 RO-05).

### 15.3 Lacunas (referenciam o catálogo §8.4)
LAC-01 unidade de VALOR\_\*; LAC-02 `en_US` (**fechada** na v1.2: 2.140 cenários, 0 divergências; A-05 segue PROV por depender do locale da máquina); LAC-03/D11 termos de uso: verificação estática não achou EULA nem termos no instalador (`LicenseText` vazio) nem nos JARs do MPF, mas a **questão jurídica continua aberta**; LAC-04 aceite do servidor com erros; LAC-05 ordem das colunas e enums (esta documentação resolve parte: `Mapa.camposTabelasA` + `getCamposArquivo()`; ver §6.3); LAC-06 telas de atendimento/investigados, PDF, empacotamento e transmissão só por amostragem; LAC-07 `simba-validador.exe` e 2 file entries anômalas; LAC-08 acentos/BOM e volumes reais; LAC-09 **fechada** na v1.2 (71 regras observadas; 82,9% de linhas no conjunto `rules+carga+validacao`); LAC-10 `Abstract*Rule` GAB lidas parcialmente. Adicionalmente **[HIP]**: o comportamento do original com arquivos de encoding diferente de ISO-8859-1 (UTF-8) não foi exercitado — a leitura sempre decodifica como ISO-8859-1 e nunca falha por byte inválido, mas o efeito sobre acentos em campos e sobre o `trim`/tamanhos (bytes × caracteres) não foi medido.

---

## 16. Registro para o orquestrador

Divergências entre o catálogo e o código verificado, propostas e achados (prefixo `RO-` = *registro ao orquestrador*, para não confundir com os componentes `R-01…` do catálogo §2.1). **Nenhuma regra foi criada ou renumerada.**

| # | Tipo | Descrição | Evidência | Sugestão | Situação |
|---|---|---|---|---|---|
| RO-01 | Divergência (E-07) | O catálogo lista como *Resultado* de E-07 a mensagem "A carga foi interrompida devido a erros anteriores." Em `D:carga/Carregar.temErroNaCarga` ela é lançada como `NegocioException`, mas `Carregar.carregar` a captura (`catch (NegocioException e)`) e apenas faz `log.error("Erro de negócio na carga.")`; nenhum `ErrorReportCmd.report` é chamado com esse texto. Ela **não aparece no relatório/tela**; o efeito de E-07 é só interromper a carga (arquivos seguintes não carregados). A reimplementação (`Pipeline`) também não emite a mensagem, coerente com o original, e `JU`/diferencial não a esperam. O mesmo vale para `CarregarGab`/`CarregarGabVer2` | `grep "interrompida"` (3 ocorrências, todas em `throw new NegocioException`); leitura de `Carregar.carregar` | Ajustar a coluna "Resultado" de E-07 para "interrompe a carga; mensagem apenas em log" | Tratado: resolvido no catálogo v1.1 (E-07) |
| RO-02 | Imprecisão de caminho | Prefixos `D:../DAO/…` e `D:../telas/…` (K-02, K-08, E-01, E-08, E-11, E-14) resolvem a `…/simba/DAO` e `…/simba/telas`, que não existem. Caminhos reais: `analise/decompilado/DAO/` e `analise/decompilado/telas/` (irmãos de `br/`, `META-INF/`) | `find analise/decompilado -name Mapa.java -o -name frmSelecionaDiretorio.java` | Corrigir a convenção do prefixo `D:` (acrescentar `DAO:`/`TELAS:` ou caminho absoluto) | Tratado: resolvido no catálogo v1.1 (prefixo `DX:`) |
| RO-03 | Precisão (E-04 / §5.0) | (a) O rótulo de arquivo de **E-04** é o nome **lógico** (`getNomeArquivo()` → `NomeArquivo.X.name()`), não o físico como sugere o §1.3 ("erros estruturais … nome físico"); ~~só E-03, G-02, E-13 e `getInt/getLong` usam o físico~~ (refutado: usam o físico também E-02, G-01 e toda `NegocioException`; ver §9 e catálogo §1.3). (b) Os nomes de campo na mensagem E-04 de TITULARES e ORIGEM_DESTINO diferem dos abreviados do §5.0: `TIPO_PESSOA_TITULAR`, `CPF_CNPJ_TITULAR`, `NOME_TITULAR`, `TELEFONE_PESSOA`…; OD: só `NUMERO_DOCUMENTO_TRANSACAO` e `OBSERVACÃO` (com til) divergem (`VALOR_TRANSACAO` e `CPF_CNPJ_OD` já constam no catálogo §5.0). A reimplementação (`N:Schemas`) usa os nomes exatos | `CarregarTabelaPessoaCmd.getCamposArquivo`, `CarregarArquivoOrigemDestinoCmd.getCamposArquivo`, `AbstractCarregarArquivoCmd.validarCampos` | Ajustar §1.3 e §5.0; LAC-05 pode ser reduzida (ordem em `Mapa.camposTabelasA`) | Tratado: tratado: (a) catálogo v1.1 §1.3 traz a regra de rótulo por mecanismo; (b) corrigido por P-27 (só `NUMERO_DOCUMENTO_TRANSACAO` e `OBSERVACÃO` divergem) |
| RO-04 | Divergência numérica (§8.2 / `A:03` §8) | O código morto "sobrecargas `validar(ExtratoOrigemDestino)` em 7 regras de OD" é verificável em **6** regras: `ValidarCpfCnpjTransferenciaTedDocOrigemDestinoRule`, `ValidarIdentificacaoTedDocTransfEleitoralRule`, `ValidarDepositoDinheiroChequeEleitoralRule`, `ValidarIdentificacaoTransferenciaMesmoBancoRule`, `ValidarIdentificacaoTedDocRule`, `ValidarIdentificacaoTransacaoEmEspecieRule` | `grep -ln "validar(ExtratoOrigemDestino" rules/*.java` | Corrigir para 6 (a 7ª, `ValidarValorExtratoOrigemDestinoRule`, só contém o nome na classe, sem a sobrecarga) | Tratado: resolvido no catálogo v1.1 (6 sobrecargas) |
| RO-05 | Proposta de rastreabilidade (não é regra nova) | A reimplementação emite `ruleId` internos que não são IDs do catálogo, o que afeta as saídas JSON/CSV: `L-NEG` (NegocioException genérica), `L-NUM` (L-EX-00/L-OD-00), `L-UNK` (E-13), `G-LARGURA` (G-02), `G-UNK` (G-DEF/E-13 GAB), `R-INFRA` (falha de infraestrutura), `L-OD-05` (campo numérico inválido, só ESTRITO) e reutiliza `E-01`/`E-02` para G-01; `R-GAB109`/`R-GAB800` em vez de `R-G109`/`R-G800` | `N:Loader`, `N:GabLoader`, `N:GabRules`, `N:GabPipeline`, `N:Pipeline` | Alinhar os `ruleId` aos IDs do catálogo (E-13, L-EX-00, L-OD-00, G-01, G-02, G-DEF, R-G109, R-G800); `L-OD-05` e `R-INFRA` seriam extensões do ESTRITO/infra, sem ID de regra — decisão do orquestrador | Aberto/informativo |
| RO-06 | Achado de dead code (informativo) | Código morto adicional não listado no §8.2: `DAO.Mapa.getCamposNaoNulos`, `getCamposNaoZeros`, `colAparaColB`, `getNomesOficios`, `getCodigoArquivoA`, `getCamposTabelasA`; e `CampoArquivo.tipo`/`TipoCampoArquivo` (nunca lidos) | `grep` fora de `Mapa.java` sem usos | Incluir em §8.2 (sem efeito em regras) | Aberto/informativo |
| RO-07 | Lacuna de teste | `JU:Casos.arquivoAusenteEVazio` só afirma E-01; **E-02 (bancário)** não tem teste unitário dedicado. Também sem teste dedicado: E-07, L-AG-02/03/07, L-CT-01/02/03, L-PE-00/01, L-TI-01/02/03, L-EX-02/03/05, R-CT-03/04/06/08, R-TI-02/03/05/06/07, R-EX-01/04 (cobertura apenas agregada/aleatória, LAC-09) | matriz §14 | Priorizar testes unitários/dirigidos para esses IDs antes de promover confiança | Tratado (RO-07 parcialmente: E-02 e outras agora observadas no original, mas sem teste unitário) |
| RO-08 | Esclarecimento de evidência | Os "golden" de `T:casos` e o `GoldenCasosTest` comparam com `esperado.txt` gerado pelo **modelo Python**; a evidência EXEC do catálogo vem de o mesmo cenário ter dado resultado idêntico no original (nível 2/3). O teste JUnit sozinho prova consistência com o modelo, não com o original | `T:gerar_casos.py` (docstring), `NT:GoldenCasosTest` | Nota explicativa no §1.2 ou §9 do catálogo | Aberto/informativo |
| RO-09 | Observação (E-08) | `ValidarAtendimento.validarArquivoOrigemDestino`/`validarArquivoExtrato` calculam "total de validações" para a barra de progresso (`view.total`) de forma que não corresponde exatamente ao nº de regras (p.ex. `isEleitoral()?3:4` em OD; EXTRATO usa `isEleitoral()?6:5` embora execute sempre 5). É apenas progresso visual, sem efeito em regras | `D:validacao/ValidarAtendimento` | Nenhuma ação; registrar como peculiaridade cosmética | Aberto/informativo |
| RO-10 | Confirmação | Ordens de execução do catálogo (CONTAS 10, TITULARES 7, EXTRATO 5, OD 3/2+1) e a lista de loaders/ordem de `validar` foram conferidas contra `ValidarAtendimento` e os `Carregar*Cmd`; **sem divergência** além dos itens acima. G-DEF confirmado (`getCamposArquivo()` retorna `null` em `CarregarArquivoGab109Cmd`) | leitura direta | — | Aberto/informativo |

**Propostas de novas regras:** nenhuma. **Divergências registradas:** RO-01 a RO-05, RO-08 (substantivas), RO-06, RO-07, RO-09 (informativas), RO-10 (confirmação).


**Revisão independente (`docs/REVISAO_DOCUMENTACAO.md`) — itens técnicos tratados nesta versão:** P-05 (finais vazios preservados no split), P-17 (34 JARs), P-18 (rótulo físico para `NegocioException`), P-19 (fluxo GAB completo), P-26 (carga 30 arquivos; certificados 9), P-27 (nomes E-04), P-28 (exemplo leniente × estrita), P-29 (contagem GABD). Matriz §14 alinhada à redação v1.1 de E-05b, E-07 e E-14.

**Rodada 3 (`docs/REVISAO_DOCUMENTACAO_2.md`, itens técnicos):** Q-03, Q-04, Q-08 (rótulo físico de G-01 na matriz), Q-09, Q-10, Q-11, Q-12, Q-13 e Q-14 aplicados; confianças e totais sincronizados com o catálogo v1.2; §13/§14 com a infraestrutura `casos-por-regra`. Observação: a lista de promoções recebida tem 48 IDs (não 50) — foram promovidas exatamente as regras observadas que estavam EST; o catálogo v1.2 cita 49 promovidas.
