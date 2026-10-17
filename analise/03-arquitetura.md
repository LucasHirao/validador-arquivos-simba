# 03 — Arquitetura (Fase 3)

Baseada exclusivamente no que foi observado. Fonte: `analise/decompilado/` (simba-validador.jar, 159 classes `.java` recuperadas com Vineflower 1.12.0, sem falhas de método) e `analise/decompilado-libs/spea-client/` (biblioteca MPF `spea-client-2.4.4.jar`, 83 classes).
Legenda de confiança: **CONFIRMADO (estático)** = determinado pelo código decompilado; **PROVÁVEL** = depende de estado externo/locale/runtime; **NÃO DETERMINADO** = não investigado.
**Nenhum item foi confirmado por execução.** O aplicativo nunca foi executado.

## 1. Visão geral
Aplicação desktop **Swing** (Java 25, Spring 6.2.18 por injeção `@Inject`/`@Component`, Spring Boot parent 3.5.14 no POM) que:
1. cadastra o "atendimento" (caso) e os investigados em um banco **HSQLDB 2.7.3** local;
2. lê 5 arquivos `.txt` de largura variável (separados por TAB, ISO-8859-1), carrega-os em tabelas HSQLDB validando linha a linha;
3. executa regras de conteúdo como consultas SQL sobre as tabelas;
4. gera hash MD5, empacota (zip) e **transmite** o resultado ao servidor SIMBA/MPF (módulo à parte).

Evidência: `Main.java` (`extends BaseMain`, `nextForm = frmIdentificaAtendimento`, `getArquivoSpring() = "validador-spring.xml"`); manifesto `Main-Class: br.mp.mpf.spea.simba.validador.Main`; `validador-spring.xml` (component-scan `br.mp.mpf, DAO, negocio, telas`; beans `connectionProvider`, `bancoHSQLDB`, `controlador`, `configurador`, `frm*`).

