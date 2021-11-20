package de.uni_leipzig.life.csv2fhir.converter;

import static de.uni_leipzig.life.csv2fhir.Converter.EmptyRecordValueErrorLevel.ERROR;
import static de.uni_leipzig.life.csv2fhir.converterFactory.VersorgungsfallConverterFactory.NeededColumns.Enddatum;
import static de.uni_leipzig.life.csv2fhir.converterFactory.VersorgungsfallConverterFactory.NeededColumns.Patient_ID;
import static de.uni_leipzig.life.csv2fhir.converterFactory.VersorgungsfallConverterFactory.NeededColumns.Startdatum;
import static de.uni_leipzig.life.csv2fhir.converterFactory.VersorgungsfallConverterFactory.NeededColumns.Versorgungsfallklasse;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

import de.uni_leipzig.imise.utils.CodeSystemMapper;
import de.uni_leipzig.life.csv2fhir.Converter;

public class VersorgungsfallConverter extends Converter {

    /**  */
    String PROFILE = "https://www.medizininformatik-initiative.de/fhir/core/modul-fall/StructureDefinition/KontaktGesundheitseinrichtung";
    // https://simplifier.net/medizininformatikinitiative-modulfall/versorgungsfall-duplicate-2
    // https://simplifier.net/medizininformatikinitiative-modulfall/example-versorgungsfall

    /*
     * NotSupported : Terminology service failed while validating code ''
     * (system ''): Cannot retrieve valueset
     * 'https://www.medizininformatik-initiative.de/fhir/core/modul-fall/
     * ValueSet/Versorgungsfallklasse'
     */

    /**
     * Stores all {@link CSVRecord}s of all encounters created by this converter
     */
    public static final Map<Encounter, CSVRecord> encounterToCSVRecordMap = new HashMap<>();

    /**
     * Maps from human readable diagnosis role description to the correspondig
     * code system code.
     */
    public static final CodeSystemMapper diagnosisRoleKeyMapper = new CodeSystemMapper("diagnosis-role.map");

    /**
     * Schwierigkeit: die Aufnahmediagnosen nicht unbedingt identisch mit der
     * Hauptdiagnosen Diagnose haben typisch keinen verpflichtenden Identifier;
     * hier wird konstruiert...
     *
     * @param record
     * @throws Exception
     */
    public VersorgungsfallConverter(CSVRecord record) throws Exception {
        super(record);
    }

    @Override
    public List<Resource> convert() throws Exception {
        Encounter encounter = new Encounter();
        encounter.setMeta(new Meta().addProfile(PROFILE));
        encounter.setId(getEncounterId());
        encounter.setIdentifier(convertIdentifier());
        encounter.setStatus(Encounter.EncounterStatus.FINISHED);//TODO
        encounter.setClass_(convertClass());
        encounter.setSubject(getPatientReference());
        encounter.setPeriod(convertPeriod());
        return Collections.singletonList(encounter);
    }

    @Override
    protected Reference getPatientReference() throws Exception {
        String patientId = record.get(Patient_ID);
        if (patientId != null) {
            return new Reference().setReference("Patient/" + patientId);
        }
        error("Patient-ID empty");
        return null;
    }

    /**
     * @return
     * @throws Exception
     */
    private List<Identifier> convertIdentifier() throws Exception {
        // generierte Encounternummer
        String id = getEncounterId();
        CodeableConcept identifierCode = createCodeableConcept("http://terminology.hl7.org/CodeSystem/v2-0203", "VN");
        Reference r = new Reference().setIdentifier(new Identifier().setValue(getDIZId()).setSystem(
                "https://www.medizininformatik-initiative.de/fhir/core/NamingSystem/org-identifier"));
        return Collections.singletonList(new Identifier().setValue(id).setSystem("Generated").setAssigner(r).setType(identifierCode));

    }

    /**
     * @return
     * @throws Exception
     */
    private Coding convertClass() throws Exception {
        return createCoding("https://www.medizininformatik-initiative.de/fhir/core/modul-fall/CodeSystem/Versorgungsfallklasse", Versorgungsfallklasse, ERROR);
    }

    /**
     * @return
     */
    private Period convertPeriod() {
        return createPeriod(Startdatum, Enddatum);
    }

    /**
     * @param useIdentifier
     * @return
     */
    public static CodeableConcept createDiagnosisUse(String useIdentifier) {
        String codeSystem = diagnosisRoleKeyMapper.getCodeSystem();
        String code = diagnosisRoleKeyMapper.getHumanToCode(useIdentifier);
        String display = diagnosisRoleKeyMapper.getCodeToHuman(code);
        CodeableConcept diagnosisUse = new CodeableConcept();
        diagnosisUse.addCoding()
                .setCode(code)
                .setSystem(codeSystem)
                .setDisplay(display);
        return diagnosisUse;
    }

}
