package estudo.simba.validador;

/** Violação de regra. {@code arquivo} é o rótulo (AGENCIAS…) ou o nome físico do arquivo, como no original. */
public record Violation(String ruleId, long linha, String arquivo, String mensagem) {
    /** Mesmo formato do original: "Erro no arquivo: X, linha: n, erro: msg". */
    public String pretty() {
        return "Erro no arquivo: " + arquivo + ", linha: " + linha + ", erro: " + mensagem;
    }
}
