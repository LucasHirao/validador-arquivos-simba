# 05 — Testes de caracterização (Fase 5)

## Estado
- **Modelo de referência** independente: `testes/validador_ref.py` (Python 3, ~500 linhas). Escrito a partir da leitura do código decompilado; **não importa nem executa nada do aplicativo**.
- **Suíte**: `testes/test_caracterizacao.py` — **48 testes, todos passando** (`cd analise/testes && python3 -m unittest -v test_caracterizacao`).
- **Casos sintéticos em arquivo**: `testes/gerar_casos.py` gera `testes/casos/<cenário>/` (16 cenários: arquivos `001-TST-000001-00_<TIPO>.txt` em ISO-8859-1/CRLF + `esperado.json`). Todos os dados são fictícios (CPF `52998224725` e CNPJ `11222333000181` são vetores públicos de teste; nomes/endereços inventados).
- **Execução diferencial NÍVEL 1: REALIZADA** (você aprovou). `testes/diferencial/diferencial_nivel1.py` + `Harness.java`: chama funções puras do JAR original (`Util.tabSeparatedStringToArray`, `Util.verificaData`, `CpfCnpjUtil`, `CnabRule`, `StringUtil`) em `bwrap --unshare-net` (sem rede, sistema de arquivos somente leitura, `/tmp` efêmero). **20.103 vetores sintéticos, 0 divergências** (split 1.508; data 3.261; CPF 6.006; CNPJ 6.005; CNAB 3.306; utilitários 17). Hipóteses H1 e H2 → **CONFIRMADO por execução**. Observação do isolamento: o logback do artefato tentou criar arquivo de log; o FS somente leitura bloqueou.
- **Execução diferencial NÍVEL 2: REALIZADA.** `testes/diferencial/Nivel2.java` + `diferencial_nivel2.py` sobem o contexto Spring REAL do aplicativo (`validador-spring.xml`), criam o HSQLDB do caso e invocam `Carregar`, `ControladorValidador.validarConteudo` e `ErrorReportCmd` — o mesmo laço de `frmSelecionaDiretorio` sem a interface gráfica, hash e cópia — em `bwrap --unshare-net`, FS somente leitura, diretório de trabalho descartável. Resultado (original × Java `compat` × modelo Python): **16 cenários + 1.500 aleatórios + 800 dirigidos às regras com limiares = 2.316 cenários, 0 divergências reais.** Em 27-37 dos dirigidos as mesmas mensagens aparecem em **ordem diferente** apenas em R-OD-04 (SQL sem `ORDER BY`; a ordem depende do plano do HSQLDB e não é contrato) — critério de equivalência: mesmo multiconjunto de mensagens e mesmo desfecho por arquivo. 96 tipos distintos de mensagem exercitados.
- **Hash MD5** (`diferencial_hash.py`, 401 arquivos × receptor 4.0.0 / 3.9.0 / 1.0.0): o original usa `%032x` só com receptor ≥ 4 e `BigInteger.toString(16)` (sem zeros à esquerda) nos demais — **CONFIRMADO**, 0 divergências (28 arquivos com MD5 iniciado em zero).
- **Fluxo GAB (corretoras)** (`Nivel2Gab.java`, `diferencial_gab.py`, `fuzz_gab.py`): (A) o original **com o defeito** × Java: 3.000 cenários idênticos; (B) o original com um remendo apenas em memória (preenche por reflexão o campo nulo `camposArquivo`, sem alterar nenhum arquivo) × Java com a lógica pretendida: 3.000 cenários idênticos, 55 tipos de mensagem. Achado: o fluxo GAB do 6.2.6 não aprova nenhum arquivo não vazio (G-DEF, 04 §G).
- **Nível 3 (TELA REAL): REALIZADO.** `Nivel3.java` + `diferencial_gui.py` sobem a janela real `frmSelecionaDiretorio` na sessão gráfica do usuário (DISPLAY/XAUTHORITY herdados), dentro de `bwrap --unshare-net` (a lista de órgãos que o programa buscaria por HTTP é injetada em memória nos beans `Controlador`, então nenhuma conexão é aberta), aciona `iniciarValidacao(File)` (o `JFileChooser` é modal; este é o método que ele chama), espera o `SwingWorker`, lê texto, ícones e botões da tela, tira captura (`gui.png`), e consulta o hash/linhas gravados no atendimento e os arquivos copiados. **73 cenários** (16 manuais, 16 dirigidos, 20 dirigidos a limiares, 20 aleatórios, +1): mensagens e ícones de conteúdo idênticos ao Java (1 só de ordem), **hash MD5 gravado 73/73**, **contagem de linhas 73/73**, **cópia dos arquivos 73/73**, 0 falhas, 0 diálogos inesperados.
- **Cobertura de código do original (JaCoCo 0.8.14, SHA-1 conferido)** durante os cenários: das classes `rules`+`carga`+`validacao`, **63,0% → 82,9% das linhas** após cenários dirigidos. O restante é código morto (`InvestigadoLocalizerCmd`, `ValidarGab112Sim…Nao`, sobrecargas `validar(ExtratoOrigemDestino)`), L-OD-04 (inalcançável), regra só de tela (`ValidarCasoPossui…`), `equals/hashCode` de chaves de cache, log e `catch(SQLException)`. **A cobertura achou 2 divergências reais que 2.316 cenários aleatórios não pegaram:** R-CT-02 sai com `Erro no arquivo: null` (a regra herda de `AbstractRule`) e R-TI-04 usa linha −1; ambas corrigidas no modelo, no Java e no catálogo. Também achou um buraco do meu gerador GAB v2 (`DATA_SITUACAO`), corrigido: GAB v2 completo agora é exercitado (1.500 cenários × 2 modos, 0 divergências).
- **Volume:** cenário com 1.200 linhas (lote de 500) e com **250.005 linhas** (commit periódico a cada 250.000) idênticos ao original. A cobertura de volume expôs que a minha primeira implementação checava PK e buscava conta por varredura linear; passou a usar índices.
- **Teste cruzado modelo × implementação Java** (`testes/fuzz_cruzado.py`): 2.100 cenários aleatórios (3 sementes × 700) — **0 divergências** entre o modelo Python e o Java em modo `compat`. Atenção: ambos foram escritos por mim a partir da mesma leitura; isso mede consistência interna, **não** fidelidade ao original.

