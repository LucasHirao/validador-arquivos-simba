package estudo.simba.validador;

import java.time.LocalDate;
import java.util.*;

import estudo.simba.validador.Db.*;

/**
 * Regras de conteúdo R-* (analise/04 §D), reescritas sobre estruturas em memória (no original são SQL em HSQLDB).
 * A ordem de execução e o texto das mensagens seguem validacao/ValidarAtendimento e rules/*.
 */
public final class ContentRules {
    private final Config cfg;
    private final Report rep;
    private final Db db;

    private final Limiares lim;

    public ContentRules(Config cfg, Report rep, Db db) { this.cfg = cfg; this.rep = rep; this.db = db; this.lim = cfg.limiares(); }

    /** Roda AGENCIAS..ORIGEM_DESTINO e PARA no primeiro arquivo que gerou erro (E-08). */
    public Map<String, Boolean> validar() {
        Map<String, Boolean> res = new LinkedHashMap<>();
        String[] nomes = {"AGENCIAS", "CONTAS", "TITULARES", "EXTRATO", "ORIGEM_DESTINO"};
        Runnable[] fases = {this::agencias, this::contas, this::titulares, this::extrato, this::origemDestino};
        for (int i = 0; i < nomes.length; i++) {
            fases[i].run();
            res.put(nomes[i], rep.isEmpty());
            if (!rep.isEmpty()) break;
        }
        return res;
    }

    private static String right(String s, int n) { return s.substring(s.length() - n); }

    private static String bbb(int banco) { return Text.padLeft(String.valueOf(banco), 3); }
    private static String aaaa(int ag) { return Text.padLeft(String.valueOf(ag), 4); }

    private String fmtConta(Ct c) {
        Ag ag = db.agencias.get(c.idAg() - 1);
        return "NUMERO_BANCO: " + bbb(ag.banco()) + ", NUMERO_AGENCIA: " + aaaa(ag.agencia()) + ", NUMERO_CONTA: " + c.conta() + ", TIPO_CONTA: " + c.tipo() + ".";
    }

    private static LocalDate d(String s) { return s == null || s.isEmpty() ? null : DateRules.of(s); }

    // ---------------- AGENCIAS ----------------
    private void agencias() {
        Map<List<Integer>, List<Integer>> g = new LinkedHashMap<>();
        for (Ag a : db.agencias) g.computeIfAbsent(List.of(a.banco(), a.agencia()), k -> new ArrayList<>()).add(a.linha());
        g.forEach((k, ls) -> {
            if (ls.size() > 1)
                rep.rep("R-AG-01", Collections.max(ls), "AGENCIAS", "Agência duplicada: NUMERO_BANCO: " + right("000" + k.get(0), 3) + ", NUMERO_AGENCIA: " + right("0000" + k.get(1), 4));
        });
    }

