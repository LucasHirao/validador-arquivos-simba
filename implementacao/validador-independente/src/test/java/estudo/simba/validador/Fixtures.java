package estudo.simba.validador;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Caso mínimo SINTÉTICO (tudo fictício; CPF 52998224725 é vetor público de teste). */
final class Fixtures {
    static final String CASO = "001-TST-000001-00", CPF = "52998224725";
    static final List<String> ORDEM = List.of("AGENCIAS", "CONTAS", "TITULARES", "EXTRATO", "ORIGEM_DESTINO");

    static String l(String... c) { return String.join("\t", c); }

    static Map<String, List<String>> minimo() {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put("AGENCIAS", new ArrayList<>(List.of(l("001", "0001", "AGENCIA TESTE", "RUA TESTE 100", "CIDADE TESTE", "DF", "BRASIL", "70000000", "6133334444", "01012000", ""))));
        m.put("CONTAS", new ArrayList<>(List.of(l("001", "0001", "12345", "1", "01012010", "", "1"))));
        m.put("TITULARES", new ArrayList<>(List.of(l("001", "0001", "12345", "1", "T", "1", "1", CPF, "FULANO DE TESTE", "RG", "1234567", "RUA X", "CIDADE", "DF", "BRASIL", "70000000", "6133334444", "", "", "01012010", ""))));
        m.put("EXTRATO", new ArrayList<>(List.of(l("1", "001", "0001", "12345", "1", "15032020", "DOC1", "DEPOSITO", "201", "50000", "C", "50000", "C", "AG"))));
        m.put("ORIGEM_DESTINO", new ArrayList<>(List.of(l("1", "1", "50000", "DOC1", "", "", "", "", "", "", "", "", "", "", "", "", "0", ""))));
        return m;
    }

    /** Altera um campo (índice 0-based) de uma linha do arquivo. */
    static Map<String, List<String>> mut(Map<String, List<String>> m, String arq, int campo, String valor) {
        String[] c = m.get(arq).get(0).split("\t", -1);
        c[campo] = valor;
        m.get(arq).set(0, String.join("\t", c));
        return m;
    }

    static Pipeline.Result rodar(Map<String, List<String>> arquivos, Path tmp, Mode modo, boolean eleitoral) throws IOException {
        String caso = eleitoral ? "001-MPE-000001-00" : CASO;
        for (String k : ORDEM)
            Files.write(tmp.resolve(caso + "_" + k + ".txt"), (String.join("\r\n", arquivos.get(k)) + (arquivos.get(k).isEmpty() ? "" : "\r\n")).getBytes(StandardCharsets.ISO_8859_1));
        return Pipeline.run(tmp, Config.of(caso, 1, modo), List.of(new Db.Inv(CPF, 1, 1, null, null)));
    }

    static List<String> msgs(Pipeline.Result r) { return r.violacoes().stream().map(Violation::mensagem).toList(); }
}
