package de.uni_leipzig.life.csv2fhir.utils;

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;

public class DateUtil {

    public static DateType parseDateType(String date) throws Exception {
        return new DateType(
                Date.from(parseLocalDate(date)
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant()),
                TemporalPrecisionEnum.DAY);
    }

    private static LocalDate parseLocalDate(String dateTime) throws Exception {
        if (!dateTime.isBlank()) {
            return tryDayFormat1(dateTime);
        } else {
            throw new Exception();
        }
    }

    private static LocalDate tryDayFormat1(String dateTime) throws Exception {
        try {
            return Year.parse(dateTime).atDay(1);
        } catch (DateTimeParseException e) {
            return tryDayFormat2(dateTime);
        }
    }

    private static LocalDate tryDayFormat2(String dateTime) throws Exception {
        try {
            return YearMonth.parse(dateTime).atDay(1);
        } catch (DateTimeParseException e) {
            return tryDayFormat3(dateTime);
        }
    }

    private static LocalDate tryDayFormat3(String dateTime) throws Exception {
        try {
            return LocalDate.parse(dateTime);
        } catch (DateTimeParseException e) {
            return tryDayFormat4(dateTime);
        }
    }

    private static LocalDate tryDayFormat4(String dateTime) throws Exception {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
            return LocalDate.parse(dateTime, formatter);
        } catch (DateTimeParseException e) {
            return tryDayFormat5(dateTime);
        }
    }

    private static LocalDate tryDayFormat5(String dateTime) throws Exception {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("M/dd/yyyy");
            return LocalDate.parse(dateTime, formatter);
        } catch (DateTimeParseException e) {
            return tryDayFormat6(dateTime);
        }
    }

    private static LocalDate tryDayFormat6(String dateTime) throws Exception {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("M/d/yyyy");
            return LocalDate.parse(dateTime, formatter);
        } catch (DateTimeParseException e) {
            throw new Exception();
        }
    }

    public static DateTimeType parseDateTimeType(String date) throws Exception {
        return new DateTimeType(
                Date.from(parseLocalDateTime(date)
                        .atZone(ZoneId.systemDefault())
                        .toInstant()),
                TemporalPrecisionEnum.SECOND);
    }

    private static LocalDateTime parseLocalDateTime(String date) throws Exception {
        try {
            LocalDate localDate = parseLocalDate(date);
            return localDate.atStartOfDay();
        } catch (Exception e) {
            return tryTimeFormat1(date);
        }
    }

    private static LocalDateTime tryTimeFormat1(String date) throws Exception {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy, H:mm");
            return LocalDateTime.parse(date, formatter);
        } catch (DateTimeParseException e) {
            throw new Exception();
        }
    }
}
