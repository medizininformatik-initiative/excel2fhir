package de.uni_leipzig.life.csv2fhir.converter;

import static de.uni_leipzig.life.csv2fhir.TableIdentifier.Fall;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Resource;
import org.junit.Before;
import org.junit.Test;

import de.uni_leipzig.life.csv2fhir.ConverterOptions;
import de.uni_leipzig.life.csv2fhir.ConverterResult;
import de.uni_leipzig.life.csv2fhir.converter.EncounterConverter.EncounterLevel1;
import de.uni_leipzig.life.csv2fhir.converter.EncounterConverter.EncounterLevel2;
import de.uni_leipzig.life.csv2fhir.converter.EncounterConverter.EncounterLevel3;

public class EncounterConverterTest {
    private static final String CONTACT_HEADER = "Patient-ID,Fall-Nr,Start,Ende,Einrichtungskontaktklasse,Fachabteilung,Station,Zimmer,Bett,Aufnahmegrund (4. Stelle),Kontaktart\n";
    private static final String ROOT_CONTACT = "PID1,1,2026-05-01T08:00:00Z,2026-05-05T12:00:00Z,stationaer,,,,,,\n";
    private static final String PRIMARY = "PID1,1,2026-05-01T08:00:00Z,2026-05-03T12:00:00Z,stationaer,Allgemeine Chirurgie,C1,Zimmer 101,Bett 1,,Normalstationär\n";
    private static final String OP = "PID1,1,2026-05-02T09:00:00Z,,stationaer,Allgemeine Chirurgie,OP,OP-Saal 1,,,Operation\n";

    @Test public void operationRunsAlongsideBedUntilPrimaryEnd() throws Exception {
        ConverterResult result = convertRecords(CONTACT_HEADER + ROOT_CONTACT + PRIMARY + OP
                + "PID1,1,2026-05-03T12:00:00Z,2026-05-05T12:00:00Z,stationaer,Innere Medizin,ITS,Zimmer 2,Bett 2,,Intensivstationär\n");
        var stays=getEncounters(result,EncounterLevel3.class);
        assertEquals(3,stays.size());
        Encounter operation=stays.get(1);
        for (var type : List.of(EncounterLevel1.class, EncounterLevel2.class, EncounterLevel3.class))
            for (var contact : getEncounters(result, type)) {
                assertEquals("IMP", contact.getClass_().getCode());
                assertEquals("http://fhir.de/CodeSystem/Kontaktebene", contact.getTypeFirstRep().getCodingFirstRep().getSystem());
            }
        assertEquals("2026-05-03T12:00:00Z",operation.getPeriod().getEndElement().getValueAsString());
        assertEquals("2026-05-01T08:00:00Z",stays.get(0).getPeriod().getStartElement().getValueAsString());
        assertEquals("2026-05-03T12:00:00Z",stays.get(0).getPeriod().getEndElement().getValueAsString());
        assertEquals(stays.get(0).getPartOf().getReference(),operation.getPartOf().getReference());
        assertEquals(3,stays.get(0).getLocation().size());
        assertEquals(2,operation.getLocation().size());
        assertEquals(1,result.contactEndDerivations.size());
    }

    @Test public void sparseRowsCreateOnlyRequestedLevelsAndLocations() throws Exception {
        var result=convertRecords(CONTACT_HEADER+ROOT_CONTACT);
        assertEquals(0,getEncounters(result,EncounterLevel2.class).size());
        assertEquals(0,getEncounters(result,EncounterLevel3.class).size());
        for (String locations : List.of("S,,",",Z,",",,B")) {
            result=convertRecords(CONTACT_HEADER+ROOT_CONTACT
                    + "PID1,1,2026-05-01T08:00:00Z,2026-05-05T12:00:00Z,stationaer,,"+locations+",,\n");
            assertEquals(0,getEncounters(result,EncounterLevel2.class).size());
            Encounter stay=getEncounters(result,EncounterLevel3.class).get(0);
            assertEquals(1,stay.getLocation().size());
            assertEquals("Encounter/"+getEncounters(result,EncounterLevel1.class).get(0).getId(),stay.getPartOf().getReference());
            assertEquals(1,com.google.common.collect.Iterables.size(result.getResources(Fall,org.hl7.fhir.r4.model.Location.class)));
        }
        result=convertRecords(CONTACT_HEADER+ROOT_CONTACT
                + "PID1,1,2026-05-01T08:00:00Z,2026-05-05T12:00:00Z,stationaer,Innere Medizin,,,,,\n");
        assertEquals(1,getEncounters(result,EncounterLevel2.class).size());
        assertEquals(0,getEncounters(result,EncounterLevel3.class).size());
    }