    // ---------------- CONTAS ----------------
    private void contas() {
        Set<Integer> extContas = new HashSet<>(), comTit = new HashSet<>(), comInv = new HashSet<>();
        for (Ex e : db.extratos) extContas.add(e.idConta());
        for (Ti t : db.titularidades) { comTit.add(t.idConta()); if ("1".equals(t.investigada())) comInv.add(t.idConta()); }
        String A = "CONTAS";
        for (Ct c : db.contas)                                                                                // R-CT-01
            if (c.conta().contains(".") || c.conta().contains("/") || c.conta().contains("\\") || c.conta().contains("-"))
                rep.rep("R-CT-01", c.linha(), A, "Conta com separador: " + c.conta());
        Map<List<Object>, List<Integer>> g = new LinkedHashMap<>();                                           // R-CT-02
        for (Ct c : db.contas) g.computeIfAbsent(List.of(c.idAg(), c.conta(), c.tipo()), k -> new ArrayList<>()).add(c.linha());
        g.forEach((k, ls) -> {
            if (ls.size() > 1) {
                int max = Collections.max(ls);
                rep.rep("R-CT-02", max, "null", "Conta duplicada - Linha: " + max + ", NUMERO_AGENCIA: " + db.agencias.get((Integer) k.get(0) - 1).agencia() + ", NUMERO_CONTA: " + k.get(1) + ", TIPO_CONTA: " + k.get(2));
            }
        });
        for (int i = 1; i <= db.contas.size(); i++) {                                                         // R-CT-03
            Ct c = db.contas.get(i - 1);
            if (c.mov() >= 1 && c.mov() <= 3 && !comTit.contains(i)) rep.rep("R-CT-03", c.linha(), A, "Conta sem registro correspondente em Titulares. " + fmtConta(c));
        }
        for (int i = 1; i <= db.contas.size(); i++) {                                                         // R-CT-04
            Ct c = db.contas.get(i - 1);
            if ((c.mov() == 1 || c.mov() == 2) && !comInv.contains(i)) rep.rep("R-CT-04", c.linha(), A, "Conta sem pessoa investigada. " + fmtConta(c));
        }
        if (db.contas.stream().noneMatch(c -> c.mov() == 1 || c.mov() == 2))                                  // R-CT-05
            rep.rep("R-CT-05", 0, A, "Atendimento sem nenhuma movimentação de conta.");
        for (Ti t : db.titularidades) {                                                                       // R-CT-06 (um erro por titularidade)
            Ct c = db.contas.get(t.idConta() - 1);
            if (c.mov() != 3 && !comInv.contains(t.idConta()))
                rep.rep("R-CT-06", c.linha(), A, "Conta com atributo MOVIMENTACAO_CONTA incorreto. Deve ser 3, pois não é conta de investigado. " + fmtConta(c));
        }
        Map<Integer, Integer> contT = new LinkedHashMap<>();                                                  // R-CT-07
        for (Ti t : db.titularidades) if ("T".equals(t.tipo())) contT.merge(t.idConta(), 1, Integer::sum);
        contT.forEach((id, q) -> {
            if (q > 1) { Ct c = db.contas.get(id - 1); rep.rep("R-CT-07", c.linha(), A, "Conta com mais de um Titular tipo T. " + fmtConta(c)); }
        });
        for (Ti t : db.titularidades) {                                                                       // R-CT-08
            Ct c = db.contas.get(t.idConta() - 1);
            if ("1".equals(t.investigada()) && c.mov() != 1 && c.mov() != 2)
                rep.rep("R-CT-08", c.linha(), A, "Conta com atributo MOVIMENTACAO_CONTA incorreto. Deve ser 1 ou 2, pois é um investigado. " + fmtConta(c));
        }
        for (int i = 1; i <= db.contas.size(); i++) {                                                         // R-CT-09 / R-CT-10
            Ct c = db.contas.get(i - 1);
            if (c.mov() == 1 && !extContas.contains(i))
                rep.rep("R-CT-09", c.linha(), A, "Conta definida com movimentação (MOVIMENTACAO_CONTA = 1), porém não possui lançamentos em EXTRATO. " + fmtConta(c));
            if (c.mov() == 2 && extContas.contains(i))
                rep.rep("R-CT-10", c.linha(), A, "Conta definida sem movimentação (MOVIMENTACAO_CONTA = 2), porém possui lançamentos em EXTRATO. " + fmtConta(c));
        }
    }

