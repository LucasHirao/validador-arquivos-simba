import br.mp.mpf.asspa.client.Arquivo;
import br.mp.mpf.pgr.client.to.Computador;
import java.io.File;
/** Nível 2: Arquivo.obterHashArquivo(Computador) com receptor de versões diferentes. Uso: HashHarness versao arquivo... */
public class HashHarness {
  public static void main(String[] a) throws Exception {
    Computador c = new Computador(); c.setVersao_receptor(a[0]);
    for (int i = 1; i < a.length; i++) System.out.println(a[i] + "\t" + new Arquivo(new File(a[i])).obterHashArquivo(c));
    System.exit(0);
  }
}
