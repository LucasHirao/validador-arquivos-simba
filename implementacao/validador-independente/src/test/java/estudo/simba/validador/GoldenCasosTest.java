package estudo.simba.validador;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Compara o modo COMPAT com esperado.txt de cada cenário em analise/testes/casos (gerado pelo modelo Python). */
class GoldenCasosTest {
    @Test void casosSinteticos() throws Exception {
        Path casos = Path.of("../../analise/testes/casos");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isDirectory(casos), "pasta de casos ausente");
        int n = 0;
        try (Stream<Path> s = Files.list(casos)) {
            for (Path d : s.filter(Files::isDirectory).sorted().toList()) {
                String caso = Cli.casoDe(d);
                Pipeline.Result r = Pipeline.run(d, Config.of(caso, 1, Mode.COMPAT), Cli.investigados(d.resolve("investigados.tsv")));
                assertEquals(Files.readString(d.resolve("esperado.txt"), StandardCharsets.UTF_8), Reports.comparavel(r), "cenário " + d.getFileName());
                n++;
            }
        }
        assertTrue(n >= 16);
    }

    /** Um caso mínimo por regra observada no ORIGINAL (esperado.txt = saída do original). Ver docs/MAPA_COBERTURA_REGRAS.md. */
    @Test void casosPorRegraObservadosNoOriginal() throws Exception {
        Path casos = Path.of("../../analise/testes/casos-por-regra");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isDirectory(casos), "pasta casos-por-regra ausente");
        int n = 0;
        try (Stream<Path> s = Files.list(casos)) {
            for (Path d : s.filter(Files::isDirectory).sorted().toList()) {
                String caso = Cli.casoDe(d);
                Pipeline.Result r = Pipeline.run(d, Config.of(caso, 1, Mode.COMPAT), Cli.investigados(d.resolve("investigados.tsv")));
                assertEquals(Files.readString(d.resolve("esperado.txt"), StandardCharsets.UTF_8), Reports.comparavel(r), "regra " + d.getFileName());
                n++;
            }
        }
        assertTrue(n >= 71);
    }
}
