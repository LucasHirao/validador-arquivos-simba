package estudo.simba.validador;

import static estudo.simba.validador.Fixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LimiaresTest {
    @Test void arquivoVersionadoEquivaleAoPadraoInterno() throws Exception {
        assertEquals(Limiares.padrao(), Limiares.carregar(Path.of("..", "limiares-6.2.6.properties")));
    }

    @Test void limiarCustomizadoMudaOResultado(@TempDir Path d1, @TempDir Path d2, @TempDir Path cfgDir) throws Exception {
        // TED/DOC de 999.999 sem identificação: com o padrão (mín. 1.000.000) não dispara; com ident.valor_minimo=500000 dispara
        var m = mut(minimo(), "CONTAS", 4, "01011990");
        mut(m, "EXTRATO", 8, "120"); mut(m, "EXTRATO", 10, "D"); mut(m, "EXTRATO", 9, "999999"); mut(m, "EXTRATO", 11, "999999"); mut(m, "ORIGEM_DESTINO", 2, "999999");
        assertTrue(rodar(m, d1, Mode.COMPAT, false).ok());
        Path cfg = cfgDir.resolve("l.properties");
        Files.writeString(cfg, "ident.valor_minimo=500000\n");
        for (String k : ORDEM) Files.copy(d1.resolve(CASO + "_" + k + ".txt"), d2.resolve(CASO + "_" + k + ".txt"));
        Config c = Config.of(CASO, 1, Mode.COMPAT).comLimiares(Limiares.carregar(cfg));
        Pipeline.Result r = Pipeline.run(d2, c, List.of(new Db.Inv(CPF, 1, 1, null, null)));
        assertEquals("R-OD-01", r.violacoes().get(0).ruleId());
    }

    @Test void arquivoInvalidoEhRejeitado(@TempDir Path d) throws Exception {
        Path a = d.resolve("a.properties"), b = d.resolve("b.properties");
        Files.writeString(a, "ident.data_corte=01/01/2006\n");
        Files.writeString(b, "especie.faixas=2013-07-01;1000\n");
        assertThrows(IllegalArgumentException.class, () -> Limiares.carregar(a));
        assertThrows(IllegalArgumentException.class, () -> Limiares.carregar(b));
    }
}
