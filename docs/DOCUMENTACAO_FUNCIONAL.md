# Documentação Funcional — Validador SIMBA 6.2.6

**Público:** Produto e QA. **Escopo:** comportamento observável do Validador Bancário SIMBA 6.2.6 (MPF) e do fluxo de corretoras (GAB), conforme o catálogo vinculante `docs/CATALOGO_REGRAS.md` v1.2 (103 regras, IDs estáveis). Esta versão incorpora a 1ª revisão independente (`docs/REVISAO_DOCUMENTACAO.md`, P-nn) e a 2ª (`docs/REVISAO_DOCUMENTACAO_2.md`, Q-nn funcionais), além do mapa de cobertura `docs/MAPA_COBERTURA_REGRAS.md` (confiança por regra).
**Aviso:** documento descritivo de uma engenharia reversa de estudo. Não é especificação oficial do MPF. Intenção de negócio só é afirmada quando o código a prova; do contrário aparece "intenção não documentada". Todos os dados de exemplo são sintéticos.

## 0. Como ler

### 0.1 Escala de confiança (única, do catálogo §1.2)
| Código | Significa |
|---|---|
| **EXEC** | Confirmado por execução do aplicativo original em sandbox sem rede (caso golden, hipótese H* ou harness citado) |
| **EST** | Determinado pelo código decompilado; não rastreado por regra individual na campanha diferencial (não promovido a EXEC) |
| **PROV** | Provável: depende de locale, banco (NULL/ordem do HSQLDB), servidor ou dado externo |
| **INALC** | Inalcançável: o código existe, mas nenhuma entrada o aciona |
| **ND** | Não determinado |

"EXEC sob remendo" (regras G-03…G-07 e R-G*): verificado no original com um remendo apenas em memória; não ocorre no aplicativo real por causa do defeito G-DEF.

### 0.2 Formato da mensagem (E-12) e rótulo (catálogo §1.3)
`Erro no arquivo: <rótulo>, linha: <n>, erro: <mensagem>`

O rótulo depende do **mecanismo** que emite o erro, não da natureza da regra:
| Mecanismo | Rótulo |
|---|---|
| Regras de carga L-* que reportam diretamente (sem lançar exceção) e todas as regras de conteúdo R-* | **Lógico**: `AGENCIAS`, `CONTAS`, `TITULARES`, `EXTRATO` ou `ORIGEM_DESTINO` |
| E-04 (tamanho de campo) | **Lógico** |
| E-03, E-13, G-02, E-02, G-01 | **Físico** (nome do arquivo `.txt`, ex.: `001-TST-000001-00_AGENCIAS.txt`) |
| Erros de negócio que interrompem a linha: L-CT-00, L-PE-00, L-TI-00, L-TI-02, L-TI-03 e as mensagens de parse de L-TI-06 | **Físico** |
| Conversões numéricas L-EX-00 e numéricos de L-OD-00 (`Campo <NOME> não é um número.`) | **Físico** |
| Exceções declaradas | L-PE-02 usa `EXTRATO`; R-CT-02 usa `null`; R-G112-1 usa `Investigado`; mensagens GAB de campo usam `Gab109/112/800` (+`Ver2`) e as de conteúdo `GAB109/112/800` |

### 0.3 Linha-base dos exemplos
Todos os cenários da §7 partem do caso golden `analise/testes/casos/00-valido/` (caso `001-TST-000001-00`, banco do atendimento = 1, aprovado em todas as etapas). Linhas-base (campos separados por TAB; `∅` = campo vazio):

| Arquivo | Linha-base |
|---|---|
| AGENCIAS | `001 · 0001 · AGENCIA TESTE · RUA TESTE 100 · CIDADE TESTE · DF · BRASIL · 70000000 · 6133334444 · 01012000 · ∅` |
| CONTAS | `001 · 0001 · 12345 · 1 · 01012010 · ∅ · 1` |
| TITULARES | `001 · 0001 · 12345 · 1 · T · 1 · 1 · 52998224725 · FULANO DE TESTE · RG · 1234567 · RUA X · CIDADE · DF · BRASIL · 70000000 · 6133334444 · ∅ · ∅ · 01012010 · ∅` |
| EXTRATO | `1 · 001 · 0001 · 12345 · 1 · 15032020 · DOC1 · DEPOSITO · 201 · 50000 · C · 50000 · C · AG` |
| ORIGEM_DESTINO | `1 · 1 · 50000 · DOC1 · ∅ ×13 · 0 · ∅` (campos 5–16 vazios, SITUACAO_IDENTIFICACAO=0, OBSERVACÃO vazia) |
| investigados.tsv | `1 · 52998224725 · FULANO DE TESTE · 1 · 1 · ∅ · ∅` (tipo pessoa, CPF/CNPJ, nome, TEVE_RELACIONAMENTO, TEVE_CONTA, INICIO_AFASTAMENTO, FIM_AFASTAMENTO) |

Os arquivos reais dos golden usam ISO-8859-1 e terminação CRLF. O CPF `52998224725` é vetor público de teste.

---

## 1. Visão geral

O Validador SIMBA é o aplicativo desktop (Swing) com o qual uma instituição financeira confere, **antes do envio ao MPF**, cinco arquivos TXT de um afastamento de sigilo bancário: **AGENCIAS, CONTAS, TITULARES, EXTRATO, ORIGEM_DESTINO**. Um fluxo paralelo atende **corretoras** (arquivos GAB109, GAB112, GAB800).

O que o sistema faz, em ordem (E-01…E-14, `docs/CATALOGO_REGRAS.md` §2.2):
1. Confere que os cinco arquivos existem na pasta (E-01) e que a versão do validador é a do atendimento (E-09).
2. Calcula o hash MD5 dos arquivos e copia os arquivos para a pasta do caso (E-14). A contagem de linhas é gravada depois, durante a carga, ao fim de cada arquivo efetivamente carregado (E-14).
3. Confere que AGENCIAS, CONTAS e TITULARES não estão vazios (E-02).
4. **Carga**: lê cada arquivo linha a linha, confere colunas, tamanhos e regras de campo (E-03…E-07, L-*), gravando em um banco HSQLDB em arquivo do caso (`jdbc:hsqldb:file:`).
5. **Conteúdo**: executa consultas SQL de coerência entre os arquivos (R-*), apenas se toda a carga passou (E-08).
6. Gera o relatório de inconsistências e habilita "Continuar" mesmo com erros (E-11).

A validação é **informativa**: a interface não impede avançar com erros (E-11, EXEC). Se o servidor aceita o envio com erros é ND (LAC-04).

Se o fluxo termina antecipadamente por E-01 ou E-09, o botão "Continuar" é habilitado mesmo assim, sem nenhuma validação (E-11).

O aplicativo real depende de rede para obter a versão do receptor (E-15, EST), que afeta o formato do hash (A-07) e L-OD-04.

---

## 2. Perfis

| Perfil | Como é determinado | Efeito funcional |
|---|---|---|
| **Bancário comum** (padrão) | `NUMERO_CASO` não começa com `001-MPE` nem `038-TSE` | Todas as regras L-* e R-* conforme a §5; ORIGEM_DESTINO usa R-OD-01, R-OD-02, R-OD-03 e R-OD-04 |
| **Eleitoral** (E-10, EXEC) | `NUMERO_CASO` começa com `001-MPE` ou `038-TSE` | L-AG-01 exige só BANCO, AGENCIA e NOME; L-AG-05 e L-AG-06 deixam de rodar; L-TI-01 sem efeito (condição e mensagem iguais nos dois ramos); ORIGEM_DESTINO troca R-OD-01…03 por R-OD-E1 e R-OD-E2 (R-OD-04 continua) |
| **Corretoras (GAB)** | Arquivos `<caso>_GAB109/112/800.txt`, largura fixa, layout v1 ou v2 escolhido na tela | Fluxo próprio (§5.8). No 6.2.6 nenhum GAB não vazio é aprovado (G-DEF, EXEC) |

O número do caso tem forma `AAA-BBB-NNNNNN-DD` (ex.: `001-TST-000001-00`); o nome físico de cada arquivo é `<NUMERO_CASO>_<TIPO>.txt` (E-01).

---

## 3. Entradas e layouts

### 3.1 Regras gerais de formato (E-03, E-04, E-05, E-05b)
- Arquivo texto, codificação **ISO-8859-1**; linha terminada por CR, LF ou CRLF (E-05, EST). Acentos e BOM **não foram testados** (LAC-08).
- Campos separados por **TAB**. Cada campo sofre `trim` (remove todo caractere ≤ U+0020) (E-05b, EXEC).
- **Atenção (E-05b):** se o primeiro campo for um campo de comprimento zero (linha começando por TAB), ele é descartado e a linha fica com uma coluna a menos. Um primeiro campo contendo apenas espaço **não** é descartado (comparar os casos golden `02-primeiro-campo-vazio`, 10 colunas, e `04-banco-vazio`, 11 colunas).
- Linha em branco = 1 coluna, portanto E-03.
- Número de colunas exigido (E-03): AGENCIAS 11, CONTAS 7, TITULARES 21, EXTRATO 14, ORIGEM_DESTINO 18.
- O tamanho máximo (E-04) é medido após `trim`; não existe tamanho mínimo.
- A ordem abaixo é a ordem física das colunas (fonte: `getCamposArquivo()` de cada carregador do original, reproduzido em `Schemas.java` da reimplementação; não existem enums `Campos*` para o layout bancário, só para GAB; ver LAC-05 e §11).

### 3.2 AGENCIAS (11 colunas)
| # | Campo | Máx. | Obrigatório | Domínio / regra |
|---|---|---|---|---|
| 1 | NUMERO_BANCO | 3 | Sim (L-AG-01) | Numérico; igual ao banco do atendimento (L-AG-04) |
| 2 | NUMERO_AGENCIA | 4 | Sim (L-AG-01) | Numérico (L-AG-07); chave única com o banco (R-AG-01) |
| 3 | NOME_AGENCIA | 50 | Sim | Texto |
| 4 | ENDERECO_LOGRADOURO | 80 | Sim (não eleitoral) | Texto |
| 5 | ENDERECO_CIDADE | 40 | Sim (não eleitoral) | Texto |
| 6 | ENDERECO_UF | 2 | Sim (não eleitoral) | 27 siglas maiúsculas ou `--` (L-AG-06) |
| 7 | ENDERECO_PAIS | 40 | Sim (não eleitoral) | Texto |
| 8 | ENDERECO_CEP | 8 | Sim (não eleitoral) | Texto |
| 9 | TELEFONE_AGENCIA | 20 | Sim (não eleitoral) | Texto |
| 10 | DATA_ABERTURA_AGENCIA | 8 | Não | `ddMMyyyy` válida, ano ≥ 1900 (L-AG-02, A-01) |
| 11 | DATA_FECHAMENTO_AGENCIA | 8 | Não | idem (L-AG-03); exige abertura e ser ≥ abertura (L-AG-05) |

