# 04 — Catálogo de regras de validação (Fase 4)

**Escopo:** fluxo **bancário** (arquivos AGENCIAS, CONTAS, TITULARES, EXTRATO, ORIGEM_DESTINO). O fluxo de corretoras (GAB109/112/800) está em §7 (resumo, não catalogado campo a campo).
**Fonte:** `analise/decompilado/br/mp/mpf/spea/simba/validador/**` e `analise/decompilado-libs/spea-client/**` (Validador SIMBA 6.2.6).
**Modelo executável das regras:** `analise/testes/validador_ref.py` (48 testes passando; ver 05-testes.md).

## Legenda de confiança
- **CONFIRMADO (estático)**: comportamento totalmente determinado pelo código decompilado, sem depender de estado externo. *Nenhuma regra está confirmada por execução* (o aplicativo nunca foi executado).
- **PROVÁVEL**: depende de semântica de runtime/banco/locale ou de dado externo.
- **NÃO DETERMINADO**: não investigado ou inconclusivo.

Formato da mensagem exibida (`ErroValidacao.getPrettyMessage`): `Erro no arquivo: <nome>, linha: <n>, erro: <mensagem>`.
`nome` = nome físico do arquivo (erros estruturais, de conversão e "Erro desconhecido") ou `AGENCIAS|CONTAS|TITULARES|EXTRATO|ORIGEM_DESTINO` (erros de regra de campo).

---
## A. Regras estruturais e de fluxo

| ID | Regra | Comportamento observado | Evidência | Conf. |
|---|---|---|---|---|
| E-01 | Arquivos esperados | Em `<pasta>` (não recursiva) devem existir, comparação case-insensitive e só `*.txt`: `<NUMERO_CASO>_AGENCIAS.txt`, `_CONTAS`, `_TITULARES`, `_EXTRATO`, `_ORIGEM_DESTINO`. Reporta só o primeiro ausente: `Arquivo esperado (<nome>) não encontrado.` | `ControladorValidador.verificarExistenciaArquivosBancario`, `Mapa.nomesTabelasA` | CONFIRMADO |
| E-02 | Arquivos não vazios | Tamanho 0 bytes é erro (linha 0) para AGENCIAS, CONTAS, TITULARES: `O arquivo X não pode ser vazio.` EXTRATO e ORIGEM_DESTINO podem ser vazios. | `ControladorValidador.verificarArquivosVazios` | CONFIRMADO |
| E-03 | Quantidade de colunas | A linha, separada por TAB, deve ter exatamente N colunas (AGENCIAS 11, CONTAS 7, TITULARES 21, EXTRATO 14, ORIGEM_DESTINO 18). `Quantidade de colunas inválida. Esperado: N Atual: M` | `AbstractCarregarArquivoCmd.carregar` | CONFIRMADO |
| E-04 | Tamanho máximo do campo | Comprimento (após `trim`) > máximo ⇒ `Tamanho do campo <NOME> excedido. Tamanho máximo permitido: <n>`. Máximos em §C. Não há mínimo/obrigatoriedade de comprimento aqui. | `AbstractCarregarArquivoCmd.validarCampos`, `getCamposArquivo()` | CONFIRMADO |
| E-05 | Codificação e quebra de linha | ISO-8859-1; `BufferedReader.readLine` (CR, LF ou CRLF); linha em branco ⇒ 1 coluna ⇒ E-03. | `AbstractCarregarArquivoCmd` | CONFIRMADO |
| E-05b | Separador | `split("(?=\t)")` + `trim()`: **campo vazio na 1ª coluna é descartado** (linha `\tb\tc` ⇒ 2 colunas). `trim` remove todo caractere ≤ U+0020. | `util/Util.tabSeparatedStringToArray`; sonda Java `SplitProbe` | CONFIRMADO (estático + sonda) |
| E-06 | Erro global bloqueia validação semântica das linhas seguintes | `ErrorReportCmd.hasErros()` é global. Após o **primeiro** erro (qualquer arquivo), nas linhas seguintes só rodam E-03/E-04; `convert()`/`validar()` **não** rodam e a linha não é inserida. Resultado: o relatório contém o 1º erro semântico (todas as mensagens daquela linha) + erros estruturais das demais linhas. | `AbstractCarregarArquivoCmd.carregar` (`if (this.errorReportCmd.hasErros()) { …continue; }` após `validarCampos` e após `validar`) | CONFIRMADO (estático) |
| E-07 | Fail-fast entre arquivos (carga) | Ordem AGENCIAS→CONTAS→TITULARES→EXTRATO→ORIGEM_DESTINO; se um arquivo tiver erro, os seguintes não são carregados. TITULARES: se a passada PESSOA tiver erro, TITULARIDADE não é carregada. | `Carregar.temErroNaCarga`, `CarregarArquivoTitularesCmd.carregar` | CONFIRMADO |
| E-08 | Fail-fast entre arquivos (conteúdo) | As regras SQL só rodam se a carga de **todos** os arquivos terminou sem erro; rodam por arquivo na ordem acima e o laço para no 1º arquivo reprovado. Dentro do arquivo, **todas** as regras rodam. | `frmSelecionaDiretorio.doInBackground`, `ValidarAtendimento` | CONFIRMADO |
| E-09 | Versão | Versão do validador deve igualar a gravada no atendimento; senão aviso e interrompe. | `ControladorValidador.checkVersaoSistemaBanco` | CONFIRMADO |
| E-11 | Validação **não** bloqueia o prosseguimento | O botão "Continuar" fica habilitado após a validação mesmo com erros (exceto no caso `999-OUTROS-000001-88`). O relatório de inconsistências é gerado, mas a interface não impede o avanço. | `frmSelecionaDiretorio.TarefaBackground` (finally); execução da tela real | CONFIRMADO (execução) |
| E-10 | Perfil "eleitoral" | `Context.isEleitoral()` = `NUMERO_CASO` começa com `001-MPE` ou `038-TSE`. Altera regras L-AG-01, L-AG-05, L-AG-06 e o conjunto de regras de ORIGEM_DESTINO. | `spea-client/pgr/client/Context.isEleitoral` | CONFIRMADO |

