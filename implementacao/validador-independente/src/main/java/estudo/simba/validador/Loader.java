package estudo.simba.validador;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import estudo.simba.validador.Db.*;

/**
 * Carga linha-a-linha com as regras L-* (analise/04 §C). Modo COMPAT: após o primeiro erro de qualquer linha, as linhas seguintes
 * só passam por E-03/E-04 (E-06). Modo ESTRITO: todas as linhas passam pela validação semântica.
 */
public final class Loader {
    private enum Kind { AGENCIAS, CONTAS, PESSOA, TITULARIDADE, EXTRATO, ORIGEM_DESTINO }

    private static final String DESCONHECIDO = "Erro desconhecido. Consulte o log do sistema.";
    private static final String OBRIG_TITULAR = "Um dos campos obrigatórios (TIPO_PESSOA, CPF_CNPJ_PESSOA, NOME_PESSOA, PESSOA_INVESTIGADA, DATA_INICIO_RELACIONAMENTO) não está preenchido.";
    private static final Set<String> UFS = Set.of("AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS", "MG", "PA", "PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO");
    private static final Pattern INT = Pattern.compile("[+-]?\\d+");

    /** NegocioException do original: a mensagem vai ao relatório e interrompe a linha. */
    private static final class Negocio extends RuntimeException {
        Negocio(String m) { super(m); }
    }

    private final Config cfg;
    private final Report rep;
    private final Db db;

    public Loader(Config cfg, Report rep, Db db) { this.cfg = cfg; this.rep = rep; this.db = db; }

    private boolean compat() { return cfg.mode() == Mode.COMPAT; }

    public boolean carregarArquivo(FileKind kind, Path arquivo) throws java.io.IOException {
        List<String> linhas = Files.readAllLines(arquivo, StandardCharsets.ISO_8859_1);
        String fn = arquivo.getFileName().toString();
        return switch (kind) {
            case AGENCIAS -> carregar(Kind.AGENCIAS, linhas, fn);
            case CONTAS -> carregar(Kind.CONTAS, linhas, fn);
            case TITULARES -> carregar(Kind.PESSOA, linhas, fn) || carregar(Kind.TITULARIDADE, linhas, fn);
            case EXTRATO -> carregar(Kind.EXTRATO, linhas, fn);
            case ORIGEM_DESTINO -> carregar(Kind.ORIGEM_DESTINO, linhas, fn);
        };
    }

    /** @return true se houve erro (CargaInfo.temErro). */
    boolean carregar(Kind kind, List<String> linhas, String fn) {
        FileKind layout = switch (kind) {
            case PESSOA, TITULARIDADE -> FileKind.TITULARES;
            default -> FileKind.valueOf(kind.name());
        };
        var campos = Schemas.LAYOUT.get(layout);
        boolean temErro = false;
        for (int n = 1; n <= linhas.size(); n++) {
            String[] d = TabSplitter.split(linhas.get(n - 1), cfg.mode());
            if (d.length != campos.size()) {
                rep.rep("E-03", n, fn, "Quantidade de colunas inválida. Esperado: " + campos.size() + " Atual: " + d.length);
                temErro = true;
                continue;
            }
            boolean estrutural = false;
            for (int i = 0; i < d.length; i++) {
                if (d[i].length() > campos.get(i).max()) {
                    rep.rep("E-04", n, layout.name(), "Tamanho do campo " + campos.get(i).name() + " excedido. Tamanho máximo permitido: " + campos.get(i).max());
                    temErro = true;
                    estrutural = true;
                }
            }
            if (compat() && !rep.isEmpty()) { temErro = true; continue; }   // E-06
            if (estrutural) continue;
            int antes = rep.size();
            try {
                Object rec = switch (kind) {
                    case AGENCIAS -> agencia(d, n);
                    case CONTAS -> conta(d, n);
                    case PESSOA -> pessoa(d, n, layout.name());
                    case TITULARIDADE -> titularidade(d, n);
                    case EXTRATO -> extrato(d, n, fn);
                    case ORIGEM_DESTINO -> od(d, n, fn);
                };
                if (rep.size() > antes) { temErro = true; continue; }
                insert(rec);
            } catch (Negocio e) {
                rep.rep("L-NEG", n, fn, e.getMessage());
                temErro = true;
            } catch (RuntimeException e) {
                rep.rep("L-UNK", n, fn, DESCONHECIDO);
                temErro = true;
            }
        }
        return temErro;
    }

