package de.uni_leipzig.life.csv2fhir.utils;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * @author fheuschkel (02.11.2020)
 */
public class DecimalUtil {

    /**  */
    static Pattern pattern = Pattern.compile("-?\\d+(\\.\\d*)?");

    /**
     * @param s
     * @return
     * @throws Exception
     */
    public static BigDecimal parseDecimal(String s) throws Exception {
        if (s != null && pattern.matcher(s).matches()) {
            return new BigDecimal(s);
        }
        throw new Exception();
    }
}
