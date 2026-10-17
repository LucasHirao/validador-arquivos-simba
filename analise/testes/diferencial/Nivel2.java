import br.mp.mpf.pgr.client.Context;
import br.mp.mpf.pgr.client.error.ErroValidacao;
import br.mp.mpf.pgr.client.error.ErrorReportCmd;
import br.mp.mpf.pgr.client.jdbc.ConnectionManager;
import br.mp.mpf.spea.simba.validador.ControladorValidador;
import br.mp.mpf.spea.simba.validador.carga.ApagarTabelasCmd;
import br.mp.mpf.spea.simba.validador.carga.Carregar;
import br.mp.mpf.spea.simba.validador.carga.GetAtendimentoBancarioCmd;
import br.mp.mpf.spea.simba.validador.desempenho.Desempenho;
import br.mp.mpf.spea.simba.validador.domain.Arquivos;
import br.mp.mpf.spea.simba.validador.enums.NomeArquivo;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import telas.frmSelecionaDiretorio;

import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.*;
import java.util.stream.Stream;

/**
 * Harness NÍVEL 2: executa o pipeline REAL do validador (carga + regras SQL em HSQLDB) sem a interface gráfica, reproduzindo o laço de
 * frmSelecionaDiretorio.doInBackground (sem hash/cópia/versão). Rodar SEM REDE, em diretório de trabalho descartável.
 * Uso: Nivel2 <diretório com subpastas de casos>   -> grava saida-original.txt em cada subpasta.
 */
public class Nivel2 {
  static final String[] ORDEM = {"AGENCIAS", "CONTAS", "TITULARES", "EXTRATO", "ORIGEM_DESTINO"};

  public static void main(String[] a) throws Exception {
    System.setProperty("java.awt.headless", "true");
    ClassPathXmlApplicationContext app = new ClassPathXmlApplicationContext("validador-spring.xml");
    Context ctx = app.getBean(Context.class);
    ConnectionManager cm = app.getBean(ConnectionManager.class);
    ErrorReportCmd erros = app.getBean(ErrorReportCmd.class);
    ControladorValidador controlador = (ControladorValidador) app.getBean("controlador");
    Path raiz = Path.of(a[0]);
    List<Path> casos;
    try (Stream<Path> s = Files.list(raiz)) { casos = s.filter(Files::isDirectory).sorted().toList(); }
    for (Path dir : casos) {
      String out;
      try { out = rodar(app, ctx, cm, erros, controlador, dir); }
      catch (Throwable t) { out = "#FALHA_HARNESS " + t + "\n"; t.printStackTrace(System.err); }
      Files.writeString(dir.resolve("saida-original.txt"), out, StandardCharsets.UTF_8);
    }
    System.exit(0);
  }

