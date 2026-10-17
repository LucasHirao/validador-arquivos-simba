package estudo.simba.validador.gab;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Layouts de largura fixa dos arquivos GAB (corretoras), versões 1 e 2 — recuperados de enums/CamposGab*.java do original. */
public final class GabLayouts {
    private GabLayouts() {}

    public record Field(String name, int size) {}

    private static List<Field> f(Object... kv) {
        List<Field> l = new ArrayList<>();
        for (int i = 0; i < kv.length; i += 2) l.add(new Field((String) kv[i], (Integer) kv[i + 1]));
        return List.copyOf(l);
    }

    public static List<Field> of(GabKind kind, int versao) {
        return switch (kind) {
            case GAB109 -> versao == 2 ? G109V2 : G109;
            case GAB112 -> versao == 2 ? G112V2 : G112;
            case GAB800 -> versao == 2 ? G800V2 : G800;
        };
    }

    /** Colunas NUMERIC / DATE de cada tabela (MapaGab do original): a gravação falha se o texto não converte (sem validação prévia). */
    public static Set<String> numericas(GabKind kind, int versao) {
        return switch (kind.name() + versao) {
            case "GAB1091" -> Set.of("CODIGO_DO_CLIENTE", "VALOR_DO_LANCAMENTO");
            case "GAB1092" -> Set.of("CODIGO_DO_CLIENTE", "VALOR_DO_LANCAMENTO", "CHAVE_OD", "BANCO_ORIGEM", "AGENCIA_ORIGEM", "CONTA_ORIGEM", "BANCO_DESTINO", "AGENCIA_DESTINO", "CONTA_DESTINO");
            case "GAB1121" -> Set.of("CODIGO_DO_CLIENTE", "CODIGO_DO_CLIENTE_MERCADO", "CODIGO_DA_ATIVIDADE");
            case "GAB1122" -> Set.of("CODIGO_DO_CLIENTE", "CODIGO_DO_CLIENTE_MERCADO", "CODIGO_DA_ATIVIDADE", "MEMBRO_COMPENSACAO", "AGENTE_CUSTODIA", "CODIGO_CORRETORA");
            case "GAB8001" -> Set.of("CODIGO_DO_CLIENTE", "SALDO_INICIAL", "VALOR_DO_LANCAMENTO");
            case "GAB8002" -> Set.of("CODIGO_DO_CLIENTE", "SALDO_INICIAL", "VALOR_DO_LANCAMENTO", "CHAVE_OD");
            default -> Set.of();
        };
    }

    public static Set<String> datas(GabKind kind, int versao) {
        return switch (kind.name() + versao) {
            case "GAB1091" -> Set.of("DATA_DO_MOVIMENTO", "DATA_DE_REFERENCIA", "DATA_DE_LIQUIDACAO");
            case "GAB1092" -> Set.of("DATA_DO_MOVIMENTO", "DATA_DE_REFERENCIA", "DATA_DE_LIQUIDACAO");
            case "GAB1121" -> Set.of("DATA_DA_CRIACAO", "DATA_DA_ATUALIZACAO", "DATA_NASCIMENTO_FUNDACAO");
            case "GAB1122" -> Set.of("DATA_DA_CRIACAO", "DATA_DA_ATUALIZACAO", "DATA_NASCIMENTO_FUNDACAO", "DATA_SITUACAO");
            case "GAB8001" -> Set.of("DATA_DE_REFERENCIA", "DATA_DE_LIQUIDACAO");
            case "GAB8002" -> Set.of("DATA_DE_REFERENCIA", "DATA_DE_LIQUIDACAO");
            default -> Set.of();
        };
    }

    public static int tamanhoLinha(GabKind kind, int versao) { return of(kind, versao).stream().mapToInt(Field::size).sum(); }