    @Test public void openContactsCloseAtPrimaryChangeButNotAtAnotherSecondary() throws Exception {
        var result=convertRecords(CONTACT_HEADER+ROOT_CONTACT.replace("2026-05-05T12:00:00Z","")
                + PRIMARY.replace("2026-05-03T12:00:00Z","")+OP
                + OP.replace("2026-05-02T09:00:00Z","2026-05-02T14:00:00Z").replace(",Operation",",Konsil")
                + "PID1,1,2026-05-03T12:00:00Z,,stationaer,Innere Medizin,ITS,Zimmer 2,Bett 2,,Intensivstationär\n");
        var stays=getEncounters(result,EncounterLevel3.class);
        for (int i=0;i<3;i++) assertEquals("2026-05-03T12:00:00Z",stays.get(i).getPeriod().getEndElement().getValueAsString());
        assertFalse(stays.get(3).getPeriod().hasEnd());
        assertEquals(Encounter.EncounterStatus.INPROGRESS,stays.get(3).getStatus());
    }

    @Test public void openContactsStayOpenAndExplicitOpEndIsPreserved() throws Exception {
        var result=convertRecords(CONTACT_HEADER+ROOT_CONTACT.replace("2026-05-05T12:00:00Z","")
                + PRIMARY.replace("2026-05-03T12:00:00Z","")+OP);
        for(var stay:getEncounters(result,EncounterLevel3.class)) assertFalse(stay.getPeriod().hasEnd());
        result=convertRecords(CONTACT_HEADER+ROOT_CONTACT+PRIMARY+OP.replace("09:00:00Z,,","09:00:00Z,2026-05-02T11:00:00Z,"));
        assertEquals("2026-05-02T11:00:00Z",getEncounters(result,EncounterLevel3.class).get(1).getPeriod().getEndElement().getValueAsString());
        assertEquals(0,result.contactEndDerivations.size());
    }

    @Test public void missingEndsUseFacilityBoundAndClosedContinuationClosesDepartment() throws Exception {
        var result = convertRecords(CONTACT_HEADER + ROOT_CONTACT
                + PRIMARY.replace("2026-05-03T12:00:00Z", "") + OP);
        for (var stay : getEncounters(result, EncounterLevel3.class))
            assertEquals("2026-05-05T12:00:00Z", stay.getPeriod().getEndElement().getValueAsString());
        assertEquals("2026-05-05T12:00:00Z", getEncounters(result, EncounterLevel2.class).get(0).getPeriod().getEndElement().getValueAsString());
        result = convertRecords(CONTACT_HEADER + PRIMARY.replace("2026-05-03T12:00:00Z", "") + OP
                + PRIMARY.replace("2026-05-01T08:00:00Z", "2026-05-03T12:00:00Z")
                         .replace("2026-05-03T12:00:00Z,stationaer", "2026-05-05T12:00:00Z,stationaer"));
        for (var type : List.of(EncounterLevel1.class, EncounterLevel2.class)) {
            Encounter parent = getEncounters(result, type).get(0);
            assertEquals("2026-05-05T12:00:00Z", parent.getPeriod().getEndElement().getValueAsString());
            assertEquals(Encounter.EncounterStatus.FINISHED, parent.getStatus());
        }
    }

