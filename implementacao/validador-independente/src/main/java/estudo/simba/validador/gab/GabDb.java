package estudo.simba.validador.gab;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import estudo.simba.validador.Db;

/** Tabelas GAB em memória. Os valores são os campos já com trim, indexados pelo nome do layout. */
public final class GabDb {
    public record Row(int linha, Map<String, String> v) {
        public String get(String campo) { return v.get(campo); }
        public BigInteger num(String campo) { return new BigInteger(v.get(campo)); }
    }

    public final List<Row> g109 = new ArrayList<>(), g112 = new ArrayList<>(), g800 = new ArrayList<>();
    public final List<Db.Inv> investigados = new ArrayList<>();

    public List<Row> of(GabKind k) { return switch (k) { case GAB109 -> g109; case GAB112 -> g112; case GAB800 -> g800; }; }
}
