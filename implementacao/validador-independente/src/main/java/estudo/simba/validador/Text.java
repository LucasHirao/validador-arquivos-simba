package estudo.simba.validador;

/** Primitivas de texto com a mesma semântica das usadas pelo validador oficial (StringUtil). */
public final class Text {
    private Text() {}

    /** null ou trim vazio (String.trim remove caracteres <= U+0020). */
    public static boolean isEmpty(String s) { return s == null || s.trim().isEmpty(); }

    public static String padLeft(String s, int n) {
        if (s == null) return null;
        return s.length() >= n ? s : "0".repeat(n - s.length()) + s;
    }

    /** Lança StringIndexOutOfBoundsException para vazio/só zeros (A-06). */
    public static String removeLeadingZeros(String s) {
        int i = 0;
        while (s.charAt(i) == '0') i++;
        return i == 0 ? s : s.substring(i);
    }

    /** Integer.valueOf estrito ou null (equivale a capturar NumberFormatException). */
    public static Integer intOrNull(String s) {
        try { return Integer.valueOf(s); } catch (NumberFormatException e) { return null; }
    }
}
