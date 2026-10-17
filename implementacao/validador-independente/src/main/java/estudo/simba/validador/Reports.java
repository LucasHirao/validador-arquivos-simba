package estudo.simba.validador;

import java.util.Map;

/** Formatação do resultado: texto idêntico ao do original, JSON e CSV. */
public final class Reports {
    private Reports() {}

    public static String texto(Pipeline.Result r) {
        StringBuilder sb = new StringBuilder();
        r.violacoes().forEach(v -> sb.append(v.pretty()).append('\n'));
        if (r.ok()) sb.append("Validação concluída sem erros.\n");
        return sb.toString();
    }

    private static String esc(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\t", "\\t") + "\"";
    }

    public static String json(Pipeline.Result r) {
        StringBuilder sb = new StringBuilder("{\"ok\":" + r.ok() + ",\"conteudo\":");
        if (r.conteudo() == null) sb.append("null");
        else {
            sb.append('{');
            int i = 0;
            for (Map.Entry<String, Boolean> e : r.conteudo().entrySet()) sb.append(i++ > 0 ? "," : "").append(esc(e.getKey())).append(':').append(e.getValue());
            sb.append('}');
        }
        sb.append(",\"violacoes\":[");
        int i = 0;
        for (Violation v : r.violacoes())
            sb.append(i++ > 0 ? "," : "").append("{\"regra\":").append(esc(v.ruleId())).append(",\"arquivo\":").append(esc(v.arquivo()))
              .append(",\"linha\":").append(v.linha()).append(",\"mensagem\":").append(esc(v.mensagem())).append('}');
        return sb.append("]}").toString();
    }

    public static String csv(Pipeline.Result r) {
        StringBuilder sb = new StringBuilder("regra;arquivo;linha;mensagem\n");
        r.violacoes().forEach(v -> sb.append(v.ruleId()).append(';').append(v.arquivo()).append(';').append(v.linha()).append(';')
            .append('"').append(v.mensagem().replace("\"", "\"\"")).append("\"\n"));
        return sb.toString();
    }

    /** Linhas "conteúdo" no formato usado pela suíte diferencial (comparável a esperado.txt). */
    public static String comparavel(Pipeline.Result r) {
        StringBuilder sb = new StringBuilder();
        r.violacoes().forEach(v -> sb.append(v.pretty()).append('\n'));
        sb.append("#CONTEUDO ");
        if (r.conteudo() == null) sb.append("null");
        else {
            int i = 0;
            for (Map.Entry<String, Boolean> e : r.conteudo().entrySet()) sb.append(i++ > 0 ? "," : "").append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.append('\n').toString();
    }
}