---
## B. Algoritmos de apoio

| ID | Algoritmo | Comportamento | Evidência | Conf. |
|---|---|---|---|---|
| A-01 | `verificaData(ddMMyyyy)` | Exatamente 8 caracteres; `Integer.valueOf(ano) ≥ 1900`; parse **não-leniente** `ddMMyyyy` (29/02 só em bissexto). Mensagem padrão: `A <CAMPO> (<valor>) é inválida ou o ano é anterior a 1900.` Campo vazio não é validado aqui. | `asspa/client/Util.verificaData` | CONFIRMADO |
| A-02 | CPF | Completa com zeros à esquerda até 11. Remove os 2 últimos como DV; rejeita se a **base** (sem DV) tiver todos os dígitos iguais; base `[0-9]{6,}`, DV `[0-9]{2}`; pesos `len+1-i` (10…2, depois 11…2); resto<2⇒0 senão 11−resto. | `pgr/util/DigitoVerificador`, `CpfCnpjUtil` | CONFIRMADO; reproduzido e comparado a algoritmo oficial independente em 3.000 CPFs aleatórios |
| A-03 | CNPJ (**alfanumérico**) | Completa com zeros até 14. Base `[0-9A-Z]{7,}`, DV `[0-9]{2}`, base não pode ter todos os caracteres iguais; valor do caractere = `ord(c) − 48` (A=17…Z=42); pesos 5,4,3,2,9…2 e 6,5,4,3,2,9…2. | idem | CONFIRMADO; reproduzido e comparado ao algoritmo oficial em 3.000 CNPJs alfanuméricos aleatórios |
| A-04 | `CnabRule` (retorna **true = inválido**) | Crédito `C`: válido 201–223 ou 999. Débito `D`: válido 101–127 exceto 116, ou 999. `*`: válido 101–127 (exceto 116), 201–223 ou 999. | `rules/CnabRule` | CONFIRMADO |
| A-05 | `isNumber` (usado por `getInt/getLong`) | `NumberFormat.getInstance().parse(texto)`: **leniente e dependente do locale** (`"12abc"`→número; `"1.000"`→1000 em pt_BR). Em seguida `Long.valueOf(texto)` (estrito) ⇒ `NumberFormatException` ⇒ "Erro desconhecido". Sonda Java em locale pt_BR confirmou o `parse`. | `pgr/util/StringUtil.isNumber`; `AbstractCarregarArquivoCmd.getLong/getInt` | PROVÁVEL (depende do locale da máquina) |
| A-07 | Hash MD5 de arquivo (`Arquivo.obterHashArquivo`) | MD5 em hexadecimal; com zeros à esquerda (`%032x`) somente se o receptor for versão ≥ 4 (`possuiCorrecaoHash`), senão `BigInteger.toString(16)` sem padding. Depende de dado do servidor. | `asspa/client/Arquivo` | PROVÁVEL |
| A-06 | `removerZerosEsquerda` | Lança `StringIndexOutOfBoundsException` se o texto for vazio ou só zeros. | `pgr/util/StringUtil` | CONFIRMADO |

Mensagem genérica (qualquer exceção não tratada na linha): `Erro desconhecido. Consulte o log do sistema.` (arquivo = nome físico).

---
## C. Regras de carga por linha (`L-*`)
Máximos de campo (E-04):
- **AGENCIAS**: NUMERO_BANCO 3, NUMERO_AGENCIA 4, NOME_AGENCIA 50, ENDERECO_LOGRADOURO 80, CIDADE 40, UF 2, PAIS 40, CEP 8, TELEFONE 20, DATA_ABERTURA 8, DATA_FECHAMENTO 8.
- **CONTAS**: BANCO 3, AGENCIA 4, NUMERO_CONTA 20, TIPO_CONTA 1, DATA_ABERTURA 8, DATA_ENCERRAMENTO 8, MOVIMENTACAO 1.
- **TITULARES**: BANCO 3, AGENCIA 4, CONTA 20, TIPO_CONTA 1, TIPO_TITULAR 1, PESSOA_INVESTIGADA 1, TIPO_PESSOA 1, CPF_CNPJ 14, NOME 80, NOME_DOC 50, NUMERO_DOC 20, LOGRADOURO 80, CIDADE 40, UF 2, PAIS 40, CEP 8, TELEFONE 20, VALOR_RENDA 14, DATA_ATUALIZACAO_RENDA 8, DATA_INICIO 8, DATA_FIM 8.
- **EXTRATO**: CODIGO_CHAVE 18, BANCO 3, AGENCIA 4, CONTA 20, TIPO_CONTA 1, DATA_LANCAMENTO 8, NUMERO_DOCUMENTO 20, DESCRICAO 50, TIPO_LANCAMENTO 3, VALOR_LANCAMENTO 14, NATUREZA_LANCAMENTO 1, VALOR_SALDO 14, NATUREZA_SALDO 1, LOCAL_TRANSACAO 80.
- **ORIGEM_DESTINO**: CODIGO_CHAVE_OD 18, CODIGO_CHAVE_EXTRATO 18, VALOR_TRANSACAO 14, NUMERO_DOCUMENTO 20, BANCO_OD 3, AGENCIA_OD 4, CONTA_OD 20, TIPO_CONTA_OD 1, TIPO_PESSOA_OD 1, CPF_CNPJ_OD 14, NOME_PESSOA_OD 80, NOME_DOC_IDENTIFICACAO_OD 50, NUMERO_DOC_IDENTIFICACAO_OD 20, CODIGO_DE_BARRAS 100, NOME_ENDOSSANTE_CHEQUE 80, DOC_ENDOSSANTE_CHEQUE 50, SITUACAO_IDENTIFICACAO 1, OBSERVACAO 250.

