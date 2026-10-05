package de.uni_leipzig.life.csv2fhir;

import static org.junit.Assert.*;
import java.util.List;
import org.hl7.fhir.r4.model.*;
import org.junit.Test;

public class ClinicalTimeShiftTest {
    @Test public void shiftsClinicalGraphOnceKeepingMetadataDarPrecisionAndOffsets() {
        var p = new Patient(); p.setId("p"); p.setBirthDateElement(new DateType("2000-02-29"));
        p.getMeta().setLastUpdatedElement(new InstantType("2026-01-01T00:00:00Z"));
        var absent = new DateTimeType(); absent.addExtension(DarOverrides.URL, new CodeType("unknown"));
        p.setDeceased(absent);
        var encounter = new Encounter(); encounter.setId("e");
        var shared = new DateTimeType("2026-03-28T12:13:14.120+01:00");
        encounter.getPeriod().setStartElement(shared);
        encounter.addLocation().setPeriod(encounter.getPeriod());
        var observation = new Observation(); observation.setId("o"); observation.setEffective(shared);
        ClinicalTimeShift.apply(List.of(p, encounter, encounter, observation), 2);
        assertEquals("2000-03-02", p.getBirthDateElement().getValueAsString());
        assertEquals("2026-03-30T12:13:14.120+01:00", shared.getValueAsString());
        assertEquals("2026-01-01T00:00:00Z", p.getMeta().getLastUpdatedElement().getValueAsString());
        assertFalse(p.getDeceasedDateTimeType().hasValue());
        assertNotNull(p.getDeceasedDateTimeType().getExtensionByUrl(DarOverrides.URL));
    }

    @Test public void repetitionUsesBasePlusIterationAndInactiveValuesDoNotShift() {
        var options = ConverterOptions.fromText("CONFIGURATION_VERSION=1\nTIME_SHIFT_ENABLED=true\nTIME_SHIFT_BASE_DAYS=10\nTIME_SHIFT_ITERATION_DAYS=-5\n");
        assertEquals(10, ClinicalTimeShift.days(options));
        options.loopCounter = 2; assertEquals(0, ClinicalTimeShift.days(options));
        options.loopCounter = 3; assertEquals(-5, ClinicalTimeShift.days(options));
        assertEquals(0, ClinicalTimeShift.days(ConverterOptions.fromText("CONFIGURATION_VERSION=1\nTIME_SHIFT_BASE_DAYS=10\n")));
        assertEquals("2025-12-31T23:59:59Z", ClinicalTimeShift.shift("2026-01-01T23:59:59Z", -1));
    }

    @Test public void partialDatesFailAtomicallyAndZeroShiftKeepsThem() {
        var p = new Patient(); p.setId("p"); p.setBirthDateElement(new DateType("2000"));
        var e = new Encounter(); e.setId("e"); e.getPeriod().setStartElement(new DateTimeType("2026-01-01"));
        var error = assertThrows(IllegalArgumentException.class, () -> ClinicalTimeShift.apply(List.of(e,p), 1));
        assertTrue(error.getMessage().contains("Patient/p.birthDate"));
        assertEquals("2026-01-01", e.getPeriod().getStartElement().getValueAsString());
        ClinicalTimeShift.apply(List.of(p), 0); assertEquals("2000", p.getBirthDateElement().getValueAsString());
    }

    @Test public void shiftedStartSelectsNextQuarterWhileAssignmentKeepsInternalEnd() {
        var config = ContractConfiguration.parse("CONFIGURATION_VERSION=1\nENCOUNTER_INPATIENT_END_POLICY=quarter-end\n");
        var index = new ContactIndex(); var e = new Encounter(); e.setId("e"); e.getClass_().setCode("IMP");
        e.getPeriod().setStartElement(new DateTimeType("2026-03-31")).setEndElement(new DateTimeType("2026-04-02"));
        index.add(e,"p","e",ContactIndex.Level.FACILITY,null,1,false);
        var o = new Observation(); o.setId("o"); o.setEffective(new DateTimeType("2026-04-01"));
        ClinicalTimeShift.apply(List.of(e,o), 1);
        assertTrue(index.match("p",ContactIndex.Level.FACILITY,List.of(o.getEffectiveDateTimeType())).isPresent());
        var output = (Encounter)new EncounterOutputPolicy(config,index).output(e);
        assertEquals("2026-06-30",output.getPeriod().getEndElement().getValueAsString());
        assertEquals("2026-04-03",e.getPeriod().getEndElement().getValueAsString());
    }
}
