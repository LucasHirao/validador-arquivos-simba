package estudo.simba.validador;

/** A-04. Cada método retorna true quando o TIPO_LANCAMENTO é INVÁLIDO para a natureza. */
public final class Cnab {
    private Cnab() {}
    public static boolean creditoInvalido(long c) { return (c < 201 || c > 223) && c != 999; }
    public static boolean debitoInvalido(long c) { return (c < 101 || c > 127 || c == 116) && c != 999; }
    public static boolean creditoDebitoInvalido(long c) { return (c < 101 || (c > 127 && c < 201) || c > 223 || c == 116) && c != 999; }
}
