package estudo.simba.validador;

/** Dígitos verificadores de CPF e CNPJ (inclusive alfanumérico) — A-02/A-03. Valor do caractere = ord(c) − 48. */
public final class CpfCnpj {
    private CpfCnpj() {}

    private static long somatorio(String n, Integer base) {
        long s = 0;
        for (int i = 0; i < n.length(); i++) {
            int peso = base == null ? n.length() + 1 - i : base - i;
            s += (long) (n.charAt(i) - '0') * peso;
        }
        return s;
    }

    private static String mod11(long soma) {
        long r = soma % 11;
        return r < 2 ? "0" : String.valueOf(11 - r);
    }

    public static String dvCpf(String base) {
        String d1 = mod11(somatorio(base, null));
        return d1 + mod11(somatorio(base + d1, null));
    }

    public static String dvCnpj(String base) {
        String d1 = mod11(somatorio(base.substring(0, 4), null) + somatorio(base.substring(4), null));
        String n2 = base + d1;
        return d1 + mod11(somatorio(n2.substring(0, 5), 6) + somatorio(n2.substring(5), null));
    }

    private static boolean todosIguais(String t) {
        for (int i = 1; i < t.length(); i++) if (t.charAt(i) != t.charAt(0)) return false;
        return true;
    }

    private static boolean valida(String numero, String regexBase, boolean cpf) {
        if (Text.isEmpty(numero)) return false;
        String sem = numero.substring(0, numero.length() - 2), dv = numero.substring(numero.length() - 2);
        if (Text.isEmpty(sem) || Text.isEmpty(dv) || todosIguais(sem)) return false;
        if (sem.matches(regexBase) && dv.matches("[0-9]{2}")) return dv.equals(cpf ? dvCpf(sem) : dvCnpj(sem));
        return false;
    }

    public static boolean validarCpf(String cpf) { return valida(Text.padLeft(cpf, 11), "[0-9]{6,}", true); }

    public static boolean validarCnpj(String cnpj) { return valida(Text.padLeft(cnpj, 14), "[0-9A-Z]{7,}", false); }
}
