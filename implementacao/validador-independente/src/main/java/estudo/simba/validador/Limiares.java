package estudo.simba.validador;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;

/**
 * Limiares e códigos de lançamento que no validador oficial ficam fixos em SQL (hard-coded em ValidarIdentificacao*Rule,
 * ValidarDepositoDinheiroChequeEleitoralRule, ValidarCpfCnpjTransferenciaTedDocOrigemDestinoRule). Aqui são configuráveis e versionáveis:
 * veja {@code limiares-6.2.6.properties}, cujos valores são exatamente o padrão {@link #padrao()}.
 * Unidade dos valores: a do arquivo EXTRATO (NUMERIC(14) sem escala; se são centavos NÃO foi determinado).
 */
public record Limiares(
    String versao,
    LocalDate identDataCorte, long identValorMinimo,
    Set<Long> tiposTedDoc, Set<Long> tiposMesmoBanco, Set<Long> tiposEspecie, List<Faixa> faixasEspecie,
    Set<Long> tiposEleitoralTransferencia, long tipoEleitoralDeposito, long eleitoralDepositoValorMinimo,
    Set<Long> tiposCpfCnpjOd) {

    /** Faixa de vigência: a partir de {@code data}, valores >= {@code valorMinimo}. */
    public record Faixa(LocalDate data, long valorMinimo) {}

    public static Limiares padrao() {
        return new Limiares("6.2.6", LocalDate.of(2006, 1, 1), 1_000_000L,
            Set.of(120L, 209L), Set.of(117L, 213L), Set.of(123L, 220L),
            List.of(new Faixa(LocalDate.of(2013, 7, 1), 1_000_000L), new Faixa(LocalDate.of(2013, 1, 2), 5_000_000L), new Faixa(LocalDate.of(2012, 7, 1), 10_000_000L)),
            Set.of(117L, 120L, 209L, 213L), 201L, 10_000L, Set.of(117L, 120L, 209L, 213L));
    }

    public static Limiares carregar(Path arquivo) throws IOException {
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(arquivo, StandardCharsets.UTF_8)) { p.load(r); }
        Limiares d = padrao();
        return new Limiares(p.getProperty("versao", d.versao()),
            data(p, "ident.data_corte", d.identDataCorte()), num(p, "ident.valor_minimo", d.identValorMinimo()),
            tipos(p, "ted_doc.tipos", d.tiposTedDoc()), tipos(p, "mesmo_banco.tipos", d.tiposMesmoBanco()), tipos(p, "especie.tipos", d.tiposEspecie()),
            faixas(p, d.faixasEspecie()),
            tipos(p, "eleitoral.transferencia.tipos", d.tiposEleitoralTransferencia()), num(p, "eleitoral.deposito.tipo", d.tipoEleitoralDeposito()),
            num(p, "eleitoral.deposito.valor_minimo", d.eleitoralDepositoValorMinimo()), tipos(p, "cpf_cnpj_od.tipos", d.tiposCpfCnpjOd()));
    }

    private static LocalDate data(Properties p, String k, LocalDate def) {
        String v = p.getProperty(k);
        try { return v == null ? def : LocalDate.parse(v.trim()); } catch (RuntimeException e) { throw new IllegalArgumentException(k + ": data inválida (use AAAA-MM-DD): " + v, e); }
    }

    private static long num(Properties p, String k, long def) {
        String v = p.getProperty(k);
        try { return v == null ? def : Long.parseLong(v.trim()); } catch (NumberFormatException e) { throw new IllegalArgumentException(k + ": número inválido: " + v, e); }
    }

    private static Set<Long> tipos(Properties p, String k, Set<Long> def) {
        String v = p.getProperty(k);
        if (v == null) return def;
        Set<Long> s = new LinkedHashSet<>();
        try { for (String x : v.split(",")) if (!x.isBlank()) s.add(Long.parseLong(x.trim())); }
        catch (NumberFormatException e) { throw new IllegalArgumentException(k + ": lista de códigos inválida: " + v, e); }
        return Set.copyOf(s);
    }

    private static List<Faixa> faixas(Properties p, List<Faixa> def) {
        String v = p.getProperty("especie.faixas");
        if (v == null) return def;
        List<Faixa> l = new ArrayList<>();
        try { for (String x : v.split(";")) if (!x.isBlank()) { String[] a = x.trim().split(":"); l.add(new Faixa(LocalDate.parse(a[0].trim()), Long.parseLong(a[1].trim()))); } }
        catch (RuntimeException e) { throw new IllegalArgumentException("especie.faixas: formato AAAA-MM-DD:valor;... inválido: " + v, e); }
        return List.copyOf(l);
    }
}
