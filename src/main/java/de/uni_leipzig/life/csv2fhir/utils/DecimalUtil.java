package de.uni_leipzig.life.csv2fhir.utils;

import java.math.BigDecimal;
import java.util.regex.Pattern;

public class DecimalUtil {

    static Pattern pattern = Pattern.compile("-?\\d+(\\.\\d*)?");

    public static BigDecimal parseDecimal(String s) throws Exception {
        if (s != null && pattern.matcher(s).matches()) {
            return new BigDecimal(s);
        } else {
            throw new Exception();
        }
    }
}
