package estudo.simba.validador;

import static estudo.simba.validador.Fixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Comportamento comum aos dois modos. */
class CasoMinimoTest {
    @TempDir Path tmp;

    @Test void casoMinimoValidoNosDoisModos() throws Exception {
        for (Mode m : Mode.values()) {
            Pipeline.Result r = rodar(minimo(), tmp.resolve("x").toFile().mkdirs() ? tmp.resolve("x") : tmp, m, false);
            assertTrue(r.ok(), m + " " + msgs(r));
            assertEquals(List.of("AGENCIAS", "CONTAS", "TITULARES", "EXTRATO", "ORIGEM_DESTINO"), List.copyOf(r.conteudo().keySet()));
        }
    }

    @Test void colunasETamanho(@TempDir Path d1, @TempDir Path d2) throws Exception {
        var a = minimo(); a.put("AGENCIAS", new java.util.ArrayList<>(List.of("001\t0001\tX")));
        assertEquals(List.of("Quantidade de colunas inválida. Esperado: 11 Atual: 3"), msgs(rodar(a, d1, Mode.COMPAT, false)));
        assertEquals(List.of("Tamanho do campo ENDERECO_UF excedido. Tamanho máximo permitido: 2"), msgs(rodar(mut(minimo(), "AGENCIAS", 5, "DFX"), d2, Mode.COMPAT, false)));
    }

    @Test void ufCaseSensitive(@TempDir Path d1, @TempDir Path d2, @TempDir Path d3) throws Exception {
        assertTrue(rodar(mut(minimo(), "AGENCIAS", 5, "--"), d1, Mode.COMPAT, false).ok());
        assertEquals(List.of("ENDERECO_UF:df não é uma sigla válida."), msgs(rodar(mut(minimo(), "AGENCIAS", 5, "df"), d2, Mode.COMPAT, false)));
        assertTrue(rodar(mut(minimo(), "AGENCIAS", 5, ""), d3, Mode.COMPAT, true).violacoes().stream().noneMatch(v -> v.ruleId().equals("L-AG-06")));
    }

    @Test void regrasDeConteudo(@TempDir Path d1, @TempDir Path d2, @TempDir Path d3) throws Exception {
        Pipeline.Result r = rodar(mut(minimo(), "CONTAS", 6, "2"), d1, Mode.COMPAT, false);
        assertEquals("R-CT-10", r.violacoes().get(0).ruleId());
        assertEquals(java.util.Map.of("AGENCIAS", true, "CONTAS", false), r.conteudo());     // laço para no 1º arquivo reprovado
        var ag = minimo(); ag.get("AGENCIAS").add(ag.get("AGENCIAS").get(0));
        assertEquals(List.of("Agência duplicada: NUMERO_BANCO: 001, NUMERO_AGENCIA: 0001"), msgs(rodar(ag, d2, Mode.COMPAT, false)));
        assertEquals("R-EX-02", rodar(mut(minimo(), "EXTRATO", 5, "15032005"), d3, Mode.COMPAT, false).violacoes().get(0).ruleId());
    }

    @Test void tedAcimaDoLimiteSemIdentificacao(@TempDir Path d) throws Exception {
        var m = mut(minimo(), "CONTAS", 4, "01011990");
        mut(m, "EXTRATO", 8, "120"); mut(m, "EXTRATO", 10, "D"); mut(m, "EXTRATO", 9, "1000000"); mut(m, "EXTRATO", 11, "1000000"); mut(m, "ORIGEM_DESTINO", 2, "1000000");
        Pipeline.Result r = rodar(m, d, Mode.COMPAT, false);
        assertEquals(1, r.violacoes().size());
        assertEquals("R-OD-01", r.violacoes().get(0).ruleId());
    }

    @Test void eleitoralTrocaRegrasDeOd(@TempDir Path d1, @TempDir Path d2) throws Exception {
        assertEquals("R-OD-E2", rodar(minimo(), d1, Mode.COMPAT, true).violacoes().get(0).ruleId());
        assertTrue(rodar(minimo(), d2, Mode.COMPAT, false).ok());
    }

    @Test void arquivoAusenteEVazio(@TempDir Path d) throws Exception {
        Pipeline.Result r = Pipeline.run(d, Config.of(CASO, 1, Mode.COMPAT), List.of());
        assertEquals("Arquivo esperado (" + CASO + "_AGENCIAS.txt) não encontrado.", r.violacoes().get(0).mensagem());
    }

    /** L-OD-04 é inalcançável: NUMERO_BANCO_OD tem tamanho máximo 3 (E-04) e a linha com erro estrutural não chega a validar(). */
    @Test void lOd04_inalcancavelPorCausaDeE04(@TempDir Path d1, @TempDir Path d2, @TempDir Path d3) throws Exception {
        var m = mut(minimo(), "ORIGEM_DESTINO", 4, "1234");
        write(d1, m); write(d2, m); write(d3, m);
        var inv = List.of(new Db.Inv(CPF, 1, 1, null, null));
        String e04 = "Tamanho do campo NUMERO_BANCO_OD excedido. Tamanho máximo permitido: 3";
        for (var r : List.of(Pipeline.run(d1, Config.of(CASO, 1, Mode.COMPAT, "3.0"), inv), Pipeline.run(d2, Config.of(CASO, 1, Mode.COMPAT, "4.0"), inv),
                             Pipeline.run(d3, Config.of(CASO, 1, Mode.COMPAT, null), inv))) {
            assertEquals(List.of(e04), msgs(r));
            assertEquals("E-04", r.violacoes().get(0).ruleId());
        }
    }

    private static void write(Path d, java.util.Map<String, List<String>> a) throws Exception {
        for (String k : ORDEM) java.nio.file.Files.write(d.resolve(CASO + "_" + k + ".txt"), (String.join("\r\n", a.get(k)) + "\r\n").getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
    }
}
