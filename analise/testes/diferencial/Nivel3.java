import br.mp.mpf.pgr.client.Context;
import br.mp.mpf.pgr.client.Controlador;
import br.mp.mpf.pgr.client.jdbc.ConnectionManager;
import br.mp.mpf.pgr.client.to.Computadores;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import telas.frmSelecionaDiretorio;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.List;
import java.util.stream.Stream;

/**
 * Harness NÍVEL 3: sobe a TELA REAL (frmSelecionaDiretorio) do validador, aciona a validação como o clique em "Selecionar Pasta" faria
 * (chama iniciarValidacao(File), pois o JFileChooser é modal), espera o SwingWorker terminar e lê o que a tela mostra.
 * A lista de órgãos (que o aplicativo buscaria por HTTP) é injetada em memória: nenhuma conexão é aberta.
 * Rodar SEM REDE. Uso: Nivel3 <pasta de cenários>   Props: -Dgui.pausa=ms  -Dgui.receptor=4.0.0
 */
public class Nivel3 {
  static final List<String> dialogos = Collections.synchronizedList(new ArrayList<>());

  static Object campo(Object o, String nome) throws Exception {
    for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
      try { Field f = c.getDeclaredField(nome); f.setAccessible(true); return f.get(o); } catch (NoSuchFieldException e) { /* sobe */ }
    }
    throw new NoSuchFieldException(nome);
  }

  public static void main(String[] a) throws Exception {
    System.setProperty("java.awt.headless", "false");
    String receptor = System.getProperty("gui.receptor", "4.0.0");
    int pausa = Integer.parseInt(System.getProperty("gui.pausa", "1500"));
    ClassPathXmlApplicationContext app = new ClassPathXmlApplicationContext("validador-spring.xml");
    Context ctx = app.getBean(Context.class);
    ConnectionManager cm = app.getBean(ConnectionManager.class);
    // injeta a lista de órgãos em TODOS os beans Controlador (evita o HTTP GET orgaos?codprog=...)
    Computadores comps = new Computadores();
    for (String nome : new String[]{"001-TST", "001-MPE"})
      comps.addComputador(nome.equals("001-TST") ? 1 : 2, 1, nome, nome, "ORGAO TESTE", "OT", "", "", "", "", "", "", "", receptor);
    comps.addComputador(999, 0, "Número do Caso", "999-OUTROS", "Outros", "", "", "", "", "", "", "", "", "");
    for (Controlador c : app.getBeansOfType(Controlador.class).values()) {
      Field f = Controlador.class.getDeclaredField("comps"); f.setAccessible(true); f.set(c, comps);
    }
    // vigia diálogos modais (ex.: avisos) para não travar o harness; registra o texto
    Thread vigia = new Thread(() -> {
      while (true) {
        try { Thread.sleep(300); } catch (InterruptedException e) { return; }
        for (Window w : Window.getWindows()) {
          if (w instanceof JDialog d && d.isShowing()) {
            StringBuilder sb = new StringBuilder();
            collect(d.getContentPane(), sb);
            dialogos.add(sb.toString().replaceAll("\\s+", " ").trim());
            SwingUtilities.invokeLater(d::dispose);
          }
        }
      }
    }, "vigia-dialogos");
    vigia.setDaemon(true); vigia.start();

    List<Path> casos;
    try (Stream<Path> s = Files.list(Path.of(a[0]))) { casos = s.filter(Files::isDirectory).sorted().toList(); }
    for (Path dir : casos) {
      String out;
      try { out = rodar(app, ctx, cm, dir, pausa); }
      catch (Throwable t) { out = "#FALHA_GUI " + t + "\n"; t.printStackTrace(System.err); }
      Files.writeString(dir.resolve("saida-gui.txt"), out, StandardCharsets.UTF_8);
    }
    System.exit(0);
  }

  static void collect(Container c, StringBuilder sb) {
    for (Component k : c.getComponents()) {
      if (k instanceof JLabel l && l.getText() != null) sb.append(l.getText()).append(' ');
      if (k instanceof JTextArea t) sb.append(t.getText()).append(' ');
      if (k instanceof Container cc) collect(cc, sb);
    }
  }

  static String rodar(ClassPathXmlApplicationContext app, Context ctx, ConnectionManager cm, Path dir, int pausa) throws Exception {
    String caso;
    try (Stream<Path> s = Files.list(dir)) {
      caso = s.map(p -> p.getFileName().toString()).filter(n -> n.endsWith("_AGENCIAS.txt")).map(n -> n.substring(0, n.length() - "_AGENCIAS.txt".length())).findFirst().orElseThrow();
    }
    dialogos.clear();
    ctx.setNumeroCaso(caso);
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
        java.time.format.DateTimeFormatter f = java.time.format.DateTimeFormatter.ofPattern("ddMMyyyy");
        ps.setObject(6, c.length > 5 && !c[5].isBlank() ? java.sql.Date.valueOf(java.time.LocalDate.parse(c[5].trim(), f)) : null, java.sql.Types.DATE);
        ps.setObject(7, c.length > 6 && !c[6].isBlank() ? java.sql.Date.valueOf(java.time.LocalDate.parse(c[6].trim(), f)) : null, java.sql.Types.DATE);
        ps.execute();
      }
    }
    final frmSelecionaDiretorio[] frm = new frmSelecionaDiretorio[1];
    SwingUtilities.invokeAndWait(() -> { frm[0] = app.getBean(frmSelecionaDiretorio.class); frm[0].setLocation(80, 60); frm[0].setVisible(true); });
    Thread.sleep(600);                                                         // a janela aparece vazia antes de iniciar
    SwingUtilities.invokeAndWait(() -> {
      try {
        ((JTextField) campo(frm[0], "pathPastaTextField")).setText(dir.toAbsolutePath().toString());
        java.lang.reflect.Method m = frmSelecionaDiretorio.class.getDeclaredMethod("iniciarValidacao", File.class);
        m.setAccessible(true); m.invoke(frm[0], dir.toFile());
      } catch (Exception e) { throw new RuntimeException(e); }
    });
    JButton sel = (JButton) campo(frm[0], "selecionarPastaButton");
    long fim = System.currentTimeMillis() + 600_000;
    Thread.sleep(300);
    while (true) {
      final boolean[] pronto = {false};
      SwingUtilities.invokeAndWait(() -> {
        try {
          Object des = campo(frm[0], "desempenho");
          Object tarefa = campo(des, "tarefaBackground");
          pronto[0] = sel.isEnabled() && tarefa != null && ((SwingWorker<?, ?>) tarefa).isDone();
        } catch (Exception e) { throw new RuntimeException(e); }
      });
      if (pronto[0]) break;
      if (System.currentTimeMillis() > fim) throw new IllegalStateException("timeout aguardando a validação");
      Thread.sleep(150);
    }
    Thread.sleep(400);
    // captura o que a tela mostra
    final String[] texto = {""}; final StringBuilder estado = new StringBuilder(); final Map<String, Boolean> conteudo = new LinkedHashMap<>();
    SwingUtilities.invokeAndWait(() -> {
      try {
        texto[0] = ((JTextArea) campo(frm[0], "detalhesTextArea")).getText();
        Icon ok = ((JLabel) campo(frm[0], "okLabel")).getIcon(), erro = ((JLabel) campo(frm[0], "erroLabel")).getIcon();
        String[][] rot = {{"agencia", "AGENCIAS"}, {"conta", "CONTAS"}, {"titulares", "TITULARES"}, {"extrato", "EXTRATO"}, {"origemDestino", "ORIGEM_DESTINO"}};
        for (String[] r : rot) {
          for (String tipo : new String[]{"Formato", "Conteudo"}) {
            Icon ic = ((JLabel) campo(frm[0], r[0] + tipo + "Label")).getIcon();
            String st = ic == ok ? "OK" : ic == erro ? "ERRO" : "-";
            estado.append("icone ").append(r[1]).append(' ').append(tipo).append('=').append(st).append('\n');
            if (tipo.equals("Conteudo") && !st.equals("-")) conteudo.put(r[1], st.equals("OK"));
          }
        }
        estado.append("botao continuar=").append(((JButton) campo(frm[0], "continuarButton")).isEnabled()).append('\n');
        estado.append("botao verRelatorio=").append(((JButton) campo(frm[0], "verRelatorioButton")).isEnabled()).append('\n');
        BufferedImage img = new BufferedImage(frm[0].getWidth(), frm[0].getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics(); frm[0].paint(g); g.dispose();
        ImageIO.write(img, "png", dir.resolve("gui.png").toFile());
      } catch (Exception e) { throw new RuntimeException(e); }
    });
    Thread.sleep(pausa);                                                       // tempo para você ver a janela
    SwingUtilities.invokeAndWait(() -> { frm[0].setVisible(false); frm[0].dispose(); });
    // hash e contagem de linhas gravados + arquivos copiados para a pasta do caso
    Connection c2 = cm.get();
    try (ResultSet rs = c2.createStatement().executeQuery("select HASH_ARQUIVO_1, HASH_ARQUIVO_2, HASH_ARQUIVO_3, HASH_ARQUIVO_4, HASH_ARQUIVO_5, LINHAS_ARQUIVO_1, LINHAS_ARQUIVO_2, LINHAS_ARQUIVO_3, LINHAS_ARQUIVO_4, LINHAS_ARQUIVO_5 from atendimento")) {
      if (rs.next()) for (int i = 1; i <= 5; i++) estado.append("hash").append(i).append('=').append(rs.getString(i)).append("  linhas").append(i).append('=').append(rs.getString(i + 5)).append('\n');
    }
    Path arqtxt = Path.of("..", "dadosValidador", caso, "arqtxt").toAbsolutePath().normalize();
    if (Files.isDirectory(arqtxt)) try (Stream<Path> s = Files.list(arqtxt)) { s.map(p -> p.getFileName().toString()).sorted().forEach(n -> estado.append("copiado=").append(n).append('\n')); }
    estado.append("dialogos=").append(dialogos).append('\n');
    Files.writeString(dir.resolve("gui-texto.txt"), texto[0], StandardCharsets.UTF_8);
    Files.writeString(dir.resolve("gui-estado.txt"), estado.toString(), StandardCharsets.UTF_8);
    StringBuilder sb = new StringBuilder();
    for (String l : texto[0].split("\n")) { String t = l.trim().replaceFirst("^\\d\\d/\\d\\d/\\d\\d \\d\\d:\\d\\d:\\d\\d - ", ""); if (t.startsWith("Erro no arquivo:")) sb.append(t).append('\n'); }
    sb.append("#CONTEUDO ");
    if (conteudo.isEmpty()) sb.append("null"); else { int i = 0; for (var e : conteudo.entrySet()) sb.append(i++ > 0 ? "," : "").append(e.getKey()).append('=').append(e.getValue()); }
    sb.append('\n');
    ctx.setNumeroCaso(null);
    Path dados = Path.of("..", "dadosValidador").toAbsolutePath().normalize();
    if (Files.exists(dados)) try (Stream<Path> w = Files.walk(dados)) { w.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete()); }
    return sb.toString();
  }
}
