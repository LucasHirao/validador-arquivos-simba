# 06 — Dúvidas abertas

| # | Dúvida | Por que importa | Como resolver |
|---|---|---|---|
| D1 | Equivalência entre modelo/implementação e o aplicativo | **RESOLVIDA para o escopo testado:** níveis 1, 2 e 3 (tela real) e hash: 0 divergências reais; cobertura do original em 82,9% das linhas de validação (restante: código morto/log/tela). Resta locale `en_US` e dados reais | rodar com `-Duser.language=en -Duser.country=US`; amostra anonimizada |
| D2 | ~~`Computador.getVersaoReceptor()` vem de dado local ou do servidor?~~ **RESOLVIDA (estático):** vem do servidor via HTTP GET `orgaos?codprog=…` (`Controlador.doGetComputadores`); só afeta formato do hash e L-OD-04 (inalcançável). Consequência: o app real exige rede (ou servidor simulado) para validar. | — | — |
| D3 | Unidade de `VALOR_*` (centavos?) — limiares 1.000.000 / 5.000.000 / 10.000 / 10.000.000 | Interpretação de negócio dos limiares | Documentação oficial do layout (fórum SIMBA); não deduzível do código |
| D4 | Locale do `NumberFormat` em `StringUtil.isNumber` | Comportamento de A-05 muda entre máquinas | Teste em `pt_BR` e `en_US` |
| D5 | ~~Loaders GAB não catalogados~~ **RESOLVIDA:** catalogados (04 §G) e verificados contra o original, inclusive o **defeito G-DEF** (todo GAB não vazio falha no 6.2.6) | — | Confirmar com o fornecedor se é defeito conhecido (não reportado) |
| D6 | ~~R-EX-01 não modelada~~ **RESOLVIDA:** modelada (Python) e implementada (Java) | — | — |
| D7 | Origem de ATENDIMENTO/INVESTIGADO (telas `frmDadosAtendimento`, `frmIdentificaAtendimento`) | Entradas da validação não vêm de arquivo | Ler as telas |
| D8 | Duas file entries anômalas no instalador Inno (dest `{app}`/vazio, loc 0) | Higiene do inventário | Reanalisar `inno65_catalog.py` (heurístico) |
| D9 | Papel do `simba-validador.exe` (73.728 B PE32 GUI) | Launcher do JRE? | Strings/imports do PE (análise estática) |
| D10 | O que o `spea-client` envia/recebe (formato do comprovante, assinatura) | Escopo de rede/privacidade | Ler `PostToUrl`, `Certificado`, `SolicitarCodigoAutenticacaoCmd` |
| D11 | Termos de uso/licença do Validador para engenharia reversa e reimplementação | Aspecto legal, **não verificado** | Consultar termos do MPF/SIMBA e orientação jurídica |
| D12 | ~~Versão do layout GAB~~ **RESOLVIDA:** escolhida pelo botão na tela do atendimento (`frmIdentificaAtendimento.validarCorretoraButtonClick(versao)` → `Configurador.setVersaoLayoutGab`); o receptor suporta v2 se major ≥ 4 ou (major ≥ 3 e minor ≥ 9) (`Versao.suportaCorretoraV2`) | — | — |
| D15 | O servidor aceita um envio cujo validador local reportou erros? (a tela deixa prosseguir: E-11) | Define se a validação local é só informativa | Não analisável sem o servidor; ler `frmTrataEnvio`/`GerarArquivoEnvioCmd` com mais profundidade |
| D13 | Ordem das linhas de erro em R-OD-04 (e, em geral, de consultas sem `ORDER BY`) depende do plano do HSQLDB | Só a ordem; conteúdo idêntico | Ordenar no relatório, se necessário |
| D14 | ~~Nível 3 (GUI) não executado~~ **RESOLVIDA:** tela real executada em 73 cenários com a lista de órgãos injetada em memória (05) | — | — |

## Peculiaridades registradas (possíveis defeitos do aplicativo)
Ver 04-regras-validacao.md §E (11 itens) e 03-arquitetura.md §8 (código morto). Não foram reportadas ao fornecedor.