### AGENCIAS — `CarregarArquivoAgenciasCmd.validar` (ordem de execução)
| ID | Regra | Mensagem | Conf. |
|---|---|---|---|
| L-AG-01 | Obrigatórios. Não eleitoral: BANCO, AGENCIA, NOME, LOGRADOURO, CIDADE, UF, PAIS, CEP, TELEFONE. Eleitoral: só BANCO, AGENCIA, NOME. ("vazio" = null ou `trim` vazio.) | `Um dos campos obrigatórios (<lista>) não está preenchido.` | CONFIRMADO |
| L-AG-02/03 | DATA_ABERTURA / DATA_FECHAMENTO, se preenchidas, passam A-01. | A-01 | CONFIRMADO |
| L-AG-04 | `Integer.valueOf(NUMERO_BANCO)` deve ser igual ao NUMERO_BANCO do atendimento. Se NUMERO_BANCO for vazio/não numérico ⇒ exceção ⇒ **segunda** mensagem "Erro desconhecido". | `NUMERO_BANCO diferente do informado em atendimento.` | CONFIRMADO |
| L-AG-05 | (não eleitoral) FECHAMENTO sem ABERTURA ⇒ erro; FECHAMENTO < ABERTURA ⇒ erro (parse **leniente** `SimpleDateFormat`). | `DATA_FECHAMENTO_AGENCIA informada, mas não há DATA_ABERTURA_AGENCIA.` / `DATA_ABERTURA_AGENCIA posterior à DATA_FECHAMENTO_AGENCIA.` / `Data inválida nos campos …` | CONFIRMADO |
| L-AG-06 | (não eleitoral) UF deve ser sigla de `UF` (27 UFs, **case-sensitive**) ou `--`. | `ENDERECO_UF:<v> não é uma sigla válida.` | CONFIRMADO |
| L-AG-07 | Inserção: NUMERO_AGENCIA vira `Integer.valueOf` (não numérico ⇒ "Erro desconhecido"). | — | CONFIRMADO |

### CONTAS — `CarregarArquivoContasCmd`
| ID | Regra | Mensagem | Conf. |
|---|---|---|---|
| L-CT-00 | (convert) agência (banco+agência numéricos, zeros à esquerda irrelevantes) deve existir em AGENCIAS. Vazio/não numérico ⇒ "Erro desconhecido". | `Agencia não localizada. Banco: <b> Agencia: <a>` | CONFIRMADO |
| L-CT-01 | Obrigatórios: NUMERO_BANCO, NUMERO_CONTA, TIPO_CONTA, MOVIMENTACAO_CONTA (mensagem lista só os 3 últimos). | `Um dos campos obrigatórios (NUMERO_CONTA, TIPO_CONTA, MOVIMENTACAO_CONTA) não está preenchido.` | CONFIRMADO |
| L-CT-02/03 | DATA_ABERTURA_CONTA / DATA_ENCERRAMENTO_CONTA passam A-01. **Não há** validação ABERTURA ≤ ENCERRAMENTO nesta carga. | A-01 | CONFIRMADO |
| L-CT-04 | TIPO_CONTA ∈ {1,2,3,4}. | `Tipo de conta inválido. Valores possíveis: 1, 2, 3 e 4.` | CONFIRMADO |
| L-CT-05 | MOVIMENTACAO_CONTA ∈ {1,2,3}. | `Movimentação de Conta inválido. Valores possíveis: 1, 2 e 3` | CONFIRMADO |