### 3.3 CONTAS (7 colunas)
| # | Campo | Máx. | Obrigatório | Domínio / regra |
|---|---|---|---|---|
| 1 | NUMERO_BANCO | 3 | Sim (L-CT-01) | Numérico; agência deve existir (L-CT-00) |
| 2 | NUMERO_AGENCIA | 4 | Numérico (L-CT-00) | Deve existir em AGENCIAS |
| 3 | NUMERO_CONTA | 20 | Sim | Sem `.`, `/`, `\`, `-` (R-CT-01); único com agência+tipo (R-CT-02) |
| 4 | TIPO_CONTA | 1 | Sim | 1, 2, 3 ou 4 (L-CT-04) |
| 5 | DATA_ABERTURA_CONTA | 8 | Não | A-01 (L-CT-02) |
| 6 | DATA_ENCERRAMENTO_CONTA | 8 | Não | A-01 (L-CT-03) |
| 7 | MOVIMENTACAO_CONTA | 1 | Sim | 1, 2 ou 3 (L-CT-05) |

### 3.4 TITULARES (21 colunas)
| # | Campo | Máx. | Obrigatório | Domínio / regra |
|---|---|---|---|---|
| 1 | NUMERO_BANCO | 3 | Sim (L-TI-00) | Conta deve existir em CONTAS |
| 2 | NUMERO_AGENCIA | 4 | Sim (L-TI-00) | idem |
| 3 | NUMERO_CONTA | 20 | Sim (L-TI-00) | idem |
| 4 | TIPO_CONTA | 1 | Sim (L-TI-00) | idem |
| 5 | TIPO_TITULAR | 1 | Sim (L-TI-01) | `T`, `1`–`9`, `R`, `L`, `P`, `O` (L-TI-04); `T` = titular principal (R-CT-07, R-TI-07) |
| 6 | PESSOA_INVESTIGADA | 1 | Sim (L-TI-01) | `0` ou `1` (L-TI-05) |
| 7 | TIPO_PESSOA_TITULAR | 1 | Sim (L-PE-01) | 1 (CPF) ou 2 (CNPJ) (L-PE-04) |
| 8 | CPF_CNPJ_TITULAR | 14 | Sim (L-PE-01) | CPF/CNPJ com dígito verificador (L-PE-03, A-02, A-03) |
| 9 | NOME_TITULAR | 80 | Sim (L-PE-01) | Texto |
| 10 | NOME_DOC_IDENTIFICACAO | 50 | Condicional | Obrigatório para titular `T` investigado (R-TI-01) |
| 11 | NUMERO_DOC_IDENTIFICACAO | 20 | Condicional | idem |
| 12 | ENDERECO_LOGRADOURO | 80 | Condicional | idem |
| 13 | ENDERECO_CIDADE | 40 | Condicional | idem |
| 14 | ENDERECO_UF | 2 | Condicional | idem (sem validação de sigla em TITULARES) |
| 15 | ENDERECO_PAIS | 40 | Condicional | idem |
| 16 | ENDERECO_CEP | 8 | Condicional | idem |
| 17 | TELEFONE_PESSOA | 20 | Condicional | idem |
| 18 | VALOR_RENDA | 14 | Não | Se preenchido, número válido (L-PE-00) |
| 19 | DATA_ATUALIZACAO_RENDA | 8 | Não | A-01 (L-PE-02) |
| 20 | DATA_INICIO_RELACIONAMENTO | 8 | Sim (L-TI-01) | A-01 (L-TI-02); ordem (L-TI-06) |
| 21 | DATA_FIM_RELACIONAMENTO | 8 | Não | A-01 (L-TI-03); ≥ início (L-TI-06) |

### 3.5 EXTRATO (14 colunas)
| # | Campo | Máx. | Obrigatório | Domínio / regra |
|---|---|---|---|---|
| 1 | CODIGO_CHAVE_EXTRATO | 18 | Numérico (L-EX-00) | Identifica o lançamento; liga a ORIGEM_DESTINO |
| 2 | NUMERO_BANCO | 3 | — | Conta deve existir em CONTAS (L-EX-01) |
| 3 | NUMERO_AGENCIA | 4 | — | idem |
| 4 | NUMERO_CONTA | 20 | — | idem |
| 5 | TIPO_CONTA | 1 | — | idem |
| 6 | DATA_LANCAMENTO | 8 | Sim (L-EX-02) | A-01 (L-EX-03); dentro do período da conta (R-EX-02) |
| 7 | NUMERO_DOCUMENTO | 20 | Sim (L-EX-02) | Texto |
| 8 | DESCRICAO_LANCAMENTO | 50 | Sim (L-EX-02) | Texto |
| 9 | TIPO_LANCAMENTO | 3 | Numérico (L-EX-00) | Código CNAB-240 coerente com a natureza (L-EX-06, A-04) |
| 10 | VALOR_LANCAMENTO | 14 | Numérico (L-EX-00) | ≥ 0 (L-EX-07); igual à soma dos OD (R-EX-05); **é a coluna comparada com os limiares de valor** de R-OD-01, R-OD-02, R-OD-03 e R-OD-E2 |
| 11 | NATUREZA_LANCAMENTO | 1 | Efetivamente sim (L-EX-04) | `C`, `D` ou `*` |
| 12 | VALOR_SALDO | 14 | Numérico (L-EX-00) | ≥ 0 (L-EX-07) |
| 13 | NATUREZA_SALDO | 1 | Efetivamente sim (L-EX-05) | `C` ou `D` |
| 14 | LOCAL_TRANSACAO | 80 | Não | Texto |

### 3.6 ORIGEM_DESTINO (18 colunas)
| # | Campo | Máx. | Domínio / regra |
|---|---|---|---|
| 1 | CODIGO_CHAVE_OD | 18 | Numérico obrigatório (L-OD-00; não numérico gera E-13) |
| 2 | CODIGO_CHAVE_EXTRATO | 18 | Liga ao lançamento (R-EX-03, R-EX-05) |
| 3 | VALOR_TRANSACAO | 14 | Soma por lançamento = VALOR_LANCAMENTO (R-EX-05). **Não** é a base dos limiares de R-OD-*: esses usam EXTRATO.VALOR_LANCAMENTO |
| 4 | NUMERO_DOCUMENTO_TRANSACAO | 20 | Texto |
| 5 | NUMERO_BANCO_OD | 3 | Identificação (R-OD-*); L-OD-04 inalcançável |
| 6 | NUMERO_AGENCIA_OD | 4 | idem; `9999` = não correntista |
| 7 | NUMERO_CONTA_OD | 20 | idem; `99999999999999999999` = não correntista |
| 8 | TIPO_CONTA_OD | 1 | Rotulado 1–4, mas **o código testa o campo errado** (L-OD-01, §6) |
| 9 | TIPO_PESSOA_OD | 1 | Vazio, 1 ou 2 (L-OD-02) |
| 10 | CPF_CNPJ_OD | 14 | Validado em R-OD-04 |
| 11 | NOME_PESSOA_OD | 80 | R-OD-01…03 |
| 12 | NOME_DOC_IDENTIFICACAO_OD | 50 | Texto |
| 13 | NUMERO_DOC_IDENTIFICACAO_OD | 20 | Texto |
| 14 | CODIGO_DE_BARRAS | 100 | Texto |
| 15 | NOME_ENDOSSANTE_CHEQUE | 80 | Texto |
| 16 | DOC_ENDOSSANTE_CHEQUE | 50 | Texto |
| 17 | SITUACAO_IDENTIFICACAO | 1 | Deve ser `0` (L-OD-03) |
| 18 | OBSERVACÃO (com til no original; erro de digitação do nome do campo, exibido assim na mensagem E-04) | 250 | `NAO-CORRENTISTA`, `099 - NEGATIVA DE IDENTIFICACAO DO PORTADOR` (exceções em R-OD-01…03) |

### 3.7 Entradas que não são arquivos TXT
- **Atendimento** (banco, número do caso, versão, hash por arquivo): cadastrado nas telas `frmDadosAtendimento`/`frmIdentificaAtendimento`, lidas só por amostragem (LAC-06).
- **Investigados** (CPF/CNPJ, TEVE_RELACIONAMENTO, TEVE_CONTA, período de afastamento): tabela do atendimento; usada por R-TI-04…06, R-EX-01 e R-G112-*. Na reimplementação vem de `investigados.tsv` (formato na §0.3).

### 3.8 Arquivos GAB (largura fixa)
`<caso>_GAB109.txt`, `_GAB112.txt`, `_GAB800.txt`; ISO-8859-1; **largura exata por linha** (G-02): GAB109 127 (v1) / 333 (v2); GAB112 307 / 726; GAB800 298 / 320. Campos extraídos por posição, com `trim`. Sem TAB. Campos de cada layout: ver §5.8 (domínios) e `analise/04-regras-validacao.md` §G.

---

## 4. Fluxo de validação

### 4.1 Bancário
```
pasta → E-01 (existem?) → E-09 (versão) → E-14 (hash, cópia) → E-02 (não vazios)
 CARGA (E-07 fail-fast; contagem de linhas gravada ao fim de cada arquivo carregado, E-14):  AGENCIAS → CONTAS → TITULARES (PESSOA → TITULARIDADE) → EXTRATO → ORIGEM_DESTINO
        por linha: E-03 → E-04 → [convert] → [validar] → inserir     (E-06 limita o que roda após o 1º erro)
 CONTEÚDO (E-08 fail-fast, só se a carga inteira passou):
        AGENCIAS(R-AG) → CONTAS(R-CT) → TITULARES(R-TI) → EXTRATO(R-EX) → ORIGEM_DESTINO(R-OD)
 → relatório → "Continuar" habilitado (E-11)
```

### 4.2 Efeitos de fail-fast
| Mecanismo | Quando dispara | O que deixa de acontecer | Efeito visível |
|---|---|---|---|
| **E-01** | Algum dos 5 arquivos ausente | Todo o resto; só o **primeiro** ausente é citado | `Arquivo esperado (<nome>) não encontrado.` (exibida na tela, sem entrar no relatório) |
| **E-02** | 0 bytes em AGENCIAS, CONTAS ou TITULARES | Carga e conteúdo | `O arquivo X não pode ser vazio.` (linha 0). EXTRATO e ORIGEM_DESTINO vazios são aceitos |
| **E-06** | Primeiro erro de qualquer arquivo (contador **global**) | Nas linhas seguintes só rodam E-03/E-04; `convert()`/`validar()` não rodam; a linha não é inserida | Relatório = todas as mensagens da 1ª linha com erro semântico + erros estruturais (E-03/E-04) das demais |
| **E-07** | Arquivo com erro na carga | Arquivos seguintes não são carregados; se PESSOA (TITULARES) tem erro, TITULARIDADE não é carregada | **Nada novo no relatório.** O texto `A carga foi interrompida devido a erros anteriores.` só é registrado em log; não aparece no relatório nem na tela (golden `03`: só a mensagem de UF) |
| **E-08** | Carga completa com erro em qualquer arquivo → conteúdo não roda. Com carga limpa: o laço de conteúdo para no 1º arquivo reprovado | Arquivos seguintes (na ordem acima) | Dentro do arquivo reprovado **todas** as regras R-* rodam; indicador `#CONTEUDO AGENCIAS=true,CONTAS=false` nos golden mostra até onde chegou |

Consequências para o QA:
- Um erro de **carga** esconde todos os erros de **conteúdo** (E-08) e os erros semânticos de linhas posteriores (E-06).
- Um erro de conteúdo em CONTAS esconde os de TITULARES, EXTRATO e ORIGEM_DESTINO (golden `09`: `CONTEUDO AGENCIAS=true,CONTAS=false`).
- Corrigir um erro pode revelar outros: o relatório é incremental por natureza.

### 4.3 Fluxo de corretoras (GAB)
`GAB109 → GAB112 → GAB800` (carga fail-fast; conteúdo na mesma ordem). Pré-checagens G-01 (vazios) e G-02 (largura). No 6.2.6 todo arquivo com a largura certa e não vazio falha em G-DEF antes de qualquer regra de campo.

---

## 5. Regras por etapa e arquivo

Notação de cada tabela: **Finalidade/quando** = o que a regra protege e em que condição se aplica; **O usuário vê** = prefixo/texto da mensagem (textos completos em `analise/04-regras-validacao.md`; as pontuações e erros de grafia dos originais foram mantidos); **Conf.** = código da §0.1.

### 5.1 Etapa pré-carga e fluxo (E-*)
| ID | Finalidade / quando | O usuário vê | Conf. |
|---|---|---|---|
| E-01 | Garante os 5 arquivos `<NUMERO_CASO>_{AGENCIAS,CONTAS,TITULARES,EXTRATO,ORIGEM_DESTINO}.txt` na pasta (sem recursão; case-insensitive; só `.txt`). Reporta apenas o primeiro ausente e para | `Arquivo esperado (<nome>) não encontrado.` | EST |
| E-02 | AGENCIAS, CONTAS e TITULARES com 0 bytes são erro; EXTRATO e ORIGEM_DESTINO podem ser vazios (intenção não documentada) | `O arquivo X não pode ser vazio.` (linha 0; rótulo físico) | EXEC |
| E-03 | Toda linha de arquivo bancário deve ter exatamente N colunas separadas por TAB | `Quantidade de colunas inválida. Esperado: N Atual: M` (rótulo físico) | EXEC |
| E-04 | Nenhum campo (após `trim`) pode exceder o máximo do layout (§3). Roda só em linha com N colunas | `Tamanho do campo <NOME> excedido. Tamanho máximo permitido: <n>` (rótulo **lógico**, ex.: `AGENCIAS`; `<NOME>` é o nome do campo em `getCamposArquivo()`, ex.: `NUMERO_BANCO`, `OBSERVACÃO` com til) | EXEC |
| E-05 | ISO-8859-1; CR/LF/CRLF aceitos; linha em branco vira E-03 | — (efeito: E-03) | EST |
| E-05b | Só o primeiro campo de comprimento zero (linha iniciada por TAB) é descartado (`\tb\tc` = 2 colunas); campos vazios finais são preservados; um primeiro campo `" "` é mantido; `trim` remove caracteres ≤ U+0020 | Indireto: `Quantidade de colunas inválida… Atual: M-1` | EXEC |
| E-06 | Após o primeiro erro de qualquer arquivo, linhas seguintes só passam por E-03/E-04 | Relatório = 1º erro semântico + estruturais das demais linhas | EXEC |
| E-07 | Fail-fast na carga entre arquivos e entre as passadas de TITULARES | Interrompe a carga dos seguintes. O texto `A carga foi interrompida devido a erros anteriores.` é lançado, capturado e **só registrado em log**: não aparece no relatório nem na tela (golden `03`) | EXEC |
| E-08 | Fail-fast no conteúdo: só roda com carga limpa; para no 1º arquivo reprovado, mas executa todas as regras daquele arquivo | Mensagens R-* do 1º arquivo reprovado | EXEC |
| E-09 | Versão do validador deve ser a do atendimento; senão interrompe | Diálogo de aviso (fora do relatório): `A versão do sistema (<v>) é diferente da versão do cadastro de atendimento (<v>). Para solucionar este problema, clique no botão Voltar, selecione o caso, clique em Ver Detalhes e grave o caso novamente.` | EST |
| E-10 | Define o perfil eleitoral por prefixo do número do caso (§2) | — | EXEC |
| E-11 | "Continuar" é habilitado no `finally` do processo, mesmo com erros (exceto caso `999-OUTROS-000001-88`, que exige ausência de erros). O `finally` também roda após os retornos antecipados de E-01 e E-09: "Continuar" fica habilitado mesmo sem validação alguma | Relatório gerado (quando há validação); interface não impede o avanço | EXEC |
| E-12 | Define o formato `Erro no arquivo: <rótulo>, linha: <n>, erro: <msg>` | Toda linha do relatório | EXEC |
| E-13 | Qualquer exceção não tratada na linha (A-06, conversão numérica, NPE) vira mensagem genérica; rótulo físico | `Erro desconhecido. Consulte o log do sistema.` | EXEC |
| E-14 | Antes de E-02: grava o MD5 dos 5 arquivos e copia os arquivos para a pasta do caso. A **contagem de linhas** é gravada depois, durante a carga, ao fim de cada arquivo carregado (arquivos não carregados por E-07/E-02 ficam sem contagem) | Nenhuma mensagem; valores gravados no atendimento | EXEC |
| E-15 | O app real busca por HTTP a lista de órgãos (versão do receptor); sem rede/servidor não valida (nos testes foi injetada em memória) | Afeta A-07 e L-OD-04 | EST |

### 5.2 Algoritmos de apoio (A-*)
| ID | Finalidade / quando | O usuário vê | Conf. |
|---|---|---|---|
| A-01 | Valida data `ddMMyyyy`: exatamente 8 caracteres, ano ≥ 1900, calendário estrito (29/02 só em bissexto). Campo vazio não é validado | `A <CAMPO> (<valor>) é inválida ou o ano é anterior a 1900.` | EXEC |
| A-02 | Dígito verificador de CPF: completa com zeros à esquerda até 11; rejeita base com todos os dígitos iguais; módulo 11 | Usado por L-PE-03, R-OD-04, G-06 | EXEC |
| A-03 | Dígito verificador de CNPJ **alfanumérico** (A–Z valem `ord−48`); completa com zeros até 14; rejeita base com todos os caracteres iguais | idem | EXEC |
| A-04 | `CnabRule` (retorna "inválido"): crédito `C` válido 201–223 ou 999; débito `D` válido 101–127 exceto 116, ou 999; `*` válido 101–127 (exceto 116), 201–223 ou 999 | Usado por L-EX-06 | EXEC |
| A-05 | `isNumber` leniente e dependente do locale (`"12abc"` passa no `parse`; `"1.000"`=1000 em pt_BR), seguido de `Long.valueOf` estrito, que lança exceção | Varia: mensagem de L-EX-00 ou E-13 | PROV |
| A-06 | `removerZerosEsquerda` lança exceção para texto vazio ou só zeros | E-13 (L-PE-03, G-06) | EXEC |
| A-07 | MD5 em hexadecimal; com zeros à esquerda só se receptor ≥ v4; senão sem padding | Valor gravado (E-14) | EXEC dado o receptor; versão real: PROV |

