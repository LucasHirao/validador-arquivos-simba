# Validador independente (estudo)

Reimplementação **de estudo** das regras de validação do Validador SIMBA 6.2.6, recuperadas por análise estática (`../analise/04-regras-validacao.md`).
**Não é o validador oficial e não substitui o aceite do MPF.** Termos de uso/licença do original **não foram verificados** (D11). Use apenas com dados sintéticos ou mediante autorização.

## Build e testes
```
cd validador-independente
mvn test            # 31 testes JUnit (inclui os 16 cenários golden de ../analise/testes/casos)
mvn package         # gera target/validador-independente-0.2.0.jar
```
## Uso
```
java -jar target/validador-independente-0.2.0.jar --pasta DIR --caso 001-XXX-0000.2.00 [--banco 1]
     [--modo compat|estrito] [--investigados investigados.tsv] [--saida texto|json|csv]
```
- `DIR` deve conter `<caso>_{AGENCIAS,CONTAS,TITULARES,EXTRATO,ORIGEM_DESTINO}.txt` (TAB, ISO-8859-1).
- `--banco`: NUMERO_BANCO do atendimento (L-AG-04). `--caso` começando por `001-MPE`/`038-TSE` ativa o perfil eleitoral.
- `investigados.tsv` (TAB): `TIPO_PESSOA, CPF_CNPJ, NOME, TEVE_RELACIONAMENTO, TEVE_CONTA, INICIO_AFASTAMENTO(ddMMyyyy), FIM_AFASTAMENTO(ddMMyyyy)`.
- Saída `texto` imita o original (`Erro no arquivo: X, linha: n, erro: msg`); `json`/`csv` incluem o ID da regra. Código de saída: 0 sem erros, 1 com violações, 2 uso incorreto.
- `--limiares ARQ` troca os limiares/códigos das regras de ORIGEM_DESTINO (modelo: `limiares-6.2.6.properties`, idêntico ao padrão).
- `--tipo corretora --layout 1|2` valida GAB109/112/800 (largura fixa). COMPAT reproduz o defeito do original (todo arquivo GAB não vazio falha); `--modo estrito` ou `--defeito-gab false` aplica a lógica pretendida.
- `--lote DIR` roda todas as subpastas e grava `saida-<modo>.txt` (usado pelo teste cruzado).

## Modos
| | COMPAT | ESTRITO |
|---|---|---|
| Linhas após o 1º erro | só validação estrutural (E-06) | validação completa de todas |
| 1º campo vazio da linha | descartado (coluna a menos) | preservado |
| `NUMERO_BANCO` vazio | 2 erros (um "Erro desconhecido") | 1 erro |
| Erro de data de renda | rotulado `EXTRATO` | rotulado `TITULARES` |
| CPF só de zeros | "Erro desconhecido" | "CPF inválido" |
| `TIPO_CONTA_OD` | valida a coluna errada | valida 1-4 |
Use COMPAT para prever o resultado do oficial; ESTRITO para um relatório mais útil (pode divergir do aceite oficial).

## Teste cruzado e diferencial
`../analise/testes/fuzz_cruzado.py` (Java × modelo Python); `../analise/testes/diferencial/` (nível 1 funções puras, nível 2 pipeline real em HSQLDB, hash, GAB) — todos contra o JAR original em `bwrap --unshare-net`, inclusive a **tela real** (`diferencial_gui.py`, abre a janela na sessão gráfica). Resultado: 0 divergências reais (05-testes.md). Cobertura de código do original medida com JaCoCo para orientar os cenários.
