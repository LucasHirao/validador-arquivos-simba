import br.mp.mpf.pgr.client.Context;
import br.mp.mpf.pgr.client.error.ErroValidacao;
import br.mp.mpf.pgr.client.error.ErrorReportCmd;
import br.mp.mpf.pgr.client.jdbc.ConnectionManager;
import br.mp.mpf.spea.simba.validador.ControladorValidador;
import br.mp.mpf.spea.simba.validador.carga.ApagarTabelasCmd;
import br.mp.mpf.spea.simba.validador.carga.CarregarGab;
import br.mp.mpf.spea.simba.validador.carga.CarregarGabVer2;
import br.mp.mpf.spea.simba.validador.carga.GetAtendimentoGabCmd;
import br.mp.mpf.pgr.client.Configurador;
import br.mp.mpf.spea.simba.validador.domain.ArquivosGab;
import br.mp.mpf.spea.simba.validador.enums.NomeArquivoGab;
import telas.frmSelecionaDiretorioGab;
import br.mp.mpf.spea.simba.validador.desempenho.Desempenho;
import org.springframework.context.support.ClassPathXmlApplicationContext;

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
 * Uso: Nivel2Gab <diretório com subpastas de casos>   -> grava saida-original.txt em cada subpasta.
 */
public class Nivel2Gab {
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
      Files.writeString(dir.resolve(System.getProperty("gab.semdefeito") != null ? "saida-original-semdefeito.txt" : "saida-original.txt"), out, StandardCharsets.UTF_8);
    }
    System.exit(0);
  }

  /** Só com -Dgab.semdefeito: preenche em memória o campo 'camposArquivo' (null no original) para que as validações dos carregadores GAB possam rodar. */
  static void remendo(Object carregarGab, int[] colunas) throws Exception {
    if (System.getProperty("gab.semdefeito") == null) return;
    String[] nomes = {"arquivoGab109", "arquivoGab112", "arquivoGab800"};
    java.lang.reflect.Field alvo = br.mp.mpf.spea.simba.validador.carga.AbstractCarregarArquivoCmd.class.getDeclaredField("camposArquivo");
    alvo.setAccessible(true);
    for (int i = 0; i < 3; i++) {
      java.lang.reflect.Field f = carregarGab.getClass().getDeclaredField(nomes[i]); f.setAccessible(true);
      Object loader = f.get(carregarGab);
      br.mp.mpf.spea.simba.validador.carga.CampoArquivo[] arr = new br.mp.mpf.spea.simba.validador.carga.CampoArquivo[colunas[i]];
      java.util.Arrays.fill(arr, br.mp.mpf.spea.simba.validador.carga.CampoArquivo.from("X", br.mp.mpf.spea.simba.validador.carga.TipoCampoArquivo.TEXTO, 1000000));
      alvo.set(loader, arr);
    }
  }

  static String rodar(ClassPathXmlApplicationContext app, Context ctx, ConnectionManager cm, ErrorReportCmd erros, ControladorValidador controlador, Path dir) throws Exception {
    String caso;
    try (Stream<Path> s = Files.list(dir)) {
      caso = s.map(p -> p.getFileName().toString()).filter(n -> n.endsWith("_GAB112.txt")).map(n -> n.substring(0, n.length() - "_GAB112.txt".length())).findFirst().orElseThrow();
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
    int versao = Integer.parseInt(Files.readString(dir.resolve("layout.txt")).trim());
    app.getBean(Configurador.class).setVersaoLayoutGab(versao);
    ArquivosGab arq = new ArquivosGab();
    arq.setGab109(dir.resolve(caso + "_GAB109.txt").toFile());
    arq.setGab112(dir.resolve(caso + "_GAB112.txt").toFile());
    arq.setGab800(dir.resolve(caso + "_GAB800.txt").toFile());
    frmSelecionaDiretorioGab.ViewGab view = (frmSelecionaDiretorioGab.ViewGab) Proxy.newProxyInstance(Nivel2Gab.class.getClassLoader(), new Class[]{frmSelecionaDiretorioGab.ViewGab.class}, (p, m, args) -> null);
    Desempenho des = new Desempenho(); des.tarefaBackgroundGab = view;
    Map<String, Boolean> conteudo = null;
    controlador.verificarArquivosGabVazios(arq, erros);
    if (!erros.hasErros()) {
      var atend = app.getBean(GetAtendimentoGabCmd.class).get(caso);
      if (versao == 1) { CarregarGab c = app.getBean(CarregarGab.class); c.setDesempenho(des); remendo(c, new int[]{7, 21, 11}); c.carregarGab(atend, arq); }
      else { CarregarGabVer2 c = app.getBean(CarregarGabVer2.class); c.setDesempenho(des); remendo(c, new int[]{17, 32, 12}); c.carregarGab(atend, arq); }
      if (!erros.hasErros()) {
        conteudo = new LinkedHashMap<>();
        for (NomeArquivoGab n : NomeArquivoGab.values()) {
          boolean ok = controlador.validarConteudoGab(n, view);
          conteudo.put(n.name(), ok);
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