### 5.3 Carga — AGENCIAS (ordem de execução)
| ID | Finalidade / quando | O usuário vê | Conf. |
|---|---|---|---|
| L-AG-01 | Campos obrigatórios. Não eleitoral: BANCO, AGENCIA, NOME, LOGRADOURO, CIDADE, UF, PAIS, CEP, TELEFONE. Eleitoral: só BANCO, AGENCIA, NOME. "Vazio" = nulo ou vazio após `trim` | `Um dos campos obrigatórios (<lista>) não está preenchido.` (a lista nomeia os campos do perfil) | EXEC |
| L-AG-02 | Se DATA_ABERTURA preenchida, aplica A-01 | Mensagem de A-01 | EXEC |
| L-AG-03 | Se DATA_FECHAMENTO preenchida, aplica A-01 | Mensagem de A-01 | EXEC |
| L-AG-04 | `NUMERO_BANCO` (como inteiro) deve ser o banco do atendimento. Vazio: L-AG-01 dispara e depois a conversão lança exceção, resultando em **dois erros**. Preenchido não numérico (ex.: `AB1`): L-AG-01 não dispara, **um** erro (E-13). Nos dois casos a exceção interrompe a validação da linha e L-AG-05 e L-AG-06 não rodam | `NUMERO_BANCO diferente do informado em atendimento.` (valor numérico diferente); vazio: L-AG-01 + E-13; não numérico: só E-13 | EXEC |
| L-AG-05 | Não eleitoral: fechamento sem abertura é erro; fechamento anterior à abertura é erro (parse leniente) | `DATA_FECHAMENTO_AGENCIA informada, mas não há DATA_ABERTURA_AGENCIA.` / `DATA_ABERTURA_AGENCIA posterior à DATA_FECHAMENTO_AGENCIA.` / `Data inválida nos campos …` | EXEC |
| L-AG-06 | Não eleitoral: UF deve estar entre as 27 siglas (maiúsculas) ou `--` | `ENDERECO_UF:<v> não é uma sigla válida.` | EXEC |
| L-AG-07 | Na inserção, NUMERO_AGENCIA vira inteiro; não numérico gera exceção | Nada, ou E-13 | EXEC |

### 5.4 Carga — CONTAS
| ID | Finalidade / quando | O usuário vê | Conf. |
|---|---|---|---|
| L-CT-00 | A agência da conta (banco+agência numéricos; zeros à esquerda irrelevantes) deve existir em AGENCIAS. Vazio/não numérico gera E-13 | `Agencia não localizada. Banco: <b> Agencia: <a>` (rótulo físico) | EXEC |
| L-CT-01 | Obrigatórios: NUMERO_BANCO, NUMERO_CONTA, TIPO_CONTA, MOVIMENTACAO_CONTA. A mensagem lista apenas os 3 últimos | `Um dos campos obrigatórios (NUMERO_CONTA, TIPO_CONTA, MOVIMENTACAO_CONTA) não está preenchido.` | EXEC |
| L-CT-02 | DATA_ABERTURA_CONTA, se preenchida, A-01. Não existe verificação "abertura ≤ encerramento" na carga | Mensagem de A-01 | EXEC |
| L-CT-03 | DATA_ENCERRAMENTO_CONTA, se preenchida, A-01 | Mensagem de A-01 | EXEC |
| L-CT-04 | TIPO_CONTA ∈ {1,2,3,4} | `Tipo de conta inválido. Valores possíveis: 1, 2, 3 e 4.` | EXEC |
| L-CT-05 | MOVIMENTACAO_CONTA ∈ {1,2,3} (significado de negócio: ver §9; 1 = com movimentação de investigado, 2 = investigado sem movimentação, 3 = não investigada, conforme R-CT-06/08/09/10 e R-EX-04) | `Movimentação de Conta inválido. Valores possíveis: 1, 2 e 3` | EXEC |

### 5.5 Carga — TITULARES
Duas passadas sobre o mesmo arquivo: **PESSOA** e depois **TITULARIDADE** (esta só roda se PESSOA não teve erro, E-07).

| ID | Finalidade / quando | O usuário vê | Conf. |
|---|---|---|---|
| L-PE-00 | VALOR_RENDA, se preenchido, deve ser número decimal válido | `Valor numérico inválido no campo Valor Renda: <v>` (rótulo físico) | EXEC |
| L-PE-01 | Obrigatórios testados: TIPO_PESSOA, CPF_CNPJ, NOME. A mensagem tem lista **fixa** de 5 campos (inclui PESSOA_INVESTIGADA e DATA_INICIO_RELACIONAMENTO, que aqui não são testados) | `Um dos campos obrigatórios (TIPO_PESSOA, CPF_CNPJ_PESSOA, NOME_PESSOA, PESSOA_INVESTIGADA, DATA_INICIO_RELACIONAMENTO) não está preenchido.` | EXEC |
| L-PE-02 | DATA_ATUALIZACAO_RENDA, se preenchida, A-01. O erro sai com rótulo `EXTRATO` (defeito de rótulo, §6) | Mensagem de A-01 com `Erro no arquivo: EXTRATO` | EXEC |
| L-PE-03 | CPF (tipo 1) ou CNPJ (tipo 2) com dígito verificador válido; CPF_CNPJ vira maiúsculo. CPF só de zeros gera E-13 em vez de "inválido" | `O CPF_CNPJ_PESSOA: <v> está inválido.` | EXEC |
| L-PE-04 | TIPO_PESSOA ∈ {1,2} | `Tipo de pessoa inválido. Valores possíveis: 1 e 2.` | EXEC |
| L-TI-00 | Conversão: BANCO/AGENCIA/CONTA/TIPO_CONTA não vazios; conta (banco+agência+conta+tipo) deve existir em CONTAS; a pessoa da linha deve existir | `O código COMPE da instituição financeira não foi informado` / `O número da agência não foi informado` / `O número da conta não foi informado` / `O tipo de conta não foi informado` / `Conta não localizada. Banco: … Agencia: … Conta: … TipoConta: …` / `Pessoa não localizada. Linha: <n>` (rótulo físico em todas as seis) | EXEC |
| L-TI-01 | Obrigatórios: TIPO_TITULAR, DATA_INICIO_RELACIONAMENTO, PESSOA_INVESTIGADA. Ramos eleitoral e não eleitoral têm condição e mensagem iguais | Mesma lista fixa de L-PE-01 (que não cita TIPO_TITULAR, embora ele seja testado) | EXEC |
| L-TI-02 | DATA_INICIO passa por A-01; o erro **interrompe a linha** (exceção de negócio) | `A DATA_INICIO_RELACIONAMENTO (<v>) é inválida ou o ano é anterior a 1900.` (rótulo físico) | EXEC |
| L-TI-03 | Idem para DATA_FIM | `A DATA_FIM_RELACIONAMENTO (<v>) é inválida ou o ano é anterior a 1900.` (rótulo físico) | EXEC |
| L-TI-04 | TIPO_TITULAR é um caractere em `T`, `1`–`9`, `R`, `L`, `P`, `O` | `Tipo de titular inválido. Valores possíveis: T, 1 até 9, R, L, P e O.` | EXEC |
| L-TI-05 | PESSOA_INVESTIGADA ∈ {0,1} (o texto fala em "conta investigada", mas o campo validado é PESSOA_INVESTIGADA) | `Tipo de conta investigada inválido. Valores possíveis: 0 e 1.` | EXEC |
| L-TI-06 | DATA_INICIO deve ser parseável (vazio é erro); DATA_FIM, se houver, parseável e ≥ início | `A DATA_INICIO_RELACIONAMENTO é inválida: <v>` / `A DATA_FIM_RELACIONAMENTO é inválida: <v>` / `A DATA_INICIO_RELACIONAMENTO (<i>) é posterior a DATA_FIM_RELACIONAMENTO (<f>).` (rótulo físico nas mensagens de parse "é inválida"; lógico na de início posterior ao fim) | EXEC |

### 5.6 Carga — EXTRATO
| ID | Finalidade / quando | O usuário vê | Conf. |
|---|---|---|---|
| L-EX-00 | CODIGO_CHAVE, TIPO_LANCAMENTO, VALOR_LANCAMENTO, VALOR_SALDO devem ser numéricos (via A-05: leniente e dependente do locale); se não, mensagem e valor 0 | `Campo <NOME> não é um número.` (rótulo físico) | PROV |
| L-EX-01 | A conta (banco+agência+conta+tipo) deve existir em CONTAS | `Conta não localizada no arquivo de CONTAS.` | EXEC |
| L-EX-02 | Obrigatórios: DATA_LANCAMENTO, NUMERO_DOCUMENTO, DESCRICAO_LANCAMENTO (mensagem com lista fixa de 8 campos). A checagem de naturezas nunca dispara por "nulo"; naturezas vazias caem em L-EX-04/05 | `Um dos campos obrigatórios (…) não está preenchido.` | EXEC |
| L-EX-03 | DATA_LANCAMENTO passa por A-01 | Mensagem de A-01 | EXEC |
| L-EX-04 | NATUREZA_LANCAMENTO ∈ {C, D, *} | `Natureza de Lancamento inválida. Valores possíveis: C, D e *(asterisco).` | EXEC |
| L-EX-05 | NATUREZA_SALDO ∈ {C, D} | `Natureza de saldo inválida. Valores possíveis: C e D.` | EXEC |
| L-EX-06 | TIPO_LANCAMENTO deve ser válido para a natureza (tabela CNAB-240, A-04) | `TIPO_LANCAMENTO inválido para NATUREZA_LANCAMENTO='C'` (ou `'D'` / `'*'`) `. Veja tabela CNAB-240.` | EXEC |
| L-EX-07 | VALOR_LANCAMENTO e VALOR_SALDO não podem ser negativos | `VALOR_LANCAMENTO e VALOR_SALDO não podem ser negativos.` | EXEC |

### 5.7 Carga — ORIGEM_DESTINO
| ID | Finalidade / quando | O usuário vê | Conf. |
|---|---|---|---|
| L-OD-00 | CODIGO_CHAVE_OD é convertido direto para inteiro longo: não numérico gera E-13. Demais numéricos passam por A-05; os nomes que entram na mensagem são os do código, **não** os do layout: `CODIGO_CHAVE_EXTRATO`, `VALOR_OPERACAO` (layout: VALOR_TRANSACAO), `NUMERO_AGENCIA` (layout: NUMERO_AGENCIA_OD; só convertido se preenchido) e `SITUACAO_IDENTIFICACAO` | CODIGO_CHAVE_OD: `Erro desconhecido. Consulte o log do sistema.` (E-13, rótulo físico). Demais: `Campo <NOME> não é um número.` com os nomes acima (rótulo físico) | EXEC (CODIGO_CHAVE_OD ⇒ E-13) / PROV (demais campos, A-05) |
| L-OD-01 | Deveria validar TIPO_CONTA_OD (1–4), mas o código testa **TIPO_PESSOA_OD** (`[12]`) com a mensagem de TIPO_CONTA. TIPO_CONTA_OD nunca é checado como intervalo; valor não numérico falha só na inserção (E-13) | `TIPO_CONTA_OD inválido. Valores possíveis: 1, 2, 3 ou 4.` (disparada por TIPO_PESSOA_OD inválido) | EXEC |
| L-OD-02 | TIPO_PESSOA_OD, se preenchido, ∈ {1,2}. Um valor inválido gera L-OD-01 **e** L-OD-02 | `TIPO_PESSOA_OD inválido. Valores possíveis: 1 ou 2.` | EXEC |
| L-OD-03 | SITUACAO_IDENTIFICACAO deve ser 0 | `SITUACAO_IDENTIFICACAO inválido. Valor possível: 0.` | EXEC |
| L-OD-04 | NUMERO_BANCO_OD com mais de 3 caracteres: erro (receptor antigo) ou exige 14 dígitos (receptor novo). **Inalcançável**: o layout limita o campo a 3 caracteres (E-04) e linha com erro estrutural não chega à validação | `Número Banco inválido. O mesmo deve ser composto de apenas 3 dígitos (COMPE).` / `… (COMPE) ou 14 dígitos(CNPJ).` | INALC |

### 5.8 Conteúdo (consultas SQL) — só com carga limpa (E-08)
Cada linha devolvida pela consulta vira **um** erro (joins podem duplicar, ver §6). A unidade de VALOR_* e o sentido de negócio dos limiares são ND (LAC-01). Ordem das linhas de uma mesma regra não é contratual (ND, D13).

**AGENCIAS**
| ID | Finalidade / quando | O usuário vê | Conf. |
|---|---|---|---|
| R-AG-01 | Evitar agências repetidas: mesmo (NUMERO_BANCO, NUMERO_AGENCIA) mais de uma vez; a linha citada é a maior (última) | `Agência duplicada: NUMERO_BANCO: bbb, NUMERO_AGENCIA: aaaa` | EXEC |