### TITULARES (duas passadas sobre o mesmo arquivo)
**Passada PESSOA** (`CarregarTabelaPessoaCmd`)
| ID | Regra | Mensagem | Conf. |
|---|---|---|---|
| L-PE-00 | VALOR_RENDA, se preenchido, deve ser `Double.valueOf` válido (convert). | `Valor numérico inválido no campo Valor Renda: <v>` | CONFIRMADO |
| L-PE-01 | Obrigatórios: TIPO_PESSOA, CPF_CNPJ, NOME. | `Um dos campos obrigatórios (TIPO_PESSOA, CPF_CNPJ_PESSOA, NOME_PESSOA, PESSOA_INVESTIGADA, DATA_INICIO_RELACIONAMENTO) não está preenchido.` (lista fixa, mesmo quando só 3 são testados) | CONFIRMADO |
| L-PE-02 | DATA_ATUALIZACAO_RENDA, se preenchida, passa A-01. **O erro é atribuído ao arquivo `EXTRATO`** (bug de rótulo no código). | A-01 | CONFIRMADO |
| L-PE-03 | Tipo `1`: `validarCpf(removerZerosEsquerda(cpf))` — CPF só de zeros ⇒ A-06 ⇒ "Erro desconhecido". Tipo `2`: `validarCnpj(cpf)`. CPF_CNPJ é convertido para maiúsculas. | `O CPF_CNPJ_PESSOA: <v> está inválido.` | CONFIRMADO |
| L-PE-04 | TIPO_PESSOA ∈ {1,2}. | `Tipo de pessoa inválido. Valores possíveis: 1 e 2.` | CONFIRMADO |

**Passada TITULARIDADE** (`CarregarTabelaTitularidadeCmd`) — só roda se PESSOA não teve erro
| ID | Regra | Mensagem | Conf. |
|---|---|---|---|
| L-TI-00 | (convert) BANCO/AGENCIA/CONTA/TIPO_CONTA não vazios; a conta (banco+agência+conta+tipo) deve existir em CONTAS; a pessoa da linha deve existir. | `O código COMPE da instituição financeira não foi informado` / `O número da agência não foi informado` / `O número da conta não foi informado` / `O tipo de conta não foi informado` / `Conta não localizada. Banco: … Agencia: … Conta: … TipoConta: …` | CONFIRMADO |
| L-TI-01 | Obrigatórios: TIPO_TITULAR, DATA_INICIO_RELACIONAMENTO, PESSOA_INVESTIGADA (ramos eleitoral e não eleitoral têm a **mesma** condição e mensagem). | lista fixa de L-PE-01 | CONFIRMADO |
| L-TI-02/03 | DATA_INICIO / DATA_FIM passam A-01, mas aqui o erro **lança `NegocioException`** (interrompe a linha). | `A DATA_INICIO_RELACIONAMENTO (<v>) é inválida ou o ano é anterior a 1900.` (idem FIM) | CONFIRMADO |
| L-TI-04 | TIPO_TITULAR casa `[T1-9RLPO]` (1 char). | `Tipo de titular inválido. Valores possíveis: T, 1 até 9, R, L, P e O.` | CONFIRMADO |
| L-TI-05 | PESSOA_INVESTIGADA casa `[01]`. | `Tipo de conta investigada inválido. Valores possíveis: 0 e 1.` | CONFIRMADO |
| L-TI-06 | DATA_INICIO parseável (vazio ⇒ erro); DATA_FIM, se houver, parseável e ≥ início. | `A DATA_INICIO_RELACIONAMENTO é inválida: <v>` / `A DATA_FIM_RELACIONAMENTO é inválida: <v>` / `A DATA_INICIO_RELACIONAMENTO (<i>) é posterior a DATA_FIM_RELACIONAMENTO (<f>).` | CONFIRMADO |

### EXTRATO — `CarregarArquivoExtratoCmd`
| ID | Regra | Mensagem | Conf. |
|---|---|---|---|
| L-EX-00 | (convert) CODIGO_CHAVE, TIPO_LANCAMENTO, VALOR_LANCAMENTO, VALOR_SALDO via `getInt/getLong` (A-05): não numérico ⇒ mensagem e valor 0. | `Campo <NOME> não é um número.` | PROVÁVEL (A-05) |
| L-EX-01 | A conta (banco+agência+conta+tipo) deve existir em CONTAS. | `Conta não localizada no arquivo de CONTAS.` | CONFIRMADO |
| L-EX-02 | Obrigatórios: DATA_LANCAMENTO, NUMERO_DOCUMENTO, DESCRICAO_LANCAMENTO. (A checagem de NATUREZA nunca falha por ser `null`: strings vazias passam por aqui e caem em L-EX-04/05.) | lista fixa de 8 campos | CONFIRMADO |
| L-EX-03 | DATA_LANCAMENTO passa A-01. | A-01 | CONFIRMADO |
| L-EX-04 | NATUREZA_LANCAMENTO casa `[CD*]`. | `Natureza de Lancamento inválida. Valores possíveis: C, D e *(asterisco).` | CONFIRMADO |
| L-EX-05 | NATUREZA_SALDO casa `[CD]`. | `Natureza de saldo inválida. Valores possíveis: C e D.` | CONFIRMADO |
| L-EX-06 | TIPO_LANCAMENTO coerente com a natureza (A-04). | `TIPO_LANCAMENTO inválido para NATUREZA_LANCAMENTO='C'|'D'|'*'. Veja tabela CNAB-240.` | CONFIRMADO |
| L-EX-07 | VALOR_LANCAMENTO e VALOR_SALDO ≥ 0. | `VALOR_LANCAMENTO e VALOR_SALDO não podem ser negativos.` | CONFIRMADO |

