package estudo.simba.validador.gab;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import estudo.simba.validador.*;

/** Fluxo de corretoras: existência → vazios (GAB) → carga com fail-fast (109, 112, 800) → conteúdo. */
public final class GabPipeline {
    private GabPipeline() {}

    public static Pipeline.Result run(Path pasta, GabConfig cfg, List<Db.Inv> investigados) throws IOException {
        Report rep = new Report();
        GabDb db = new GabDb();
        db.investigados.addAll(investigados);
        Map<GabKind, Path> arq = new EnumMap<>(GabKind.class);
        List<Path> txts;
        try (Stream<Path> s = Files.list(pasta)) {
            txts = s.filter(p -> !Files.isDirectory(p) && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".txt")).toList();
        }
        for (GabKind k : GabKind.values()) {
            String esperado = cfg.caso() + "_" + k.name() + ".txt";
            Optional<Path> a = txts.stream().filter(p -> p.getFileName().toString().equalsIgnoreCase(esperado)).findFirst();
            if (a.isEmpty()) { rep.rep("E-01", 0, esperado, "Arquivo esperado (" + esperado + ") não encontrado."); return new Pipeline.Result(rep.all(), null, true); }
            arq.put(k, a.get());
        }
        long t109 = Files.size(arq.get(GabKind.GAB109)), t112 = Files.size(arq.get(GabKind.GAB112)), t800 = Files.size(arq.get(GabKind.GAB800));
        if (t112 == 0) rep.rep("E-02", 0, arq.get(GabKind.GAB112).getFileName().toString(), "O arquivo GAB-112 não pode ser vazio.");
        if (t800 == 0 && t109 != 0) rep.rep("E-02", 0, arq.get(GabKind.GAB800).getFileName().toString(), "O arquivo GAB-800 não pode ser vazio quando o arquivo GAB-109 possui dados.");
        if (t109 == 0 && t800 != 0) rep.rep("E-02", 0, arq.get(GabKind.GAB109).getFileName().toString(), "O arquivo GAB-109 não pode ser vazio quando o arquivo GAB-800 possui dados.");
        if (!rep.isEmpty()) return new Pipeline.Result(rep.all(), null, true);
        GabLoader loader = new GabLoader(cfg, rep, db);
        for (GabKind k : GabKind.values())
            if (loader.carregar(k, arq.get(k))) return new Pipeline.Result(rep.all(), null, true);
        try {
            return new Pipeline.Result(rep.all(), new GabRules(rep, db).validar(), false);
        } catch (IllegalStateException e) {
            rep.rep("R-INFRA", 0, "GAB112", "Ocorreu um erro do sistema durante a validação: " + e.getMessage());
            return new Pipeline.Result(rep.all(), null, true);
        }
    }
}