    private void insert(Object rec) {
        switch (rec) {
            case Ag r -> db.addAgencia(r);
            case Ct r -> db.addConta(r);
            case Pe r -> db.pessoas.add(r);
            case Ti r -> db.titularidades.add(r);
            case Ex r -> db.addExtrato(r);
            case Od r -> db.addOd(r);
            default -> throw new IllegalStateException();
        }
    }

    // ---------- números ----------
    /** Integer.valueOf: no COMPAT lança (vira "Erro desconhecido"); no ESTRITO devolve null para o chamador reportar. */
    private Integer toInt(String s) {
        Integer v = Text.intOrNull(s);
        if (v == null && compat()) throw new NumberFormatException(s);
        return v;
    }

    /** getLong do original (A-05): prefixo numérico leniente, depois Long.valueOf estrito. ESTRITO: só inteiros completos. */
    private long getLong(String s, int n, String campo, String fn) {
        boolean ok = compat() ? Pattern.compile("^-?\\d").matcher(s).find() : INT.matcher(s).matches();
        if (ok) return Long.parseLong(s);       // COMPAT: NumberFormatException => "Erro desconhecido"
        rep.rep("L-NUM", n, fn, "Campo " + campo + " não é um número.");
        return 0;
    }

    private void dataValida(String valor, String campo, int n, String arq, String id) {
        if (!Text.isEmpty(valor) && !DateRules.verificaData(valor))
            rep.rep(id, n, arq, "A " + campo + " (" + valor + ") é inválida ou o ano é anterior a 1900.");
    }

    // ---------- AGENCIAS ----------
    private Ag agencia(String[] d, int n) {
        String arq = "AGENCIAS";
        int n0 = rep.size();
        boolean ele = cfg.eleitoral();
        boolean falta;
        String lista;
        if (ele) {
            falta = Text.isEmpty(d[0]) || Text.isEmpty(d[1]) || Text.isEmpty(d[2]);
            lista = "NUMERO_BANCO, NUMERO_AGENCIA, NOME_AGENCIA";
        } else {
            falta = false;
            for (int i = 0; i <= 8; i++) falta |= Text.isEmpty(d[i]);
            lista = "NUMERO_BANCO, NUMERO_AGENCIA, NOME_AGENCIA, ENDERECO_LOGRADOURO, ENDERECO_CIDADE, ENDERECO_UF, ENDERECO_PAIS, ENDERECO_CEP, TELEFONE_AGENCIA";
        }
        if (falta) rep.rep("L-AG-01", n, arq, "Um dos campos obrigatórios (" + lista + ") não está preenchido.");
        dataValida(d[9], "DATA_ABERTURA_AGENCIA", n, arq, "L-AG-02");
        dataValida(d[10], "DATA_FECHAMENTO_AGENCIA", n, arq, "L-AG-03");
        Integer banco = toInt(d[0]);                                                     // L-AG-04
        if (banco == null) {
            if (!Text.isEmpty(d[0])) rep.rep("L-AG-04", n, arq, "NUMERO_BANCO não é numérico: " + d[0]);
        } else if (banco != cfg.bancoAtendimento()) {
            rep.rep("L-AG-04", n, arq, "NUMERO_BANCO diferente do informado em atendimento.");
        }
        if (!ele && !Text.isEmpty(d[10])) {                                               // L-AG-05
            if (Text.isEmpty(d[9])) {
                rep.rep("L-AG-05", n, arq, "DATA_FECHAMENTO_AGENCIA informada, mas não há DATA_ABERTURA_AGENCIA.");
            } else {
                try {
                    if (DateRules.parseLenient(d[10]).isBefore(DateRules.parseLenient(d[9])))
                        rep.rep("L-AG-05", n, arq, "DATA_ABERTURA_AGENCIA posterior à DATA_FECHAMENTO_AGENCIA.");
                } catch (java.text.ParseException e) {
                    rep.rep("L-AG-05", n, arq, "Data inválida nos campos DATA_ABERTURA_AGENCIA e/ou DATA_FECHAMENTO_AGENCIA.");
                }
            }
        }
        if (!ele && !UFS.contains(d[5]) && !"--".equals(d[5]))                            // L-AG-06 (case-sensitive)
            rep.rep("L-AG-06", n, arq, "ENDERECO_UF:" + d[5] + " não é uma sigla válida.");
        Integer ag = null;
        if (rep.size() == n0 && !Text.isEmpty(d[1])) {                                    // L-AG-07 (inserção)
            ag = toInt(d[1]);
            if (ag == null) rep.rep("L-AG-07", n, arq, "NUMERO_AGENCIA não é numérico: " + d[1]);
        }
        return new Ag(n, banco == null ? -1 : banco, ag == null ? -1 : ag);
    }

