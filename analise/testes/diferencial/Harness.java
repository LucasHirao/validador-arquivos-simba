import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Harness nível 1: chama SOMENTE funções puras do artefato original. Lê vetores (func<TAB>hex) de stdin, imprime func<TAB>hex-entrada<TAB>resultado. */
public class Harness {
  static String hex(String s){ StringBuilder b=new StringBuilder(); for(byte x: s.getBytes(StandardCharsets.UTF_8)) b.append(String.format("%02x",x)); return b.toString(); }
  static String unhex(String h){ byte[] r=new byte[h.length()/2]; for(int i=0;i<r.length;i++) r[i]=(byte)Integer.parseInt(h.substring(2*i,2*i+2),16); return new String(r,StandardCharsets.UTF_8); }
  public static void main(String[] a) throws Exception {
    BufferedReader in=new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
    String l;
    while((l=in.readLine())!=null){
      String[] p=l.split("\t",2); String f=p[0]; String h=p.length>1?p[1]:""; String x=unhex(h); String out;
      try {
        switch(f){
          case "split": { String[] d=br.mp.mpf.spea.simba.validador.util.Util.tabSeparatedStringToArray(x); StringBuilder b=new StringBuilder(String.valueOf(d.length)); for(String s: d) b.append(",").append(hex(s)); out=b.toString(); break; }
          case "data": out=String.valueOf(br.mp.mpf.asspa.client.Util.verificaData(x)); break;
          case "cpf": out=String.valueOf(br.mp.mpf.pgr.util.CpfCnpjUtil.validarCpf(x)); break;
          case "cnpj": out=String.valueOf(br.mp.mpf.pgr.util.CpfCnpjUtil.validarCnpj(x)); break;
          case "cnabC": out=String.valueOf(new br.mp.mpf.spea.simba.validador.rules.CnabRule().validarCnabCredito(Integer.parseInt(x))); break;
          case "cnabD": out=String.valueOf(new br.mp.mpf.spea.simba.validador.rules.CnabRule().validarCnabDebito(Integer.parseInt(x))); break;
          case "cnabCD": out=String.valueOf(new br.mp.mpf.spea.simba.validador.rules.CnabRule().validarCnabCreditoDebito(Integer.parseInt(x))); break;
          case "rmzeros": out=hex(br.mp.mpf.pgr.util.StringUtil.removerZerosEsquerda(x)); break;
          case "zeros11": out=hex(String.valueOf(br.mp.mpf.pgr.util.StringUtil.completarZerosEsquerda(x,11))); break;
          case "empty": out=String.valueOf(br.mp.mpf.pgr.util.StringUtil.isEmpty(x)); break;
          default: out="?func";
        }
      } catch(Throwable t){ out="EXC:"+t.getClass().getSimpleName(); }
      System.out.println(f+"\t"+h+"\t"+out);
    }
  }
}
