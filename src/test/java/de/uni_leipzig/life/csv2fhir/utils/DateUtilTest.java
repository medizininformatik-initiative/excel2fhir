package de.uni_leipzig.life.csv2fhir.utils;

import static org.junit.Assert.assertEquals;

import java.util.Calendar;
import java.util.TimeZone;

import org.hl7.fhir.r4.model.DateTimeType;
import org.junit.Test;

public class DateUtilTest {

    @Test
    public void parseDateTimeTypeAcceptsGermanTimestampWithSeconds() throws Exception {
        DateTimeType result = DateUtil.parseDateTimeType("03.05.2026 14:15:16");

        assertDateTime(result, 2026, Calendar.MAY, 3, 14, 15, 16);
    }

    @Test
    public void parseDateTimeTypeAcceptsIsoTimestampWithBlankSeparator() throws Exception {
        DateTimeType result = DateUtil.parseDateTimeType("2026-05-03 14:15:16");

        assertDateTime(result, 2026, Calendar.MAY, 3, 14, 15, 16);
    }

    @Test
    public void parseDateTimeTypeAcceptsIsoTimestampWithTSeparator() throws Exception {
        DateTimeType result = DateUtil.parseDateTimeType("2026-05-03T14:15:16");

        assertDateTime(result, 2026, Calendar.MAY, 3, 14, 15, 16);
    }

    private static void assertDateTime(DateTimeType actual, int year, int month, int day, int hour, int minute,
            int second) {
        Calendar calendar = actual.toCalendar();
        calendar.setTimeZone(TimeZone.getDefault());
        assertEquals(year, calendar.get(Calendar.YEAR));
        assertEquals(month, calendar.get(Calendar.MONTH));
        assertEquals(day, calendar.get(Calendar.DAY_OF_MONTH));
        assertEquals(hour, calendar.get(Calendar.HOUR_OF_DAY));
        assertEquals(minute, calendar.get(Calendar.MINUTE));
        assertEquals(second, calendar.get(Calendar.SECOND));
    }
}