    // ---------- CONTAS ----------
    private Ct conta(String[] d, int n) {
        String arq = "CONTAS";
        Integer b = toInt(d[0]), a = toInt(d[1]);                                          // L-CT-00
        if (b == null || a == null) {
            rep.rep("L-CT-00", n, arq, "NUMERO_BANCO e NUMERO_AGENCIA devem ser numéricos.");
            return null;
        }
        int idAg = db.findAgencia(b, a);
        if (idAg == -1) throw new Negocio("Agencia não localizada. Banco: " + d[0] + " Agencia: " + d[1]);
        if (Text.isEmpty(d[0]) || Text.isEmpty(d[2]) || Text.isEmpty(d[3]) || Text.isEmpty(d[6]))   // L-CT-01
            rep.rep("L-CT-01", n, arq, "Um dos campos obrigatórios (NUMERO_CONTA, TIPO_CONTA, MOVIMENTACAO_CONTA) não está preenchido.");
        dataValida(d[4], "DATA_ABERTURA_CONTA", n, arq, "L-CT-02");
        dataValida(d[5], "DATA_ENCERRAMENTO_CONTA", n, arq, "L-CT-03");
        Integer tipo = Text.intOrNull(d[3]);
        if (tipo == null || tipo < 1 || tipo > 4) rep.rep("L-CT-04", n, arq, "Tipo de conta inválido. Valores possíveis: 1, 2, 3 e 4.");
        Integer mov = Text.intOrNull(d[6]);
        if (mov == null || mov < 1 || mov > 3) rep.rep("L-CT-05", n, arq, "Movimentação de Conta inválido. Valores possíveis: 1, 2 e 3");
        return new Ct(n, idAg, d[2], tipo == null ? 0 : tipo, d[4], d[5], mov == null ? 0 : mov);
    }

    // ---------- TITULARES: passada PESSOA ----------
    private Pe pessoa(String[] d, int n, String arq) {
        Double renda = null;
        if (!Text.isEmpty(d[17])) {
            try { renda = Double.valueOf(d[17]); }
            catch (NumberFormatException e) { throw new Negocio("Valor numérico inválido no campo Valor Renda: " + d[17]); }
        }
        String tipo = d[6], cpf = d[7].toUpperCase(java.util.Locale.ROOT);
        if (Text.isEmpty(tipo) || Text.isEmpty(cpf) || Text.isEmpty(d[8])) rep.rep("L-PE-01", n, arq, OBRIG_TITULAR);
        if (!Text.isEmpty(d[18]) && !DateRules.verificaData(d[18]))                       // L-PE-02: rótulo EXTRATO só no COMPAT
            rep.rep("L-PE-02", n, compat() ? "EXTRATO" : arq, "A DATA_ATUALIZACAO_RENDA (" + d[18] + ") é inválida ou o ano é anterior a 1900.");
        if ("1".equals(tipo)) {                                                            // L-PE-03
            String c = compat() ? Text.removeLeadingZeros(cpf) : cpf;                      // COMPAT: exceção para CPF só de zeros
            if (!CpfCnpj.validarCpf(c)) rep.rep("L-PE-03", n, arq, "O CPF_CNPJ_PESSOA: " + cpf + " está inválido.");
        } else if ("2".equals(tipo) && !CpfCnpj.validarCnpj(cpf)) {
            rep.rep("L-PE-03", n, arq, "O CPF_CNPJ_PESSOA: " + cpf + " está inválido.");
        }
        if (!"1".equals(tipo) && !"2".equals(tipo)) rep.rep("L-PE-04", n, arq, "Tipo de pessoa inválido. Valores possíveis: 1 e 2.");
        String padded = Text.padLeft(cpf, 14);
        Inv inv = db.investigados.stream().filter(i -> Text.padLeft(i.cpf(), 14).equals(padded)).findFirst().orElse(null);
        return new Pe(n, tipo, cpf, d[9], d[10], d[11], d[12], d[13], d[14], d[15], d[16], inv);
    }

