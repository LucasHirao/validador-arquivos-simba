package estudo.simba.validador;

import java.util.List;
import java.util.Map;

/** Layouts dos arquivos de entrada (nome do campo, tamanho máximo) — fonte única para E-03/E-04. */
public final class Schemas {
    private Schemas() {}

    public record Field(String name, int max) {}

    private static List<Field> f(Object... kv) {
        var l = new java.util.ArrayList<Field>();
        for (int i = 0; i < kv.length; i += 2) l.add(new Field((String) kv[i], (Integer) kv[i + 1]));
        return List.copyOf(l);
    }

    public static final Map<FileKind, List<Field>> LAYOUT = Map.of(
        FileKind.AGENCIAS, f("NUMERO_BANCO", 3, "NUMERO_AGENCIA", 4, "NOME_AGENCIA", 50, "ENDERECO_LOGRADOURO", 80, "ENDERECO_CIDADE", 40,
            "ENDERECO_UF", 2, "ENDERECO_PAIS", 40, "ENDERECO_CEP", 8, "TELEFONE_AGENCIA", 20, "DATA_ABERTURA_AGENCIA", 8, "DATA_FECHAMENTO_AGENCIA", 8),
        FileKind.CONTAS, f("NUMERO_BANCO", 3, "NUMERO_AGENCIA", 4, "NUMERO_CONTA", 20, "TIPO_CONTA", 1, "DATA_ABERTURA_CONTA", 8,
            "DATA_ENCERRAMENTO_CONTA", 8, "MOVIMENTACAO_CONTA", 1),
        FileKind.TITULARES, f("NUMERO_BANCO", 3, "NUMERO_AGENCIA", 4, "NUMERO_CONTA", 20, "TIPO_CONTA", 1, "TIPO_TITULAR", 1, "PESSOA_INVESTIGADA", 1,
            "TIPO_PESSOA_TITULAR", 1, "CPF_CNPJ_TITULAR", 14, "NOME_TITULAR", 80, "NOME_DOC_IDENTIFICACAO", 50, "NUMERO_DOC_IDENTIFICACAO", 20,
            "ENDERECO_LOGRADOURO", 80, "ENDERECO_CIDADE", 40, "ENDERECO_UF", 2, "ENDERECO_PAIS", 40, "ENDERECO_CEP", 8, "TELEFONE_PESSOA", 20,
            "VALOR_RENDA", 14, "DATA_ATUALIZACAO_RENDA", 8, "DATA_INICIO_RELACIONAMENTO", 8, "DATA_FIM_RELACIONAMENTO", 8),
        FileKind.EXTRATO, f("CODIGO_CHAVE_EXTRATO", 18, "NUMERO_BANCO", 3, "NUMERO_AGENCIA", 4, "NUMERO_CONTA", 20, "TIPO_CONTA", 1, "DATA_LANCAMENTO", 8,
            "NUMERO_DOCUMENTO", 20, "DESCRICAO_LANCAMENTO", 50, "TIPO_LANCAMENTO", 3, "VALOR_LANCAMENTO", 14, "NATUREZA_LANCAMENTO", 1,
            "VALOR_SALDO", 14, "NATUREZA_SALDO", 1, "LOCAL_TRANSACAO", 80),
        FileKind.ORIGEM_DESTINO, f("CODIGO_CHAVE_OD", 18, "CODIGO_CHAVE_EXTRATO", 18, "VALOR_TRANSACAO", 14, "NUMERO_DOCUMENTO_TRANSACAO", 20,
            "NUMERO_BANCO_OD", 3, "NUMERO_AGENCIA_OD", 4, "NUMERO_CONTA_OD", 20, "TIPO_CONTA_OD", 1, "TIPO_PESSOA_OD", 1, "CPF_CNPJ_OD", 14,
            "NOME_PESSOA_OD", 80, "NOME_DOC_IDENTIFICACAO_OD", 50, "NUMERO_DOC_IDENTIFICACAO_OD", 20, "CODIGO_DE_BARRAS", 100,
            "NOME_ENDOSSANTE_CHEQUE", 80, "DOC_ENDOSSANTE_CHEQUE", 50, "SITUACAO_IDENTIFICACAO", 1, "OBSERVACÃO", 250));
}
