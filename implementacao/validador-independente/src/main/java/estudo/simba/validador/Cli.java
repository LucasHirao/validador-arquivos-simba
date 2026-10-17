package estudo.simba.validador;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Stream;

/**
 * Uso: java -jar validador-independente.jar --pasta DIR --caso 001-XXX-000001-00 [--banco 1] [--modo compat|estrito]
 *        [--investigados arquivo.tsv] [--saida texto|json|csv] [--receptor major.minor] [--limiares arquivo.properties] [--tipo bancario|corretora --layout 1|2 --defeito-gab true|false]
 *      java -jar ... --lote DIR_DE_CASOS [--modo compat|estrito]     (grava saida.txt em cada subpasta)
 * Investigados (TAB): TIPO_PESSOA, CPF_CNPJ, NOME, TEVE_RELACIONAMENTO, TEVE_CONTA, INICIO_AFASTAMENTO(ddMMyyyy), FIM_AFASTAMENTO(ddMMyyyy)
 * Código de saída: 0 = sem erros; 1 = há violações; 2 = uso incorreto.
 */
public final class Cli {
    private Cli() {}

    static List<Db.Inv> investigados(Path f) throws IOException {
        List<Db.Inv> l = new ArrayList<>();
        if (f == null || !Files.exists(f)) return l;
        for (String linha : Files.readAllLines(f, StandardCharsets.ISO_8859_1)) {
            if (linha.isBlank()) continue;
            String[] c = linha.split("\t", -1);
            LocalDate ini = c.length > 5 && !c[5].isBlank() ? DateRules.of(c[5].trim()) : null, fim = c.length > 6 && !c[6].isBlank() ? DateRules.of(c[6].trim()) : null;
            l.add(new Db.Inv(c[1].trim(), Integer.parseInt(c[3].trim()), Integer.parseInt(c[4].trim()), ini, fim));
        }
        return l;
    }

    static Config comLimiares(Config c, Map<String, String> o) throws IOException {
        return o.containsKey("--limiares") ? c.comLimiares(Limiares.carregar(Path.of(o.get("--limiares")))) : c;
    }

    static String casoDe(Path pasta) throws IOException { return casoDe(pasta, "AGENCIAS"); }

    static String casoDe(Path pasta, String sufixoArquivo) throws IOException {
        String suf = "_" + sufixoArquivo + ".txt";
        try (Stream<Path> s = Files.list(pasta)) {
            return s.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(suf)).map(n -> n.substring(0, n.length() - suf.length())).findFirst().orElseThrow();
        }
    }

    public static void main(String[] a) throws IOException {
        Map<String, String> o = new HashMap<>();
        for (int i = 0; i + 1 < a.length; i += 2) o.put(a[i], a[i + 1]);
        Mode modo = "estrito".equalsIgnoreCase(o.getOrDefault("--modo", "compat")) ? Mode.ESTRITO : Mode.COMPAT;
        int banco = Integer.parseInt(o.getOrDefault("--banco", "1"));
        boolean corretora = "corretora".equalsIgnoreCase(o.getOrDefault("--tipo", "bancario"));
        Boolean defeito = o.containsKey("--defeito-gab") ? Boolean.valueOf(o.get("--defeito-gab")) : null;
        if (corretora && o.containsKey("--lote")) {      // cada subpasta traz layout.txt (1|2)
            try (Stream<Path> s = Files.list(Path.of(o.get("--lote")))) {
                for (Path d : s.filter(Files::isDirectory).sorted().toList()) {
                    int layout = Integer.parseInt(Files.readString(d.resolve("layout.txt")).trim());
                    Pipeline.Result r = estudo.simba.validador.gab.GabPipeline.run(d, estudo.simba.validador.gab.GabConfig.of(casoDe(d, "GAB112"), layout, modo, defeito),
                        investigados(d.resolve("investigados.tsv")));
                    Files.writeString(d.resolve("saida-" + modo.name().toLowerCase() + (defeito == null ? "" : defeito ? "-defeito" : "-semdefeito") + ".txt"), Reports.comparavel(r), StandardCharsets.UTF_8);
                }
            }
            return;
        }
        if (o.containsKey("--lote")) {
            try (Stream<Path> s = Files.list(Path.of(o.get("--lote")))) {
                for (Path d : s.filter(Files::isDirectory).sorted().toList()) {
                    Config cfg = comLimiares(Config.of(casoDe(d), banco, modo, o.get("--receptor")), o);
                    Pipeline.Result r = Pipeline.run(d, cfg, investigados(d.resolve("investigados.tsv")));
                    Files.writeString(d.resolve("saida-" + modo.name().toLowerCase() + ".txt"), Reports.comparavel(r), StandardCharsets.UTF_8);
                }
            }
            return;
        }
        if (!o.containsKey("--pasta") || !o.containsKey("--caso")) {
            System.err.println("uso: --pasta DIR --caso CASO [--banco N] [--modo compat|estrito] [--investigados F] [--saida texto|json|csv]  |  --lote DIR");
            System.exit(2);
        }
        Pipeline.Result r = corretora
            ? estudo.simba.validador.gab.GabPipeline.run(Path.of(o.get("--pasta")), estudo.simba.validador.gab.GabConfig.of(o.get("--caso"), Integer.parseInt(o.getOrDefault("--layout", "1")), modo, defeito),
                investigados(o.containsKey("--investigados") ? Path.of(o.get("--investigados")) : null))
            : Pipeline.run(Path.of(o.get("--pasta")), comLimiares(Config.of(o.get("--caso"), banco, modo, o.get("--receptor")), o),
            investigados(o.containsKey("--investigados") ? Path.of(o.get("--investigados")) : null));
        String saida = o.getOrDefault("--saida", "texto");
        System.out.print(switch (saida) { case "json" -> Reports.json(r) + "\n"; case "csv" -> Reports.csv(r); default -> Reports.texto(r); });
        System.exit(r.ok() ? 0 : 1);
    }
}