    // ---------------- TITULARES ----------------
    private void titulares() {
        String A = "TITULARES";
        Set<Integer> comT = new HashSet<>();
        for (Ti t : db.titularidades) if ("T".equals(t.tipo())) comT.add(t.idConta());
        for (Ti t : db.titularidades) {                                                                       // R-TI-01
            Pe p = db.pessoas.get(t.idPessoa() - 1);
            if ("1".equals(t.investigada()) && "T".equals(t.tipo()) && (Text.isEmpty(p.docNome()) || Text.isEmpty(p.docNum()) || Text.isEmpty(p.logradouro())
                || Text.isEmpty(p.cidade()) || Text.isEmpty(p.uf()) || Text.isEmpty(p.pais()) || Text.isEmpty(p.cep()) || Text.isEmpty(p.tel())))
                rep.rep("R-TI-01", t.linha(), A, "O titular de conta investigada deve ter os seguintes dados preenchidos: TIPO_PESSOA, CPF_CNPJ, NOME_PESSOA, NOME_DOC_IDENTIFICACAO, NUMERO_DOC_IDENTIFICACAO, ENDERECO_LOGRADOURO, CIDADE, UF, PAIS, CEP, TELEFONE_PESSOA");
        }
        for (Ti t : db.titularidades) {                                                                       // R-TI-02
            Ct c = db.contas.get(t.idConta() - 1);
            LocalDate ini = d(t.inicio()), fim = d(t.fim()), ab = d(c.abertura());
            if (ab != null && (ini.isBefore(ab) || (fim != null && fim.isBefore(ab))))
                rep.rep("R-TI-02", t.linha(), A, "A DATA_INICIO_RELACIONAMENTO ou DATA_FIM_RELACIONAMENTO está menor que a DATA_ABERTURA_CONTA (" + ab);
        }
        for (Ti t : db.titularidades) {                                                                       // R-TI-03
            Ct c = db.contas.get(t.idConta() - 1);
            LocalDate ini = d(t.inicio()), fim = d(t.fim()), en = d(c.encerramento());
            if (en != null && (ini.isAfter(en) || (fim != null && fim.isAfter(en))))
                rep.rep("R-TI-03", t.linha(), A, "A DATA_INICIO_RELACIONAMENTO ou DATA_FIM_RELACIONAMENTO está maior que a DATA_ENCERRAMENTO_CONTA (" + en);
        }
        Set<String> invArquivo = new HashSet<>(), invCad = new HashSet<>();
        for (Ti t : db.titularidades) if ("1".equals(t.investigada())) invArquivo.add(Text.padLeft(db.pessoas.get(t.idPessoa() - 1).cpf(), 14));
        for (Inv i : db.investigados) if (i.teveRelacionamento() == 1 && i.teveConta() == 1) invCad.add(Text.padLeft(i.cpf(), 14));
        for (Inv i : db.investigados)                                                                         // R-TI-04
            if (i.teveRelacionamento() == 1 && i.teveConta() == 1 && !invArquivo.contains(Text.padLeft(i.cpf(), 14)))
                rep.rep("R-TI-04", -1, A, "O CPF_CNPJ " + i.cpf() + " cadastrado no atendimento como investigado, não foi encontrado no arquivo TITULARES como PESSOA_INVESTIGADA = 1");
        for (Ti t : db.titularidades) {                                                                       // R-TI-05
            Pe p = db.pessoas.get(t.idPessoa() - 1);
            if (p.investigado() != null && p.investigado().teveRelacionamento() == 1 && p.investigado().teveConta() == 1 && "0".equals(t.investigada()))
                rep.rep("R-TI-05", p.linha(), A, "O CPF_CNPJ " + p.cpf() + " cadastrado no atendimento como investigado, deve possuir o atributo PESSOA_INVESTIGADA = 1");
        }
        for (Ti t : db.titularidades) {                                                                       // R-TI-06
            Pe p = db.pessoas.get(t.idPessoa() - 1);
            if ("1".equals(t.investigada()) && !invCad.contains(Text.padLeft(p.cpf(), 14)))
                rep.rep("R-TI-06", p.linha(), A, "O CPF_CNPJ " + p.cpf() + " informado como PESSOA_INVESTIGADA = 1 não foi cadastrado no atendimento.");
        }
        for (Ti t : db.titularidades)                                                                         // R-TI-07
            if ("1".equals(t.investigada()) && !comT.contains(t.idConta()))
                rep.rep("R-TI-07", t.linha(), A, "Conta investigada sem Titular do tipo T. " + fmtConta(db.contas.get(t.idConta() - 1)));
    }

    // ---------------- EXTRATO ----------------
    private void extrato() {
        String A = "EXTRATO";
        for (Ex e : db.extratos) {                                                                            // R-EX-01
            LocalDate dl = d(e.data());
            for (Ti t : db.titularidades) {
                if (t.idConta() != e.idConta() || !"1".equals(t.investigada())) continue;
                Inv inv = db.pessoas.get(t.idPessoa() - 1).investigado();
                if (inv != null && inv.inicioAfastamento() != null && inv.fimAfastamento() != null && (dl.isBefore(inv.inicioAfastamento()) || dl.isAfter(inv.fimAfastamento())))
                    rep.rep("R-EX-01", e.linha(), A, "Lançamento em " + DateRules.br(dl) + " fora do período de afastamento (" + DateRules.br(inv.inicioAfastamento()) + " a "
                        + DateRules.br(inv.fimAfastamento()) + ") do investigado: CPF_CNPJ (" + inv.cpf() + ")");
            }
        }
        for (Ex e : db.extratos) {                                                                            // R-EX-02
            Ct c = db.contas.get(e.idConta() - 1);
            LocalDate dl = d(e.data()), ab = d(c.abertura()), en = d(c.encerramento());
            if ((ab != null && dl.isBefore(ab)) || (en != null && dl.isAfter(en))) {
                if (ab == null && cfg.mode() == Mode.COMPAT)
                    throw new IllegalStateException("NullPointerException no original (ValidarExtratoForaPeriodoContaAbertaRule: DATA_ABERTURA_CONTA nula)");
                String abS = ab == null ? "sem data" : DateRules.br(ab);
                rep.rep("R-EX-02", e.linha(), A, "A DATA_LANCAMENTO (" + DateRules.br(dl) + ") está fora do período de DATA_ABERTURA_CONTA (" + abS
                    + (en != null ? ") e DATA_ENCERRAMENTO_CONTA (" + DateRules.br(en) : ") e sem DATA_ENCERRAMENTO_CONTA") + ").");
            }
        }
        Map<Long, List<Od>> odPorExt = odPorExtrato();
        for (Ex e : db.extratos)                                                                              // R-EX-03
            if (!odPorExt.containsKey(e.chave()))
                rep.rep("R-EX-03", e.linha(), A, "Lançamento em EXTRATO sem correspondente em ORIGEM_DESTINO. CODIGO_CHAVE_EXTRATO: " + e.chave());
        for (Ex e : db.extratos)                                                                              // R-EX-04
            if (db.contas.get(e.idConta() - 1).mov() == 3)
                rep.rep("R-EX-04", e.linha(), A, "No arquivo CONTAS, se o campo MOVIMENTAÇÃO DA CONTA estiver com o parâmetro \"3\"(conta não investigada) não pode haver nenhum registro para essa conta no arquivo EXTRATO");
        for (Ex e : db.extratos) {                                                                            // R-EX-05
            List<Od> l = odPorExt.get(e.chave());
            if (l != null && e.valor() != l.stream().mapToLong(Od::valor).sum())
                rep.rep("R-EX-05", e.linha(), A, "O Valor do lancamento em EXTRATO (CODIGO_CHAVE: " + e.chave() + ") não é igual ao somatório dos registros correspondentes em ORIGEM_DESTINO.");
        }
    }

