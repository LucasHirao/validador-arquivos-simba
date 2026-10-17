package estudo.simba.validador;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

/** Datas ddMMyyyy (A-01). */
public final class DateRules {
    private DateRules() {}

    /** 8 caracteres, ano >= 1900, ddMMyyyy não-leniente. */
    public static boolean verificaData(String s) {
        try {
            if (s.length() != 8) return false;
            int ano = Integer.valueOf(s.substring(4, 8));
            if (ano < 1900) return false;
            SimpleDateFormat f = new SimpleDateFormat("ddMMyyyy");
            f.setLenient(false);
            f.parse(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Parse leniente (SimpleDateFormat padrão), usado pelo original em L-AG-05 e L-TI-06. */
    public static LocalDate parseLenient(String s) throws ParseException {
        Date d = new SimpleDateFormat("ddMMyyyy").parse(s);
        return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    /** Para datas já validadas por {@link #verificaData}. */
    public static LocalDate of(String s) {
        return LocalDate.of(Integer.parseInt(s.substring(4, 8)), Integer.parseInt(s.substring(2, 4)), Integer.parseInt(s.substring(0, 2)));
    }

    public static String br(LocalDate d) {
        return String.format("%02d/%02d/%04d", d.getDayOfMonth(), d.getMonthValue(), d.getYear());
    }
}
