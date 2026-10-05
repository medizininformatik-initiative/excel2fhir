package de.uni_leipzig.life.csv2fhir;

import static org.junit.Assert.*;
import static de.uni_leipzig.life.csv2fhir.ContactIndex.Level.*;

import java.util.Arrays;
import java.util.List;

import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Period;
import org.junit.Test;

public class ContactIndexTest {
    private Encounter contact(ContactIndex index, String patient, String id, ContactIndex.Level level,
            long row, String start, String end, boolean secondary) {
        var encounter = new Encounter(); encounter.setId(id);
        var period = new Period();
        if (start != null) period.setStartElement(new DateTimeType(start));
        if (end != null) period.setEndElement(new DateTimeType(end));
        encounter.setPeriod(period);
        index.add(encounter, patient, "case", level, null, row, secondary);
        return encounter;
    }
    private String match(ContactIndex index, String patient, ContactIndex.Level level, String... times) {
        return index.match(patient, level, Arrays.stream(times).map(DateTimeType::new).toList())
                .map(e -> e.encounter().getId()).orElse(null);
    }

    @Test public void boundariesAreInclusiveAndLatestStartThenOriginalRowWin() {
        var index = new ContactIndex();
        contact(index, "p1", "earlier", DEPARTMENT, 1, "2026-05-01T00:00:00Z", "2026-05-02T00:00:00Z", false);
        // Register in the opposite order to input-row precedence.
        contact(index, "p1", "later-row", DEPARTMENT, 8, "2026-05-02T00:00:00Z", null, false);
        contact(index, "p1", "first-row", DEPARTMENT, 7, "2026-05-02T00:00:00Z", null, false);
        assertEquals("earlier", match(index, "p1", DEPARTMENT, "2026-05-01T00:00:00Z"));
        assertEquals("first-row", match(index, "p1", DEPARTMENT, "2026-05-02T00:00:00Z"));
        assertEquals("first-row", match(index, "p1", DEPARTMENT, "2027-01-01T00:00:00Z"));
        var only = new ContactIndex();
        contact(only, "p1", "closed", DEPARTMENT, 1, "2026-05-01T00:00:00Z", "2026-05-02T00:00:00Z", false);
        assertEquals("closed", match(only, "p1", DEPARTMENT, "2026-05-02T00:00:00Z"));
        assertNull(match(only, "p1", DEPARTMENT, "2026-05-02T00:00:01Z"));
    }

    @Test public void matchingKeepsPatientAndLevelAndTriesTheNextTimestamp() {
        var index = new ContactIndex();
        contact(index, "p1", "facility", FACILITY, 1, "2026-05-01T00:00:00Z", null, false);
        contact(index, "p2", "department", DEPARTMENT, 2, "2026-05-01T00:00:00Z", null, false);
        assertNull(match(index, "p1", DEPARTMENT, "2026-05-02T00:00:00Z"));
        assertEquals("facility", match(index, "p1", FACILITY, "2026-04-01T00:00:00Z", "2026-05-02T00:00:00Z"));
        assertNull(match(index, "missing", FACILITY, "2026-05-02T00:00:00Z"));
        assertTrue(index.match("p1", FACILITY, Arrays.asList(null, new DateTimeType())).isEmpty());
    }

    @Test public void secondaryKindsNeverBecomeWardTargetsAndDerivedEndsRemainLive() {
        var index = new ContactIndex();
        var ward = contact(index, "p1", "ward", WARD_SERVICE, 1, "2026-05-01T00:00:00Z", null, false);
        for (String id : List.of("operation", "consultation", "examination"))
            contact(index, "p1", id, WARD_SERVICE, 2, "2026-05-02T00:00:00Z", null, true);
        assertEquals("ward", match(index, "p1", WARD_SERVICE, "2026-05-03T00:00:00Z"));
        ward.getPeriod().setEndElement(new DateTimeType("2026-05-02T00:00:00Z"));
        assertNull(match(index, "p1", WARD_SERVICE, "2026-05-03T00:00:00Z"));
        contact(index, "p1", "missing-start", WARD_SERVICE, 5, null, null, false);
        assertNull(match(index, "p1", WARD_SERVICE, "2026-05-03T00:00:00Z"));
    }
    @Test public void inpatientWinsAcrossTimestampsAndAmbulatoryRemainsFallback() {
        var index = new ContactIndex();
        var ambulatory = contact(index, "p1", "amb", FACILITY, 1, "2026-05-01", null, false);
        ambulatory.getClass_().setCode("AMB");
        var inpatient = contact(index, "p1", "imp", FACILITY, 2, "2026-04-01", "2026-04-30", false);
        inpatient.getClass_().setCode("IMP");
        assertEquals("imp", match(index, "p1", FACILITY, "2026-05-15", "2026-04-15"));
        assertEquals("amb", match(index, "p1", FACILITY, "2026-05-15"));
        assertEquals("amb", index.match("p1", FACILITY, List.of(new DateTimeType("2026-05-15"),
                new DateTimeType("2026-04-15")), entry -> !entry.encounter().getId().equals("imp"))
                .orElseThrow().encounter().getId());
        inpatient.getPeriod().setEndElement(null);
        var later = contact(index, "p1", "imp-later", FACILITY, 3, "2026-04-10", null, false);
        later.getClass_().setCode("IMP");
        assertEquals("imp-later", match(index, "p1", FACILITY, "2026-05-15"));
        var other = contact(index, "p1", "other", FACILITY, 4, "2026-05-10", null, false);
        other.getClass_().setCode("VR");
        assertEquals("other", match(index, "p1", FACILITY, "2026-05-15"));
    }
}
