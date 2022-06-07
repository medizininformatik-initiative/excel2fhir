package de.uni_leipzig.life.csv2fhir.converter;

import static de.uni_leipzig.life.csv2fhir.BundleFunctions.getEncounterDate;
import static de.uni_leipzig.life.csv2fhir.Converter.EmptyRecordValueErrorLevel.ERROR;
import static de.uni_leipzig.life.csv2fhir.TableIdentifier.Diagnose;
import static de.uni_leipzig.life.csv2fhir.converterFactory.DiagnosisConverterFactory.Diagnosis_Columns.Bezeichner;
import static de.uni_leipzig.life.csv2fhir.converterFactory.DiagnosisConverterFactory.Diagnosis_Columns.Dokumentationsdatum;
import static de.uni_leipzig.life.csv2fhir.converterFactory.DiagnosisConverterFactory.Diagnosis_Columns.ICD;
import static de.uni_leipzig.life.csv2fhir.converterFactory.DiagnosisConverterFactory.Diagnosis_Columns.Typ;

import java.util.Collections;
import java.util.List;

import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Resource;

import de.uni_leipzig.imise.FHIRValidator;
import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.ConverterResult;
import de.uni_leipzig.life.csv2fhir.TableColumnIdentifier;
import de.uni_leipzig.life.csv2fhir.converterFactory.DiagnosisConverterFactory.Diagnosis_Columns;
import de.uni_leipzig.life.csv2fhir.utils.DateUtil;

public class DiagnosisConverter extends Converter {

    /**  */
    String PROFILE = "https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose";
    // https://simplifier.net/medizininformatikinitiative-moduldiagnosen/diagnose

    /**
     * @param record
     * @param result
     * @param validator
     * @throws Exception
     */
    public DiagnosisConverter(CSVRecord record, ConverterResult result, FHIRValidator validator) throws Exception {
        super(record, result, validator);
    }

    @Override
    public List<Resource> convert() throws Exception {
        Condition condition = new Condition();
        // Nicht im Profil, aber notwendig für Fall Aufnahmediagnose
        String id = getDiagnoseId();
        condition.setId(id);
        condition.setIdentifier(Collections.singletonList(new Identifier().setValue(id)));
        condition.setMeta(new Meta().addProfile(PROFILE));
        //        condition.addCategory(convertCategory());
        condition.setCode(convertCode());
        condition.setSubject(getPatientReference());
        condition.setRecordedDateElement(convertRecordedDate());

        if (!isValid(condition)) {
            return Collections.emptyList();
        }

        //enable this to get the reference from condition to encounter. This is optional
        //but it creates a circle, because the encounter has also a reference list to all
        //diagnosis.
        //condition.setEncounter(getEncounterReference());

        //now add an the encounter a reference to this procedure as diagnosis (Yes thats the logic of KDS!?)
        String encounterId = getEncounterId();
        String diagnosisUseIdentifier = get(Typ);
        EncounterLevel1Converter.addDiagnosisToEncounter(result, encounterId, condition, diagnosisUseIdentifier);

        return Collections.singletonList(condition);
    }

    @Override
    protected Enum<?> getPatientIDColumnIdentifier() {
        return Diagnose.getPIDColumnIdentifier();
    }

    /**
     * @return
     * @throws Exception
     */
    private String getDiagnoseId() throws Exception {
        String icd = get(ICD);
        if (icd == null) {
            error("ICD empty");
            return null;
        }
        int nextId = result.getNextId(Diagnose, Condition.class);
        return getPatientId() + "-CD-" + nextId;
    }

    /**
     * @return
     * @throws Exception
     */
    private CodeableConcept convertCategory() throws Exception {
        String code = get(Typ);
        if (code != null) {
            return new CodeableConcept().setText(code);
        }
        warning(Typ + " empty for Record");
        return null;
    }

    /**
     * @return
     * @throws Exception
     */
    private CodeableConcept convertCode() throws Exception {
        Coding icdCoding = createCoding("http://fhir.de/CodeSystem/bfarm/icd-10-gm", ICD, ERROR);
        if (icdCoding != null) {
            icdCoding.setVersion("2020"); // just to be KDS compatible
            return createCodeableConcept(icdCoding, Bezeichner);
        }
        return null;
    }

    /**
     * @return
     * @throws Exception
     */
    private DateTimeType convertRecordedDate() throws Exception {
        try {
            String date = get(Dokumentationsdatum);
            return DateUtil.parseDateTimeType(date);
        } catch (Exception e) {
            //extract a date from an encounter
            String pid = parsePatientId();
            DateTimeType encounterDate = getEncounterDate(result, pid);
            if (encounterDate != null) {
                warning("Can not parse " + Dokumentationsdatum + " for Record. Extract date from encounter. " + this);
                return encounterDate;
            }
        }
        error("Can not parse " + Dokumentationsdatum + " for Record");
        return null;
    }

    @Override
    protected TableColumnIdentifier getMainEncounterNumberColumnIdentifier() {
        return Diagnosis_Columns.Versorgungsfall_Nr;
    }

}
