package estudo.simba.validador.gab;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import estudo.simba.validador.*;

/** Carga dos arquivos GAB (corretoras): largura fixa + validações de campo (analise/04 §G). */
public final class GabLoader {
    private static final String DESCONHECIDO = "Erro desconhecido. Consulte o log do sistema.";
    private static final String MSG_LARGURA = "Certifique-se de completar com espaços os campos que não atingirem o tamanho predeterminado e Não use tab para separar os campos.";
    private static final Set<String> UFS = Set.of("AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS", "MG", "PA", "PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO");

    private final GabConfig cfg;
    private final Report rep;
    private final GabDb db;

    public GabLoader(GabConfig cfg, Report rep, GabDb db) { this.cfg = cfg; this.rep = rep; this.db = db; }

    /** @return true se houve erro. */
    public boolean carregar(GabKind kind, Path arquivo) throws java.io.IOException {
        List<String> linhas = Files.readAllLines(arquivo, StandardCharsets.ISO_8859_1);
        String fn = arquivo.getFileName().toString();
        var campos = GabLayouts.of(kind, cfg.versaoLayout());
        int esperado = GabLayouts.tamanhoLinha(kind, cfg.versaoLayout());
        String tabela = "Gab" + kind.name().substring(3) + (cfg.versaoLayout() == 2 ? "Ver2" : "");
        boolean temErro = false;
        for (int n = 1; n <= linhas.size(); n++) {
            String linha = linhas.get(n - 1);
            if (linha.length() != esperado) { rep.rep("G-LARGURA", n, fn, MSG_LARGURA); temErro = true; continue; }
            if (cfg.defeito()) { rep.rep("G-UNK", n, fn, DESCONHECIDO); temErro = true; continue; }   // NPE em validarCampos
            if (cfg.mode() == Mode.COMPAT && !rep.isEmpty()) { temErro = true; continue; }              // E-06
            Map<String, String> v = new LinkedHashMap<>();
            String resto = linha;
            for (var f : campos) { v.put(f.name(), resto.substring(0, f.size()).trim()); resto = resto.substring(f.size()); }
            int antes = rep.size();
            try {
                switch (kind) { case GAB109 -> v109(v, n, tabela); case GAB112 -> v112(v, n, tabela); case GAB800 -> v800(v, n, tabela); }
                if (rep.size() > antes) { temErro = true; continue; }
                gravar(kind, v);                                   // setParametros: pode falhar mesmo após a validação
                db.of(kind).add(new GabDb.Row(n, v));
            } catch (RuntimeException e) {
                rep.rep("G-UNK", n, fn, DESCONHECIDO);
                temErro = true;
            }
        }
        return temErro;
    }

    /** Emula a gravação: colunas NUMERIC rejeitam texto não numérico e colunas DATE usam Util.getDateGabSql (NPE/ParseException se vazio/inválido). */
    private void gravar(GabKind kind, Map<String, String> v) {
        for (String c : GabLayouts.numericas(kind, cfg.versaoLayout())) {
            String x = v.get(c);
            if (!Text.isEmpty(x)) new java.math.BigDecimal(x);      // NumberFormatException => "Erro desconhecido"
        }
        for (String c : GabLayouts.datas(kind, cfg.versaoLayout()))
            if (!dataOk(v.get(c))) throw new IllegalStateException("getDateGabSql: data nula");
    }

    // ---------- utilitários (util.Util do original) ----------
    private static boolean vazio(String s) { return Text.isEmpty(s); }

    /** Util.isNumber: Long.parseLong e BigInteger. */
    static boolean isNumber(String t) {
        try { Long.parseLong(t); new BigInteger(t); return true; } catch (Exception e) { return false; }
    }

    /** Util.getDateGab: yyyyMMdd leniente com verificação de ida-e-volta; ParseException ⇒ falha de infraestrutura. */
    static boolean dataOk(String data) {
        if (data == null || data.isEmpty()) return false;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        try {
            java.util.Date d = sdf.parse(data.trim());
            return sdf.format(d).equals(data.trim());
        } catch (ParseException e) {
            throw new IllegalStateException("Erro na formatação da data", e);
        }
    }

    private void obrig(Map<String, String> v, int n, String tab, String... campos) {
        for (String c : campos) if (vazio(v.get(c))) rep.rep("G-OBRIG", n, tab, "O campo " + c + " é obrigatório.");
    }

    private void numero(Map<String, String> v, int n, String tab, String[][] campos) {   // {campo, prefixo-do-campo}
        for (String[] c : campos) if (!isNumber(v.get(c[0]))) rep.rep("G-NUM", n, tab, "O dado informado não é um número. " + c[1] + c[0]);
    }

    private void cd(String valor, String campo, int n, String tab) {   // TipoLancamento.valueOf
        if (!"C".equals(valor) && !"D".equals(valor)) rep.rep("G-DOM", n, tab, "Informe C para crédito ou D para débito. Campo: " + campo);
    }

    // ---------- GAB109 ----------
    private void v109(Map<String, String> v, int n, String tab) {
        obrig(v, n, tab, "CODIGO_DO_CLIENTE", "DESCRICAO_DO_LANCAMENTO", "VALOR_DO_LANCAMENTO", "NATUREZA_LANCAMENTO", "DATA_DE_LIQUIDACAO", "DATA_DO_MOVIMENTO", "DATA_DE_REFERENCIA");
        String e = " está incorreto. A data deve estar no formato AAAAMMDD e válida.";    // cadeia else-if: só o primeiro erro de data
        if (!dataOk(v.get("DATA_DO_MOVIMENTO"))) rep.rep("G-DATA", n, tab, "O campo DATA_DO_MOVIMENTO" + e);
        else if (!dataOk(v.get("DATA_DE_REFERENCIA"))) rep.rep("G-DATA", n, tab, "O campo DATA_DE_REFERENCIA" + e);
        else if (!dataOk(v.get("DATA_DE_LIQUIDACAO"))) rep.rep("G-DATA", n, tab, "O campo DATA_DE_LIQUIDACAO" + e);
        numero(v, n, tab, new String[][]{{"CODIGO_DO_CLIENTE", "Campo: "}, {"VALOR_DO_LANCAMENTO", " Campo "}});
        cd(v.get("NATUREZA_LANCAMENTO"), "NATUREZA_LANCAMENTO", n, tab);
    }

