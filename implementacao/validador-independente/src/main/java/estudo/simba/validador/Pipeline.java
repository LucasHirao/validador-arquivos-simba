package estudo.simba.validador;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/** Orquestra: existência (E-01) → vazios (E-02) → carga com fail-fast (E-07) → conteúdo com fail-fast (E-08). */
public final class Pipeline {
    public record Result(List<Violation> violacoes, Map<String, Boolean> conteudo, boolean falhaCarga) {
        public boolean ok() { return violacoes.isEmpty(); }
    }

    private Pipeline() {}

    public static Result run(Path pasta, Config cfg, List<Db.Inv> investigados) throws IOException {
        Report rep = new Report();
        Db db = new Db();
        db.investigados.addAll(investigados);
        Map<FileKind, Path> arquivos = new EnumMap<>(FileKind.class);
        List<Path> txts;
        try (Stream<Path> s = Files.list(pasta)) {
            txts = s.filter(p -> !Files.isDirectory(p) && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".txt")).toList();
        }
        for (FileKind k : FileKind.values()) {                                                       // E-01 (só o primeiro ausente é reportado)
            String esperado = cfg.caso() + "_" + k.name() + ".txt";
            Optional<Path> achado = txts.stream().filter(p -> p.getFileName().toString().equalsIgnoreCase(esperado)).findFirst();
            if (achado.isEmpty()) {
                rep.rep("E-01", 0, esperado, "Arquivo esperado (" + esperado + ") não encontrado.");
                return new Result(rep.all(), null, true);
            }
            arquivos.put(k, achado.get());
        }
        for (FileKind k : List.of(FileKind.AGENCIAS, FileKind.CONTAS, FileKind.TITULARES))           // E-02
            if (Files.size(arquivos.get(k)) == 0) rep.rep("E-02", 0, arquivos.get(k).getFileName().toString(), "O arquivo " + k + " não pode ser vazio.");
        if (!rep.isEmpty()) return new Result(rep.all(), null, true);

        Loader loader = new Loader(cfg, rep, db);
        for (FileKind k : FileKind.values())
            if (loader.carregarArquivo(k, arquivos.get(k))) return new Result(rep.all(), null, true);   // E-07

        try {
            Map<String, Boolean> conteudo = new ContentRules(cfg, rep, db).validar();
            return new Result(rep.all(), conteudo, false);
        } catch (IllegalStateException e) {   // equivalente à falha de infraestrutura do original
            rep.rep("R-INFRA", 0, "EXTRATO", "Ocorreu um erro do sistema durante a validação: " + e.getMessage());
            return new Result(rep.all(), null, true);
        }
    }
}
