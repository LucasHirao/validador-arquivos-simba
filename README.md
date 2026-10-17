# Validador SIMBA — engenharia reversa de estudo

Estudo técnico do **Validador Bancário SIMBA 6.2.6** (Ministério Público Federal): identificação da tecnologia, recuperação das regras de validação por análise estática, verificação por execução diferencial e uma reimplementação independente de estudo.

> **Aviso.** Este repositório **não contém** o instalador nem os binários do MPF, nem a saída literal do decompilador (ver `.gitignore`). Termos de uso/licença do software original **não foram verificados**. A implementação é de estudo, **não é o validador oficial** e não substitui o aceite do MPF. Use apenas dados sintéticos.

## Resultado em uma página
- Instalador Inno Setup 6.5.0 (Delphi) sem criptografia; aplicativo **Java 25** (Swing + Spring + HSQLDB) com JRE embutido.
- Catálogo de ~84 regras recuperadas (`analise/04-regras-validacao.md`), incluindo o algoritmo de CPF/CNPJ **alfanumérico**.
- Achados notáveis: no 6.2.6 o fluxo de **corretoras (GAB) não aprova nenhum arquivo não vazio** (NPE em `validarCampos`); o botão "Continuar" fica habilitado mesmo com erros; erro global bloqueia a validação semântica das linhas seguintes; `Erro no arquivo: null` em R-CT-02.
- Verificação contra o aplicativo original, em sandbox sem rede (`bwrap --unshare-net`): funções puras (20.103 vetores), pipeline real em HSQLDB (2.334 cenários), tela real (73 cenários), hash MD5, GAB (4.500 × 2 modos): **0 divergências reais**. Cobertura de código do original: 82,9% das linhas de validação.

## Estrutura
| Pasta | Conteúdo |
|---|---|
| `analise/` | Relatórios 01–07 e `progresso.md` (inventário, tecnologias, arquitetura, regras, testes, dúvidas, proposta) |
| `analise/ferramentas/` | Leitor de container Inno Setup 6.5.0 (o `innoextract` não suporta 6.5) e extrator verificado por SHA-256 |
| `analise/testes/` | Modelo de referência (Python), testes de caracterização, casos sintéticos (`casos/` e `casos-por-regra/`, 1 caso mínimo por regra observada no original), fuzzers e harnesses diferenciais (níveis 1–3, hash, GAB, mapa de cobertura) |
| `docs/` | Catálogo de regras (103), documentação funcional e técnica, mapa de cobertura por regra e relatórios de revisão |
| `implementacao/validador-independente/` | Reimplementação em Java 21 (Maven): modos `compat` e `estrito`, fluxo bancário e GAB, limiares configuráveis |
| `implementacao/limiares-6.2.6.properties` | Limiares/códigos das regras de ORIGEM_DESTINO (hard-coded no original) |

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
Documentação detalhada: `analise/progresso.md`.
