package de.uni_leipzig.life.csv2fhir.converter;

import static org.junit.Assert.*;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import org.junit.Test;

public class ContactInputValidatorTest {
    private ContactInputValidator.Input row(long n, String patient, String number, String start, String end, String department, String station, String kind) {
        return new ContactInputValidator.Input(n, patient, Map.of("Fall-Nr", number, "Start", start, "Ende", end,
                "Fachabteilung", department, "Station", station, "Kontaktart", kind, "Einrichtungskontaktklasse", "stationaer"));
    }
    @Test public void allIndependentErrorsAreCollectedWithoutDependentNoise() {
        var check = new ContactInputValidator();
        List<ContactInputValidator.Issue> issues = new ArrayList<>();
        issues.addAll(check.accept(row(2,"p","1","bad","2026-01-03","","","bad kind")));
        issues.addAll(check.accept(row(3,"p","1","2026-01-02","","","OP","Operation")));
        issues.addAll(check.accept(row(4,"p","1","2026-01-03","2026-01-02","","","")));
        issues.addAll(check.accept(row(5,"q","1","2026-01-02","2026-01-01","","","")));
        assertEquals(5, issues.size());
        assertTrue(issues.stream().noneMatch(i -> i.row()==3));
        assertTrue(issues.get(0).message().contains("Dependent encounter assignments"));
    }
    @Test public void missingOrInvalidStartIsReportedBeforeComparingStays() {
        for (String start : List.of("", "not-a-date")) {
            var check = new ContactInputValidator();
            assertTrue(check.accept(row(2, "p", "1", "2026-01-01", "", "", "Bett 1", "")).isEmpty());
            var issues = check.accept(row(3, "p", "1", start, "", "", "Bett 2", ""));
            assertEquals(1, issues.size());
            assertEquals("Start", issues.get(0).field());
            assertTrue(issues.get(0).message().contains("Invalid timestamp"));
            assertTrue(issues.get(0).message().contains("Dependent encounter assignments"));
        }
    }
    @Test public void openPrimaryAndParallelContactsNeedNoInventedTimes() {
        var check = new ContactInputValidator();
        for (var input : List.of(
                row(2,"p","1","2026-01-01","","","",""),
                row(3,"p","1","2026-01-01","","","Bett 1",""),
                row(4,"p","1","2026-01-02","","","OP-Bett","Operation"),
                row(5,"p","1","2026-01-02","2026-01-02","","Bett 1","Konsil"),
                row(6,"p","1","2026-01-03","","","Bett 2","")))
            assertTrue(check.accept(input).isEmpty());
    }
    @Test public void transferCannotCutOffAnExplicitSecondaryEnd() {
        var check = new ContactInputValidator();
        assertTrue(check.accept(row(2,"p","1","2026-01-01","","Innere Medizin","S1","")).isEmpty());
        assertTrue(check.accept(row(3,"p","1","2026-01-02","2026-01-04","","OP","Operation")).isEmpty());
        assertEquals(1,check.accept(row(4,"p","1","2026-01-03","","","S2","")).size());
        // A new case restores independent sequence checking.
        assertTrue(check.accept(row(5,"p","2","2026-02-01","2026-02-04","","","")).isEmpty());
        assertEquals(1,check.accept(row(6,"p","2","2026-02-02","","","OP","Operation")).size());
    }
}