    // ---------- GAB800 ----------
    private void v800(Map<String, String> v, int n, String tab) {
        obrig(v, n, tab, "CODIGO_DO_CLIENTE", "DESCRICAO_DO_LANCAMENTO", "VALOR_DO_LANCAMENTO", "NOME_DO_CLIENTE", "DATA_DE_LIQUIDACAO", "ENDERECO", "DATA_DE_REFERENCIA",
            "SALDO_INICIAL", "NATUREZA_DO_SALDO_INICIAL", "NATUREZA_DO_LANCAMENTO");
        String e = "A data deve estar no formato AAAAMMDD e válida.  Campo: ";
        if (!dataOk(v.get("DATA_DE_REFERENCIA"))) rep.rep("G-DATA", n, tab, e + "DATA_DE_REFERENCIA");
        if (!dataOk(v.get("DATA_DE_LIQUIDACAO"))) rep.rep("G-DATA", n, tab, e + "DATA_DE_LIQUIDACAO");
        numero(v, n, tab, new String[][]{{"CODIGO_DO_CLIENTE", "Campo: "}, {"VALOR_DO_LANCAMENTO", " Campo "}, {"SALDO_INICIAL", " Campo "}});
        cd(v.get("NATUREZA_DO_SALDO_INICIAL"), "NATUREZA_DO_SALDO_INICIAL", n, tab);
        cd(v.get("NATUREZA_DO_LANCAMENTO"), "NATUREZA_DO_LANCAMENTO", n, tab);
    }

    // ---------- GAB112 ----------
    private void v112(Map<String, String> v, int n, String tab) {
        obrig(v, n, tab, "CODIGO_DO_CLIENTE", "DOCUMENTO_DO_CLIENTE", "DATA_DA_CRIACAO", "DATA_DA_ATUALIZACAO", "DATA_NASCIMENTO_FUNDACAO", "TIPO_DE_PESSOA", "NOME_DO_CLIENTE",
            "DOCUMENTO_DE_IDENTIFICACAO", "CODIGO_DO_CLIENTE_MERCADO", "ENDERECO", "BAIRRO", "CIDADE", "DOCUMENTO_DO_CLIENTE", "ESTADO", "CEP", "COMPLEMENTO_DO_CEP",
            "COMPLEMENTO_DO_CEP", "CONSTANTE_0000A", "CODIGO_DA_ATIVIDADE", "MERCADO");     // o original repete DOCUMENTO_DO_CLIENTE e COMPLEMENTO_DO_CEP
        String e = "A data deve estar no formato AAAAMMDD e válida.  Campo: ";
        for (String c : new String[]{"DATA_DA_CRIACAO", "DATA_DA_ATUALIZACAO", "DATA_NASCIMENTO_FUNDACAO"})
            if (!dataOk(v.get(c))) rep.rep("G-DATA", n, tab, e + c);
        numero(v, n, tab, new String[][]{{"CODIGO_DO_CLIENTE", "Campo: "}, {"DOCUMENTO_DO_CLIENTE", " Campo "}, {"CODIGO_DO_CLIENTE_MERCADO", " Campo "}, {"CODIGO_DA_ATIVIDADE", " Campo "}});
        if (!"0000A".equals(v.get("CONSTANTE_0000A"))) rep.rep("G-DOM", n, tab, "Valor esperado para o campo CONSTANTE_0000A: 0000A.");
        String tipo = v.get("TIPO_DE_PESSOA");
        String msgTipo = "Informe F para pessoa física ou J para pessoa jurídica. Campo: TIPO_DE_PESSOA";
        if (!"F".equals(tipo) && !"J".equals(tipo)) rep.rep("G-DOM", n, tab, msgTipo);
        if (!Set.of("BOVESPA", "BMF", "SWAP").contains(v.get("MERCADO")))
            rep.rep("G-DOM", n, tab, "Informe BOVESPA para mercado à vista, BMF para derivativos ou SWAP para balcão. Campo: MERCADO");
        if (!UFS.contains(v.get("ESTADO"))) rep.rep("G-DOM", n, tab, "A sigla do campo estado está incorreta");
        String doc = v.get("DOCUMENTO_DO_CLIENTE");                                                      // validarCpfCnpj
        if (doc.length() != 15)
            rep.rep("G-DOC", n, tab, "O campo DOCUMENTO_DO_CLIENTE é obrigatório conter 15posições. Devendo ser completementado com zeros à esquerda.");
        String semZeros = Text.removeLeadingZeros(doc);                                                  // lança para vazio/só zeros
        if ("F".equals(tipo)) {
            if (!CpfCnpj.validarCpf(semZeros)) rep.rep("G-DOC", n, tab, "CPF inválido para o tipo de pessoa: " + tipo + ". Campo: DOCUMENTO_DO_CLIENTE");
        } else if ("J".equals(tipo)) {
            if (!CpfCnpj.validarCnpj(semZeros)) rep.rep("G-DOC", n, tab, "CNPJ inválido para o tipo de pessoa: " + tipo + ". Campo: DOCUMENTO_DO_CLIENTE");
        } else {
            rep.rep("G-DOC", n, tab, msgTipo);
        }
    }
}
