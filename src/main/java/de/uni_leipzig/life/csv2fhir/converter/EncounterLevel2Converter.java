package de.uni_leipzig.life.csv2fhir.converter;

import static de.uni_leipzig.life.csv2fhir.ConverterOptions.IntOption.START_ID_ENCOUNTER_LEVEL_2;
import static de.uni_leipzig.life.csv2fhir.TableIdentifier.Abteilungsfall;
import static de.uni_leipzig.life.csv2fhir.converter.EncounterLevel2Converter.EncounterLevel2_Columns.Enddatum;
import static de.uni_leipzig.life.csv2fhir.converter.EncounterLevel2Converter.EncounterLevel2_Columns.Fachabteilung;
import static de.uni_leipzig.life.csv2fhir.converter.EncounterLevel2Converter.EncounterLevel2_Columns.Startdatum;

import java.util.Collections;
import java.util.List;

import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Encounter.EncounterLocationComponent;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

import de.uni_leipzig.imise.validate.FHIRValidator;
import de.uni_leipzig.life.csv2fhir.CodeSystemMapper;
import de.uni_leipzig.life.csv2fhir.ConverterOptions;
import de.uni_leipzig.life.csv2fhir.ConverterResult;
import de.uni_leipzig.life.csv2fhir.TableColumnIdentifier;

/**
 * @author fheuschkel (29.10.2020), fmeinecke, AXS
 */
public class EncounterLevel2Converter extends EncounterLevel1Converter {

    /**
     * toString() result of these enum values are the names of the columns in
     * the correspunding excel sheet.
     */
    public static enum EncounterLevel2_Columns implements TableColumnIdentifier {
        Startdatum,
        Enddatum,
        Fachabteilung
    }

    /**
     * Maps from human readable department description to the number code for
     * the department.
     */
    private final CodeSystemMapper departmentKeyMapper = new CodeSystemMapper("Department_Key.map");

    /**
     * @param record
     * @param result
     * @param validator
     * @param options
     * @throws Exception
     */
    public EncounterLevel2Converter(CSVRecord record, ConverterResult result, FHIRValidator validator, ConverterOptions options) throws Exception {
        super(record, result, validator, options);
    }

    @Override
    protected List<Resource> convertInternal() throws Exception {
        int nextId = result.getNextId(Abteilungsfall, Encounter.class, START_ID_ENCOUNTER_LEVEL_2);
        Encounter encounter = new Encounter();
        Reference superEncounterReference = getEncounterReference();
        // If the super encounter is defined for this sub encounter so we can use it for the id. If not we can only use the PID
        String id = (superEncounterReference != null ? getEncounterId() : getPatientId()) + "-A-" + nextId;
        encounter.setId(id);
        encounter.setMeta(getMeta());
        encounter.setStatus(Encounter.EncounterStatus.FINISHED);
        //encounter.setClass_(createCoding(CLASS_CODE_SYSTEM, "IMP", "inpatient encounter")); //correct class code will be added in the BundlePostProcessor because it comes from the main level 1 encounter
        encounter.setServiceType(createCodeableConcept(Fachabteilung, departmentKeyMapper));
        encounter.setSubject(getPatientReference());
        encounter.setPeriod(createPeriod(Startdatum, Enddatum));
        encounter.setType(getEncounterType());
        encounter.setPartOf(superEncounterReference);
        return Collections.singletonList(encounter);
    }

    /**
     * @return
     * @throws Exception
     */
    private List<EncounterLocationComponent> convertLocation() throws Exception {
        Identifier identifier = new Identifier();
        identifier.setSystem("https://diz.mii.de/fhir/CodeSystem/TestOrganisationAbteilungen");
        identifier.setValue(get(Fachabteilung));
        Reference reference = new Reference();
        reference.setIdentifier(identifier);

        EncounterLocationComponent encounterLocationComponent = new EncounterLocationComponent();
        encounterLocationComponent.setLocation(reference);
        encounterLocationComponent.setStatus(Encounter.EncounterLocationStatus.COMPLETED);
        encounterLocationComponent.setPeriod(createPeriod(Startdatum, Enddatum));
        return Collections.singletonList(encounterLocationComponent);
    }

    /**
     * @param encounterID
     * @param conditionOrProcedureAsDiagnosis
     * @param diagnosisUseIdentifier
     */
    private void addDiagnosisToEncounterInternal(String encounterID, Resource conditionOrProcedureAsDiagnosis, String diagnosisUseIdentifier) {
        // encounter should be only null in error cases, but mybe we
        // should catch and log
        Encounter encounter = result.get(Abteilungsfall, Encounter.class, encounterID);
        EncounterLevel1Converter.addDiagnosisToEncounter(encounter, conditionOrProcedureAsDiagnosis, diagnosisUseIdentifier);
    }

}