    private Map<Long, List<Od>> odPorExtrato() {
        Map<Long, List<Od>> m = new LinkedHashMap<>();
        for (Od o : db.ods) m.computeIfAbsent(o.chaveExtrato(), k -> new ArrayList<>()).add(o);
        return m;
    }

    // ---------------- ORIGEM_DESTINO ----------------
    private record N(Od o, Integer banco, Long agencia, String conta, String cpf, String nome, String obs, String tipoPessoa) {
        static N of(Od o) {
            return new N(o, Text.isEmpty(o.banco()) ? null : Integer.valueOf(o.banco()), o.agencia(), Text.isEmpty(o.conta()) ? null : o.conta(),
                Text.isEmpty(o.cpf()) ? null : o.cpf(), Text.isEmpty(o.nome()) ? null : o.nome(), Text.isEmpty(o.obs()) ? null : o.obs(),
                Text.isEmpty(o.tipoPessoa()) ? null : o.tipoPessoa());
        }
        boolean contaZeros() { return conta != null && !conta.isEmpty() && conta.chars().allMatch(c -> c == '0'); }
        boolean identAusente() {
            return cpf == null || nome == null || banco == null || agencia == null || conta == null || banco == 0 || agencia == 0 || contaZeros();
        }
    }

    @FunctionalInterface
    private interface RegraOd { void aplicar(Od raw, N od, Ex extrato); }

    private void passe(Map<Long, Ex> ext, RegraOd regra) {
        for (Od raw : db.ods) {
            Ex e = ext.get(raw.chaveExtrato());
            if (e != null) regra.aplicar(raw, N.of(raw), e);
        }
    }

