package estudo.simba.validador;

import static org.junit.jupiter.api.Assertions.*;

import estudo.simba.validador.gab.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Fluxo de corretoras (GAB). Dados sintéticos. */
class GabTest {
    static final String CASO = "001-TST-000001-00", CPF = "52998224725";
    static final Set<String> NUM = Set.of("CODIGO_DO_CLIENTE", "DOCUMENTO_DO_CLIENTE", "CODIGO_DO_CLIENTE_MERCADO", "CODIGO_DA_ATIVIDADE", "VALOR_DO_LANCAMENTO", "SALDO_INICIAL");

    static String linha(GabKind k, int v, Map<String, String> valores) {
        StringBuilder sb = new StringBuilder();
        for (var f : GabLayouts.of(k, v)) {
            String x = valores.getOrDefault(f.name(), "");
            sb.append(NUM.contains(f.name()) && !x.isEmpty() ? String.format("%" + f.size() + "s", x).replace(' ', '0') : String.format("%-" + f.size() + "s", x));
        }
        return sb.toString();
    }

    static Map<String, String> mapa(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    static Map<GabKind, List<String>> valido(int v) {
        Map<GabKind, List<String>> m = new EnumMap<>(GabKind.class);
        m.put(GabKind.GAB109, new ArrayList<>(List.of(linha(GabKind.GAB109, v, Map.of("DATA_DO_MOVIMENTO", "20200315", "DATA_DE_REFERENCIA", "20200315", "CODIGO_DO_CLIENTE", "1",
            "DATA_DE_LIQUIDACAO", "20200317", "DESCRICAO_DO_LANCAMENTO", "COMPRA", "VALOR_DO_LANCAMENTO", "10000", "NATUREZA_LANCAMENTO", "C")))));
        m.put(GabKind.GAB112, new ArrayList<>(List.of(linha(GabKind.GAB112, v, Map.ofEntries(Map.entry("CODIGO_DO_CLIENTE", "1"), Map.entry("DOCUMENTO_DO_CLIENTE", CPF),
            Map.entry("DATA_DA_CRIACAO", "20200101"), Map.entry("DATA_DA_ATUALIZACAO", "20200101"), Map.entry("DATA_NASCIMENTO_FUNDACAO", "19800101"), Map.entry("TIPO_DE_PESSOA", "F"),
            Map.entry("NOME_DO_CLIENTE", "FULANO"), Map.entry("DOCUMENTO_DE_IDENTIFICACAO", "1234567"), Map.entry("CODIGO_DO_CLIENTE_MERCADO", "1"), Map.entry("ENDERECO", "RUA"),
            Map.entry("BAIRRO", "CENTRO"), Map.entry("CIDADE", "BSB"), Map.entry("ESTADO", "DF"), Map.entry("CEP", "70000"), Map.entry("COMPLEMENTO_DO_CEP", "000"),
            Map.entry("CONSTANTE_0000A", "0000A"), Map.entry("CODIGO_DA_ATIVIDADE", "1"), Map.entry("MERCADO", "BOVESPA"), Map.entry("DATA_SITUACAO", "20200101"))))));
        m.put(GabKind.GAB800, new ArrayList<>(List.of(linha(GabKind.GAB800, v, mapa("CODIGO_DO_CLIENTE", "1", "NOME_DO_CLIENTE", "FULANO", "ENDERECO", "RUA", "DATA_DE_REFERENCIA", "20200315",
            "DATA_DE_LIQUIDACAO", "20200317", "DESCRICAO_DO_LANCAMENTO", "SALDO", "SALDO_INICIAL", "10000", "NATUREZA_DO_SALDO_INICIAL", "C", "VALOR_DO_LANCAMENTO", "500",
            "NATUREZA_DO_LANCAMENTO", "D")))));
        return m;
    }

    static Pipeline.Result rodar(Map<GabKind, List<String>> a, Path d, int v, Mode mode, Boolean defeito) throws Exception {
        for (var e : a.entrySet())
            Files.write(d.resolve(CASO + "_" + e.getKey() + ".txt"), (String.join("\r\n", e.getValue()) + (e.getValue().isEmpty() ? "" : "\r\n")).getBytes(StandardCharsets.ISO_8859_1));
        return GabPipeline.run(d, GabConfig.of(CASO, v, mode, defeito), List.of(new Db.Inv(CPF, 1, 1, null, null)));
    }

    static List<String> msgs(Pipeline.Result r) { return r.violacoes().stream().map(Violation::mensagem).toList(); }

    @Test void tamanhosDeLinha() {
        assertEquals(127, GabLayouts.tamanhoLinha(GabKind.GAB109, 1));
        assertEquals(333, GabLayouts.tamanhoLinha(GabKind.GAB109, 2));
        assertEquals(307, GabLayouts.tamanhoLinha(GabKind.GAB112, 1));
        assertEquals(726, GabLayouts.tamanhoLinha(GabKind.GAB112, 2));
        assertEquals(298, GabLayouts.tamanhoLinha(GabKind.GAB800, 1));
        assertEquals(320, GabLayouts.tamanhoLinha(GabKind.GAB800, 2));
    }

    @Test void compatReproduzOdefeitoDoOriginal(@TempDir Path d1, @TempDir Path d2) throws Exception {
        // linha com a largura certa => "Erro desconhecido" (NPE em validarCampos no original 6.2.6), mesmo sendo válida
        Pipeline.Result r = rodar(valido(1), d1, 1, Mode.COMPAT, null);
        assertEquals(List.of("Erro desconhecido. Consulte o log do sistema."), msgs(r));
        assertEquals(CASO + "_GAB109.txt", r.violacoes().get(0).arquivo());
        var a = valido(1); a.get(GabKind.GAB109).set(0, "curta");
        assertTrue(msgs(rodar(a, d2, 1, Mode.COMPAT, null)).get(0).startsWith("Certifique-se de completar com espaços"));
    }

    @Test void logicaPretendidaAceitaCasoValido(@TempDir Path d1, @TempDir Path d2) throws Exception {
        for (int v : new int[]{1, 2}) {
            Pipeline.Result r = rodar(valido(v), v == 1 ? d1 : d2, v, Mode.ESTRITO, null);
            assertTrue(r.ok(), v + " " + msgs(r));
            assertEquals(List.of("GAB109", "GAB112", "GAB800"), List.copyOf(r.conteudo().keySet()));
        }
    }

    @Test void validacoesDeCampo(@TempDir Path d1, @TempDir Path d2, @TempDir Path d3) throws Exception {
        var a = valido(1);
        a.get(GabKind.GAB109).set(0, linha(GabKind.GAB109, 1, Map.of("DATA_DO_MOVIMENTO", "20200230", "DATA_DE_REFERENCIA", "20200315", "CODIGO_DO_CLIENTE", "1",
            "DATA_DE_LIQUIDACAO", "20200317", "DESCRICAO_DO_LANCAMENTO", "X", "VALOR_DO_LANCAMENTO", "1", "NATUREZA_LANCAMENTO", "X")));
        assertEquals(List.of("O campo DATA_DO_MOVIMENTO está incorreto. A data deve estar no formato AAAAMMDD e válida.", "Informe C para crédito ou D para débito. Campo: NATUREZA_LANCAMENTO"),
            msgs(rodar(a, d1, 1, Mode.ESTRITO, null)));
        var b = valido(1);   // CPF inválido, estado e mercado errados no GAB112
        b.get(GabKind.GAB112).set(0, b.get(GabKind.GAB112).get(0).replace(CPF, "52998224724").replace("BOVESPA", "ACOES  ").replace("DF", "XX"));
        List<String> m = msgs(rodar(b, d2, 1, Mode.ESTRITO, null));
        assertTrue(m.contains("CPF inválido para o tipo de pessoa: F. Campo: DOCUMENTO_DO_CLIENTE"), m.toString());
        assertTrue(m.contains("A sigla do campo estado está incorreta"));
        var c = valido(2); c.get(GabKind.GAB112).set(0, c.get(GabKind.GAB112).get(0).substring(0, c.get(GabKind.GAB112).get(0).length() - 8) + "        "); // DATA_SITUACAO vazia (v2)
        assertEquals(List.of("Erro desconhecido. Consulte o log do sistema."), msgs(rodar(c, d3, 2, Mode.ESTRITO, null)));   // getDateGabSql(null) no original
    }

    @Test void regrasDeConteudo(@TempDir Path d1, @TempDir Path d2) throws Exception {
        var a = valido(1);
        a.get(GabKind.GAB109).set(0, linha(GabKind.GAB109, 1, Map.of("DATA_DO_MOVIMENTO", "20200315", "DATA_DE_REFERENCIA", "20200315", "CODIGO_DO_CLIENTE", "9",
            "DATA_DE_LIQUIDACAO", "20200317", "DESCRICAO_DO_LANCAMENTO", "X", "VALOR_DO_LANCAMENTO", "1", "NATUREZA_LANCAMENTO", "C")));
        Pipeline.Result r = rodar(a, d1, 1, Mode.ESTRITO, null);
        assertEquals(List.of("O cliente com o código 9 não foi informado no arquivo gab112."), msgs(r));
        assertEquals(java.util.Map.of("GAB109", false), r.conteudo());
        // investigado com relacionamento ausente do GAB112
        rodar(valido(1), d2, 1, Mode.ESTRITO, null);   // grava os arquivos válidos em d2
        Pipeline.Result r2 = GabPipeline.run(d2, GabConfig.of(CASO, 1, Mode.ESTRITO, null), List.of(new Db.Inv("11222333000181", 1, 1, null, null)));
        assertTrue(msgs(r2).contains("O investigado com CPF/CNPJ 11222333000181 não foi encontrado no arquivo cadastral(gab112)."), msgs(r2).toString());
    }

    @Test void arquivosVazios(@TempDir Path d) throws Exception {
        var a = valido(1); a.get(GabKind.GAB112).clear();
        assertEquals(List.of("O arquivo GAB-112 não pode ser vazio."), msgs(rodar(a, d, 1, Mode.COMPAT, null)));
    }
}