**CONTAS (10 regras, nesta ordem)**
| ID | Finalidade / quando | O usuário vê | Conf. |
|---|---|---|---|
| R-CT-01 | NUMERO_CONTA não pode conter `.`, `/`, `\` ou `-` | `Conta com separador: <conta>` | EXEC |
| R-CT-02 | Conta (agência, número, tipo) repetida. Rótulo sai como `null` (§6) | `Erro no arquivo: null, linha…, erro: Conta duplicada - Linha: …` | EXEC |
| R-CT-03 | Conta com MOVIMENTACAO 1, 2 ou 3 deve ter titularidade em TITULARES | `Conta sem registro correspondente em Titulares. NUMERO_BANCO: …` | EXEC |
| R-CT-04 | Conta com MOVIMENTACAO 1 ou 2 deve ter ao menos um titular com PESSOA_INVESTIGADA=1 | `Conta sem pessoa investigada. …` | EXEC |
| R-CT-05 | O atendimento deve ter ao menos uma conta com MOVIMENTACAO 1 ou 2 (erro na linha 0) | `Atendimento sem nenhuma movimentação de conta.` | EXEC |
| R-CT-06 | Conta sem investigado deve ter MOVIMENTACAO 3; um erro por titularidade (duplicação por join) | `Conta com atributo MOVIMENTACAO_CONTA incorreto. Deve ser 3, pois não é conta de investigado. …` | EXEC |
| R-CT-07 | No máximo um titular tipo `T` por conta | `Conta com mais de um Titular tipo T. …` | EXEC |
| R-CT-08 | Conta com titular investigado deve ter MOVIMENTACAO 1 ou 2 (duplica por join) | `… Deve ser 1 ou 2, pois é um investigado. …` | EXEC |
| R-CT-09 | MOVIMENTACAO=1 exige lançamentos em EXTRATO | `Conta definida com movimentação (MOVIMENTACAO_CONTA = 1), porém não possui lançamentos em EXTRATO. …` | EXEC |
| R-CT-10 | MOVIMENTACAO=2 não pode ter lançamentos em EXTRATO | `Conta definida sem movimentação (MOVIMENTACAO_CONTA = 2), porém possui lançamentos em EXTRATO. …` | EXEC |

**TITULARES (7 regras)**
| ID | Finalidade / quando | O usuário vê | Conf. |
|---|---|---|---|
| R-TI-01 | Titular `T` investigado deve ter NOME_DOC, NUMERO_DOC, LOGRADOURO, CIDADE, UF, PAIS, CEP e TELEFONE preenchidos | `O titular de conta investigada deve ter os seguintes dados preenchidos: …` | EXEC |
| R-TI-02 | Relacionamento (início ou fim) não pode ser anterior à abertura da conta | `A DATA_INICIO_RELACIONAMENTO ou DATA_FIM_RELACIONAMENTO está menor que a DATA_ABERTURA_CONTA (…` | EXEC |
| R-TI-03 | Relacionamento (início ou fim) não pode ser posterior ao encerramento da conta | `… está maior que a DATA_ENCERRAMENTO_CONTA (…` | EXEC |
| R-TI-04 | Investigado do atendimento (relacionamento=1 e conta=1) deve aparecer em TITULARES como PESSOA_INVESTIGADA=1; reportado com **linha −1** | `O CPF_CNPJ … cadastrado no atendimento como investigado, não foi encontrado no arquivo TITULARES como PESSOA_INVESTIGADA = 1` | EXEC |
| R-TI-05 | Pessoa de investigado não pode estar com PESSOA_INVESTIGADA=0 | `… deve possuir o atributo PESSOA_INVESTIGADA = 1` | EXEC |
| R-TI-06 | PESSOA_INVESTIGADA=1 deve corresponder a investigado cadastrado no atendimento | `… informado como PESSOA_INVESTIGADA = 1 não foi cadastrado no atendimento.` | EXEC |
| R-TI-07 | Conta investigada deve ter titular tipo `T` | `Conta investigada sem Titular do tipo T. …` | EXEC |

**EXTRATO (5 regras)**
| ID | Finalidade / quando | O usuário vê | Conf. |
|---|---|---|---|
| R-EX-01 | Lançamento em conta de investigado deve estar no período de afastamento do **investigado** | `Lançamento em dd/MM/yyyy fora do período de afastamento (…) do investigado: CPF_CNPJ (…)` | EXEC |
| R-EX-02 | Lançamento deve estar entre abertura e encerramento da conta (nulo não dispara). Com abertura nula e lançamento após encerramento o original lança exceção de infraestrutura (§6) | `A DATA_LANCAMENTO (dd/MM/yyyy) está fora do período de DATA_ABERTURA_CONTA (…` | EXEC |
| R-EX-03 | Todo lançamento deve ter ao menos um registro em ORIGEM_DESTINO | `Lançamento em EXTRATO sem correspondente em ORIGEM_DESTINO. CODIGO_CHAVE_EXTRATO: n` | EXEC |
| R-EX-04 | Conta com MOVIMENTACAO=3 não pode ter lançamentos | `No arquivo CONTAS, se o campo MOVIMENTAÇÃO DA CONTA estiver com o parâmetro "3"(conta não investigada) não pode haver nenhum registro para essa conta no arquivo EXTRATO` | EXEC |
| R-EX-05 | VALOR_LANCAMENTO deve igualar a soma de VALOR_TRANSACAO dos OD do lançamento | `O Valor do lancamento em EXTRATO (CODIGO_CHAVE: n) não é igual ao somatório dos registros correspondentes em ORIGEM_DESTINO.` | EXEC |

**ORIGEM_DESTINO** (limiares e datas fixos em SQL; versionados na reimplementação). **Atenção:** a data e o valor dos limiares referem-se a `EXTRATO.DATA_LANCAMENTO` e `EXTRATO.VALOR_LANCAMENTO` do lançamento ligado ao OD, não a VALOR_TRANSACAO. R-OD-E1 não tem limiar de data nem de valor.
| ID | Perfil | Finalidade / quando | O usuário vê | Conf. |
|---|---|---|---|---|
| R-OD-01 | Não eleitoral | Exige identificar depositante/favorecido em **TED/DOC**: TIPO_LANCAMENTO 120 ou 209 (do lançamento), EXTRATO.DATA_LANCAMENTO ≥ 01/01/2006, EXTRATO.VALOR_LANCAMENTO ≥ 1.000.000, identificação ausente (CPF_CNPJ_OD, NOME, BANCO, AGENCIA ou CONTA nulos; BANCO=0; AGENCIA=0; CONTA só zeros). **Exceção:** AGENCIA=9999, CONTA=`99999999999999999999` e OBSERVACAO=`NAO-CORRENTISTA` (sem diferenciar maiúsculas) | `É OBRIGATÓRIA a identicação do depositante/favorecido de Transferências Interbancária (TED, DOC) nos campos CPF_CNPJ_OD, NOME_PESSOA_OD, NUMERO_BANCO_OD, NUMERO_AGENCIA_OD e NUMERO_CONTA_OD. …` | EXEC |
| R-OD-02 | Não eleitoral | Idem para **transferência entre contas do mesmo banco**: TIPO 117 ou 213, mesmos limiares de data e valor de EXTRATO, mesma exceção | `É OBRIGATÓRIA a identicação … de Transferências entre Contas …` | EXEC |
| R-OD-03 | Não eleitoral | Identificação em **espécie**: TIPO 123 ou 220 e (EXTRATO.DATA_LANCAMENTO ≥ 01/07/2013 e EXTRATO.VALOR_LANCAMENTO ≥ 1.000.000, ou data ≥ 02/01/2013 e valor ≥ 5.000.000, ou data ≥ 01/07/2012 e valor ≥ 10.000.000), NOME_PESSOA_OD nulo, e OBSERVACAO diferente de `099 - NEGATIVA DE IDENTIFICACAO DO PORTADOR` | `É obrigatória a identificação do depositante/favorecido de transações em espécie …` | EXEC |
| R-OD-04 | Ambos | CPF/CNPJ de OD válido em transferências: TIPO 117/120/209/213, EXTRATO.DATA_LANCAMENTO ≥ 01/01/2006, CPF_CNPJ_OD preenchido. Tipo pessoa 1 = CPF; 2 = CNPJ; outro/vazio = CNPJ **ou** CPF (últimos 11 de 14) | `O campo CPF_CNPJ_PESSOA_OD com o valor <v> é inválido [para TIPO_PESSOA_OD = …]` | EXEC (conteúdo); ordem ND |
| R-OD-E1 | Eleitoral (substitui R-OD-01…03) | Identificação em transferências (TIPO 117/120/209/213) **sem** limite de data nem de valor | `É OBRIGATÓRIA a identicação … de transferências no mesmo banco ou transferências interbancárias (TED/DOC) …` | EXEC |
| R-OD-E2 | Eleitoral | Depósito em cheque/dinheiro: TIPO 201, EXTRATO.VALOR_LANCAMENTO ≥ 10.000 e CPF_CNPJ_OD nulo | `É OBRIGATÓRIA a identicação … de depósito em cheque e/ou dinheiro no campo CPF_CNPJ_OD.` | EXEC |

### 5.9 Fluxo de corretoras (GAB)
| ID | Finalidade / quando | O usuário vê | Conf. |
|---|---|---|---|
| G-01 | Três checagens independentes: GAB112 não pode ser vazio; GAB800 vazio com GAB109 com dados; GAB109 vazio com GAB800 com dados | `O arquivo GAB-112 não pode ser vazio.` / `O arquivo GAB-800 não pode ser vazio quando o arquivo GAB-109 possui dados.` / `O arquivo GAB-109 não pode ser vazio quando o arquivo GAB-800 possui dados.` | EXEC |
| G-02 | A linha deve ter exatamente a largura do layout (v1/v2, §3.8) | `Certifique-se de completar com espaços os campos que não atingirem o tamanho predeterminado e Não use tab para separar os campos.` (rótulo físico) | EXEC |
| G-DEF | **Defeito:** a lista de campos dos 6 carregadores GAB é nula; toda linha de largura certa gera exceção. Nenhum GAB não vazio é aprovado; G-03…G-07 e R-G* nunca rodam no app real | `Erro desconhecido. Consulte o log do sistema.` por linha (sem o filtro de E-06) | EXEC |
| G-03 | Obrigatórios por arquivo (GAB109: 7 campos; GAB800: 10; GAB112: 20 verificações, com DOCUMENTO_DO_CLIENTE e COMPLEMENTO_DO_CEP verificados duas vezes, o que duplica a mensagem) | `O campo <CAMPO> é obrigatório.` | EXEC sob remendo |
| G-04 | Datas `yyyyMMdd` válidas. GAB109: só o primeiro erro (movimento, referência, liquidação); GAB112/800: um erro por campo; não parseável gera E-13 | GAB109: `O campo X está incorreto. A data deve estar no formato AAAAMMDD e válida.` GAB112/800: `A data deve estar no formato AAAAMMDD e válida.  Campo: X` (dois espaços) | EXEC sob remendo |
| G-05 | Numéricos: GAB109 CODIGO_DO_CLIENTE e VALOR_DO_LANCAMENTO; GAB112 CODIGO_DO_CLIENTE, DOCUMENTO_DO_CLIENTE, CODIGO_DO_CLIENTE_MERCADO, CODIGO_DA_ATIVIDADE; GAB800 CODIGO_DO_CLIENTE, VALOR_DO_LANCAMENTO, SALDO_INICIAL | `O dado informado não é um número. Campo: CODIGO_DO_CLIENTE` (1º campo) / `… não é um número.  Campo X` | EXEC sob remendo |
| G-06 | Domínios: naturezas C/D; CONSTANTE_0000A=`0000A`; TIPO_DE_PESSOA F/J; MERCADO BOVESPA/BMF/SWAP; ESTADO (27 UFs maiúsculas); DOCUMENTO_DO_CLIENTE com 15 caracteres; CPF (F) ou CNPJ (J) válido após remover zeros à esquerda (só zeros gera E-13 via A-06) | `Informe C para crédito ou D para débito. Campo: X`; `Valor esperado para o campo CONSTANTE_0000A: 0000A.`; `Informe F para pessoa física ou J para pessoa jurídica. Campo: TIPO_DE_PESSOA`; `Informe BOVESPA para mercado à vista, BMF para derivativos ou SWAP para balcão. Campo: MERCADO`; `A sigla do campo estado está incorreta`; `O campo DOCUMENTO_DO_CLIENTE é obrigatório conter 15posições. Devendo ser completementado com zeros à esquerda.`; `CPF inválido para o tipo de pessoa: F. Campo: DOCUMENTO_DO_CLIENTE` / `CNPJ inválido …` | EXEC sob remendo |
| G-07 | Gravação layout v2: colunas NUMERIC (GAB109: CHAVE_OD, BANCO/AGENCIA/CONTA de origem e destino; GAB112: MEMBRO_COMPENSACAO, AGENTE_CUSTODIA, CODIGO_CORRETORA) rejeitam texto; DATA_SITUACAO (GAB112 v2) sem validação: vazia/inválida gera exceção | E-13 | EXEC sob remendo |
| R-G109 | O CODIGO_DO_CLIENTE do GAB109 (comparação numérica) deve existir no GAB112 | `O cliente com o código N não foi informado no arquivo gab112.` | EXEC sob remendo |
| R-G800 | Idem para o GAB800 | idem | EXEC sob remendo |
| R-G112-1 | Pelo menos um investigado deve ter relacionamento (linha 0, rótulo `Investigado`); com a tabela de investigados vazia o original lança exceção de infraestrutura | `Não foi encontrado relacionamento no arquivo investigado` | EXEC sob remendo |
| R-G112-2 | DOCUMENTO_DO_CLIENTE (sem zeros à esquerda) deve constar entre os investigados | `O CPF/CNPJ <doc> não teve relacionamento com esta instituição no período do afastamento de sigilo.` | EXEC sob remendo |
| R-G112-3 | Investigado com relacionamento deve constar do GAB112 (linha 0) | `O investigado com CPF/CNPJ <cpf> não foi encontrado no arquivo cadastral(gab112).` | EXEC sob remendo |

---

## 6. Comportamentos inesperados e defeitos do original

Todos estão dentro de regras do catálogo (§8.3). Servem para o QA **não** tratar como defeito da reimplementação o que é fiel ao original (modo COMPAT; o modo ESTRITO corrige alguns, ver `implementacao/README.md`).

| # | Comportamento | Regra | Conf. | Efeito para o usuário |
|---|---|---|---|---|
| D-01 | Só o 1º erro semântico do atendimento é reportado; linhas seguintes só têm erros estruturais | E-06 | EXEC | Relatórios incompletos; corrigir e revalidar |
| D-02 | Campo inicial de comprimento zero some e desloca as colunas | E-05b | EXEC | Erro de colunas "Atual: M-1" mesmo com o TAB presente |
| D-03 | `NUMERO_BANCO` **vazio** gera 2 erros (obrigatório e "Erro desconhecido"); preenchido não numérico gera só o "Erro desconhecido". Em ambos, L-AG-05 e L-AG-06 não rodam | L-AG-04 | EXEC | Mensagem genérica extra |
| D-04 | Erro de data de renda rotulado `EXTRATO` | L-PE-02 | EXEC | Rótulo trocado |
| D-05 | Validação de TIPO_CONTA_OD testa TIPO_PESSOA_OD; TIPO_PESSOA_OD inválido gera 2 mensagens | L-OD-01, L-OD-02 | EXEC | Mensagem de tipo de conta para erro de tipo de pessoa; tipo de conta de OD nunca validado |
| D-06 | CPF só de zeros gera "Erro desconhecido" em vez de "CPF inválido" | L-PE-03, A-06 | EXEC | Mensagem genérica com rótulo físico |
| D-07 | Teste de nulo em naturezas nunca dispara; vazios caem nas regras de domínio | L-EX-02 | EXEC | Natureza vazia aparece como "inválida", não "obrigatória" |
| D-08 | Conversão numérica leniente e dependente do locale | A-05, L-EX-00, L-OD-00 | PROV | A-05 depende do locale da máquina; `en_US` foi testado (0 divergências, LAC-02), mas a dependência permanece |
| D-09 | Duplicação de erros por join | R-CT-06, R-CT-08 | EXEC | Mesma conta aparece várias vezes |
| D-10 | Rótulo `Erro no arquivo: null` | R-CT-02 | EXEC | Rótulo ausente |
| D-11 | Linha −1 no relatório | R-TI-04 | EXEC | Linha sem sentido de arquivo |
| D-12 | Mensagem de L-PE-01/L-TI-01/L-EX-02 lista campos que a regra não testa (L-TI-01 também omite TIPO_TITULAR, que é testado); a de L-CT-01 omite o NUMERO_BANCO, cujo ramo é inalcançável porque a conversão de L-CT-00 falha antes (E-13) | L-PE-01, L-TI-01, L-EX-02, L-CT-01 | EXEC | Mensagem enganosa quanto ao campo faltante |
| D-13 | Texto "Tipo de conta investigada" refere-se a PESSOA_INVESTIGADA | L-TI-05 | EXEC | Rótulo confuso |
| D-14 | Nenhum arquivo GAB não vazio é aprovado | G-DEF | EXEC | Corretoras sempre veem "Erro desconhecido" por linha; fornecedor não notificado (progresso/D5) |
| D-15 | GAB112: mensagem de obrigatório duplicada para DOCUMENTO_DO_CLIENTE e COMPLEMENTO_DO_CEP; GAB112 v2 exige DATA_SITUACAO válida | G-03, G-07 | EXEC sob remendo | Inatingível no app real |
| D-16 | NPE quando DATA_ABERTURA_CONTA nula e lançamento após o encerramento (dedução estática do código; o golden `10` não exercita este cenário) | R-EX-02 | EST | Falha de infraestrutura em vez de mensagem de regra |
| D-17 | Erros de grafia mantidos nos textos: "identicação", "completementado", "15posições", "Agencia", "lancamento", "Lancamento" | R-OD-*, G-06, L-CT-00, R-EX-05, L-EX-04 | EXEC | Não "corrigir" nos testes de texto exato |
| D-18 | Validação não impede avanço | E-11 | EXEC | Usuário pode enviar com erros |
| D-19 | Ordem das linhas de R-OD-04 (e consultas sem ordenação) pode variar | R-OD-04 | ND | Comparar relatórios como conjunto de mensagens |
| D-20 | Limiares e datas fixos no código (2006, 2012, 2013) | R-OD-01…03, R-OD-04 | EXEC | Mudanças regulatórias exigiriam nova versão |
| D-21 | Dependência de rede para obter versão do receptor | E-15, A-07 | EST | Validação offline pode não funcionar |

---

## 7. Cenários de teste para QA

Convenção: **Base** = §0.3. "Δ" = alteração sobre a base. "Golden NN" = caso existente em `analise/testes/casos/NN-.../` (esperado em `esperado.txt`). Onde existe caso em `analise/testes/casos-por-regra/<ID>/` (um caso mínimo por regra observada, com `esperado.txt` = saída do **aplicativo original**), o cenário está marcado "confirmado no original" com o caminho; o dado de entrada do caso mínimo pode diferir do Δ descrito, mas a mensagem é a observada. Cenários sem esse caso são **derivados do catálogo**, não foram executados, e permanecem marcados "prev.". Rótulo físico `F` = nome do arquivo físico; o rótulo é exibido como nas mensagens do catálogo.

### 7.1 Estruturais e de fluxo
| Regra | Entrada (Δ) | Resultado esperado |
|---|---|---|
| E-01 | Remover `001-TST-000001-00_CONTAS.txt` e `_EXTRATO.txt` da pasta | `Arquivo esperado (…) não encontrado.` só para o primeiro ausente na ordem dos arquivos (prev.); nada mais roda |
| E-01 | Renomear com extensão `.TXT` maiúscula | Aceito (case-insensitive) (prev., catálogo) |
| E-02 | AGENCIAS com 0 bytes | `Erro no arquivo: 001-TST-000001-00_AGENCIAS.txt, linha: 0, erro: O arquivo AGENCIAS não pode ser vazio.` (rótulo físico; confirmado no original: `analise/testes/casos-por-regra/E-02/`) |
| E-02 | EXTRATO e ORIGEM_DESTINO com 0 bytes e CONTAS com MOVIMENTACAO=2 (demais da base, titular investigado mantido) | Sem erro de vazio. R-CT-09 só dispara com MOVIMENTACAO=1; R-CT-10 não dispara sem EXTRATO. Não usar MOVIMENTACAO=3: dispararia R-CT-05 e R-CT-08 (prev.) |
| E-03 | Golden 01: AGENCIAS com `001 · 0001 · X` | `Erro no arquivo: 001-TST-000001-00_AGENCIAS.txt, linha: 1, erro: Quantidade de colunas inválida. Esperado: 11 Atual: 3` |
| E-04 | AGENCIAS com NUMERO_BANCO `0011` | `Erro no arquivo: AGENCIAS, linha: 1, erro: Tamanho do campo NUMERO_BANCO excedido. Tamanho máximo permitido: 3` (rótulo lógico). O caso mínimo observou `Erro no arquivo: ORIGEM_DESTINO, linha: 2, erro: Tamanho do campo SITUACAO_IDENTIFICACAO excedido. Tamanho máximo permitido: 1` (prev. para este campo) |
| E-04 | Campo com 3 caracteres + espaços ao redor | Aceito: medido após `trim` (prev.) |
| E-05 | Mesmo arquivo salvo com LF puro em vez de CRLF | Mesmo resultado que a base (prev.) |
| E-05 | Linha em branco no meio de AGENCIAS | E-03 com `Atual: 1` (prev.) |
| E-05b | Golden 02: AGENCIAS iniciando por TAB (BANCO vazio de comprimento zero) | `Quantidade de colunas inválida. Esperado: 11 Atual: 10` |
| E-05b | Golden 04: primeiro campo = um espaço | Sem erro de colunas (11 colunas); segue para L-AG-01 |
| E-06 | Golden 03 estendido (prev.): AGENCIAS com 2 linhas, ambas UF `XX` | Só a 1ª linha produz erro de UF; a 2ª não gera erro semântico. Golden 03 (1 linha) confirma a mensagem |
| E-06 | AGENCIAS linha 1 UF `XX`; linha 2 com 3 colunas | Erro de UF (linha 1) **e** erro de colunas (linha 2) (prev.) |
| E-07 | AGENCIAS com UF `XX`; CONTAS com erro de tipo | Apenas o erro de UF (como o golden `03`); CONTAS e seguintes não são carregados e **nenhuma** mensagem adicional aparece: `A carga foi interrompida devido a erros anteriores.` só vai para o log. Confirmado no original: caso `l07-fail-fast-carga` em `analise/testes/casos-por-regra/E-07/` (origem em `E-07/ORIGEM.md`) |
| E-07 | TITULARES com CPF inválido | Passada TITULARIDADE não roda (prev.) |
| E-08 | Golden 09: CONTAS MOVIMENTACAO=2 com lançamentos | Só o erro de CONTAS; `#CONTEUDO AGENCIAS=true,CONTAS=false`; TITULARES/EXTRATO/OD não avaliados |
| E-08 | Carga com UF `XX` e extrato fora de período | Só erro de carga; nenhuma regra R-* roda (prev.) |
| E-09 | Versão do validador diferente da gravada no atendimento | Diálogo `A versão do sistema (<v>) é diferente da versão do cadastro de atendimento (<v>). Para solucionar este problema, clique no botão Voltar, …` e interrupção; nada no relatório (prev.) |
| E-10 | Golden 13 (caso `001-MPE-…`) | Perfil eleitoral ativo; ver R-OD-E2 |
| E-10 | Mesma AGENCIAS da golden 04 (sem endereço) com caso `001-MPE-…` | L-AG-01 só exige BANCO, AGENCIA, NOME (prev.) |
| E-11 | Qualquer golden com erro (ex.: 03) | Relatório com o erro e botão "Continuar" habilitado (EXEC, nível 3) |
| E-11 | Pasta sem um dos arquivos (E-01) ou versão divergente (E-09) | Mensagem de E-01/E-09 na tela e "Continuar" habilitado mesmo assim, sem validação (prev., código) |
| E-12 | Qualquer golden | Formato `Erro no arquivo: <rótulo>, linha: <n>, erro: <msg>` |
| E-13 | Golden 07 (CPF `00000000000`) | `Erro no arquivo: 001-TST-000001-00_TITULARES.txt, linha: 1, erro: Erro desconhecido. Consulte o log do sistema.` |
| E-14 | Qualquer golden | MD5 e cópia gravados antes de E-02; contagem de linhas gravada durante a carga (73/73 cenários de nível 3). Verificação não coberta por `esperado.txt` |
| E-14 | Golden `03` (carga interrompida após AGENCIAS) | Arquivos seguintes não carregados ficam sem contagem de linhas (prev., código) |
| E-15 | Executar sem rede nem servidor simulado | Dependência: comportamento do app real ND (prev.: não valida) |

### 7.2 Algoritmos
| Regra | Entrada | Resultado |
|---|---|---|
| A-01 | AGENCIAS DATA_ABERTURA `29022001` | `A DATA_ABERTURA_AGENCIA (29022001) é inválida ou o ano é anterior a 1900.` (2001 não é bissexto) |
| A-01 | `29022000` | Aceita (bissexto) |
| A-01 | `01011899`, `13012000`, `1012000` (7 chars) | Inválidas |
| A-02 | TITULARES tipo 1 CPF `52998224725` | Válido (base). Golden 06: `52998224724` inválido |
| A-02 | CPF `11111111111` | Inválido (base com dígitos iguais) (prev.) |
| A-03 | Tipo 2 CNPJ `11222333000181` | Válido (vetor de teste dos arquivos de testes) |
| A-03 | CNPJ alfanumérico válido (ex.: gerado pelo algoritmo oficial) | Aceito |
| A-04 | EXTRATO natureza C, tipo `201` / `223` / `224` / `999` / `101` | 201, 223, 999 válidos; 224 e 101 inválidos (golden 08 confirma 101). Natureza D: 116 inválido, 127 e 999 válidos (prev.) |
| A-05 | EXTRATO VALOR_LANCAMENTO `12abc` | PROV: aceito no `parse`, `Long.valueOf` estrito lança exceção (E-13) ou L-EX-00; repetir em `pt_BR` e `en_US` |
| A-06 | CPF só de zeros | Ver E-13/golden 07 |
| A-07 | Arquivo com MD5 iniciado em zero e receptor 4.0.0 vs 3.9.0 | Hash com 32 caracteres vs sem zeros à esquerda (harness `diferencial_hash.py`) |

### 7.3 AGENCIAS
| Regra | Entrada (Δ) | Resultado |
|---|---|---|
| L-AG-01 | Golden 04: NUMERO_BANCO = um espaço | `Erro no arquivo: AGENCIAS, linha: 1, erro: Um dos campos obrigatórios (NUMERO_BANCO, NUMERO_AGENCIA, NOME_AGENCIA, ENDERECO_LOGRADOURO, ENDERECO_CIDADE, ENDERECO_UF, ENDERECO_PAIS, ENDERECO_CEP, TELEFONE_AGENCIA) não está preenchido.` **mais** `Erro desconhecido` (ver L-AG-04) |
| L-AG-01 | Telefone vazio, caso `001-TST` | Mesma mensagem (prev.). Caso `001-MPE`: sem erro |
| L-AG-02 | DATA_ABERTURA `31042000` | Mensagem de A-01 com `DATA_ABERTURA_AGENCIA` (confirmado no original: `analise/testes/casos-por-regra/L-AG-02/`) |
| L-AG-03 | DATA_FECHAMENTO `00000000` | Mensagem de A-01 com `DATA_FECHAMENTO_AGENCIA` (confirmado no original: `analise/testes/casos-por-regra/L-AG-03/`) |
| L-AG-04 | Golden 04 (NUMERO_BANCO vazio) | Segundo erro: `Erro no arquivo: 001-TST-000001-00_AGENCIAS.txt, linha: 1, erro: Erro desconhecido. Consulte o log do sistema.` |
| L-AG-04 | NUMERO_BANCO `AB1` (preenchido, não numérico) | Apenas `Erro desconhecido…` (rótulo físico); L-AG-01 não dispara (prev.) |
| L-AG-04 | NUMERO_BANCO `002` (atendimento = 1) | `NUMERO_BANCO diferente do informado em atendimento.` (confirmado no original: `analise/testes/casos-por-regra/L-AG-04/`) |
| L-AG-05 | FECHAMENTO `01012005`, ABERTURA vazia | `DATA_FECHAMENTO_AGENCIA informada, mas não há DATA_ABERTURA_AGENCIA.` (confirmado no original: `analise/testes/casos-por-regra/L-AG-05/`) |
| L-AG-05 | ABERTURA `01012010`, FECHAMENTO `01012005` | `DATA_ABERTURA_AGENCIA posterior à DATA_FECHAMENTO_AGENCIA.` (prev.) |
| L-AG-06 | Golden 03: UF `XX` | `Erro no arquivo: AGENCIAS, linha: 1, erro: ENDERECO_UF:XX não é uma sigla válida.` |
| L-AG-06 | UF `df` (minúscula) | Inválida (case-sensitive); `--` válida (prev.) |
| L-AG-07 | NUMERO_AGENCIA `AB12` | `Erro desconhecido…` (E-13) (confirmado no original: `analise/testes/casos-por-regra/L-AG-07/`) |

### 7.4 CONTAS
| Regra | Entrada (Δ) | Resultado |
|---|---|---|
| L-CT-00 | AGENCIA da conta `0002` (inexistente em AGENCIAS) | `Erro no arquivo: 001-TST-000001-00_CONTAS.txt, linha: 1, erro: Agencia não localizada. Banco: <b> Agencia: <a>` (rótulo físico; o caso mínimo mostra o banco sem zeros à esquerda: `Banco: 0 Agencia: 0001`) |
| L-CT-00 | AGENCIA `0001` vs `1` | Equivalentes (zeros à esquerda irrelevantes) (prev.) |
| L-CT-01 | NUMERO_CONTA vazio | `Um dos campos obrigatórios (NUMERO_CONTA, TIPO_CONTA, MOVIMENTACAO_CONTA) não está preenchido.` (confirmado no original: `analise/testes/casos-por-regra/L-CT-01/`) Atenção: no caso mínimo (TIPO_CONTA inválido) também sai L-CT-04. |
| L-CT-02 | DATA_ABERTURA `32012010` | A-01 (confirmado no original: `analise/testes/casos-por-regra/L-CT-02/`) |
| L-CT-03 | ABERTURA `01012020`, ENCERRAMENTO `01012010` | Sem erro de carga (não há regra); possíveis erros de conteúdo adiante (prev.) |
| L-CT-04 | Golden 05: TIPO_CONTA `5` | `Erro no arquivo: CONTAS, linha: 1, erro: Tipo de conta inválido. Valores possíveis: 1, 2, 3 e 4.` |
| L-CT-05 | MOVIMENTACAO `4` | `Movimentação de Conta inválido. Valores possíveis: 1, 2 e 3` (confirmado no original: `analise/testes/casos-por-regra/L-CT-05/`) |

### 7.5 TITULARES
| Regra | Entrada (Δ) | Resultado |
|---|---|---|
| L-PE-00 | VALOR_RENDA `ABC` | `Erro no arquivo: 001-TST-000001-00_TITULARES.txt, linha: 1, erro: Valor numérico inválido no campo Valor Renda: ABC` (rótulo físico; confirmado no original: `analise/testes/casos-por-regra/L-PE-00/`) |
| L-PE-01 | NOME_TITULAR vazio | Mensagem fixa de 5 campos (confirmado no original: `analise/testes/casos-por-regra/L-PE-01/`) |
| L-PE-02 | DATA_ATUALIZACAO_RENDA `99999999` | `Erro no arquivo: EXTRATO, linha: 1, erro: A DATA_ATUALIZACAO_RENDA (99999999) é inválida…` (rótulo EXTRATO; confirmado no original: `analise/testes/casos-por-regra/L-PE-02/`) |
| L-PE-03 | Golden 06: CPF `52998224724` | `Erro no arquivo: TITULARES, linha: 1, erro: O CPF_CNPJ_PESSOA: 52998224724 está inválido.` |
| L-PE-03 | Golden 07: CPF `00000000000` | `Erro desconhecido…` com rótulo físico |
| L-PE-03 | Tipo 2 com CNPJ `11222333000180` | `O CPF_CNPJ_PESSOA: … está inválido.` (prev.) |
| L-PE-04 | TIPO_PESSOA `3` | `Tipo de pessoa inválido. Valores possíveis: 1 e 2.` (confirmado no original: `analise/testes/casos-por-regra/L-PE-04/`) |
| L-TI-00 | NUMERO_CONTA `99999` (inexistente) | (rótulo físico) `Conta não localizada. Banco: … Agencia: … Conta: … TipoConta: …` (prev.) |
| L-TI-00 | TIPO_CONTA vazio | `O tipo de conta não foi informado` (prev.) |
| L-TI-01 | PESSOA_INVESTIGADA vazio | Mensagem fixa de 5 campos (confirmado no original: `analise/testes/casos-por-regra/L-TI-01/`) |
| L-TI-02 | DATA_INICIO `01011850` | (rótulo físico) `A DATA_INICIO_RELACIONAMENTO (01011850) é inválida ou o ano é anterior a 1900.` (confirmado no original: `analise/testes/casos-por-regra/L-TI-02/`) |
| L-TI-03 | DATA_FIM `31022020` | (rótulo físico) `A DATA_FIM_RELACIONAMENTO (31022020) é inválida ou o ano é anterior a 1900.` (confirmado no original: `analise/testes/casos-por-regra/L-TI-03/`) |
| L-TI-04 | TIPO_TITULAR `X` | `Tipo de titular inválido. Valores possíveis: T, 1 até 9, R, L, P e O.` (confirmado no original: `analise/testes/casos-por-regra/L-TI-04/`) |
| L-TI-05 | PESSOA_INVESTIGADA `2` | `Tipo de conta investigada inválido. Valores possíveis: 0 e 1.` (confirmado no original: `analise/testes/casos-por-regra/L-TI-05/`) |
| L-TI-06 | INICIO `01012020`, FIM `01012015` | `A DATA_INICIO_RELACIONAMENTO (…) é posterior a DATA_FIM_RELACIONAMENTO (…).` (confirmado no original: `analise/testes/casos-por-regra/L-TI-06/`) |

### 7.6 EXTRATO
| Regra | Entrada (Δ) | Resultado |
|---|---|---|
| L-EX-00 | VALOR_SALDO `abc` | `Campo VALOR_SALDO não é um número.` (rótulo físico; confirmado no original: `analise/testes/casos-por-regra/L-EX-00/`, PROV) |
| L-EX-01 | NUMERO_CONTA `99999` | `Conta não localizada no arquivo de CONTAS.` (confirmado no original: `analise/testes/casos-por-regra/L-EX-01/`) |
| L-EX-02 | DESCRICAO vazia | Mensagem fixa de 8 campos (confirmado no original: `analise/testes/casos-por-regra/L-EX-02/`) |
| L-EX-03 | DATA_LANCAMENTO `15132020` | A-01 com `DATA_LANCAMENTO` (confirmado no original: `analise/testes/casos-por-regra/L-EX-03/`) |
| L-EX-04 | NATUREZA_LANCAMENTO `X`; vazia | `Natureza de Lancamento inválida. Valores possíveis: C, D e *(asterisco).` (nos dois casos; confirmado no original: `analise/testes/casos-por-regra/L-EX-04/`) |
| L-EX-05 | NATUREZA_SALDO `*` | `Natureza de saldo inválida. Valores possíveis: C e D.` (confirmado no original: `analise/testes/casos-por-regra/L-EX-05/`) |
| L-EX-06 | Golden 08: tipo `101` com natureza C | `Erro no arquivo: EXTRATO, linha: 1, erro: TIPO_LANCAMENTO inválido para NATUREZA_LANCAMENTO='C'. Veja tabela CNAB-240.` |
| L-EX-07 | VALOR_LANCAMENTO `-1` | `VALOR_LANCAMENTO e VALOR_SALDO não podem ser negativos.` (confirmado no original: `analise/testes/casos-por-regra/L-EX-07/`) |

### 7.7 ORIGEM_DESTINO (carga)
| Regra | Entrada (Δ) | Resultado |
|---|---|---|
| L-OD-00 | CODIGO_CHAVE_OD `X1` | `Erro desconhecido…` (rótulo físico; confirmado no original: `analise/testes/casos-por-regra/L-OD-00/`) |
| L-OD-00 | VALOR_TRANSACAO (coluna 3) `ABC` | `Campo VALOR_OPERACAO não é um número.` (rótulo físico; o nome na mensagem é VALOR_OPERACAO, não o do layout; PROV via A-05; derivado do código, prev.) |
| L-OD-01 | Golden 15: TIPO_PESSOA_OD `3`, TIPO_CONTA_OD vazio | 1ª mensagem: `TIPO_CONTA_OD inválido. Valores possíveis: 1, 2, 3 ou 4.` |
| L-OD-01 | TIPO_CONTA_OD `9`, TIPO_PESSOA_OD vazio | **Sem erro** (campo errado testado) (prev., inferência do defeito; o caso mínimo `casos-por-regra/L-OD-01/` confirma só as duas mensagens por TIPO_PESSOA_OD inválido) |
| L-OD-02 | Golden 15 | 2ª mensagem: `TIPO_PESSOA_OD inválido. Valores possíveis: 1 ou 2.` |
| L-OD-03 | SITUACAO_IDENTIFICACAO `1` | `SITUACAO_IDENTIFICACAO inválido. Valor possível: 0.` (confirmado no original: `analise/testes/casos-por-regra/L-OD-03/`) |
| L-OD-04 | NUMERO_BANCO_OD `0011` | Bloqueado antes por E-04 (`Tamanho do campo NUMERO_BANCO_OD excedido…`); L-OD-04 inalcançável |

### 7.8 Conteúdo
| Regra | Entrada (Δ) | Resultado |
|---|---|---|
| R-AG-01 | Golden 12: duas linhas idênticas em AGENCIAS | `Erro no arquivo: AGENCIAS, linha: 2, erro: Agência duplicada: NUMERO_BANCO: 001, NUMERO_AGENCIA: 0001`; `#CONTEUDO AGENCIAS=false` |
| R-CT-01 | NUMERO_CONTA `12345-6` (em CONTAS, TITULARES e EXTRATO coerentes) | `Conta com separador: 12345-6` (confirmado no original: `analise/testes/casos-por-regra/R-CT-01/`) |
| R-CT-02 | Duas linhas de CONTAS com mesma agência, conta e tipo | `Erro no arquivo: null, linha: …, erro: Conta duplicada - Linha: …` (confirmado no original: `analise/testes/casos-por-regra/R-CT-02/`; rótulo documentado em EXEC) Atenção: no caso mínimo a conta sem titular/extrato gera também R-CT-03, R-CT-04 e R-CT-09. |
| R-CT-03 | CONTAS com segunda conta `67890` sem titularidade | `Conta sem registro correspondente em Titulares. NUMERO_BANCO: …` (confirmado no original: `analise/testes/casos-por-regra/R-CT-03/`) |
| R-CT-04 | TITULARES com PESSOA_INVESTIGADA=0 e conta MOVIMENTACAO=1 (investigados.tsv sem investigado) | `Conta sem pessoa investigada. …` (confirmado no original: `analise/testes/casos-por-regra/R-CT-04/`) Atenção: no caso mínimo também dispara R-CT-06. |
| R-CT-05 | Todas as contas com MOVIMENTACAO=3 | `Atendimento sem nenhuma movimentação de conta.` (linha 0; confirmado no original: `analise/testes/casos-por-regra/R-CT-05/`) Atenção: no caso mínimo também dispara R-CT-08 (titular investigado em conta MOVIMENTACAO=3). |
| R-CT-06 | MOVIMENTACAO=1 em conta cujo titular tem PESSOA_INVESTIGADA=0 | `Conta com atributo MOVIMENTACAO_CONTA incorreto. Deve ser 3, pois não é conta de investigado. …` (um por titularidade; confirmado no original: `analise/testes/casos-por-regra/R-CT-06/`) Atenção: no caso mínimo também dispara R-CT-04. |
| R-CT-07 | Dois titulares tipo `T` na mesma conta | `Conta com mais de um Titular tipo T. …` (confirmado no original: `analise/testes/casos-por-regra/R-CT-07/`) |
| R-CT-08 | Titular investigado e MOVIMENTACAO=3 | `… Deve ser 1 ou 2, pois é um investigado. …` (confirmado no original: `analise/testes/casos-por-regra/R-CT-08/`) Atenção: no caso mínimo também dispara R-CT-05. |
| R-CT-09 | MOVIMENTACAO=1 com EXTRATO e OD vazios | `Conta definida com movimentação (MOVIMENTACAO_CONTA = 1), porém não possui lançamentos em EXTRATO. …` (confirmado no original: `analise/testes/casos-por-regra/R-CT-09/`) |
| R-CT-10 | Golden 09: MOVIMENTACAO=2 com lançamento | `Erro no arquivo: CONTAS, linha: 1, erro: Conta definida sem movimentação (MOVIMENTACAO_CONTA = 2), porém possui lançamentos em EXTRATO. NUMERO_BANCO: 001, NUMERO_AGENCIA: 0001, NUMERO_CONTA: 12345, TIPO_CONTA: 1.` |
| R-TI-01 | Titular `T` investigado com TELEFONE vazio | `O titular de conta investigada deve ter os seguintes dados preenchidos: …` (confirmado no original: `analise/testes/casos-por-regra/R-TI-01/`) |
| R-TI-02 | Relacionamento início `01012005` (conta aberta 01/01/2010) | `A DATA_INICIO_RELACIONAMENTO ou DATA_FIM_RELACIONAMENTO está menor que a DATA_ABERTURA_CONTA (…` (confirmado no original: `analise/testes/casos-por-regra/R-TI-02/`) |
| R-TI-03 | Conta encerrada `01012012`; relacionamento início `01012010`, fim `01012015` | `… está maior que a DATA_ENCERRAMENTO_CONTA (…` (confirmado no original: `analise/testes/casos-por-regra/R-TI-03/`) |
| R-TI-04 | investigados.tsv com CPF `11144477735` (rel=1, conta=1) ausente de TITULARES | `O CPF_CNPJ … cadastrado no atendimento como investigado, não foi encontrado no arquivo TITULARES como PESSOA_INVESTIGADA = 1`, linha −1 (confirmado no original: `analise/testes/casos-por-regra/R-TI-04/` sobre os valores; defeito de linha EXEC) |
| R-TI-05 | TITULARES com PESSOA_INVESTIGADA=0 para o CPF do investigado | `… deve possuir o atributo PESSOA_INVESTIGADA = 1` (confirmado no original: `analise/testes/casos-por-regra/R-TI-05/`) |
| R-TI-06 | TITULARES com PESSOA_INVESTIGADA=1 de CPF fora de investigados.tsv | `… informado como PESSOA_INVESTIGADA = 1 não foi cadastrado no atendimento.` (confirmado no original: `analise/testes/casos-por-regra/R-TI-06/`) |
| R-TI-07 | Conta investigada sem titular tipo `T` (TIPO_TITULAR `1`) | `Conta investigada sem Titular do tipo T. …` (confirmado no original: `analise/testes/casos-por-regra/R-TI-07/`) |
| R-EX-01 | investigados.tsv com afastamento 01012021 a 31122021; lançamento 15032020 | `Lançamento em 15/03/2020 fora do período de afastamento (…) do investigado: CPF_CNPJ (…)` (confirmado no original: `analise/testes/casos-por-regra/R-EX-01/`) |
| R-EX-02 | Golden 10: lançamento 15032005 e abertura 01012010 | `Erro no arquivo: EXTRATO, linha: 1, erro: A DATA_LANCAMENTO (15/03/2005) está fora do período de DATA_ABERTURA_CONTA (01/01/2010) e sem DATA_ENCERRAMENTO_CONTA).`; `#CONTEUDO …EXTRATO=false` |
| R-EX-03 | ORIGEM_DESTINO vazio e extrato com lançamento | `Lançamento em EXTRATO sem correspondente em ORIGEM_DESTINO. CODIGO_CHAVE_EXTRATO: 1` (confirmado no original: `analise/testes/casos-por-regra/R-EX-03/`) |
| R-EX-04 | Conta MOVIMENTACAO=3 com lançamento | `No arquivo CONTAS, se o campo MOVIMENTAÇÃO DA CONTA estiver com o parâmetro "3"(conta não investigada) não pode haver nenhum registro para essa conta no arquivo EXTRATO` (confirmado no original: `analise/testes/casos-por-regra/R-EX-04/`; combina com R-CT-06/08) |
| R-EX-05 | Golden 11: OD com VALOR 40000 vs lançamento 50000 | `Erro no arquivo: EXTRATO, linha: 1, erro: O Valor do lancamento em EXTRATO (CODIGO_CHAVE: 1) não é igual ao somatório dos registros correspondentes em ORIGEM_DESTINO.` |
| R-OD-01 | Golden 14: tipo 120, valor 1.000.000, OD sem identificação | `Erro no arquivo: ORIGEM_DESTINO, linha: 1, erro: É OBRIGATÓRIA a identicação do depositante/favorecido de Transferências Interbancária (TED, DOC) nos campos CPF_CNPJ_OD, … NAO-CORRENTISTA.` Fronteira (campanha): 999.999 ou data 31/12/2005 não disparam |
| R-OD-01 | Mesma entrada com AGENCIA_OD `9999`, CONTA_OD `99999999999999999999`, OBS `nao-correntista` | Sem erro (exceção; sem distinção de maiúsculas) (prev.) |
| R-OD-02 | Golden 14, trocando EXTRATO.TIPO_LANCAMENTO para `117` com NATUREZA_LANCAMENTO `D` (para `213` a natureza deve ser `C`, senão L-EX-06 barra na carga); VALOR_LANCAMENTO 1.000.000 e OD com o mesmo valor (soma igual, senão R-EX-05 para antes) | Mensagem de transferências entre contas (confirmado no original: `analise/testes/casos-por-regra/R-OD-02/`) |
| R-OD-03 | Lançamento tipo 123 com NATUREZA_LANCAMENTO `D` (123 é válido só para `D` no CNAB), data 15032020 e EXTRATO.VALOR_LANCAMENTO 1.000.000; OD com VALOR_TRANSACAO igual (soma igual, R-EX-05); NOME_PESSOA_OD vazio | `É obrigatória a identificação do depositante/favorecido de transações em espécie …` (confirmado no original: `analise/testes/casos-por-regra/R-OD-03/`); com OBS `099 - NEGATIVA DE IDENTIFICACAO DO PORTADOR`: sem erro |
| R-OD-04 | Tipo 120 com NATUREZA_LANCAMENTO `D`, data 15032020, VALOR_LANCAMENTO 50000 e OD com a mesma soma (abaixo do limiar de R-OD-01), CPF_CNPJ_OD `12345678900`, TIPO_PESSOA_OD vazio | `O campo CPF_CNPJ_PESSOA_OD com o valor 12345678900 é inválido` (CNPJ **ou** CPF; prev. para estes dados; o caso mínimo observou tipo 2: `…valor 00052998224725 é inválido para TIPO_PESSOA_OD = 2 (Pessoa Jurídica).`) |
| R-OD-E1 | Caso `001-MPE`, lançamento tipo 117 com NATUREZA_LANCAMENTO `D` e VALOR_LANCAMENTO 100; OD com VALOR_TRANSACAO 100 (soma igual) e sem identificação | `É OBRIGATÓRIA a identicação … de transferências no mesmo banco ou transferências interbancárias (TED/DOC) …` (sem limiar; confirmado no original: `analise/testes/casos-por-regra/R-OD-E1/`) |
| R-OD-E2 | Golden 13: tipo 201, valor 50000, CPF_CNPJ_OD vazio | `Erro no arquivo: ORIGEM_DESTINO, linha: 1, erro: É OBRIGATÓRIA a identicação do depositante/favorecido de depósito em cheque e/ou dinheiro no campo CPF_CNPJ_OD.`; mesmo dado em caso `001-TST` **não** dispara R-OD-E2 (confirmado no original: `analise/testes/casos-por-regra/R-OD-E2/`) |

### 7.9 Corretoras (GAB)
Execução real: ver G-DEF. Os cenários "sob remendo" só valem para a reimplementação com `--modo estrito` ou `--defeito-gab false`.
| Regra | Entrada | Resultado |
|---|---|---|
| G-01 | GAB112 vazio | `O arquivo GAB-112 não pode ser vazio.` |
| G-01 | GAB109 com dados, GAB800 vazio | `O arquivo GAB-800 não pode ser vazio quando o arquivo GAB-109 possui dados.` |
| G-01 | GAB800 com dados, GAB109 vazio | `O arquivo GAB-109 não pode ser vazio quando o arquivo GAB-800 possui dados.` |
| G-02 | Linha GAB109 v1 com 126 ou 128 caracteres, ou com TAB | `Certifique-se de completar com espaços os campos que não atingirem o tamanho predeterminado e Não use tab para separar os campos.` |
| G-DEF | Qualquer GAB válido de largura correta (v1 ou v2) | `Erro desconhecido. Consulte o log do sistema.` uma vez por linha, rótulo físico |
| G-03 | (remendo) GAB109 sem CODIGO_DO_CLIENTE | `O campo CODIGO_DO_CLIENTE é obrigatório.`; GAB112 sem DOCUMENTO_DO_CLIENTE: mensagem duplicada |
| G-04 | (remendo) GAB109 DATA_DO_MOVIMENTO `20231332` | Só o 1º erro: `O campo DATA_DO_MOVIMENTO está incorreto. A data deve estar no formato AAAAMMDD e válida.` (nome do campo prev.) |
| G-05 | (remendo) GAB800 VALOR_DO_LANCAMENTO `ABC` | `O dado informado não é um número. …` |
| G-06 | (remendo) GAB112 TIPO_DE_PESSOA `X`; MERCADO `NYSE`; ESTADO `df` | Mensagens F/J, BOVESPA/BMF/SWAP e `A sigla do campo estado está incorreta` |
| G-07 | (remendo, v2) GAB112 DATA_SITUACAO vazia | `Erro desconhecido…` |
| R-G109 | (remendo) código de cliente do GAB109 ausente no GAB112 | `O cliente com o código N não foi informado no arquivo gab112.` |
| R-G800 | idem para GAB800 | idem |
| R-G112-1 | (remendo) todos os investigados com TEVE_RELACIONAMENTO≠1 | `Não foi encontrado relacionamento no arquivo investigado` (linha 0) |
| R-G112-2 | (remendo) DOCUMENTO_DO_CLIENTE fora dos investigados | `O CPF/CNPJ <doc> não teve relacionamento com esta instituição no período do afastamento de sigilo.` |
| R-G112-3 | (remendo) investigado com relacionamento fora do GAB112 | `O investigado com CPF/CNPJ <cpf> não foi encontrado no arquivo cadastral(gab112).` |

### 7.10 Teste de regressão do conjunto
Rodar os 16 golden (`00` a `15`) e comparar com `esperado.txt` (inclui a linha `#CONTEUDO …`). Critério de equivalência da campanha original: mesmo **multiconjunto** de mensagens e mesmo desfecho por arquivo (ordem de R-OD-04 não é contratual).

---

## 8. Matriz regra × arquivo × mensagem

Legenda do rótulo (catálogo §1.3): **F** físico; **L** lógico; **EXTRATO\*** = rótulo trocado; **null** = rótulo ausente. "Msg." = identificador curto da mensagem (texto na §5).

| ID | Etapa | Arquivo de dados | Rótulo | Msg. (curta) | Conf. |
|---|---|---|---|---|---|
| E-01 | Pré-carga | todos | — | Arquivo esperado (…) não encontrado | EST |
| E-02 | Pré-carga | AGENCIAS, CONTAS, TITULARES | F | O arquivo X não pode ser vazio | EXEC |
| E-03 | Carga | 5 arquivos | F | Quantidade de colunas inválida | EXEC |
| E-04 | Carga | 5 arquivos | L | Tamanho do campo … excedido | EXEC |
| E-05 | Carga | 5 arquivos | — | (efeito E-03) | EST |
| E-05b | Carga | 5 arquivos | F | (efeito E-03) | EXEC |
| E-06 | Carga | todos | — | (filtra mensagens) | EXEC |
| E-07 | Carga | todos | — (só log) | A carga foi interrompida… (não chega ao relatório) | EXEC |
| E-08 | Conteúdo | todos | — | (filtra mensagens R-*) | EXEC |
| E-09 | Pré-carga | atendimento | — (diálogo) | A versão do sistema (…) é diferente… | EST |
| E-10 | Perfil | AGENCIAS, TITULARES, ORIGEM_DESTINO | — | (troca de regras) | EXEC |
| E-11 | Final | — | — | (Continuar habilitado) | EXEC |
| E-12 | Relatório | todos | — | Formato da mensagem | EXEC |
| E-13 | Carga | 5 arquivos, GAB | F | Erro desconhecido | EXEC |
| E-14 | Pré-carga (hash, cópia) e carga (contagem de linhas) | 5 arquivos | — | (hash/linhas/cópia) | EXEC |
| E-15 | Pré-carga | — | — | (rede para versão) | EST |
| A-01 | Algoritmo | datas | do chamador | A … é inválida ou ano < 1900 | EXEC |
| A-02 | Algoritmo | CPF | do chamador | (CPF inválido) | EXEC |
| A-03 | Algoritmo | CNPJ | do chamador | (CNPJ inválido) | EXEC |
| A-04 | Algoritmo | EXTRATO | L | (L-EX-06) | EXEC |
| A-05 | Algoritmo | EXTRATO, OD | — | (L-EX-00 / E-13) | PROV |
| A-06 | Algoritmo | TITULARES, GAB112 | F | (E-13) | EXEC |
| A-07 | Algoritmo | 5 arquivos | — | (hash) | EXEC/PROV |
| L-AG-01 | Carga | AGENCIAS | L | Campos obrigatórios | EXEC |
| L-AG-02 | Carga | AGENCIAS | L | Data (A-01) | EXEC |
| L-AG-03 | Carga | AGENCIAS | L | Data (A-01) | EXEC |
| L-AG-04 | Carga | AGENCIAS | L (+F) | NUMERO_BANCO diferente do atendimento | EXEC |
| L-AG-05 | Carga | AGENCIAS | L | Fechamento sem/antes da abertura | EXEC |
| L-AG-06 | Carga | AGENCIAS | L | UF não é sigla válida | EXEC |
| L-AG-07 | Carga | AGENCIAS | F | (E-13) | EXEC |
| L-CT-00 | Carga | CONTAS | F | Agencia não localizada | EXEC |
| L-CT-01 | Carga | CONTAS | L | Campos obrigatórios | EXEC |
| L-CT-02 | Carga | CONTAS | L | Data (A-01) | EXEC |
| L-CT-03 | Carga | CONTAS | L | Data (A-01) | EXEC |
| L-CT-04 | Carga | CONTAS | L | Tipo de conta inválido | EXEC |
| L-CT-05 | Carga | CONTAS | L | Movimentação inválida | EXEC |
| L-PE-00 | Carga | TITULARES | F | Valor numérico inválido (renda) | EXEC |
| L-PE-01 | Carga | TITULARES | L | Campos obrigatórios (lista fixa) | EXEC |
| L-PE-02 | Carga | TITULARES | EXTRATO\* | Data (A-01) | EXEC |
| L-PE-03 | Carga | TITULARES | L / F | CPF_CNPJ_PESSOA inválido / E-13 | EXEC |
| L-PE-04 | Carga | TITULARES | L | Tipo de pessoa inválido | EXEC |
| L-TI-00 | Carga | TITULARES | F | Conta/pessoa não localizada; campo não informado | EXEC |
| L-TI-01 | Carga | TITULARES | L | Campos obrigatórios (lista fixa) | EXEC |
| L-TI-02 | Carga | TITULARES | F | DATA_INICIO inválida | EXEC |
| L-TI-03 | Carga | TITULARES | F | DATA_FIM inválida | EXEC |
| L-TI-04 | Carga | TITULARES | L | Tipo de titular inválido | EXEC |
| L-TI-05 | Carga | TITULARES | L | Tipo de conta investigada inválido | EXEC |
| L-TI-06 | Carga | TITULARES | F (parse) / L (início > fim) | Datas de relacionamento | EXEC |
| L-EX-00 | Carga | EXTRATO | F | Campo … não é um número | PROV |
| L-EX-01 | Carga | EXTRATO | L | Conta não localizada | EXEC |
| L-EX-02 | Carga | EXTRATO | L | Campos obrigatórios (lista fixa) | EXEC |
| L-EX-03 | Carga | EXTRATO | L | Data (A-01) | EXEC |
| L-EX-04 | Carga | EXTRATO | L | Natureza de Lancamento inválida | EXEC |
| L-EX-05 | Carga | EXTRATO | L | Natureza de saldo inválida | EXEC |
| L-EX-06 | Carga | EXTRATO | L | TIPO_LANCAMENTO × CNAB | EXEC |
| L-EX-07 | Carga | EXTRATO | L | Valores negativos | EXEC |
| L-OD-00 | Carga | ORIGEM_DESTINO | F | (E-13) / Campo … não é um número | EXEC (CODIGO_CHAVE_OD ⇒ E-13) / PROV (demais campos, A-05) |
| L-OD-01 | Carga | ORIGEM_DESTINO | L | TIPO_CONTA_OD inválido | EXEC |
| L-OD-02 | Carga | ORIGEM_DESTINO | L | TIPO_PESSOA_OD inválido | EXEC |
| L-OD-03 | Carga | ORIGEM_DESTINO | L | SITUACAO_IDENTIFICACAO inválido | EXEC |
| L-OD-04 | Carga | ORIGEM_DESTINO | L | Número Banco inválido | INALC |
| R-AG-01 | Conteúdo | AGENCIAS | L | Agência duplicada | EXEC |
| R-CT-01 | Conteúdo | CONTAS | L | Conta com separador | EXEC |
| R-CT-02 | Conteúdo | CONTAS | null | Conta duplicada | EXEC |
| R-CT-03 | Conteúdo | CONTAS × TITULARES | L | Conta sem registro em Titulares | EXEC |
| R-CT-04 | Conteúdo | CONTAS × TITULARES | L | Conta sem pessoa investigada | EXEC |
| R-CT-05 | Conteúdo | CONTAS | L (linha 0) | Atendimento sem movimentação | EXEC |
| R-CT-06 | Conteúdo | CONTAS × TITULARES | L | MOVIMENTACAO deve ser 3 | EXEC |
| R-CT-07 | Conteúdo | TITULARES | L | Mais de um Titular T | EXEC |
| R-CT-08 | Conteúdo | CONTAS × TITULARES | L | MOVIMENTACAO deve ser 1 ou 2 | EXEC |
| R-CT-09 | Conteúdo | CONTAS × EXTRATO | L | Movimentação 1 sem lançamentos | EXEC |
| R-CT-10 | Conteúdo | CONTAS × EXTRATO | L | Movimentação 2 com lançamentos | EXEC |
| R-TI-01 | Conteúdo | TITULARES | L | Dados obrigatórios do titular investigado | EXEC |
| R-TI-02 | Conteúdo | TITULARES × CONTAS | L | Relacionamento < abertura | EXEC |
| R-TI-03 | Conteúdo | TITULARES × CONTAS | L | Relacionamento > encerramento | EXEC |
| R-TI-04 | Conteúdo | TITULARES × investigados | L (linha −1) | Investigado não encontrado | EXEC |
| R-TI-05 | Conteúdo | TITULARES × investigados | L | Deve ter PESSOA_INVESTIGADA = 1 | EXEC |
| R-TI-06 | Conteúdo | TITULARES × investigados | L | PESSOA_INVESTIGADA = 1 não cadastrada | EXEC |
| R-TI-07 | Conteúdo | TITULARES | L | Conta investigada sem Titular T | EXEC |
| R-EX-01 | Conteúdo | EXTRATO × investigados | L | Fora do período de afastamento | EXEC |
| R-EX-02 | Conteúdo | EXTRATO × CONTAS | L | Fora do período da conta | EXEC |
| R-EX-03 | Conteúdo | EXTRATO × OD | L | Sem correspondente em OD | EXEC |
| R-EX-04 | Conteúdo | EXTRATO × CONTAS | L | Conta não investigada com extrato | EXEC |
| R-EX-05 | Conteúdo | EXTRATO × OD | L | Valor ≠ somatório OD | EXEC |
| R-OD-01 | Conteúdo | ORIGEM_DESTINO × EXTRATO | L | Identificação TED/DOC | EXEC |
| R-OD-02 | Conteúdo | ORIGEM_DESTINO × EXTRATO | L | Identificação transf. entre contas | EXEC |
| R-OD-03 | Conteúdo | ORIGEM_DESTINO × EXTRATO | L | Identificação em espécie | EXEC |
| R-OD-04 | Conteúdo | ORIGEM_DESTINO × EXTRATO | L | CPF_CNPJ_PESSOA_OD inválido | EXEC / ordem ND |
| R-OD-E1 | Conteúdo | ORIGEM_DESTINO × EXTRATO | L | Identificação eleitoral transferências | EXEC |
| R-OD-E2 | Conteúdo | ORIGEM_DESTINO × EXTRATO | L | Depósito eleitoral sem CPF_CNPJ_OD | EXEC |
| G-01 | Pré-carga GAB | GAB109/112/800 | F | O arquivo GAB-… não pode ser vazio | EXEC |
| G-02 | Carga GAB | GAB109/112/800 | F | Certifique-se de completar com espaços… | EXEC |
| G-DEF | Carga GAB | GAB109/112/800 | F | Erro desconhecido (por linha) | EXEC |
| G-03 | Carga GAB | GAB109/112/800 | Gab… | O campo … é obrigatório | EXEC sob remendo |
| G-04 | Carga GAB | GAB109/112/800 | Gab… | Data AAAAMMDD | EXEC sob remendo |
| G-05 | Carga GAB | GAB109/112/800 | Gab… | Não é um número | EXEC sob remendo |
| G-06 | Carga GAB | GAB112 (e naturezas GAB109/800) | Gab… | Domínios | EXEC sob remendo |
| G-07 | Carga GAB v2 | GAB109, GAB112 | F | (E-13) | EXEC sob remendo |
| R-G109 | Conteúdo GAB | GAB109 × GAB112 | GAB109 | Cliente não informado no gab112 | EXEC sob remendo |
| R-G800 | Conteúdo GAB | GAB800 × GAB112 | GAB800 | Cliente não informado no gab112 | EXEC sob remendo |
| R-G112-1 | Conteúdo GAB | investigados | Investigado | Sem relacionamento | EXEC sob remendo |
| R-G112-2 | Conteúdo GAB | GAB112 × investigados | GAB112 | CPF/CNPJ sem relacionamento | EXEC sob remendo |
| R-G112-3 | Conteúdo GAB | investigados × GAB112 | GAB112 | Investigado não encontrado no gab112 | EXEC sob remendo |

Contagem: E 16 + A 7 + L 38 + R 29 + G 13 = 103.

---

## 9. Glossário

| Termo | Significado |
|---|---|
| Atendimento | Cadastro do afastamento de sigilo (banco, número do caso, versão, hash dos arquivos, investigados) feito fora dos arquivos TXT |
| Carga | Fase em que os TXT são lidos linha a linha e gravados no banco HSQLDB em arquivo do caso (regras E-03…E-07 e L-*) |
| Conteúdo | Fase de consultas SQL cruzando os arquivos (R-*) |
| COMPE | Código de compensação do banco (NUMERO_BANCO, 3 dígitos) |
| CNAB-240 | Tabela de códigos de lançamento aceitos conforme a natureza (A-04) |
| Fail-fast | Interromper etapas seguintes ao primeiro arquivo/erro (E-06, E-07, E-08) |
| GAB (109/112/800) | Arquivos de corretoras em largura fixa: lançamentos, cadastro de clientes, saldos/posições (nomes dos arquivos; semântica detalhada: intenção não documentada neste material) |
| Golden | Caso sintético de referência com saída esperada (`analise/testes/casos/`) |
| Investigado | Pessoa alvo do afastamento (registro do atendimento, com TEVE_RELACIONAMENTO, TEVE_CONTA e período) |
| Linha 0 / linha −1 | Erros sem linha de arquivo (arquivo vazio, regra global) / marcador do R-TI-04 |
| MOVIMENTACAO_CONTA | 1, 2 ou 3: conforme as regras R-CT-06/08/09/10 e R-EX-04, 1 = conta de investigado com extrato, 2 = conta de investigado sem extrato, 3 = conta não investigada (a definição formal no layout oficial não consta do material: intenção não documentada) |
| PESSOA / TITULARIDADE | As duas passadas sobre TITULARES (cadastro da pessoa; vínculo pessoa-conta) |
| Perfil eleitoral | `NUMERO_CASO` iniciado por `001-MPE` ou `038-TSE` (E-10) |
| Remendo em memória | Preenchimento por reflexão do campo nulo dos carregadores GAB, usado só em teste para exercitar G-03…G-07 |
| Rótulo físico / lógico | Nome do arquivo com extensão / nome da tabela (§0.2) |
| TED/DOC | Transferências interbancárias (R-OD-01) |
| E-xx, A-xx, L-xx, R-xx, G-xx | Estrutural/fluxo, Algoritmo, Carga por linha, Regra de conteúdo, Corretoras |

---

## 10. Lacunas e limites de confiança

| Cód. | Lacuna | Implicação para o QA |
|---|---|---|
| LAC-01 | Unidade de VALOR_* (centavos?) e sentido de negócio dos limiares (1.000.000; 5.000.000; 10.000; 10.000.000) | Não afirmar valores em reais |
| LAC-02 | Locale `en_US` **testado** em 2.140 cenários (0 divergências original × reimplementação; saídas do original idênticas às de `pt_BR` em 700 cenários comparados). A-05 e L-EX-00 seguem PROV porque dependem do locale da máquina | Nenhuma ação obrigatória; testar outros locales se houver demanda |
| LAC-03 | Verificação estática: o instalador (`LicenseText` vazio) e os JARs do MPF (`simba-validador.jar`, `spea-client-2.4.4.jar`) **não contêm EULA, termos nem arquivo de licença** (só avisos de copyright do JRE e de bibliotecas abertas). A questão jurídica (D11) **continua aberta**: requer consulta ao MPF/jurídico | Uso apenas com dados sintéticos |
| LAC-04 | Se o servidor aceita envio com erros | E-11 informa só o comportamento local |
| LAC-05 | Ordem das colunas e nomes de campo não tabulados nos relatórios | A §3 usa `Schemas.java`, que reproduz `getCamposArquivo()` de cada carregador (não há enums `Campos*` bancários) |
| LAC-06 | Telas de atendimento/investigados, relatório PDF, empacotamento, transmissão lidos por amostragem | Origem de ATENDIMENTO/INVESTIGADO não documentada |
| LAC-07 | Papel de `simba-validador.exe` e 2 entradas anômalas do instalador | Fora do escopo funcional |
| LAC-08 | Acentos/BOM, dados reais e volumes > 250.000 linhas só sintéticos | Testar codificação separadamente |
| LAC-09 | **Fechada** para as regras com mensagem própria: 71 regras observadas no original (`docs/MAPA_COBERTURA_REGRAS.md`, 2.940 cenários, 0 divergências reais). Sem observação direta: E-01, E-05, E-09, E-15 (estáticas/GUI) e L-OD-04 (inalcançável). Cobertura de linhas do original: 82,9% | Restam EST apenas E-01, E-05, E-09, E-15 |
| LAC-10 | `ValidarGab112SimGab800NaoRule` e 6 regras abstratas lidas parcialmente | Código não injetado: sem efeito |

Limites adicionais desta documentação:
- Cenários marcados "prev." são **derivados do catálogo** e não foram executados; têm resultado confirmado os 16 golden e os casos de `analise/testes/casos-por-regra/` (71 regras observadas no original em 2.940 cenários, 0 divergências reais; `docs/MAPA_COBERTURA_REGRAS.md`).
- Confiança no catálogo v1.2: EXEC 86, EXEC sob remendo 10, EST 4 (E-01, E-05, E-09, E-15), PROV 2 (A-05, L-EX-00) e INALC 1 (L-OD-04); total 103. L-OD-00 conta como EXEC (CODIGO_CHAVE_OD ⇒ E-13) com PROV nos demais campos (A-05).
- Mensagens longas aparecem truncadas ("…") onde o catálogo só dá o prefixo; o texto integral está em `analise/04-regras-validacao.md` e no código.
- Efeitos de NULL, `NOT IN`, `REPEAT` e `UCASE` do HSQLDB são PROV quanto a detalhes.

---

## 11. Registro para o orquestrador

Nenhuma regra foi criada, removida ou renumerada. Itens do registro original e tratamento após as revisões independentes (`docs/REVISAO_DOCUMENTACAO.md` e `docs/REVISAO_DOCUMENTACAO_2.md`; catálogo v1.2):

1. **Nomes de campos divergentes entre catálogo §5.0 e o original.** Tratado (P-11 no catálogo; P-12 aqui). A fonte real é `getCamposArquivo()` de cada carregador (não há enums `Campos*` bancários); o nome exibido na mensagem E-04 usa os nomes completos (`NUMERO_BANCO`, `CPF_CNPJ_TITULAR`, `NOME_TITULAR`, `CODIGO_CHAVE_EXTRATO`, `NUMERO_DOCUMENTO_TRANSACAO`) e `OBSERVACÃO` **com til** (erro de digitação do original). O registro anterior, que dizia que as mensagens usam `OBSERVACAO`, estava errado e foi retirado.
2. **Textos de E-04 e E-09.** Tratado (P-31, P-25): o texto de E-04 já estava no catálogo; o de E-09 foi incorporado ao catálogo v1.1 e à §5.1 e §7.1.
3. **Cobertura golden ausente para a maioria das regras.** Tratado: 71 regras observadas no original, com caso mínimo em `analise/testes/casos-por-regra/` (LAC-09 fechada para regras com mensagem própria; restam sem observação E-01, E-05, E-09, E-15, L-OD-04).
4. **E-05b: precisão do enunciado.** Tratado (P-22 no catálogo v1.1, que já traz a redação correta).
5. **Significado de MOVIMENTACAO_CONTA.** Mantido: deduzido das regras R-CT-06/08/09/10 e R-EX-04, confirmado pelo revisor como não documentado.
6. **L-AG-04 e E-13.** Tratado (P-14): dois erros só com NUMERO_BANCO vazio; preenchido não numérico gera um erro; a exceção interrompe L-AG-05 e L-AG-06.
7. **L-PE-02 rotulado `EXTRATO`.** Mantido: sem golden, só testes do modelo e da reimplementação.
8. **Maiúscula em "Não use tab" e dois espaços em G-04/G-05.** Confirmado pelo revisor; mantido.
9. **Código morto.** Sem objeção; a contagem correta de sobrecargas é 6 (P-21 do catálogo).

Correções aplicadas por P-nn nesta versão: P-01 (E-07 só em log), P-03 e P-07 (rótulos conforme catálogo §1.3 em §0.2, §5, §7 e §8), P-06 (limiares de OD sobre EXTRATO.VALOR_LANCAMENTO; R-OD-E1 sem limiar), P-08 (HSQLDB em arquivo), P-10 (hash/cópia antes de E-02; contagem de linhas na carga), P-12 (fonte `getCamposArquivo()`, `OBSERVACÃO`), P-14, P-15 (D-16 como EST), P-16 (cenário de E-02 com MOVIMENTACAO=2), P-30 (L-OD-00), P-31, P-32 (E-11 após retornos antecipados de E-01/E-09).

Correções da 2ª revisão (Q-nn de responsável funcional): Q-01 (nomes `VALOR_OPERACAO` e `NUMERO_AGENCIA` na mensagem `Campo <NOME> não é um número.` de OD, §5.7 e §7.7), Q-02 (diagrama §4.1: hash e cópia antes de E-02; contagem de linhas na carga), Q-05 (cenários de R-OD-02/03/04/E1 com natureza CNAB e soma do OD igual ao VALOR_LANCAMENTO), Q-06 (6ª mensagem de L-TI-00 na §5.5), Q-08 (G-01 com rótulo físico em §0.2 e §8), Q-15 (qualificador PROV em L-OD-00).
Q-14 (diagrama mermaid) é de responsabilidade do documento técnico e não se aplica a este documento.

Sincronização com o catálogo v1.2: 49 regras promovidas de EST a EXEC (a lista recebida tinha 48 IDs mais o caso especial L-OD-00; o catálogo registra 49 promoções), conferidas na §5 e na §8. Totais: EXEC 86, remendo 10, EST 4, PROV 2, INALC 1. Cenários da §7 com caso em `analise/testes/casos-por-regra/<ID>/` passaram a "confirmado no original"; permanecem "prev." os cenários de nuance sem caso (ex.: L-AG-04 com `AB1`, L-AG-06 com `df`, L-CT-00 `0001` × `1`, L-OD-01 com TIPO_CONTA_OD `9`).
Observação para o orquestrador: nos casos mínimos, várias regras de CONTAS disparam em conjunto (R-CT-02 com R-CT-03/04/09; R-CT-04 com R-CT-06; R-CT-05 com R-CT-08), e o caso de L-CT-01 traz também L-CT-04; a §7 registra essas coocorrências.