## O que a suíte demonstra (e o que não)
| Grupo | Testes | O que sustenta | Limite |
|---|---|---|---|
| Primitivas Java | 4 testes: `split("(?=\t)")`, `verificaData`, `removerZerosEsquerda`, `completarZeros` | Valores esperados do `split` conferidos com **sonda Java independente** (`SplitProbe.java`, JDK 25, código nosso) | `verificaData` e demais são port; falta conferir contra a classe original |
| CPF/CNPJ | 5 testes: vetores conhecidos + **6.000 vetores aleatórios** comparados com algoritmo oficial escrito separadamente (incl. CNPJ alfanumérico) | O algoritmo recuperado (A-02/A-03) coincide com o oficial | Prova equivalência *do port*, não do binário |
| CNAB | 3 testes: fronteiras 100/101/116/127/128/200/201/223/224/999 | A-04 | idem |
| Carga estrutural | 6 testes: colunas, 1º campo vazio, tamanho máx., **erro global (E-06)**, banco vazio ⇒ 2 erros | E-03/04/05b/06 e L-AG-04 | idem |
| Carga por arquivo | 16 testes (AGENCIAS, CONTAS, EXTRATO, TITULARES, ORIGEM_DESTINO, perfil eleitoral) | L-* | idem |
| Regras de conteúdo | 14 testes (R-AG-01, R-CT-01/05/09/10, R-EX-02/03/05, R-OD-01/04/E2, limiares) | R-* | NULL/`NOT IN`/`REPEAT`/`UCASE` do HSQLDB são **modelados**, não executados |

## Categorias de borda cobertas
Campos vazios; fora do formato; datas inválidas (bissexto, mês 13, ano < 1900); arquivo incompleto/colunas faltando; duplicados (agência); inconsistência entre registros (conta ↔ extrato ↔ OD); perfil eleitoral vs. não eleitoral; limiares de valor/data (999.999 vs 1.000.000; 31/12/2005 vs 01/01/2006); letras em CNPJ.
**Não cobertas:** codificação de caracteres (acentos em ISO-8859-1 vs UTF-8/BOM); arquivos grandes (> 250.000 linhas dispara `commit` periódico); NUMERO_BANCO_OD com 14 dígitos (L-OD-04); R-EX-01; fluxo GAB; unidade de valor.

## Hipóteses a confirmar por execução diferencial (em ordem de ganho/risco)
| # | Hipótese | Como refutar |
|---|---|---|
| H1 ✔ | E-05b: `"\tb\tc"` produz 2 colunas — **CONFIRMADO (nível 1)** | `Util.tabSeparatedStringToArray` |
| H2 ✔ | A-01 `verificaData`, A-02/A-03 CPF/CNPJ, A-04 CNAB, A-06 — **CONFIRMADO (nível 1)** | funções puras do JAR |
| H3 ✔ | E-06: só o 1º erro semântico por arquivo — **CONFIRMADO (nível 2)** | `casos/03-uf-invalida` e 1.500 aleatórios |
| H4 ✔ | L-AG-04: `NUMERO_BANCO` vazio ⇒ 2 erros | `casos/04-banco-vazio` |
| H5 ✔ | L-PE-03: CPF só de zeros ⇒ "Erro desconhecido" | `casos/07-cpf-so-zeros` |
| H6 ✔ | L-OD-01/02: TIPO_PESSOA_OD inválido ⇒ 2 mensagens | `casos/15-od-tipo-pessoa-invalido` |
| H7 ✔ | R-* com HSQLDB real — **CONFIRMADO (nível 2)**, exceto ordem das linhas (não contratual) | 2.316 cenários |
| H8 | A-05 `isNumber` leniente depende de locale — **parcial:** coberto em `pt_BR` (0 divergências); `en_US` não testado | rodar o nível 2 com `-Duser.language=en -Duser.country=US` |

## Execução diferencial pendente — pré-condições (aguardam sua autorização)
1. **Nível 1 (menor risco):** harness Java mínimo, *headless*, que carrega apenas classes puras (`Util`, `CpfCnpjUtil`, `DigitoVerificador`, `CnabRule`, `StringUtil`) do JAR e imprime resultados para os vetores H1/H2. Não toca rede, GUI nem banco. Ainda assim é **execução de código do artefato original** (restrição 12).
2. **Nível 2:** contexto Spring + HSQLDB em memória invocando `Carregar`/`ValidarAtendimento` sem a UI. Exige simular `Context`/`Configurador`; maior chance de acionar `Conexao`/`TestaConexao` — executar **sem rede** (`unshare -n`/VM isolada).
3. **Nível 3:** aplicativo completo (GUI) em VM Linux/Windows isolada, sem rede e sem dados reais, usando `testes/casos/`.
Em todos: ambiente descartável, sem rede, só dados de `testes/casos/`, nunca contra o módulo de transmissão.