    @Test public void invalidOrAmbiguousAssignmentsAreRejected() {
        assertThrows(IllegalArgumentException.class,()->convertRecords(CONTACT_HEADER+ROOT_CONTACT+OP));
        assertThrows(IllegalArgumentException.class,()->convertRecords(CONTACT_HEADER+ROOT_CONTACT+PRIMARY+PRIMARY));
        assertThrows(IllegalArgumentException.class,()->convertRecords(CONTACT_HEADER+ROOT_CONTACT+PRIMARY+OP.replace("2026-05-02","2026-05-04")));
        assertThrows(IllegalArgumentException.class,()->convertRecords(CONTACT_HEADER+ROOT_CONTACT+PRIMARY+OP.replace("09:00:00Z,,","09:00:00Z,2026-05-04T11:00:00Z,")));
        assertThrows(IllegalArgumentException.class,()->convertRecords(CONTACT_HEADER+ROOT_CONTACT+PRIMARY+OP.replace("OP,OP-Saal 1",",")));
    }

    @Before
    public void resetEncounterState() {
        EncounterConverter.resetStateForTesting();
    }

    @Test
    public void representsEmergencySeparatelyFromAmbulatoryClass() throws Exception {
        ConverterResult result = convertRecords(
                "Patient-ID,Fall-Nr,Start,Ende,Einrichtungskontaktklasse,Fachabteilung,Station,Zimmer,Bett,Aufnahmegrund (4. Stelle)\n"
                        + "PID1,1,01.05.2026 08:00,01.05.2026 12:00,ambulant,,,,,Notfall\n");
        Encounter encounter = getEncounters(result, EncounterLevel1.class).get(0);
        assertEquals("AMB", encounter.getClass_().getCode());
        var reason = encounter.getExtensionByUrl(AdmissionReasonValues.EXTENSION);
        var coding = (org.hl7.fhir.r4.model.Coding) reason.getExtensionByUrl("VierteStelle").getValue();
        assertEquals(AdmissionReasonValues.SYSTEM, coding.getSystem());
        assertEquals("7", coding.getCode());
        assertFalse(encounter.hasHospitalization());
        assertFalse(encounter.hasPriority());
        assertFalse(encounter.getPeriod().hasExtension());
    }

    @Test
    public void admissionReasonSupportsExplicitDarAndRejectsUnknownValues() {
        var reason = AdmissionReasonValues.extension("!dar:masked");
        var coding = (org.hl7.fhir.r4.model.Coding) reason.getExtensionByUrl("VierteStelle").getValue();
        assertFalse(coding.getCodeElement().hasValue());
        assertEquals("masked", coding.getCodeElement().getExtensionFirstRep().getValue().primitiveValue());
        assertEquals(null, AdmissionReasonValues.extension(""));
        assertThrows(IllegalArgumentException.class, () -> AdmissionReasonValues.extension("EMER"));
    }

    @Test
    public void repeatedOrEmptyDepartmentKeepsSameDepartmentEncounter() throws Exception {
        ConverterResult result = convertRecords(
                "Patient-ID,Fall-Nr,Start,Ende,Einrichtungskontaktklasse,Fachabteilung,Station,Zimmer,Bett,Aufnahmegrund (4. Stelle)\n"
                        + "PID1,1,01.05.2026 08:00,05.05.2026 12:00,stationaer,Innere,INT1,R101,,\n"
                        + ",,05.05.2026 12:00,10.05.2026 12:00,,,INT1,R102,,\n"
                        + ",,10.05.2026 12:00,15.05.2026 12:00,,Innere,INT2,R201,,\n");

        List<Encounter> departmentEncounters = getEncounters(result, EncounterLevel2.class);
        List<Encounter> wardEncounters = getEncounters(result, EncounterLevel3.class);
        List<Encounter> facilityEncounters = getEncounters(result, EncounterLevel1.class);

        assertEquals(1, facilityEncounters.size());
        assertEquals(1, departmentEncounters.size());
        assertEquals(3, wardEncounters.size());
        assertEquals(wardEncounters.get(2).getPeriod().getEnd(), facilityEncounters.get(0).getPeriod().getEnd());
        assertEquals(wardEncounters.get(2).getPeriod().getEnd(), departmentEncounters.get(0).getPeriod().getEnd());

        String departmentReference = "Encounter/" + departmentEncounters.get(0).getId();
        for (Encounter wardEncounter : wardEncounters) {
            assertEquals(departmentReference, wardEncounter.getPartOf().getReference());
        }
        assertEquals("R102",result.get(Fall,org.hl7.fhir.r4.model.Location.class,
                wardEncounters.get(1).getLocation().get(1).getLocation().getReference().substring(9)).getName());
        assertEncounterIdentifierSystem(facilityEncounters);
        assertEncounterIdentifierSystem(departmentEncounters);
        assertEncounterIdentifierSystem(wardEncounters);
    }

