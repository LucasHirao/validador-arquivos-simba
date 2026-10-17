package estudo.simba.validador;

import static estudo.simba.validador.Fixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Diferenças documentadas entre COMPAT (bug-a-bug com o oficial) e ESTRITO (corrigido) — analise/04 §E. */
class ModosTest {
    private static List<String> linhasAgencia(String uf1, String uf2, String curta, String longo) {
        String[] base = minimo().get("AGENCIAS").get(0).split("\t", -1);
        String[] a = base.clone(), b = base.clone(), d = base.clone();
        a[5] = uf1; b[5] = uf2; d[2] = longo;
        return new ArrayList<>(List.of(String.join("\t", a), String.join("\t", b), curta, String.join("\t", d)));
    }

    @Test void e06_erroGlobalSoNoCompat(@TempDir Path c, @TempDir Path e) throws Exception {
        var m1 = minimo(); m1.put("AGENCIAS", linhasAgencia("XX", "YY", "001\t0001", "N".repeat(51)));
        var m2 = minimo(); m2.put("AGENCIAS", linhasAgencia("XX", "YY", "001\t0001", "N".repeat(51)));
        assertEquals(List.of("ENDERECO_UF:XX não é uma sigla válida.", "Quantidade de colunas inválida. Esperado: 11 Atual: 2",
            "Tamanho do campo NOME_AGENCIA excedido. Tamanho máximo permitido: 50"), msgs(rodar(m1, c, Mode.COMPAT, false)));
        assertEquals(List.of("ENDERECO_UF:XX não é uma sigla válida.", "ENDERECO_UF:YY não é uma sigla válida.", "Quantidade de colunas inválida. Esperado: 11 Atual: 2",
            "Tamanho do campo NOME_AGENCIA excedido. Tamanho máximo permitido: 50"), msgs(rodar(m2, e, Mode.ESTRITO, false)));
    }

    @Test void e05b_primeiroCampoVazio(@TempDir Path c, @TempDir Path e) throws Exception {
        var m1 = minimo(); m1.get("AGENCIAS").set(0, m1.get("AGENCIAS").get(0).substring(3));   // NUMERO_BANCO vazio (linha começa com TAB)
        var m2 = minimo(); m2.get("AGENCIAS").set(0, m2.get("AGENCIAS").get(0).substring(3));
        assertEquals(List.of("Quantidade de colunas inválida. Esperado: 11 Atual: 10"), msgs(rodar(m1, c, Mode.COMPAT, false)));
        List<String> est = msgs(rodar(m2, e, Mode.ESTRITO, false));
        assertTrue(est.get(0).startsWith("Um dos campos obrigatórios (NUMERO_BANCO"));    // coluna vazia reconhecida como tal
    }

    @Test void lAg04_bancoVazioGeraDoisErrosNoCompat(@TempDir Path c, @TempDir Path e) throws Exception {
        List<String> compat = msgs(rodar(mut(minimo(), "AGENCIAS", 0, " "), c, Mode.COMPAT, false));
        assertEquals(2, compat.size());
        assertEquals("Erro desconhecido. Consulte o log do sistema.", compat.get(1));
        assertEquals(1, msgs(rodar(mut(minimo(), "AGENCIAS", 0, " "), e, Mode.ESTRITO, false)).size());
    }

    @Test void lPe02_rotuloEExtratoSoNoCompat(@TempDir Path c, @TempDir Path e) throws Exception {
        assertEquals("EXTRATO", rodar(mut(minimo(), "TITULARES", 18, "31022020"), c, Mode.COMPAT, false).violacoes().get(0).arquivo());
        assertEquals("TITULARES", rodar(mut(minimo(), "TITULARES", 18, "31022020"), e, Mode.ESTRITO, false).violacoes().get(0).arquivo());
    }

    @Test void lPe03_cpfSoZeros(@TempDir Path c, @TempDir Path e) throws Exception {
        assertEquals(List.of("Erro desconhecido. Consulte o log do sistema."), msgs(rodar(mut(minimo(), "TITULARES", 7, "00000000000"), c, Mode.COMPAT, false)));
        assertEquals(List.of("O CPF_CNPJ_PESSOA: 00000000000 está inválido."), msgs(rodar(mut(minimo(), "TITULARES", 7, "00000000000"), e, Mode.ESTRITO, false)));
    }

    @Test void lOd01_tipoContaOd(@TempDir Path c, @TempDir Path e) throws Exception {
        // COMPAT: TIPO_PESSOA_OD inválido gera DUAS mensagens (uma com texto de conta); ESTRITO: só a de pessoa
        assertEquals(2, rodar(mut(minimo(), "ORIGEM_DESTINO", 8, "3"), c, Mode.COMPAT, false).violacoes().size());
        assertEquals(List.of("TIPO_PESSOA_OD inválido. Valores possíveis: 1 ou 2."), msgs(rodar(mut(minimo(), "ORIGEM_DESTINO", 8, "3"), e, Mode.ESTRITO, false)));
    }
}
