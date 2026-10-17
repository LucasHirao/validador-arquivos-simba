package estudo.simba.validador;

import java.nio.file.*;
import java.util.stream.Stream;

/** Para cada subpasta de cenário imprime: cenario TAB regra TAB arquivo TAB linha TAB mensagem (modo COMPAT). */
public final class MapaRegras {
    public static void main(String[] a) throws Exception {
        try (Stream<Path> s = Files.list(Path.of(a[0]))) {
            for (Path d : s.filter(Files::isDirectory).sorted().toList()) {
                try {
                    Config cfg = Config.of(Cli.casoDe(d), 1, Mode.COMPAT, null);
                    Pipeline.Result r = Pipeline.run(d, cfg, Cli.investigados(d.resolve("investigados.tsv")));
                    if (r.violacoes().isEmpty()) System.out.println(d.getFileName() + "\t-\t-\t0\t-");
                    for (Violation v : r.violacoes())
                        System.out.println(d.getFileName() + "\t" + v.ruleId() + "\t" + v.arquivo() + "\t" + v.linha() + "\t" + v.mensagem().replace("\t", " ").replace("\n", " "));
                } catch (Exception e) { System.out.println(d.getFileName() + "\t#ERRO\t-\t0\t" + e); }
            }
        }
    }
}