### ORIGEM_DESTINO — `CarregarArquivoOrigemDestinoCmd`
| ID | Regra | Mensagem | Conf. |
|---|---|---|---|
| L-OD-00 | (convert) `Long.valueOf(CODIGO_CHAVE_OD)` direto: não numérico ⇒ "Erro desconhecido". Demais numéricos via `getInt/getLong`. | — | CONFIRMADO / A-05 |
| L-OD-01 | **Bug**: "validarTipoConta" testa TIPO_**PESSOA** (`[12]`) com a mensagem de TIPO_CONTA. Assim TIPO_CONTA_OD nunca é validado como intervalo; valor não numérico falha só na inserção (`Integer.valueOf` ⇒ "Erro desconhecido"). | `TIPO_CONTA_OD inválido. Valores possíveis: 1, 2, 3 ou 4.` | CONFIRMADO |
| L-OD-02 | TIPO_PESSOA_OD, se preenchido, ∈ {1,2}. (Valor inválido gera L-OD-01 **e** L-OD-02.) | `TIPO_PESSOA_OD inválido. Valores possíveis: 1 ou 2.` | CONFIRMADO |
| L-OD-03 | SITUACAO_IDENTIFICACAO deve ser 0. | `SITUACAO_IDENTIFICACAO inválido. Valor possível: 0.` | CONFIRMADO |
| L-OD-04 | NUMERO_BANCO_OD com > 3 caracteres: sem suporte do receptor ⇒ erro; com suporte (`Versao.suportaCNPJComoNumeroBanco`: major ≥ 4 ou major ≥ 3 e minor ≥ 3) exige 14 dígitos. **Inalcançável na prática**: o layout limita NUMERO_BANCO_OD a 3 caracteres (E-04) e a linha com erro estrutural não chega a `validar()`. A versão do receptor vem do **servidor** (`Controlador.getComputadores` → HTTP GET `orgaos?codprog=…`). | `Número Banco inválido. O mesmo deve ser composto de apenas 3 dígitos (COMPE).` / `… (COMPE) ou 14 dígitos(CNPJ).` | CONFIRMADO (estático) como inalcançável; confirmado por teste na implementação independente |

---
## D. Regras de conteúdo — consultas SQL (`R-*`)
Todas em `rules/*.java`, executadas após a carga completa. Ordem = `ValidarAtendimento`. Cada linha retornada pela consulta vira **um** erro (JOINs podem duplicar). Mensagens completas no código; abaixo, o prefixo.

### AGENCIAS (1)
| ID | Condição | Mensagem (prefixo) | Conf. |
|---|---|---|---|
| R-AG-01 | `GROUP BY NUMERO_BANCO, NUMERO_AGENCIA HAVING COUNT(*)>1`; linha = MAX(LINHA) | `Agência duplicada: NUMERO_BANCO: bbb, NUMERO_AGENCIA: aaaa` | CONFIRMADO |