    private void origemDestino() {
        String A = "ORIGEM_DESTINO";
        Map<Long, Ex> ext = new HashMap<>();
        for (Ex e : db.extratos) ext.putIfAbsent(e.chave(), e);
        // Cada regra é uma consulta SEPARADA sobre todos os registros no original (ordem: R-OD-01, 02, 03 | E1, E2; depois 04).
        if (!cfg.eleitoral()) {
            passe(ext, (raw, od, e) -> {                                                                                                       // R-OD-01
                boolean naoCorr = od.agencia != null && od.agencia == 9999 && "99999999999999999999".equals(od.conta) && od.obs != null
                    && od.obs.toUpperCase(Locale.ROOT).equals("NAO-CORRENTISTA");
                if (lim.tiposTedDoc().contains(e.tipo()) && !d(e.data()).isBefore(lim.identDataCorte()) && e.valor() >= lim.identValorMinimo() && od.identAusente() && !naoCorr)
                    rep.rep("R-OD-01", raw.linha(), A, "É OBRIGATÓRIA a identicação do depositante/favorecido de Transferências Interbancária (TED, DOC) nos campos CPF_CNPJ_OD, NOME_PESSOA_OD, NUMERO_BANCO_OD, NUMERO_AGENCIA_OD e NUMERO_CONTA_OD. Caso o depositante/favorecido não possua conta no banco, os campos NUMERO_AGENCIA_OD, NUMERO_CONTA_OD e OBSERVACAO_OD devem possuir, respectivamente, os seguintes valores: 9999, 99999999999999999999 e NAO-CORRENTISTA.");
            });
            passe(ext, (raw, od, e) -> {                                                                                                       // R-OD-02
                if (lim.tiposMesmoBanco().contains(e.tipo()) && !d(e.data()).isBefore(lim.identDataCorte()) && e.valor() >= lim.identValorMinimo() && od.identAusente())
                    rep.rep("R-OD-02", raw.linha(), A, "É OBRIGATÓRIA a identicação do depositante/favorecido de Transferências entre Contas nos campos CPF_CNPJ_OD, NOME_PESSOA_OD, NUMERO_BANCO_OD, NUMERO_AGENCIA_OD e NUMERO_CONTA_OD.");
            });
            passe(ext, (raw, od, e) -> {                                                                                                       // R-OD-03
                LocalDate dl = d(e.data());
                long valor = e.valor();
                if (lim.tiposEspecie().contains(e.tipo()) && lim.faixasEspecie().stream().anyMatch(f -> !dl.isBefore(f.data()) && valor >= f.valorMinimo())
                    && od.nome == null && (od.obs == null || !od.obs.toUpperCase(Locale.ROOT).equals("099 - NEGATIVA DE IDENTIFICACAO DO PORTADOR")))
                    rep.rep("R-OD-03", raw.linha(), A, "É obrigatória a identificação do depositante/favorecido de transações em espécie, campo NOME_PESSOA_OD. Caso o depositante/favorecido recusou se identificar, o campo OBSERVACAO_OD deve conter o seguinte valor: '099 - NEGATIVA DE IDENTIFICACAO DO PORTADOR'.");
            });
        } else {
            passe(ext, (raw, od, e) -> {                                                                                                       // R-OD-E1
                long tipo = e.tipo();
                if (lim.tiposEleitoralTransferencia().contains(tipo) && (od.cpf == null || od.banco == null || od.agencia == null || od.conta == null
                        || od.banco == 0 || od.agencia == 0 || od.contaZeros()))
                    rep.rep("R-OD-E1", raw.linha(), A, "É OBRIGATÓRIA a identicação do depositante/favorecido de transferências no mesmo banco ou transferências interbancárias (TED/DOC) nos campos CPF_CNPJ_OD, NUMERO_BANCO_OD, NUMERO_AGENCIA_OD e NUMERO_CONTA_OD.");
            });
            passe(ext, (raw, od, e) -> {                                                                                                       // R-OD-E2
                if (e.tipo() == lim.tipoEleitoralDeposito() && e.valor() >= lim.eleitoralDepositoValorMinimo() && od.cpf == null)
                    rep.rep("R-OD-E2", raw.linha(), A, "É OBRIGATÓRIA a identicação do depositante/favorecido de depósito em cheque e/ou dinheiro no campo CPF_CNPJ_OD.");
            });
        }
        for (Od raw : db.ods) {                                                                               // R-OD-04
            N od = N.of(raw);
            Ex e = ext.get(raw.chaveExtrato());
            if (e == null || !lim.tiposCpfCnpjOd().contains(e.tipo()) || d(e.data()).isBefore(lim.identDataCorte()) || od.cpf == null) continue;
            String tp = od.tipoPessoa, cpf = od.cpf;
            boolean ok;
            if ("1".equals(tp)) ok = CpfCnpj.validarCpf(cpf);
            else if ("2".equals(tp)) { cpf = Text.padLeft(cpf, 14); ok = CpfCnpj.validarCnpj(cpf); }       // o original reatribui antes de reportar
            else {
                cpf = Text.padLeft(cpf, 14);
                ok = CpfCnpj.validarCnpj(cpf);
                if (!ok) { cpf = cpf.substring(3); ok = CpfCnpj.validarCpf(cpf); }
            }
            if (!ok)
                rep.rep("R-OD-04", raw.linha(), A, "O campo CPF_CNPJ_PESSOA_OD com o valor " + cpf
                    + (Text.isEmpty(tp) ? " é inválido." : " é inválido para TIPO_PESSOA_OD = " + tp + " (" + ("1".equals(tp) ? "Pessoa Física" : "Pessoa Jurídica") + ")."));
        }
    }
}