## 2. Mapa de pacotes (`br/mp/mpf/spea/simba/validador/`)
| Pacote | Classes | Papel | Evidência |
|---|---|---|---|
| (raiz) | Main, ControladorValidador, ConfiguradorValidador, CriarTabelasValidador | Entrada, façade de negócio, DDL | `ControladorValidador.java` |
| `carga` | 44 | Leitura/parse/validação por linha + inserção no HSQLDB; localizadores (cache Guava) | `AbstractCarregarArquivoCmd.java`, `Carregar.java` |
| `validacao` | 2 | Orquestra a ordem das regras de conteúdo | `ValidarAtendimento.java`, `ValidarAtendimentoGab.java` |
| `rules` | 46 | Regras de conteúdo (SQL) + `CnabRule` | `rules/*.java` |
| `domain`, `to`, `enums` | 20+4+22 | Modelos e enumerações (layouts, domínios) | `enums/Campos*.java` |
| `cmd` | 18 | Hash MD5, cópia de arquivos, geração/empacotamento/**transmissão** | `cmd/*.java` |
| `ui`, `telas`, `DAO` | 22+59+3 | Interface Swing, acesso a dados (`BancoHSQLDB`, `Mapa`, `MapaGab`) | `telas/frmSelecionaDiretorio.java` |
| `desempenho`, `util` | 1+1 | Progresso/medição; utilitários (`tabSeparatedStringToArray`, datas) | `util/Util.java` |

Biblioteca `spea-client` (MPF): `Context`, `Controlador`, `ErrorReportCmd`/`ErroValidacao` (lista de erros em arquivo), `CpfCnpjUtil`/`DigitoVerificador`, `StringUtil`, `asspa.client.Util` (`verificaData`), `Arquivo` (hash), `PostToUrl`/`Conexao` (HTTP), `Certificado`, `Computador`.

## 3. Fluxo de validação (ponta a ponta) — CONFIRMADO (estático)
Fonte: `telas/frmSelecionaDiretorio.java` (`doInBackground`, linhas ~819-925), `carga/Carregar.java`, `validacao/ValidarAtendimento.java`.
```
selecionar pasta ──► E-01 existem <caso>_{AGENCIAS,CONTAS,TITULARES,EXTRATO,ORIGEM_DESTINO}.txt?   (senão: para)
   │ E-09 versão do validador == versão gravada no atendimento?                                   (senão: aviso e para)
   │ MD5 dos 5 arquivos → gravado em ATENDIMENTO.HASH_ARQUIVO_n ; cópia p/ pasta do caso
   │ E-02 AGENCIAS, CONTAS, TITULARES não podem ter 0 bytes                                         (erro → fim)
   ▼
CARGA (Carregar.carregar): AGENCIAS → CONTAS → TITULARES(PESSOA, depois TITULARIDADE) → EXTRATO → ORIGEM_DESTINO
   por linha: split TAB → E-03 colunas → E-04 tamanho → [convert + validar linha] → INSERT (batch 500, commit)
   após cada arquivo: se houve erro → "A carga foi interrompida devido a erros anteriores." (fail-fast)
   ▼
CONTEÚDO (laço i=0..5 em NomeArquivo): AGENCIAS(1 regra) → CONTAS(10) → TITULARES(7) → EXTRATO(5) → ORIGEM_DESTINO(3 ou 4 + 1)
   todas as regras do arquivo rodam; ao fim, !hasErros() decide; o laço PARA no 1º arquivo reprovado
   ▼
tabelas temporárias apagadas (ApagarTabelasCmd) ; relatório de erros ; botão "Continuar" habilitado se sem erros
```
Observação (CONFIRMADO estático): `ErrorReportCmd.hasErros()` é **global** (lista única); isso governa o comportamento descrito em 04-regras (E-06).
**Botão "Continuar" (CONFIRMADO por execução da tela real; corrige leitura anterior errada):** no bloco `finally` de `TarefaBackground.doInBackground`, `continuarButton` é habilitado para **todo caso diferente de `999-OUTROS-000001-88`, inclusive quando a validação encontrou erros** (captura de tela: erro de agência e "Continuar >>" ativo). Só para `999-OUTROS-000001-88` ele exige ausência de erros. A tela seguinte (`frmTrataEnvio`) não referencia o resultado da validação (sem `hasErros`/relatório de erros no código). Se o servidor rejeita o envio após erros não foi analisado (PROVÁVEL que sim, NÃO DETERMINADO).

## 4. Modelo de dados (HSQLDB `CACHED TABLE`) — `DAO/Mapa.java`
`ATENDIMENTO` (dados do caso, 5 hashes, 5 contagens de linha, versão), `INVESTIGADO`, `AGENCIA`, `CONTA` (FK→AGENCIA), `PESSOA` (FK→INVESTIGADO), `TITULARIDADE` (PK pessoa+conta), `EXTRATO` (FK→CONTA), `ORIGEM_DESTINO` (FK→EXTRATO), `SISTEMA`. Todas guardam `LINHA_ARQUIVO` (usada nas mensagens de erro). `MapaGab` define tabelas `GAB109/112/800` (+`VER2`) para o fluxo de corretoras.

## 5. Leitura e parsing
- Leitura: `FileInputStream` → `Channels.newReader(..., ISO_8859_1)` → `BufferedReader(10 MiB)`; `readLine()` (aceita CR, LF, CRLF).
- Colunas (bancário): `Util.tabSeparatedStringToArray` = `split("(?=\t)")` + `trim()`; **campo vazio na 1ª posição é descartado** (verificado com sonda Java independente).
- GAB (corretoras): largura fixa; comprimento da linha deve ser exatamente a soma dos campos (GAB109 127/333, GAB112 307/726, GAB800 298/320 para layout v1/v2 — soma calculada a partir de `enums/CamposGab*.java`); campos por `substring` + `trim` (`GetCamposGabColunaFixaCmd`).
- Datas: `ddMMyyyy` (bancário) e `yyyyMMdd` (GAB, `Util.getDateGab`).

## 6. Dependências externas relevantes
Spring (context/beans/aop/core/expression 6.2.18), HSQLDB 2.7.3, Guava 33.4.8, commons-io/codec/lang3, gson, **httpclient5/httpcore5**, logback/slf4j, zip4j 2.6.1, **OpenPDF 3.0.0 + openpdf-html + neko-htmlunit + Velocity 2.4.1** (geração de relatório/comprovante em PDF a partir de templates), micrometer. JRE 25 próprio (módulos: java.base, java.desktop, java.sql, java.xml, java.naming, jdk.jfr…).

## 7. Módulo de comunicação com servidor (fora do escopo de validação) — **não executar**
`cmd/TransmitirDocumentosCmd` (etapas: autenticação → empacotamento → transmissão → aguardando resposta), `PostToUrl`, `Conexao`, `TestaConexao`, `Certificado`. Strings de URL observadas em `Configurador`/`Conexao`: `https://simba.mpf.mp.br/vamp/`, `https://bender.pgr.mpf.mp.br/vamp/` (aparente ambiente de desenvolvimento), `https://localhost:8443/vamp/`. Usa certificados MPF (`certificados/*.crt|.cer|.pem` no JAR) e código de autenticação. Também `Arquivo.obterHashArquivo`: MD5 em hexadecimal (com zeros à esquerda somente se `Computador.getVersaoReceptor().possuiCorrecaoHash(...)`) — depende de dado do servidor/receptor (PROVÁVEL).
**Rede durante a validação (CONFIRMADO estático):** `frmSelecionaDiretorio` chama `controlador.getComputador(...)` antes do hash; `Controlador.getComputadores()` faz HTTP GET `<urlVamp>orgaos?codprog=<codigo>[&T=1]` e guarda o resultado em memória (versão do receptor de cada órgão). Ou seja, o aplicativo real **precisa de rede mesmo só para validar** (ou de um servidor simulado). Implicação: executar o aplicativo real pode abrir conexões de rede. Qualquer execução futura deve ser em ambiente **sem rede** (ver 06).

## 8. Código morto / não utilizado (CONFIRMADO estático — nenhuma referência encontrada por `grep`)
- Sobrecargas `validar(ExtratoOrigemDestino)` em 7 regras de OD (condições diferentes das consultas SQL; ex.: `ValidarCpfCnpjTransferenciaTedDocOrigemDestinoRule` testa `getCpfCnpjPessoaOd() == null` antes de validar, `ValidarIdentificacaoTransacaoEmEspecieRule` tem `dataLimite20120701 = "01-07-2013"`). A SQL é o que executa.
- `ValidarGab112SimGab109NaoRule`, `ValidarGab112SimGab800NaoRule` (nunca injetadas).
- `ValidarCasoPossuiInvestigadoComRelacionamentoRule` é usada só por `frmIdentificaAtendimento`.

## 9. Limitações
- Fluxo das **telas** (cadastro de atendimento/investigados, relatório PDF, empacotamento) lido apenas por amostragem.
- Loaders GAB (corretoras) e suas regras: mapeados (layouts/ordem) mas **não** catalogados campo a campo.
- `Computador.getVersaoReceptor()` vem do servidor (ver §7); formato exato da resposta XML (`<computador>`) não analisado.
