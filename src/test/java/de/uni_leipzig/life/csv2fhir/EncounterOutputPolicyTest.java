package de.uni_leipzig.life.csv2fhir;

import static org.junit.Assert.*;
import org.junit.Test;
import org.hl7.fhir.r4.model.*;

public class EncounterOutputPolicyTest {
    private String end(String start, String policy) {
        return EncounterOutputPolicy.end(new DateTimeType(start), policy).getValueAsString();
    }
    @Test public void darPreservesExistingEncounterAndLocationStatus() {
        var config = ContractConfiguration.parse("CONFIGURATION_VERSION=1\nDAR_ENCOUNTER_PERIOD_END=unknown\n");
        var e = new Encounter().setStatus(Encounter.EncounterStatus.CANCELLED);
        e.getPeriod().setStartElement(new DateTimeType("2026-05-01")).setEndElement(new DateTimeType("2026-05-03"));
        e.addLocation().setStatus(Encounter.EncounterLocationStatus.RESERVED);
        var policy = new EncounterOutputPolicy(config, new ContactIndex());
        var out = (Encounter)policy.finish(new DarOverrides(config).output(policy.output(e)));
        assertEquals(Encounter.EncounterStatus.CANCELLED, out.getStatus());
        assertEquals(Encounter.EncounterLocationStatus.RESERVED, out.getLocationFirstRep().getStatus());
        assertFalse(out.getPeriod().getEndElement().hasValue());
        assertTrue(out.getPeriod().equalsDeep(out.getLocationFirstRep().getPeriod()));
    }
    @Test public void calendarEndsPreserveDatePrecisionAndExplicitOffsets() {
        assertEquals("2024-03-31", end("2024-02-29", "quarter-end"));
        assertEquals("2026-12-31", end("2026-12-31", "year-end"));
        assertEquals("2026-06-30T23:59:59+02:00", end("2026-04-01T08:00:00+02:00", "quarter-end"));
        assertEquals("2026-12-31T23:59:59.999-05:00", end("2026-01-01T08:00:00.120-05:00", "year-end"));
    }
    @Test public void secondArithmeticCrossesBoundariesWithoutChangingOtherPrecision() {
        assertEquals("2027-01-01T00:00:00Z", end("2026-12-31T23:59:59Z", "start-plus-second"));
        assertEquals("2026-03-29T01:00:00.120+01:00", end("2026-03-29T00:59:59.120+01:00", "start-plus-second"));
        assertEquals("2026-05-01T00:00:01Z", end("2026-05-01", "start-plus-second"));
        assertEquals("2026-05", end("2026-05", "start"));
        assertEquals("2026-05-01", end("2026-05-01", "start"));
        assertThrows(IllegalArgumentException.class, () -> end("2026-05", "quarter-end"));
        assertThrows(IllegalArgumentException.class, () -> end("2026", "start-plus-second"));
        assertThrows(IllegalArgumentException.class, () -> EncounterOutputPolicy.end(new DateTimeType(), "year-end"));
        assertNull(EncounterOutputPolicy.end(new DateTimeType(), "open"));
    }
    @Test public void originalMissingEndWinsOverDerivationAndDarWinsOverPolicyOnEveryLevel() {
        for (var level : ContactIndex.Level.values()) {
            var config = ContractConfiguration.parse("CONFIGURATION_VERSION=1\nENCOUNTER_INPATIENT_END_POLICY=start\n"
                    + "ENCOUNTER_INPATIENT_END_APPLICATION=missing-input-end\nDAR_ENCOUNTER_PERIOD_END=unknown\n"
                    + "DAR_ENCOUNTER_INPATIENT_PERIOD_END=masked\n");
            var index = new ContactIndex();
            var encounter = new Encounter(); encounter.setId("e"); encounter.getClass_().setCode("IMP");
            encounter.getPeriod().setStartElement(new DateTimeType("2026-05-01"));
            encounter.addLocation().setLocation(new Reference("Location/l"));
            index.add(encounter, "p", "e", level, null, 1, false);
            encounter.getPeriod().setEndElement(new DateTimeType("2026-05-20"));
            var policy = new EncounterOutputPolicy(config, index);
            var out = (Encounter)policy.output(encounter);
            assertEquals("2026-05-01", out.getPeriod().getEndElement().getValueAsString());
            out = (Encounter)policy.finish(new DarOverrides(config).output(out));
            assertFalse(out.getPeriod().getEndElement().hasValue());
            assertEquals("masked", ((CodeType)out.getPeriod().getEndElement().getExtensionByUrl(DarOverrides.URL).getValue()).getValue());
            assertEquals(Encounter.EncounterStatus.FINISHED, out.getStatus());
            assertEquals(Encounter.EncounterLocationStatus.COMPLETED, out.getLocationFirstRep().getStatus());
            assertTrue(out.getPeriod().equalsDeep(out.getLocationFirstRep().getPeriod()));
            assertEquals("2026-05-20", encounter.getPeriod().getEndElement().getValueAsString());
            assertEquals(1, policy.changes().size());
            index.add(encounter, "p", "e", level, null, 1, false, false);
            assertSame(encounter, policy.output(encounter));
        }
    }
    @Test public void unchangedScopedDarInheritsAndOtherClassesKeepTheirEnd() {
        var config = ContractConfiguration.parse("CONFIGURATION_VERSION=1\nENCOUNTER_AMBULATORY_END_POLICY=open\n"
                + "DAR_ENCOUNTER_PERIOD_END=unknown\nDAR_ENCOUNTER_AMBULATORY_PERIOD_END=unchanged\n");
        var e = new Encounter(); e.setId("e"); e.getClass_().setCode("AMB");
        e.getPeriod().setStartElement(new DateTimeType("2026-05-01")).setEndElement(new DateTimeType("2026-06-01"));
        var index = new ContactIndex(); index.add(e,"p","e",ContactIndex.Level.FACILITY,null,1,false);
        var policy = new EncounterOutputPolicy(config,index);
        var out = (Encounter)new DarOverrides(config).output(policy.output(e));
        assertEquals("unknown", ((CodeType)out.getPeriod().getEndElement().getExtensionByUrl(DarOverrides.URL).getValue()).getValue());
        e.getClass_().setCode("PRENC");
        assertSame(e, policy.output(e));
    }
}
