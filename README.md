# Validador SIMBA — engenharia reversa de estudo

Estudo técnico do **Validador Bancário SIMBA 6.2.6** (Ministério Público Federal): identificação da tecnologia, recuperação das regras de validação por análise estática, verificação por execução diferencial e uma reimplementação independente de estudo.

> **Aviso.** Este repositório **não contém** o instalador nem os binários do MPF, nem a saída literal do decompilador (ver `.gitignore`). Termos de uso/licença do software original **não foram verificados juridicamente** (o instalador e os JARs do MPF não trazem EULA). A implementação é de estudo, **não é o validador oficial** e não substitui o aceite do MPF. Use apenas dados sintéticos.

## Resultado em uma página
- Instalador Inno Setup 6.5.0 (Delphi) sem criptografia; aplicativo **Java 25** (Swing + Spring + HSQLDB) com JRE embutido.
- **103 regras catalogadas** (`docs/CATALOGO_REGRAS.md`), incluindo o algoritmo de CPF/CNPJ **alfanumérico**. 86 confirmadas por execução do original, 10 sob remendo em memória (GAB), 4 só estáticas, 2 prováveis, 1 inalcançável.
- Achados notáveis: no 6.2.6 o fluxo de **corretoras (GAB) não aprova nenhum arquivo não vazio** (NPE em `validarCampos`); o botão "Continuar" fica habilitado mesmo com erros; erro global bloqueia a validação semântica das linhas seguintes; `Erro no arquivo: null` em R-CT-02; a mensagem "A carga foi interrompida…" nunca chega ao relatório.
- Verificação contra o aplicativo original, em sandbox sem rede (`bwrap --unshare-net`): funções puras (20.103 vetores), pipeline real em HSQLDB (2.940 cenários, 71 regras observadas), tela real (73 cenários), hash MD5, GAB (4.500 × 2 modos), locale `en_US` (2.140 cenários): **0 divergências reais**.

## Por onde começar (guia de leitura)
| Se você é… | Leia nesta ordem |
|---|---|
| **Produto / QA** | 1. `docs/DOCUMENTACAO_FUNCIONAL.md` (o que o sistema faz, mensagens, cenários de teste) → 2. `docs/CATALOGO_REGRAS.md` (consulta por ID de regra) → 3. `analise/testes/casos-por-regra/<ID>/` (entrada e saída real de cada regra) |
| **Desenvolvedor / arquiteto** | 1. `docs/DOCUMENTACAO_TECNICA.md` (arquitetura, classes, SQL, matriz regra → código → teste) → 2. `docs/CATALOGO_REGRAS.md` → 3. `implementacao/README.md` |
| **Quem avalia a confiabilidade** | 1. `docs/MAPA_COBERTURA_REGRAS.md` (quais regras o original realmente emitiu) → 2. `docs/REVISAO_DOCUMENTACAO.md` e `docs/REVISAO_DOCUMENTACAO_2.md` (auditorias independentes) → 3. `analise/05-testes.md` |
| **Quem quer o histórico da engenharia reversa** | `analise/01…07` na ordem numérica e `analise/progresso.md` |

### O que representa cada documento
| Documento | O que é | Use para |
|---|---|---|
| `docs/CATALOGO_REGRAS.md` | **Fonte única das regras**: 103 IDs estáveis (E, A, L, R, G), condição, mensagem, evidência, confiança, componentes. Os dois documentos abaixo derivam dele | Consultar uma regra por ID; saber o que é confirmado e o que não é |
| `docs/DOCUMENTACAO_FUNCIONAL.md` | Comportamento visto de fora: layouts, fluxo, mensagens ao usuário, defeitos do original, cenários de QA | Escrever/validar casos de teste; explicar o sistema |
| `docs/DOCUMENTACAO_TECNICA.md` | Implementação conhecida: stack, pacotes, DDL, SQL das regras, algoritmos, reimplementação, matriz de rastreabilidade | Manter ou reimplementar; achar o código de uma regra |
| `docs/MAPA_COBERTURA_REGRAS.md` | Para cada regra, em quantos cenários o aplicativo original a emitiu e qual caso mínimo a reproduz | Medir o grau de evidência de cada regra |
| `docs/REVISAO_DOCUMENTACAO.md` / `_2.md` | Auditorias independentes (1ª e 2ª passada) com problemas P-nn e Q-nn e sua resolução | Entender o que foi corrigido e por quê |
| `analise/04-regras-validacao.md` | Catálogo original da engenharia reversa (anterior ao `docs/`; IDs idênticos, algumas formulações superadas — prevalece `docs/`) | Mensagens completas de cada regra |
| `analise/03-arquitetura.md`, `05-testes.md`, `06-duvidas-abertas.md`, `07-proposta-implementacao.md` | Arquitetura, estratégia de testes e resultados, dúvidas abertas, proposta da reimplementação | Contexto e decisões |