    // ---------- TITULARES: passada TITULARIDADE ----------
    private Ti titularidade(String[] d, int n) {
        String arq = "TITULARES";
        String[] msgs = {"O código COMPE da instituição financeira não foi informado", "O número da agência não foi informado",
                         "O número da conta não foi informado", "O tipo de conta não foi informado"};
        for (int i = 0; i < 4; i++) if (d[i].isEmpty()) throw new Negocio(msgs[i]);
        Integer b = toInt(d[0]), a = toInt(d[1]), t = toInt(d[3]);
        if (b == null || a == null || t == null) {
            rep.rep("L-TI-00", n, arq, "NUMERO_BANCO, NUMERO_AGENCIA e TIPO_CONTA devem ser numéricos.");
            return null;
        }
        int idConta = db.findConta(b, a, d[2], t);
        if (idConta == -1) throw new Negocio("Conta não localizada. Banco: " + d[0] + " Agencia: " + d[1] + " Conta: " + d[2] + " TipoConta: " + d[3]);
        String tipo = d[4], inv = d[5], ini = d[19], fim = d[20];
        if (Text.isEmpty(tipo) || Text.isEmpty(ini) || Text.isEmpty(inv)) rep.rep("L-TI-01", n, arq, OBRIG_TITULAR);
        if (!Text.isEmpty(ini) && !DateRules.verificaData(ini))                            // L-TI-02 (lança)
            throw new Negocio("A DATA_INICIO_RELACIONAMENTO (" + ini + ") é inválida ou o ano é anterior a 1900.");
        if (!fim.isEmpty() && !DateRules.verificaData(fim))                                // L-TI-03 (lança)
            throw new Negocio("A DATA_FIM_RELACIONAMENTO (" + fim + ") é inválida ou o ano é anterior a 1900.");
        if (!Text.isEmpty(tipo) && !tipo.matches("[T1-9RLPO]"))
            rep.rep("L-TI-04", n, arq, "Tipo de titular inválido. Valores possíveis: T, 1 até 9, R, L, P e O.");
        if (!Text.isEmpty(inv) && !inv.matches("[01]"))
            rep.rep("L-TI-05", n, arq, "Tipo de conta investigada inválido. Valores possíveis: 0 e 1.");
        java.time.LocalDate dIni, dFim = null;
        try { dIni = DateRules.parseLenient(ini); }                                        // L-TI-06
        catch (java.text.ParseException e) { throw new Negocio("A DATA_INICIO_RELACIONAMENTO é inválida: " + ini); }
        if (!fim.isEmpty()) {
            try { dFim = DateRules.parseLenient(fim); }
            catch (java.text.ParseException e) { throw new Negocio("A DATA_FIM_RELACIONAMENTO é inválida: " + fim); }
            if (dFim.isBefore(dIni))
                rep.rep("L-TI-06", n, arq, "A DATA_INICIO_RELACIONAMENTO (" + ini + ") é posterior a DATA_FIM_RELACIONAMENTO (" + fim + ").");
        }
        return new Ti(n, tipo, inv, ini, fim, idConta, n);   // PessoaLocalizer.findIdPessoa(linha): a pessoa da mesma linha
    }

