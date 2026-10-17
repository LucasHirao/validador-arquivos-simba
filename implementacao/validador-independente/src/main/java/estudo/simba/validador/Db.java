package estudo.simba.validador;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Armazenamento em memória (substitui as tabelas HSQLDB do original). Ids são posições 1-based nas listas. */
public final class Db {
    public record Inv(String cpf, int teveRelacionamento, int teveConta, LocalDate inicioAfastamento, LocalDate fimAfastamento) {}
    public record Ag(int linha, int banco, int agencia) {}
    public record Ct(int linha, int idAg, String conta, int tipo, String abertura, String encerramento, int mov) {}
    public record Pe(int linha, String tipo, String cpf, String docNome, String docNum, String logradouro, String cidade, String uf,
                     String pais, String cep, String tel, Inv investigado) {}
    public record Ti(int linha, String tipo, String investigada, String inicio, String fim, int idConta, int idPessoa) {}
    public record Ex(int linha, long chave, String data, long tipo, long valor, int idConta) {}
    public record Od(int linha, long chave, long chaveExtrato, long valor, String banco, Long agencia, String conta, String tipoPessoa, String cpf,
                     String nome, String obs) {}

    public final List<Inv> investigados = new ArrayList<>();
    public final List<Ag> agencias = new ArrayList<>();
    public final List<Ct> contas = new ArrayList<>();
    public final List<Pe> pessoas = new ArrayList<>();
    public final List<Ti> titularidades = new ArrayList<>();
    public final List<Ex> extratos = new ArrayList<>();
    public final List<Od> ods = new ArrayList<>();

    // Índices (o original usa índices/PK do HSQLDB): evitam varreduras lineares em arquivos grandes
    private final java.util.Map<Long, Integer> idxAgencia = new java.util.HashMap<>();
    private final java.util.Map<String, Integer> idxConta = new java.util.HashMap<>();
    private final java.util.Set<Long> chavesExtrato = new java.util.HashSet<>(), chavesOd = new java.util.HashSet<>();

    public void addAgencia(Ag a) {
        agencias.add(a);
        idxAgencia.putIfAbsent(a.banco() * 100_000L + a.agencia(), agencias.size());        // a primeira ocorrência vence
    }

    public void addConta(Ct c) {
        contas.add(c);
        idxConta.putIfAbsent(c.idAg() + "|" + c.conta() + "|" + c.tipo(), contas.size());
    }

    /** PRIMARY KEY de EXTRATO. */
    public void addExtrato(Ex e) {
        if (!chavesExtrato.add(e.chave())) throw new IllegalStateException("PK EXTRATO");
        extratos.add(e);
    }

    /** PRIMARY KEY de ORIGEM_DESTINO e FOREIGN KEY para EXTRATO. */
    public void addOd(Od o) {
        if (!chavesExtrato.contains(o.chaveExtrato()) || chavesOd.contains(o.chave())) throw new IllegalStateException("PK/FK ORIGEM_DESTINO");
        chavesOd.add(o.chave());
        ods.add(o);
    }

    public int findAgencia(int banco, int agencia) { return idxAgencia.getOrDefault(banco * 100_000L + agencia, -1); }

    public int findConta(int banco, int agencia, String conta, int tipo) {
        int idAg = findAgencia(banco, agencia);
        return idAg == -1 ? -1 : idxConta.getOrDefault(idAg + "|" + conta + "|" + tipo, -1);
    }
}