Convenções: **EXEC** = emitido pelo aplicativo original em execução; **EST** = só leitura de código; **PROV** = depende de locale/servidor; **INALC** = inalcançável. Detalhes na §1.2 do catálogo. Em divergência entre documentos, vale o catálogo.

## Estrutura
| Pasta | Conteúdo |
|---|---|
| `docs/` | Catálogo, documentação funcional e técnica, mapa de cobertura, revisões e `exportar.py` (gera PDF/DOCX/HTML em `docs/export/`, ignorado pelo git) |
| `analise/` | Relatórios 01–07 e `progresso.md` (inventário, tecnologias, arquitetura, regras, testes, dúvidas, proposta) |
| `analise/ferramentas/` | Leitor de container Inno Setup 6.5.0 (o `innoextract` não suporta 6.5) e extrator verificado por SHA-256 |
| `analise/testes/` | Modelo de referência (Python), testes de caracterização, fuzzers, harnesses diferenciais (níveis 1–3, hash, GAB, mapa de cobertura) |
| `analise/testes/casos/` | 16 casos golden sintéticos |
| `analise/testes/casos-por-regra/` | 71 casos mínimos, um por regra observada no original; `esperado.txt` = saída do **original**; `ORIGEM.md` diz de qual cenário veio |
| `implementacao/validador-independente/` | Reimplementação em Java 21 (Maven): modos `compat` e `estrito`, fluxo bancário e GAB, limiares configuráveis |
| `implementacao/limiares-6.2.6.properties` | Limiares/códigos das regras de ORIGEM_DESTINO (hard-coded no original) |
| `.claude/agents/` | Definições dos subagentes usados para gerar e revisar `docs/` (analista funcional, arquiteto, revisor) |

## Como usar
1. **Ler:** siga o guia acima. Não é preciso rodar nada para consultar a documentação.
2. **Validar um conjunto de arquivos com a reimplementação** (arquivos TAB, ISO-8859-1, nome `<caso>_{AGENCIAS,CONTAS,TITULARES,EXTRATO,ORIGEM_DESTINO}.txt`):
```
cd implementacao/validador-independente && mvn package
java -jar target/validador-independente-0.2.0.jar --pasta DIR --caso 001-XXX-000001-00 --modo compat
```
   `compat` prevê o resultado do validador oficial (inclui seus defeitos); `estrito` gera relatório mais útil, mas pode divergir do aceite oficial. Opções completas em `implementacao/README.md`.
3. **Achar a evidência de uma regra:** procure o ID no catálogo → pasta `analise/testes/casos-por-regra/<ID>/` para ver entrada e saída reais → matriz da §14 da documentação técnica para o código.
4. **Regenerar PDF/Word:** `python3 docs/exportar.py` (requer LibreOffice).

## Como reproduzir
```
# implementação independente
cd implementacao/validador-independente && mvn test && mvn package

# modelo e testes de caracterização
cd analise/testes && python3 -m unittest test_caracterizacao && python3 gerar_casos.py casos

# execuções diferenciais (exigem o instalador original em original/, que você deve obter da fonte oficial):
# 1) extrair: analise/ferramentas/inno65*.py  (SHA-256 em analise/01-inventario.md)
# 2) decompilar (opcional): Vineflower 1.12.0
# 3) analise/testes/diferencial/diferencial_nivel1.py | diferencial_nivel2.py | diferencial_gui.py | diferencial_gab.py
```
Histórico e pendências: `analise/progresso.md`.
