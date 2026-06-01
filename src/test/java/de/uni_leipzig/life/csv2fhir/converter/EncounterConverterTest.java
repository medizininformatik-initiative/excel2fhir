package de.uni_leipzig.life.csv2fhir.converter;

import static de.uni_leipzig.life.csv2fhir.TableIdentifier.Fall;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

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

    @Before
    public void resetEncounterState() {
        EncounterConverter.resetStateForTesting();
    }

    @Test
    public void repeatedOrEmptyDepartmentKeepsSameDepartmentEncounter() throws Exception {
        ConverterResult result = convertRecords(
                "Patient-ID,Fall-Nr,Start,Ende,Einrichtungskontaktklasse,Fachabteilung,Station,Zimmer,Bett\n"
                        + "PID1,1,01.05.2026 08:00,05.05.2026 12:00,stationaer,Innere,INT1,R101,\n"
                        + ",,05.05.2026 12:00,10.05.2026 12:00,,,INT1,R102,\n"
                        + ",,10.05.2026 12:00,15.05.2026 12:00,,Innere,INT2,R201,\n");

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
        assertEquals(
                "Location/Innere-INT1-R102",
                wardEncounters.get(1).getLocation().get(1).getLocation().getReference());
        assertEncounterIdentifierSystem(facilityEncounters);
        assertEncounterIdentifierSystem(departmentEncounters);
        assertEncounterIdentifierSystem(wardEncounters);
    }

    @Test
    public void changedDepartmentCreatesNewDepartmentEncounter() throws Exception {
        ConverterResult result = convertRecords(
                "Patient-ID,Fall-Nr,Start,Ende,Einrichtungskontaktklasse,Fachabteilung,Station,Zimmer,Bett\n"
                        + "PID1,1,01.05.2026 08:00,05.05.2026 12:00,stationaer,Innere,INT1,R101,\n"
                        + ",,05.05.2026 12:00,10.05.2026 12:00,,Chirurgie,INT2,R201,\n");

        List<Encounter> departmentEncounters = getEncounters(result, EncounterLevel2.class);
        List<Encounter> wardEncounters = getEncounters(result, EncounterLevel3.class);

        assertEquals(2, departmentEncounters.size());
        assertEquals(2, wardEncounters.size());
        assertEquals("Encounter/" + departmentEncounters.get(0).getId(),
                wardEncounters.get(0).getPartOf().getReference());
        assertEquals("Encounter/" + departmentEncounters.get(1).getId(),
                wardEncounters.get(1).getPartOf().getReference());
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
