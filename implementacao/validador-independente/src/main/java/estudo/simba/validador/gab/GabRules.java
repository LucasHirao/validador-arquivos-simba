package estudo.simba.validador.gab;

import java.math.BigInteger;
import java.util.*;

import estudo.simba.validador.Db;
import estudo.simba.validador.Report;
import estudo.simba.validador.Text;

/** Regras de conteúdo do fluxo GAB (ValidarAtendimentoGab). O laço para no primeiro arquivo reprovado. */
public final class GabRules {
    private final Report rep;
    private final GabDb db;

    public GabRules(Report rep, GabDb db) { this.rep = rep; this.db = db; }

    public Map<String, Boolean> validar() {
        Map<String, Boolean> res = new LinkedHashMap<>();
        for (GabKind k : GabKind.values()) {
            switch (k) {
                case GAB109 -> clientesAusentesNoGab112("GAB109", db.g109);
                case GAB112 -> gab112();
                case GAB800 -> clientesAusentesNoGab112("GAB800", db.g800);
            }
            res.put(k.name(), rep.isEmpty());
            if (!rep.isEmpty()) break;
        }
        return res;
    }

    /** ValidarGab112NaoGab109SimRule / ValidarGab112NaoGab800SimRule: codigo_do_cliente not in (select ... from gab112) — comparação numérica. */
    private void clientesAusentesNoGab112(String arquivo, List<GabDb.Row> linhas) {
        Set<BigInteger> c112 = new HashSet<>();
        for (GabDb.Row r : db.g112) c112.add(r.num("CODIGO_DO_CLIENTE"));
        for (GabDb.Row r : linhas)
            if (!c112.contains(r.num("CODIGO_DO_CLIENTE")))
                rep.rep("R-" + arquivo, r.linha(), arquivo, "O cliente com o código " + r.num("CODIGO_DO_CLIENTE") + " não foi informado no arquivo gab112.");
    }

    private static String ltrim0(String s) {
        int i = 0;
        while (i < s.length() && s.charAt(i) == '0') i++;
        return s.substring(i);
    }

    private void gab112() {
        long total = db.investigados.size(), semRel = db.investigados.stream().filter(i -> i.teveRelacionamento() != 1).count();
        if (total == 0) throw new IllegalStateException("InvestigadoGabNenhumRelacionamentoRule: consulta sem linhas (INVESTIGADO vazio) — falha de infraestrutura no original");
        if (total == semRel) rep.rep("R-G112-1", 0, "Investigado", "Não foi encontrado relacionamento no arquivo investigado");
        Set<String> invCpfs = new HashSet<>();
        for (Db.Inv i : db.investigados) invCpfs.add(ltrim0(i.cpf()));
        for (GabDb.Row r : db.g112)
            if (!invCpfs.contains(ltrim0(r.get("DOCUMENTO_DO_CLIENTE"))))
                rep.rep("R-G112-2", r.linha(), "GAB112", "O CPF/CNPJ " + r.get("DOCUMENTO_DO_CLIENTE") + " não teve relacionamento com esta instituição no período do afastamento de sigilo.");
        Set<String> docs = new HashSet<>();
        for (GabDb.Row r : db.g112) docs.add(ltrim0(r.get("DOCUMENTO_DO_CLIENTE")));
        for (Db.Inv i : db.investigados)
            if (i.teveRelacionamento() == 1 && !docs.contains(ltrim0(i.cpf())))
                rep.rep("R-G112-3", 0, "GAB112", "O investigado com CPF/CNPJ " + i.cpf() + " não foi encontrado no arquivo cadastral(gab112).");
    }
}
