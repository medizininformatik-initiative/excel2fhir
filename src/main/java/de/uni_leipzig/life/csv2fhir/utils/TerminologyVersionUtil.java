package de.uni_leipzig.life.csv2fhir.utils;

import java.util.Calendar;
import java.util.Date;

import org.hl7.fhir.r4.model.DateTimeType;

/**
 * Picks German BfArM terminology versions from the year of the clinical event.
 */
public final class TerminologyVersionUtil {

    private TerminologyVersionUtil() {
    }

    public static String getIcd10GmVersion(DateTimeType date) {
        return getYearVersion(date, 2009, 2025);
    }

    public static String getOpsVersion(DateTimeType date) {
        return getYearVersion(date, 2010, 2025);
    }

    public static String getAtcVersion(DateTimeType date) {
        return getYearVersion(date, 2018, 2025);
    }

    private static String getYearVersion(DateTimeType date, int minYear, int maxYear) {
        int year = getYear(date);
        if (year < minYear) {
            year = minYear;
        } else if (year > maxYear) {
            year = maxYear;
        }
        return Integer.toString(year);
    }

    private static int getYear(DateTimeType date) {
        if (date != null && date.hasValue()) {
            Date value = date.getValue();
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(value);
            return calendar.get(Calendar.YEAR);
        }
        return Calendar.getInstance().get(Calendar.YEAR);
    }
}
