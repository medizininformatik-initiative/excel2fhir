package de.uni_leipzig.life.csv2fhir.converter;

import static de.uni_leipzig.life.csv2fhir.TableIdentifier.Abteilungsfall;
import static de.uni_leipzig.life.csv2fhir.converterFactory.EncounterLevel2ConverterFactory.EncounterLevel2_Columns.Enddatum;
import static de.uni_leipzig.life.csv2fhir.converterFactory.EncounterLevel2ConverterFactory.EncounterLevel2_Columns.Fachabteilung;
import static de.uni_leipzig.life.csv2fhir.converterFactory.EncounterLevel2ConverterFactory.EncounterLevel2_Columns.Startdatum;

import java.util.Collections;
import java.util.List;

import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Encounter.EncounterLocationComponent;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

import de.uni_leipzig.imise.FHIRValidator;
import de.uni_leipzig.life.csv2fhir.CodeSystemMapper;
import de.uni_leipzig.life.csv2fhir.ConverterResult;
import de.uni_leipzig.life.csv2fhir.TableColumnIdentifier;
import de.uni_leipzig.life.csv2fhir.converterFactory.EncounterLevel2ConverterFactory.EncounterLevel2_Columns;

/**
 * @author fheuschkel (29.10.2020), fmeinecke, AXS
 */
public class EncounterLevel2Converter extends EncounterLevel1Converter {

    /**
     * Maps from human readable department description to the number code for
     * the department.
     */
    private final CodeSystemMapper departmentKeyMapper = new CodeSystemMapper("Department_Key.map");

    /**
     * @param record
     * @param result
     * @param validator
     * @throws Exception
     */
    public EncounterLevel2Converter(CSVRecord record, ConverterResult result, FHIRValidator validator) throws Exception {
        super(record, result, validator);
    }

    @Override
    protected Enum<?> getPatientIDColumnIdentifier() {
        return Abteilungsfall.getPIDColumnIdentifier();
    }

    @Override
    public List<Resource> convert() throws Exception {
        int nextId = result.getNextId(Abteilungsfall, Encounter.class);
        Encounter encounter = new Encounter();
        encounter.setId(getEncounterId() + "-A-" + nextId);
        encounter.setMeta(getMeta());
        encounter.setStatus(Encounter.EncounterStatus.FINISHED);
        //encounter.setClass_(createCoding(CLASS_CODE_SYSTEM, "IMP", "inpatient encounter")); //correct class code will be added in the BundlePostProcessor because it comes from the main level 1 encounter
        encounter.setServiceType(createCodeableConcept(Fachabteilung, departmentKeyMapper));
        encounter.setSubject(getPatientReference());
        encounter.setPeriod(createPeriod(Startdatum, Enddatum));
        //        encounter.setLocation(convertLocation()); bringt nichts
        encounter.setPartOf(getEncounterReference());
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

    @Override
    protected TableColumnIdentifier getMainEncounterNumberColumnIdentifier() {
        return EncounterLevel2_Columns.Versorgungsfall_Nr;
    }

}