    @Test
    public void changedDepartmentCreatesNewDepartmentEncounter() throws Exception {
        ConverterResult result = convertRecords(
                "Patient-ID,Fall-Nr,Start,Ende,Einrichtungskontaktklasse,Fachabteilung,Station,Zimmer,Bett,Aufnahmegrund (4. Stelle)\n"
                        + "PID1,1,01.05.2026 08:00,05.05.2026 12:00,stationaer,Innere,INT1,R101,,\n"
                        + ",,05.05.2026 12:00,10.05.2026 12:00,,Chirurgie,INT2,R201,,\n");

        List<Encounter> departmentEncounters = getEncounters(result, EncounterLevel2.class);
        List<Encounter> wardEncounters = getEncounters(result, EncounterLevel3.class);

        assertEquals(2, departmentEncounters.size());
        assertEquals(2, wardEncounters.size());
        assertEquals("Encounter/" + departmentEncounters.get(0).getId(),
                wardEncounters.get(0).getPartOf().getReference());
        assertEquals("Encounter/" + departmentEncounters.get(1).getId(),
                wardEncounters.get(1).getPartOf().getReference());
    }

    @Test public void preflightAndConverterAgreeOnRepresentativeSequences() throws Exception {
        for (String csv : List.of(CONTACT_HEADER+ROOT_CONTACT+PRIMARY+OP,
                CONTACT_HEADER+ROOT_CONTACT+OP,
                CONTACT_HEADER+ROOT_CONTACT+PRIMARY+PRIMARY,
                CONTACT_HEADER+ROOT_CONTACT+PRIMARY+OP.replace("2026-05-02", "2026-05-04"),
                CONTACT_HEADER+ROOT_CONTACT+PRIMARY.replace("2026-05-03T12:00:00Z", "")+OP,
                CONTACT_HEADER+ROOT_CONTACT.replace("2026-05-05T12:00:00Z", "")+PRIMARY.replace("2026-05-03T12:00:00Z", "")+OP)) {
            var preflight = new ContactInputValidator();
            boolean rejected = false;
            for (var record : createRecords(csv))
                rejected |= !preflight.accept(new ContactInputValidator.Input(record.getRecordNumber(), "PID1", record.toMap())).isEmpty();
            boolean failed = false;
            try { convertRecords(csv); } catch (IllegalArgumentException e) { failed = true; }
            assertEquals(csv, failed, rejected);
        }
    }

    private static ConverterResult convertRecords(String csv) throws Exception {
        ConverterResult result = new ConverterResult(new ConverterOptions(""));
        String previousPatientId = null;
        for (CSVRecord record : createRecords(csv)) {
            EncounterConverter converter = new EncounterConverter(record, previousPatientId, result, null,
                    new ConverterOptions(""));
            previousPatientId = converter.getPatientId();
            for (Resource resource : converter.convertInternal()) {
                result.add(Fall, resource);
            }
        }
        return result;
    }

    private static List<CSVRecord> createRecords(String csv) throws Exception {
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                .setNullString("")
                .setHeader()
                .setSkipHeaderRecord(true)
                .build();
        CSVParser parser = csvFormat.parse(new StringReader(csv));
        return parser.getRecords();
    }

    private static List<Encounter> getEncounters(ConverterResult result, Class<? extends Encounter> encounterType) {
        List<Encounter> encounters = new ArrayList<>();
        for (Encounter encounter : result.getResources(Fall, encounterType)) {
            encounters.add(encounter);
        }
        encounters.sort(Comparator.comparing(Encounter::getId));
        return encounters;
    }

    private static void assertEncounterIdentifierSystem(List<Encounter> encounters) {
        for (Encounter encounter : encounters) {
            assertEquals(EncounterConverter.ENCOUNTER_IDENTIFIER_SYSTEM, encounter.getIdentifierFirstRep().getSystem());
            assertFalse(encounter.getIdentifierFirstRep().getSystemElement().hasExtension());
        }
    }
}
