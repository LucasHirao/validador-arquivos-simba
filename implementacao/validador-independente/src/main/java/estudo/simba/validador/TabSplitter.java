package estudo.simba.validador;

/** E-05b. COMPAT: split("(?=\t)") + trim (descarta campo vazio inicial). ESTRITO: split exato por TAB, preservando vazios. */
public final class TabSplitter {
    private TabSplitter() {}

    public static String[] split(String linha, Mode mode) {
        String[] d = mode == Mode.COMPAT ? linha.split("(?=\t)") : linha.split("\t", -1);
        for (int i = 0; i < d.length; i++) d[i] = d[i].trim();
        return d;
    }
}
