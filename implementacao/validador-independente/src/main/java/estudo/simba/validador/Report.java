package estudo.simba.validador;

import java.util.ArrayList;
import java.util.List;

/** Coletor de violações (equivale ao ErrorReportCmd; a política de "erro global" é aplicada pelo Loader no modo COMPAT). */
public final class Report {
    private final List<Violation> list = new ArrayList<>();

    public void rep(String ruleId, long linha, String arquivo, String msg) {
        list.add(new Violation(ruleId, linha, arquivo, msg));
    }

    public int size() { return list.size(); }
    public boolean isEmpty() { return list.isEmpty(); }
    public List<Violation> all() { return list; }
}
