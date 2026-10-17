# Progresso

**Etapa atual:** investigação concluída; implementação independente v0.2.0 verificada contra o aplicativo original nos níveis 1, 2 e 3 (tela real).

## Confirmado por execução (aplicativo original em sandbox sem rede)
- Nível 1 (funções puras): 20.103 vetores, 0 divergências. Hash MD5: `%032x` só com receptor ≥ 4 (401 arquivos × 3 versões).
- Nível 2 (pipeline real em HSQLDB): 2.316 cenários + 18 dirigidos pela cobertura (inclui 1.200 e 250.005 linhas): 0 divergências reais.
- **Nível 3 (tela real `frmSelecionaDiretorio`, sessão gráfica do usuário):** 73 cenários — mensagens, ícones, hash gravado, linhas e cópia dos arquivos: 0 divergências.
- GAB: 4.500 cenários × 2 modos, 0 divergências; **defeito G-DEF** (todo GAB não vazio falha no 6.2.6).
- **Botão "Continuar" fica habilitado mesmo com erros** (E-11) — corrigida leitura anterior errada.
- Cobertura JaCoCo do original (rules+carga+validacao): 63,0% → 82,9%; a cobertura achou 2 divergências reais (R-CT-02 `null`, R-TI-04 linha −1), já corrigidas.

## Pendências
- Locale `en_US`; unidade dos valores (D3); termos de uso/licença (D11) NÃO verificados; dados reais; se o servidor aceita envio com erros (D15).

## Decisões técnicas
- Execução do original só em `bwrap --unshare-net`, FS somente leitura, diretório descartável. Para a tela: DISPLAY do usuário, lista de órgãos injetada em memória (nenhuma conexão).
- Equivalência = mesmo multiconjunto de mensagens + mesmo desfecho por arquivo (ordem de R-OD-04 não é contratual).

## Próxima ação recomendada
1. Verificar D11 antes de distribuir; apagar `/home/lucasmh/Downloads/validador/root.txt`.
2. Opcional: rodar a bateria em `en_US`; amostra anonimizada real para fechar o último risco.
