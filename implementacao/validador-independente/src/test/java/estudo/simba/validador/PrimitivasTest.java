package estudo.simba.validador;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import org.junit.jupiter.api.Test;

class PrimitivasTest {
    @Test void splitCompatDescartaCampoVazioInicial() {
        assertArrayEquals(new String[]{"b", "c"}, TabSplitter.split("\tb\tc", Mode.COMPAT));
        assertArrayEquals(new String[]{"a", "", "c"}, TabSplitter.split("a\t\tc", Mode.COMPAT));
        assertArrayEquals(new String[]{"a", "b", ""}, TabSplitter.split("a\tb\t", Mode.COMPAT));
        assertArrayEquals(new String[]{""}, TabSplitter.split("", Mode.COMPAT));
    }

    @Test void splitEstritoPreservaVazios() {
        assertArrayEquals(new String[]{"", "b", "c"}, TabSplitter.split("\tb\tc", Mode.ESTRITO));
        assertArrayEquals(new String[]{"a", "b", "", ""}, TabSplitter.split("a\tb\t\t", Mode.ESTRITO));
    }

    @Test void verificaData() {
        for (String ok : new String[]{"01012000", "29022024", "31121999", "01011900"}) assertTrue(DateRules.verificaData(ok), ok);
        for (String no : new String[]{"", "1012000", "010120000", "29022023", "00012000", "32012000", "01132000", "31042021", "01011899", "ab012000"})
            assertFalse(DateRules.verificaData(no), no);
    }

    @Test void cpfCnpjVetores() {
        assertTrue(CpfCnpj.validarCpf("52998224725"));
        assertFalse(CpfCnpj.validarCpf("52998224724"));
        assertFalse(CpfCnpj.validarCpf("11111111111"));
        assertTrue(CpfCnpj.validarCnpj("11222333000181"));
        assertTrue(CpfCnpj.validarCnpj("12ABC34501DE35"));       // CNPJ alfanumérico (exemplo público)
        assertFalse(CpfCnpj.validarCnpj("00000000000000"));
    }

    /** Algoritmo oficial independente (pesos 2..9 da direita para a esquerda; valor = ord - 48). */
    private static String oficialCnpj(String b12) {
        String n = b12, dvs = "";
        for (int k = 0; k < 2; k++) {
            int soma = 0, peso = 2;
            for (int i = n.length() - 1; i >= 0; i--) { soma += (n.charAt(i) - '0') * peso; peso = peso == 9 ? 2 : peso + 1; }
            int r = soma % 11, d = r < 2 ? 0 : 11 - r;
            dvs += d; n += d;
        }
        return dvs;
    }

    @Test void cnpjAlfanumericoContraAlgoritmoOficial() {
        Random rnd = new Random(1);
        String alfa = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        for (int i = 0; i < 3000; i++) {
            StringBuilder b = new StringBuilder();
            for (int j = 0; j < 12; j++) b.append(alfa.charAt(rnd.nextInt(alfa.length())));
            if (b.chars().distinct().count() == 1) continue;
            assertEquals(oficialCnpj(b.toString()), CpfCnpj.dvCnpj(b.toString()));
            assertTrue(CpfCnpj.validarCnpj(b + oficialCnpj(b.toString())));
        }
    }

    @Test void cnabFronteiras() {
        for (long c : new long[]{201, 210, 223, 999}) assertFalse(Cnab.creditoInvalido(c));
        for (long c : new long[]{200, 224, 101, 0}) assertTrue(Cnab.creditoInvalido(c));
        for (long c : new long[]{101, 115, 117, 127, 999}) assertFalse(Cnab.debitoInvalido(c));
        for (long c : new long[]{100, 116, 128, 201}) assertTrue(Cnab.debitoInvalido(c));
        for (long c : new long[]{101, 127, 201, 223, 999}) assertFalse(Cnab.creditoDebitoInvalido(c));
        for (long c : new long[]{100, 116, 128, 200, 224}) assertTrue(Cnab.creditoDebitoInvalido(c));
    }

    @Test void removerZerosEsquerdaFalhaParaSoZeros() {
        assertEquals("123", Text.removeLeadingZeros("00123"));
        assertThrows(StringIndexOutOfBoundsException.class, () -> Text.removeLeadingZeros("000"));
    }
}