  static String rodar(ClassPathXmlApplicationContext app, Context ctx, ConnectionManager cm, ErrorReportCmd erros, ControladorValidador controlador, Path dir) throws Exception {
    String caso;
    try (Stream<Path> s = Files.list(dir)) {
      caso = s.map(p -> p.getFileName().toString()).filter(n -> n.endsWith("_AGENCIAS.txt")).map(n -> n.substring(0, n.length() - "_AGENCIAS.txt".length())).findFirst().orElseThrow();
    }
    erros.reset();
    ctx.setNumeroCaso(caso);                       // cria o HSQLDB do caso + tabelas (CriarTabelasValidador)
    Connection con = cm.get();
    con.createStatement().execute("delete from investigado");
    con.createStatement().execute("delete from atendimento");
    try (PreparedStatement ps = con.prepareStatement("insert into atendimento (NUMERO_CASO, NUMERO_BANCO, NOME_BANCO, DATA_INICIO_AFASTAMENTO, DATA_FIM_AFASTAMENTO, VERSAO_VALIDADOR) values (?, 1, 'BANCO TESTE', DATE '2020-01-01', DATE '2020-12-31', '6.2.6')")) {
      ps.setString(1, caso); ps.execute();
    }
    Path inv = dir.resolve("investigados.tsv");
    if (Files.exists(inv)) for (String l : Files.readAllLines(inv, StandardCharsets.ISO_8859_1)) {
      if (l.isBlank()) continue;
      String[] c = l.split("\t", -1);
      try (PreparedStatement ps = con.prepareStatement("insert into investigado (TIPO_PESSOA, CPF_CNPJ_PESSOA, NOME_PESSOA, TEVE_RELACIONAMENTO, TEVE_CONTA, TEVE_BDV, DATA_INICIO_AFASTAMENTO, DATA_FIM_AFASTAMENTO) values (?, ?, ?, ?, ?, 0, ?, ?)")) {
        ps.setInt(1, Integer.parseInt(c[0].trim())); ps.setString(2, c[1].trim()); ps.setString(3, c[2].trim());
        ps.setInt(4, Integer.parseInt(c[3].trim())); ps.setInt(5, Integer.parseInt(c[4].trim()));
        ps.setObject(6, c.length > 5 && !c[5].isBlank() ? java.sql.Date.valueOf(java.time.LocalDate.parse(c[5].trim(), java.time.format.DateTimeFormatter.ofPattern("ddMMyyyy"))) : null, java.sql.Types.DATE);
        ps.setObject(7, c.length > 6 && !c[6].isBlank() ? java.sql.Date.valueOf(java.time.LocalDate.parse(c[6].trim(), java.time.format.DateTimeFormatter.ofPattern("ddMMyyyy"))) : null, java.sql.Types.DATE);
        ps.execute();
      }
    }
    Arquivos arq = new Arquivos();
    arq.setAgencias(dir.resolve(caso + "_AGENCIAS.txt").toFile());
    arq.setContas(dir.resolve(caso + "_CONTAS.txt").toFile());
    arq.setTitulares(dir.resolve(caso + "_TITULARES.txt").toFile());
    arq.setExtrato(dir.resolve(caso + "_EXTRATO.txt").toFile());
    arq.setOrigemDestino(dir.resolve(caso + "_ORIGEM_DESTINO.txt").toFile());
    frmSelecionaDiretorio.View view = (frmSelecionaDiretorio.View) Proxy.newProxyInstance(Nivel2.class.getClassLoader(), new Class[]{frmSelecionaDiretorio.View.class}, (p, m, args) -> null);
    Desempenho des = new Desempenho(); des.tarefaBackground = view;
    Map<String, Boolean> conteudo = null;
    controlador.verificarArquivosVazios(arq, erros);
    if (!erros.hasErros()) {
      Carregar carregar = app.getBean(Carregar.class);
      carregar.setDesempenho(des);
      carregar.carregar(app.getBean(GetAtendimentoBancarioCmd.class).get(caso), arq);
      if (!erros.hasErros()) {
        conteudo = new LinkedHashMap<>();
        NomeArquivo[] nomes = NomeArquivo.values();
        for (int i = 0; i < nomes.length; i++) {
          boolean ok = controlador.validarConteudo(i, view);
          if (i > 0) conteudo.put(nomes[i].name(), ok);
          if (!ok) break;
        }
      }
    }
    StringBuilder sb = new StringBuilder();
    for (Iterator<ErroValidacao> it = erros.iterator(); it.hasNext(); ) sb.append(it.next().getPrettyMessage()).append('\n');
    sb.append("#CONTEUDO ");
    if (conteudo == null) sb.append("null"); else { int i = 0; for (var e : conteudo.entrySet()) sb.append(i++ > 0 ? "," : "").append(e.getKey()).append('=').append(e.getValue()); }
    sb.append('\n');
    erros.reset();
    ctx.setNumeroCaso(null);                      // SHUTDOWN COMPACT
    Path dados = Path.of("..", "dadosValidador").toAbsolutePath().normalize();
    if (Files.exists(dados)) try (Stream<Path> w = Files.walk(dados)) { w.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete()); }
    return sb.toString();
  }
}
