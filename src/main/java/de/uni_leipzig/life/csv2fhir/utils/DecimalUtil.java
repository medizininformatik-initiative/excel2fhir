package de.uni_leipzig.life.csv2fhir.utils;

import java.math.BigDecimal;

/**
 * @author fheuschkel (02.11.2020)
 */
public class DecimalUtil {

    /**
     * @param s
     * @return
     * @throws Exception
     */
    public static BigDecimal parseDecimal(String s) throws Exception {
        s = s.trim().replace(',', '.');
        return new BigDecimal(s);
    }

    /**
     * @param s
     * @return
     */
    public static String parseComparator(String s) {
        s = s.trim();
        if (s.startsWith("<=")) {
            return "<=";
        }
        if (s.startsWith(">=")) {
            return ">=";
        }
        if (s.startsWith("<")) {
            return "<";
        }
        if (s.startsWith(">")) {
            return ">";
        }
        return null;
    }

}
