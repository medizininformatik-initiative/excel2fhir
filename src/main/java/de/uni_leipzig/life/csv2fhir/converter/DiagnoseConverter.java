package de.uni_leipzig.life.csv2fhir.converter;

import static de.uni_leipzig.life.csv2fhir.BundleFuntions.getEncounterDate;
import static de.uni_leipzig.life.csv2fhir.Converter.EmptyRecordValueErrorLevel.ERROR;
import static de.uni_leipzig.life.csv2fhir.TableIdentifier.Diagnose;
import static de.uni_leipzig.life.csv2fhir.converterFactory.DiagnoseConverterFactory.Diagnose_Columns.Bezeichner;
import static de.uni_leipzig.life.csv2fhir.converterFactory.DiagnoseConverterFactory.Diagnose_Columns.Dokumentationsdatum;
import static de.uni_leipzig.life.csv2fhir.converterFactory.DiagnoseConverterFactory.Diagnose_Columns.ICD;
import static de.uni_leipzig.life.csv2fhir.converterFactory.DiagnoseConverterFactory.Diagnose_Columns.Patient_ID;
import static de.uni_leipzig.life.csv2fhir.converterFactory.DiagnoseConverterFactory.Diagnose_Columns.Typ;

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
import de.uni_leipzig.life.csv2fhir.utils.DateUtil;

public class DiagnoseConverter extends Converter {

    /** Simple counter to generate unique identifier */
    static int n = 1;

    /**  */
    String PROFILE = "https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose";
    // https://simplifier.net/medizininformatikinitiative-moduldiagnosen/diagnose

    /**
     * @param record
     * @param result
     * @param validator
     * @throws Exception
     */
    public DiagnoseConverter(CSVRecord record, ConverterResult result, FHIRValidator validator) throws Exception {
        super(record, result, validator);
    }

    public static void reset() {
        n = 1;
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

        //now add an the encounter a reference to this procedure as diagnosis (Yes thats the logic of KDS!?)
        String encounterId = getEncounterId();
        String diagnosisUseIdentifier = get(Typ);
        VersorgungsfallConverter.addDiagnosisToEncounter(result, encounterId, condition, diagnosisUseIdentifier);

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
        return getPatientId() + "-CD-" + n++;
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
            String pid = get(Patient_ID);
            DateTimeType encounterDate = getEncounterDate(result, pid);
            if (encounterDate != null) {
                warning("Can not parse " + Dokumentationsdatum + " for Record. Extract date from encounter. " + this);
                return encounterDate;
            }
        }
        error("Can not parse " + Dokumentationsdatum + " for Record");
        return null;
    }

}