### CONTAS (10, nesta ordem)
| ID | Condição | Mensagem (prefixo) |
|---|---|---|
| R-CT-01 | NUMERO_CONTA contém `.` `/` `\` ou `-` | `Conta com separador: <conta>` |
| R-CT-02 | (agência, NUMERO_CONTA, TIPO_CONTA) repetido. **Peculiaridade:** a regra herda de `AbstractRule` e não de `AbstractContaRule`, então o relatório sai como `Erro no arquivo: null` | `Conta duplicada - Linha: …` |
| R-CT-03 | MOVIMENTACAO ∈ {1,2,3} e a conta não está em TITULARIDADE | `Conta sem registro correspondente em Titulares. NUMERO_BANCO: …` |
| R-CT-04 | MOVIMENTACAO ∈ {1,2} e nenhuma titularidade com PESSOA_INVESTIGADA=1 | `Conta sem pessoa investigada. …` |
| R-CT-05 | nenhuma conta com MOVIMENTACAO ∈ {1,2} (erro na linha 0) | `Atendimento sem nenhuma movimentação de conta.` |
| R-CT-06 | MOVIMENTACAO ≠ 3 e a conta não tem investigado (um erro por titularidade, JOIN) | `Conta com atributo MOVIMENTACAO_CONTA incorreto. Deve ser 3, pois não é conta de investigado. …` |
| R-CT-07 | mais de um titular `T` na conta | `Conta com mais de um Titular tipo T. …` |
| R-CT-08 | titular investigado (=1) em conta com MOVIMENTACAO ∉ {1,2} | `… Deve ser 1 ou 2, pois é um investigado. …` |
| R-CT-09 | MOVIMENTACAO=1 e conta sem lançamentos em EXTRATO | `Conta definida com movimentação (MOVIMENTACAO_CONTA = 1), porém não possui lançamentos em EXTRATO. …` |
| R-CT-10 | MOVIMENTACAO=2 e conta com lançamentos em EXTRATO | `Conta definida sem movimentação (MOVIMENTACAO_CONTA = 2), porém possui lançamentos em EXTRATO. …` |
Conf.: CONFIRMADO (estático) quanto à condição SQL; PROVÁVEL quanto a detalhes de NULL/ordem do HSQLDB.

### TITULARES (7)
| ID | Condição | Mensagem (prefixo) |
|---|---|---|
| R-TI-01 | titular `T` investigado com qualquer de NOME_DOC, NUMERO_DOC, LOGRADOURO, CIDADE, UF, PAIS, CEP, TELEFONE nulo | `O titular de conta investigada deve ter os seguintes dados preenchidos: …` |
| R-TI-02 | `INICIO < ABERTURA_CONTA` ou `FIM < ABERTURA_CONTA` | `A DATA_INICIO_RELACIONAMENTO ou DATA_FIM_RELACIONAMENTO está menor que a DATA_ABERTURA_CONTA (…` |
| R-TI-03 | `INICIO > ENCERRAMENTO_CONTA` ou `FIM > ENCERRAMENTO_CONTA` | `… está maior que a DATA_ENCERRAMENTO_CONTA (…` |
| R-TI-04 | investigado (TEVE_RELACIONAMENTO=1 e TEVE_CONTA=1) cujo CPF/CNPJ (14 dígitos com zeros) não aparece em TITULARES como investigado; **reportado com linha −1** | `O CPF_CNPJ … cadastrado no atendimento como investigado, não foi encontrado no arquivo TITULARES como PESSOA_INVESTIGADA = 1` |
| R-TI-05 | pessoa ligada a investigado (rel=1, conta=1) com PESSOA_INVESTIGADA=0 | `… deve possuir o atributo PESSOA_INVESTIGADA = 1` |
| R-TI-06 | PESSOA_INVESTIGADA=1 cujo CPF/CNPJ não está entre os investigados (rel=1, conta=1) | `… informado como PESSOA_INVESTIGADA = 1 não foi cadastrado no atendimento.` |
| R-TI-07 | conta com investigado sem titular tipo `T` | `Conta investigada sem Titular do tipo T. …` |

### EXTRATO (5)
| ID | Condição | Mensagem (prefixo) |
|---|---|---|
| R-EX-01 | lançamento em conta de investigado com `DATA_LANCAMENTO < INICIO_AFASTAMENTO` ou `> FIM_AFASTAMENTO` (datas do INVESTIGADO) | `Lançamento em dd/MM/yyyy fora do período de afastamento (…) do investigado: CPF_CNPJ (…)` |
| R-EX-02 | `DATA_LANCAMENTO < ABERTURA_CONTA` ou `> ENCERRAMENTO_CONTA` (NULL não dispara) | `A DATA_LANCAMENTO (dd/MM/yyyy) está fora do período de DATA_ABERTURA_CONTA (…` |
| R-EX-03 | lançamento sem registro em ORIGEM_DESTINO | `Lançamento em EXTRATO sem correspondente em ORIGEM_DESTINO. CODIGO_CHAVE_EXTRATO: n` |
| R-EX-04 | lançamento em conta com MOVIMENTACAO=3 | `No arquivo CONTAS, se o campo MOVIMENTAÇÃO DA CONTA estiver com o parâmetro "3"(conta não investigada) não pode haver nenhum registro para essa conta no arquivo EXTRATO` |
| R-EX-05 | `VALOR_LANCAMENTO ≠ SUM(VALOR_OPERACAO)` dos OD do lançamento | `O Valor do lancamento em EXTRATO (CODIGO_CHAVE: n) não é igual ao somatório dos registros correspondentes em ORIGEM_DESTINO.` |
(R-EX-01 modelada em `validador_ref.py` e implementada em `ContentRules`; a `NullPointerException` do original quando `DATA_ABERTURA_CONTA` é nula e há lançamento após o encerramento está reproduzida como falha de infraestrutura no modo COMPAT.) Unidade de valor (centavos?) **NÃO DETERMINADO**: o DDL é `numeric(14)` sem escala.

### ORIGEM_DESTINO
**Não eleitoral** (executa nesta ordem; depois R-OD-04):
| ID | Condição (em SQL) | Mensagem (prefixo) |
|---|---|---|
| R-OD-01 | `TIPO_LANCAMENTO IN (120,209)` e `DATA ≥ 2006-01-01` e `VALOR ≥ 1000000` e identificação ausente (CPF_CNPJ_OD, NOME, BANCO, AGENCIA ou CONTA nulos; BANCO=0; AGENCIA=0; CONTA só de zeros) **e** NÃO (AGENCIA=9999 **e** CONTA='99999999999999999999' **e** `UCASE(OBS)='NAO-CORRENTISTA'`) | `É OBRIGATÓRIA a identicação do depositante/favorecido de Transferências Interbancária (TED, DOC) …` |
| R-OD-02 | `TIPO IN (117,213)`, mesma data/valor, identificação ausente | `… de Transferências entre Contas …` |
| R-OD-03 | `TIPO IN (123,220)` e [`DATA≥2013-07-01 ∧ VALOR≥1.000.000` ∨ `DATA≥2013-01-02 ∧ VALOR≥5.000.000` ∨ `DATA≥2012-07-01 ∧ VALOR≥10.000.000`] e `NOME_PESSOA_OD` nulo e `UCASE(OBS)≠'099 - NEGATIVA DE IDENTIFICACAO DO PORTADOR'` | `É obrigatória a identificação do depositante/favorecido de transações em espécie …` |
**Eleitoral** (substitui R-OD-01…03):
| R-OD-E1 | `TIPO IN (117,120,209,213)` e (CPF_CNPJ_OD, BANCO, AGENCIA ou CONTA nulos; BANCO=0; AGENCIA=0; CONTA só zeros) — sem limite de data/valor | `É OBRIGATÓRIA a identicação … de transferências no mesmo banco ou transferências interbancárias (TED/DOC) …` |
| R-OD-E2 | `TIPO=201` e `VALOR ≥ 10000` e CPF_CNPJ_OD nulo | `É OBRIGATÓRIA a identicação … de depósito em cheque e/ou dinheiro no campo CPF_CNPJ_OD.` |
**Ambos:**
| R-OD-04 | `TIPO IN (117,120,209,213)`, `DATA ≥ 2006-01-01`, CPF_CNPJ_OD preenchido: tipo `1` ⇒ CPF; `2` ⇒ CNPJ (14); outro/vazio ⇒ CNPJ **ou** CPF(últimos 11 de 14). | `O campo CPF_CNPJ_PESSOA_OD com o valor <v> é inválido [para TIPO_PESSOA_OD = …]` |
Conf.: CONFIRMADO (estático) para as condições SQL; PROVÁVEL para semântica de NULL em `NOT IN`/comparações e `REPEAT/LENGTH/UCASE` do HSQLDB. Os limiares de data/valor estão **hard-coded**.

---
## E. Peculiaridades que um reimplementador precisa saber (todas CONFIRMADO estático, salvo indicação)
1. E-06 — só o 1º erro semântico do arquivo é reportado.
2. E-05b — primeiro campo vazio some (coluna a menos).
3. L-AG-04 — NUMERO_BANCO vazio gera dois erros.
4. L-PE-02 — rótulo `EXTRATO` num erro de TITULARES.
5. L-OD-01 — validação de TIPO_CONTA_OD checa a coluna errada.
6. L-PE-03 — CPF só de zeros ⇒ "Erro desconhecido" em vez de "CPF inválido".
7. L-EX-02 — `NATUREZA_* == null` nunca é verdadeiro.
8. A-05 — `isNumber` leniente + locale (PROVÁVEL).
9. Regras SQL duplicam erros por JOIN (R-CT-06, R-CT-08).
10. Código morto em 7 regras de OD (§8 de 03-arquitetura.md): divergências entre sobrecarga e SQL não afetam o resultado.
11. A barra de progresso declara 6 etapas para EXTRATO em caso eleitoral, mas só 5 regras existem (`ValidarAtendimento.validarArquivoExtrato`) — irrelevante para o resultado.

## F. Cobertura e limitações
- Cobertas: `rules/` tem 46 arquivos. Lidos integralmente: `CnabRule`, 29 regras bancárias de conteúdo, `ValidarCasoPossuiInvestigadoComRelacionamentoRule` (só usada por tela) e 6 regras GAB (inclui `ValidarGab112SimGab109NaoRule`, não utilizada). As 8 `Abstract*Rule` têm 10 linhas cada (apenas fixam `nomeArquivo`; 2 lidas, 6 inferidas pelo tamanho idêntico). `ValidarGab112SimGab800NaoRule` não foi lida (análoga à de GAB109; nunca injetada).
- **Não catalogado:** loaders GAB (`CarregarArquivoGab*Cmd`, ~1.450 linhas) e suas validações de campo; telas; relatórios.
- Nada foi executado: nenhuma regra está confirmada por comportamento real.

## G. Fluxo GAB (corretoras) — CONFIRMADO por execução do aplicativo original
Arquivos `<caso>_GAB109.txt`, `_GAB112.txt`, `_GAB800.txt` (ISO-8859-1, **largura fixa**; layout v1 ou v2 escolhido na tela). Larguras de linha: GAB109 127/333, GAB112 307/726, GAB800 298/320 (v1/v2). Campos extraídos por `substring` + `trim` (`GetCamposGabColunaFixaCmd`). Ordem de carga: GAB109 → GAB112 → GAB800 (fail-fast); conteúdo na mesma ordem.

| ID | Regra | Mensagem | Conf. |
|---|---|---|---|
| G-01 | `verificarArquivosGabVazios`: GAB112 não pode ser vazio; GAB800 vazio com GAB109 com dados; GAB109 vazio com GAB800 com dados (as 3 checagens são independentes) | `O arquivo GAB-112 não pode ser vazio.` / `O arquivo GAB-800 não pode ser vazio quando o arquivo GAB-109 possui dados.` / `O arquivo GAB-109 não pode ser vazio quando o arquivo GAB-800 possui dados.` | CONFIRMADO (execução) |
| G-02 | Largura da linha deve ser exatamente a soma dos campos | `Certifique-se de completar com espaços os campos que não atingirem o tamanho predeterminado e Não use tab para separar os campos.` (arquivo = nome físico) | CONFIRMADO (execução) |
| **G-DEF** | **DEFEITO do 6.2.6:** `getCamposArquivo()` devolve `null` nos 6 carregadores GAB e `AbstractCarregarArquivoCmd.validarCampos` faz `camposArquivo[i]` ⇒ `NullPointerException` ⇒ **toda linha com a largura certa gera `Erro desconhecido. Consulte o log do sistema.`**, uma por linha (sem o filtro de erro global). Logo **nenhum arquivo GAB não vazio é aprovado** e as validações abaixo (G-03…G-06, R-G*) nunca rodam no aplicativo real. Pilha observada: `Cannot load from object array because "this.camposArquivo" is null at …validarCampos(AbstractCarregarArquivoCmd.java:299)`. | `Erro desconhecido. Consulte o log do sistema.` | **CONFIRMADO (execução)**: 500+1.200+1.200 cenários idênticos ao original |
| G-03 | Obrigatórios (vazio = null/trim vazio). GAB109: CODIGO_DO_CLIENTE, DESCRICAO_DO_LANCAMENTO, VALOR_DO_LANCAMENTO, NATUREZA_LANCAMENTO, DATA_DE_LIQUIDACAO, DATA_DO_MOVIMENTO, DATA_DE_REFERENCIA. GAB800: CODIGO_DO_CLIENTE, DESCRICAO_DO_LANCAMENTO, VALOR_DO_LANCAMENTO, NOME_DO_CLIENTE, DATA_DE_LIQUIDACAO, ENDERECO, DATA_DE_REFERENCIA, SALDO_INICIAL, NATUREZA_DO_SALDO_INICIAL, NATUREZA_DO_LANCAMENTO. GAB112: 20 verificações (DOCUMENTO_DO_CLIENTE e COMPLEMENTO_DO_CEP são verificados **duas vezes**, gerando mensagem duplicada). | `O campo <CAMPO> é obrigatório.` | CONFIRMADO (execução, lógica remendada em memória) |
| G-04 | Datas `yyyyMMdd` (parse leniente + conferência de ida e volta). GAB109: só o 1º erro (cadeia else-if: movimento, referência, liquidação). GAB112/GAB800: um erro por campo. Texto não parseável ⇒ exceção ⇒ "Erro desconhecido". | GAB109: `O campo X está incorreto. A data deve estar no formato AAAAMMDD e válida.` ; GAB112/800: `A data deve estar no formato AAAAMMDD e válida.  Campo: X` (dois espaços) | idem |
| G-05 | Numéricos (`Long.parseLong` e `BigInteger`): GAB109 CODIGO_DO_CLIENTE, VALOR_DO_LANCAMENTO; GAB112 CODIGO_DO_CLIENTE, DOCUMENTO_DO_CLIENTE, CODIGO_DO_CLIENTE_MERCADO, CODIGO_DA_ATIVIDADE; GAB800 CODIGO_DO_CLIENTE, VALOR_DO_LANCAMENTO, SALDO_INICIAL | `O dado informado não é um número. Campo: CODIGO_DO_CLIENTE` (1º campo) / `… não é um número.  Campo X` (demais) | idem |
| G-06 | Domínios: naturezas ∈ {C,D}; GAB112: CONSTANTE_0000A = `0000A`; TIPO_DE_PESSOA ∈ {F,J}; MERCADO ∈ {BOVESPA,BMF,SWAP}; ESTADO sigla de UF (27, maiúsculas); DOCUMENTO_DO_CLIENTE com 15 caracteres; CPF (tipo F) ou CNPJ (tipo J) válido após remover zeros à esquerda (documento só de zeros ⇒ exceção ⇒ "Erro desconhecido") | `Informe C para crédito ou D para débito. Campo: X`; `Valor esperado para o campo CONSTANTE_0000A: 0000A.`; `Informe F para pessoa física ou J para pessoa jurídica. Campo: TIPO_DE_PESSOA`; `Informe BOVESPA para mercado à vista, BMF para derivativos ou SWAP para balcão. Campo: MERCADO`; `A sigla do campo estado está incorreta`; `O campo DOCUMENTO_DO_CLIENTE é obrigatório conter 15posições. Devendo ser completementado com zeros à esquerda.`; `CPF inválido para o tipo de pessoa: F. Campo: DOCUMENTO_DO_CLIENTE` / `CNPJ inválido …` | idem |
| G-07 | Gravação (layout v2): colunas NUMERIC (CHAVE_OD, BANCO/AGENCIA/CONTA de origem e destino em GAB109; MEMBRO_COMPENSACAO, AGENTE_CUSTODIA, CODIGO_CORRETORA em GAB112) rejeitam texto não numérico e **DATA_SITUACAO (GAB112 v2) usa `getDateGabSql` sem validação**: vazia/inválida ⇒ NPE ⇒ "Erro desconhecido" (portanto GAB112 v2 só passa com DATA_SITUACAO válida) | `Erro desconhecido…` | idem |
| R-G109 / R-G800 | CODIGO_DO_CLIENTE (comparação **numérica**) do GAB109/GAB800 deve existir no GAB112 | `O cliente com o código N não foi informado no arquivo gab112.` | idem |
| R-G112-1 | todos os investigados com TEVE_RELACIONAMENTO≠1 (linha 0, arquivo `Investigado`); com INVESTIGADO vazio a consulta não devolve linha e o original lança exceção de infraestrutura | `Não foi encontrado relacionamento no arquivo investigado` | idem |
| R-G112-2 | DOCUMENTO_DO_CLIENTE (sem zeros à esquerda) não consta dos investigados | `O CPF/CNPJ <doc> não teve relacionamento com esta instituição no período do afastamento de sigilo.` | idem |
| R-G112-3 | investigado com relacionamento ausente do GAB112 (linha 0) | `O investigado com CPF/CNPJ <cpf> não foi encontrado no arquivo cadastral(gab112).` | idem |
Rótulo de arquivo das mensagens de campo: `Gab109`/`Gab112`/`Gab800` (+`Ver2`); das regras de conteúdo: `GAB109`/`GAB112`/`GAB800`. Código morto: `ValidarGab112SimGab109NaoRule`, `ValidarGab112SimGab800NaoRule`.
