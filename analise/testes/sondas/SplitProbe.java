import java.util.*;
public class SplitProbe {
  static String[] f(String t){ String[] d=t.split("(?=\t)"); for(int j=0;j<d.length;j++) d[j]=d[j].trim(); return d; }
  public static void main(String[] a){
    String[] c={"a\tb\tc","\tb\tc","a\t\tc","a\tb\t","a\tb\t\t","","\t","\t\t","  a \t b "};
    for(String s:c) System.out.println(s.replace("\t","<TAB>")+" -> "+f(s).length+" "+Arrays.toString(f(s)));
    System.out.println("valueOf(\"\")? ");
    try{Integer.valueOf("");}catch(Exception e){System.out.println(e.getClass().getSimpleName());}
    java.text.NumberFormat nf=java.text.NumberFormat.getInstance();
    for(String s:new String[]{"12abc","1.000","1,5",""}){ try{System.out.println("isNumber("+s+")="+nf.parse(s)+" locale="+Locale.getDefault());}catch(Exception e){System.out.println("isNumber("+s+")=ParseException");} }
  }
}
