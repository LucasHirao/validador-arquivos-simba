package estudo.simba.validador;

/**
 * Parâmetros do atendimento. {@code eleitoral} segue Context.isEleitoral do original: caso começa com 001-MPE ou 038-TSE (E-10).
 * {@code receptor} (opcional, "major.minor") reproduz a versão do receptor do órgão, que no original vem do servidor
 * ({@code Controlador.getComputadores} → HTTP GET {@code orgaos?codprog=…}) e habilita a regra L-OD-04. Sem ele, L-OD-04 não é avaliada.
 */
public record Config(String caso, int bancoAtendimento, boolean eleitoral, Mode mode, int[] receptor, Limiares limiares) {
    public static Config of(String caso, int bancoAtendimento, Mode mode) { return of(caso, bancoAtendimento, mode, null); }

    public static Config of(String caso, int bancoAtendimento, Mode mode, String receptor) {
        boolean ele = caso != null && (caso.startsWith("001-MPE") || caso.startsWith("038-TSE"));
        int[] r = null;
        if (receptor != null) {
            String[] p = receptor.split("\\.");
            r = new int[]{Integer.parseInt(p[0]), p.length > 1 ? Integer.parseInt(p[1]) : 0};
        }
        return new Config(caso, bancoAtendimento, ele, mode, r, Limiares.padrao());
    }

    public Config comLimiares(Limiares l) { return new Config(caso, bancoAtendimento, eleitoral, mode, receptor, l); }

    /** Versao.suportaCNPJComoNumeroBanco: major >= 4 ou (major >= 3 e minor >= 3). */
    public boolean receptorSuportaCnpjComoBanco() { return receptor != null && (receptor[0] >= 4 || (receptor[0] >= 3 && receptor[1] >= 3)); }
}