    private static final List<Field> G109 = f("DATA_DO_MOVIMENTO", 8, "DATA_DE_REFERENCIA", 8, "CODIGO_DO_CLIENTE", 7, "DATA_DE_LIQUIDACAO", 8, "DESCRICAO_DO_LANCAMENTO", 80, "VALOR_DO_LANCAMENTO", 15, "NATUREZA_LANCAMENTO", 1);
    private static final List<Field> G109V2 = f("DATA_DO_MOVIMENTO", 8, "DATA_DE_REFERENCIA", 8, "CODIGO_DO_CLIENTE", 9, "DATA_DE_LIQUIDACAO", 8, "DESCRICAO_DO_LANCAMENTO", 80, "VALOR_DO_LANCAMENTO", 15, "NATUREZA_LANCAMENTO", 1, "CHAVE_OD", 20, "BANCO_ORIGEM", 8, "AGENCIA_ORIGEM", 5, "CONTA_ORIGEM", 15, "CPF_CNPJ_ORIGEM", 14, "BANCO_DESTINO", 8, "AGENCIA_DESTINO", 5, "CONTA_DESTINO", 15, "CPF_CNPJ_DESTINO", 14, "OBSERVACAO", 100);
    private static final List<Field> G112 = f("CODIGO_DO_CLIENTE", 7, "DOCUMENTO_DO_CLIENTE", 15, "DATA_DA_CRIACAO", 8, "DATA_DA_ATUALIZACAO", 8, "DATA_NASCIMENTO_FUNDACAO", 8, "TIPO_DE_PESSOA", 1, "NOME_DO_CLIENTE", 60, "DOCUMENTO_DE_IDENTIFICACAO", 10, "NOME_DO_CONJUGE", 60, "CODIGO_DO_CLIENTE_MERCADO", 7, "ENDERECO", 30, "NUMERO_E_COMPLEMENTO", 15, "BAIRRO", 18, "CIDADE", 18, "ESTADO", 2, "CEP", 5, "COMPLEMENTO_DO_CEP", 3, "CONSTANTE_0000A", 5, "CODIGO_DA_ATIVIDADE", 3, "TELEFONE", 14, "MERCADO", 10);
    private static final List<Field> G112V2 = f("CODIGO_DO_CLIENTE", 9, "DOCUMENTO_DO_CLIENTE", 15, "DATA_DA_CRIACAO", 8, "DATA_DA_ATUALIZACAO", 8, "DATA_NASCIMENTO_FUNDACAO", 8, "TIPO_DE_PESSOA", 1, "NOME_DO_CLIENTE", 60, "DOCUMENTO_DE_IDENTIFICACAO", 10, "NOME_DO_CONJUGE", 60, "CODIGO_DO_CLIENTE_MERCADO", 9, "ENDERECO", 30, "NUMERO_E_COMPLEMENTO", 15, "BAIRRO", 18, "CIDADE", 18, "ESTADO", 2, "CEP", 5, "COMPLEMENTO_DO_CEP", 3, "CONSTANTE_0000A", 5, "CODIGO_DA_ATIVIDADE", 3, "DESCRICAO_DA_ATIVIDADE", 100, "TELEFONE", 14, "MERCADO", 10, "EMAIL", 100, "CELULAR", 14, "MEMBRO_COMPENSACAO", 4, "DESCRICAO_MEMBRO_COMPENSACAO", 60, "AGENTE_CUSTODIA", 4, "DESCRICAO_AGENTE_CUSTODIA", 60, "CODIGO_CORRETORA", 4, "DESCRICAO_CODIGO_CORRETORA", 60, "SITUACAO_CLIENTE", 1, "DATA_SITUACAO", 8);
    private static final List<Field> G800 = f("CODIGO_DO_CLIENTE", 7, "NOME_DO_CLIENTE", 60, "ENDERECO", 52, "ENDERECO_COMPLEMENTO", 47, "DATA_DE_REFERENCIA", 8, "DATA_DE_LIQUIDACAO", 8, "DESCRICAO_DO_LANCAMENTO", 80, "SALDO_INICIAL", 17, "NATUREZA_DO_SALDO_INICIAL", 1, "VALOR_DO_LANCAMENTO", 17, "NATUREZA_DO_LANCAMENTO", 1);
    private static final List<Field> G800V2 = f("CODIGO_DO_CLIENTE", 9, "NOME_DO_CLIENTE", 60, "ENDERECO", 52, "ENDERECO_COMPLEMENTO", 47, "DATA_DE_REFERENCIA", 8, "DATA_DE_LIQUIDACAO", 8, "DESCRICAO_DO_LANCAMENTO", 80, "SALDO_INICIAL", 17, "NATUREZA_DO_SALDO_INICIAL", 1, "VALOR_DO_LANCAMENTO", 17, "NATUREZA_DO_LANCAMENTO", 1, "CHAVE_OD", 20);
}
