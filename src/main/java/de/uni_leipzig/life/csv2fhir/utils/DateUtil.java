package de.uni_leipzig.life.csv2fhir.utils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Calendar;
import java.util.Date;

import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;

/**
 * @author fheuschkel (02.11.2020)
 */
public class DateUtil {

    /** Parse dates without inventing a month or day for partial ISO dates. */
    public static DateType parseDateType(String date) throws Exception {
        if (date == null || date.isBlank()) return null;
        if (date.matches("\\d{4}(-\\d{2}(-\\d{2})?)?")) return new DateType(date);
        return new DateType(parseLocalDateTime(date).toLocalDate().toString());
    }

    /**
     * @param dateTime
     * @return
     * @throws Exception
     */
    private static LocalDate parseLocalDate(String dateTime) throws Exception {
        if (!dateTime.isBlank()) {
            return tryDayFormat1(dateTime);
        }
        throw new Exception();
    }

    /**
     * @param dateTime
     * @return
     * @throws Exception
     */
    private static LocalDate tryDayFormat1(String dateTime) throws Exception {
        try {
            return Year.parse(dateTime).atDay(1);
        } catch (DateTimeParseException e) {
            return tryDayFormat2(dateTime);
        }
    }

    /**
     * @param dateTime
     * @return
     * @throws Exception
     */
    private static LocalDate tryDayFormat2(String dateTime) throws Exception {
        try {
            return YearMonth.parse(dateTime).atDay(1);
        } catch (DateTimeParseException e) {
            return tryDayFormat3(dateTime);
        }
    }

    /**
     * @param dateTime
     * @return
     * @throws Exception
     */
    private static LocalDate tryDayFormat3(String dateTime) throws Exception {
        try {
            return LocalDate.parse(dateTime);
        } catch (DateTimeParseException e) {
            return tryDayFormat4(dateTime);
        }
    }

    /**
     * @param dateTime
     * @return
     * @throws Exception
     */
    private static LocalDate tryDayFormat4(String dateTime) throws Exception {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
            return LocalDate.parse(dateTime, formatter);
        } catch (DateTimeParseException e) {
            return tryDayFormat5(dateTime);
        }
    }

    /**
     * @param dateTime
     * @return
     * @throws Exception
     */
    private static LocalDate tryDayFormat5(String dateTime) throws Exception {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("M/dd/yyyy");
            return LocalDate.parse(dateTime, formatter);
        } catch (DateTimeParseException e) {
            return tryDayFormat6(dateTime);
        }
    }

    /**
     * @param dateTime
     * @return
     * @throws Exception
     */
    private static LocalDate tryDayFormat6(String dateTime) throws Exception {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("M/d/yyyy");
            return LocalDate.parse(dateTime, formatter);
        } catch (DateTimeParseException e) {
            return LocalDate.parse(dateTime, DateTimeFormatter.ofPattern("dd.MM.uuuu")
                    .withResolverStyle(java.time.format.ResolverStyle.STRICT));
        }
    }

    /**
     * @param date
     * @return
     * @throws Exception
     */
    public static DateTimeType parseDateTimeType(String date) throws Exception {
        if (date.matches("\\d{4}(-\\d{2}(-\\d{2})?)?")) return new DateTimeType(date);
        if (!date.contains(":")) return new DateTimeType(parseLocalDate(date).toString());
        try {
            java.time.OffsetDateTime.parse(date);
            return new DateTimeType(date);
        } catch (DateTimeParseException e) {
            // Local timestamps use the system zone below.
        }
        LocalDateTime parsedLocalDateTime = parseLocalDateTime(date);
        ZoneId systemDefaultZoneId = ZoneId.systemDefault();
        Instant instantDate = parsedLocalDateTime.atZone(systemDefaultZoneId).toInstant();
        Date resultDate = Date.from(instantDate);
        return new DateTimeType(resultDate, TemporalPrecisionEnum.SECOND);
    }

    /**
     * @param date
     * @return
     * @throws Exception
     */
    private static LocalDateTime parseLocalDateTime(String date) throws Exception {
        try {
            LocalDate localDate = parseLocalDate(date);
            return localDate.atStartOfDay();
        } catch (Exception e) {
            return tryTimeFormat1(date);
        }
    }

    /**
     * @param date
     * @return
     * @throws Exception
     */
    private static LocalDateTime tryTimeFormat1(String date) throws Exception {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy, H:mm");
            return LocalDateTime.parse(date, formatter);
        } catch (DateTimeParseException e) {
            return tryTimeFormat2(date);
        }
    }

    /**
     * @param date
     * @return
     * @throws Exception
     */
    private static LocalDateTime tryTimeFormat2(String date) throws Exception {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy H:mm");
            return LocalDateTime.parse(date, formatter);
        } catch (DateTimeParseException e) {
            return tryTimeFormat3(date);
        }
    }

    /**
     * @param date
     * @return
     * @throws Exception
     */
    private static LocalDateTime tryTimeFormat3(String date) throws Exception {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy H:mm:ss");
            return LocalDateTime.parse(date, formatter);
        } catch (DateTimeParseException e) {
            return tryTimeFormat4(date);
        }
    }

    /**
     * @param date
     * @return
     * @throws Exception
     */
    private static LocalDateTime tryTimeFormat4(String date) throws Exception {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd H:mm:ss");
            return LocalDateTime.parse(date, formatter);
        } catch (DateTimeParseException e) {
            return tryTimeFormat5(date);
        }
    }

    /**
     * @param date
     * @return
     * @throws Exception
     */
    private static LocalDateTime tryTimeFormat5(String date) throws Exception {
        try {
            return LocalDateTime.parse(date, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException e) {
            throw new Exception();
        }
    }

    /**
     * Adds or substract (if days negative) the given amount of days to to given
     * date.
     *
     * @param dateTimeType
     * @param days
     * @return a new {@link DateTimeType} changed by the given amount of days
     */
    public static DateTimeType addDays(DateTimeType dateTimeType, int days) {
        Calendar calendar = dateTimeType.toCalendar();
        calendar.add(Calendar.DAY_OF_YEAR, days);
        return new DateTimeType(calendar);
    }

}