    // ---------- EXTRATO ----------
    private Ex extrato(String[] d, int n, String fn) {
        String arq = "EXTRATO";
        long chave = getLong(d[0], n, "CODIGO_CHAVE_EXTRATO", fn);
        long tipo = getLong(d[8], n, "TIPO_LANCAMENTO", fn), valor = getLong(d[9], n, "VALOR_LANCAMENTO", fn), saldo = getLong(d[11], n, "VALOR_SALDO", fn);
        Integer b = toInt(d[1]), a = toInt(d[2]), tc = toInt(d[4]);
        int idConta = (b == null || a == null || tc == null) ? -1 : db.findConta(b, a, d[3], tc);
        if (idConta <= 0) rep.rep("L-EX-01", n, arq, "Conta não localizada no arquivo de CONTAS.");
        if (Text.isEmpty(d[5]) || Text.isEmpty(d[6]) || Text.isEmpty(d[7]))
            rep.rep("L-EX-02", n, arq, "Um dos campos obrigatórios (DATA_LANCAMENTO, NUMERO_DOCUMENTO, DESCRICAO_LANCAMENTO, TIPO_LANCAMENTO, VALOR_LANCAMENTO, NATUREZA_LANCAMENTO, VALOR_SALDO, NATUREZA_SALDO) não está preenchido.");
        dataValida(d[5], "DATA_LANCAMENTO", n, arq, "L-EX-03");
        String nat = d[10];
        if (!nat.matches("[CD*]")) rep.rep("L-EX-04", n, arq, "Natureza de Lancamento inválida. Valores possíveis: C, D e *(asterisco).");
        if (!d[12].matches("[CD]")) rep.rep("L-EX-05", n, arq, "Natureza de saldo inválida. Valores possíveis: C e D.");
        if (nat.equals("C") && Cnab.creditoInvalido(tipo)) rep.rep("L-EX-06", n, arq, "TIPO_LANCAMENTO inválido para NATUREZA_LANCAMENTO='C'. Veja tabela CNAB-240.");
        if (nat.equals("D") && Cnab.debitoInvalido(tipo)) rep.rep("L-EX-06", n, arq, "TIPO_LANCAMENTO inválido para NATUREZA_LANCAMENTO='D'. Veja tabela CNAB-240.");
        if (nat.equals("*") && Cnab.creditoDebitoInvalido(tipo)) rep.rep("L-EX-06", n, arq, "TIPO_LANCAMENTO inválido para NATUREZA_LANCAMENTO='*'. Veja tabela CNAB-240.");
        if (valor < 0 || saldo < 0) rep.rep("L-EX-07", n, arq, "VALOR_LANCAMENTO e VALOR_SALDO não podem ser negativos.");
        return new Ex(n, chave, d[5], tipo, valor, idConta);
    }

    // ---------- ORIGEM_DESTINO ----------
    private Od od(String[] d, int n, String fn) {
        String arq = "ORIGEM_DESTINO";
        int n0 = rep.size();
        long chave = compat() ? Long.valueOf(d[0]) : getLong(d[0], n, "CODIGO_CHAVE_OD", fn);   // COMPAT: lança => erro desconhecido
        long chaveExt = getLong(d[1], n, "CODIGO_CHAVE_EXTRATO", fn), valor = getLong(d[2], n, "VALOR_OPERACAO", fn);
        Long agencia = d[5].isEmpty() ? null : getLong(d[5], n, "NUMERO_AGENCIA", fn);
        long situacao = getLong(d[16], n, "SITUACAO_IDENTIFICACAO", fn);
        String tipoPessoa = d[8];
        boolean tpOk = Text.isEmpty(tipoPessoa) || tipoPessoa.matches("[12]");
        if (compat()) {
            if (!tpOk) rep.rep("L-OD-01", n, arq, "TIPO_CONTA_OD inválido. Valores possíveis: 1, 2, 3 ou 4.");   // bug: checa TIPO_PESSOA
        } else if (!Text.isEmpty(d[7]) && !d[7].matches("[1-4]")) {
            rep.rep("L-OD-01", n, arq, "TIPO_CONTA_OD inválido. Valores possíveis: 1, 2, 3 ou 4.");
        }
        if (!tpOk) rep.rep("L-OD-02", n, arq, "TIPO_PESSOA_OD inválido. Valores possíveis: 1 ou 2.");
        if (situacao != 0) rep.rep("L-OD-03", n, arq, "SITUACAO_IDENTIFICACAO inválido. Valor possível: 0.");
        if (cfg.receptor() != null && !Text.isEmpty(d[4]) && d[4].length() > 3) {                  // L-OD-04 (depende da versão do receptor)
            if (!cfg.receptorSuportaCnpjComoBanco())
                rep.rep("L-OD-04", n, arq, "Número Banco inválido. O mesmo deve ser composto de apenas 3 dígitos (COMPE).");
            else if (d[4].length() != 14)
                rep.rep("L-OD-04", n, arq, "Número Banco inválido. O mesmo deve ser composto de apenas 3 dígitos (COMPE) ou 14 dígitos(CNPJ).");
        }
        if (rep.size() == n0) {                                       // inserção: setInt(String) / numeric(3)
            for (String v : new String[]{d[7], d[8], d[4]}) {
                if (Text.isEmpty(v) || Text.intOrNull(v) != null) continue;
                if (compat()) throw new NumberFormatException(v);
                rep.rep("L-OD-05", n, arq, "Campo numérico inválido: " + v);
            }
        }
        return new Od(n, chave, chaveExt, valor, d[4], agencia, d[6], d[8], d[9], d[10], d[17]);
    }
}
