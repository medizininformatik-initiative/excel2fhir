package de.uni_leipzig.life.csv2fhir.converter;

import static de.uni_leipzig.life.csv2fhir.Converter.EmptyRecordValueErrorLevel.ERROR;
import static de.uni_leipzig.life.csv2fhir.converterFactory.DiagnoseConverterFactory.NeededColumns.Bezeichner;
import static de.uni_leipzig.life.csv2fhir.converterFactory.DiagnoseConverterFactory.NeededColumns.Dokumentationsdatum;
import static de.uni_leipzig.life.csv2fhir.converterFactory.DiagnoseConverterFactory.NeededColumns.ICD;
import static de.uni_leipzig.life.csv2fhir.converterFactory.DiagnoseConverterFactory.NeededColumns.Typ;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Resource;

import com.google.common.base.Strings;

import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.utils.DateUtil;

public class DiagnoseConverter extends Converter {

    /**  */
    String PROFILE = "https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose";
    // https://simplifier.net/medizininformatikinitiative-moduldiagnosen/diagnose

    /**
     * Stores all {@link CSVRecord}s of all conditions created by this converter
     */
    public static final Map<Condition, CSVRecord> conditionToCSVRecordMap = new HashMap<>();

    /**
     * @param record
     * @throws Exception
     */
    public DiagnoseConverter(CSVRecord record) throws Exception {
        super(record);
    }

    @Override
    public List<Resource> convert() throws Exception {
        Condition condition = new Condition();
        conditionToCSVRecordMap.put(condition, record);
        // Nicht im Profil, aber notwendig für Fall Aufnahmediagnose
        condition.setId(getDiagnoseId());
        condition.setIdentifier(Collections.singletonList(new Identifier().setValue(getDiagnoseId())));
        condition.setMeta(new Meta().addProfile(PROFILE));
        //        condition.addCategory(convertCategory());
        condition.setCode(convertCode());
        condition.setSubject(getPatientReference());
        condition.setEncounter(getEncounterReference());
        condition.setRecordedDateElement(convertRecordedDate());
        return Collections.singletonList(condition);
    }

    /**
     * @return
     * @throws Exception
     */
    private String getDiagnoseId() throws Exception {
        String icd = record.get(ICD);
        if (icd == null) {
            error("ICD empty");
            return null;
        }
        String diagnosisRole = record.get(Typ);
        String diagnosisRoleHash = Strings.isNullOrEmpty(diagnosisRole) ? "" : String.valueOf(diagnosisRole.hashCode());
        return getPatientId() + "-C-" + icd.hashCode() + diagnosisRoleHash;
    }

    /**
     * @return
     * @throws Exception
     */
    private CodeableConcept convertCategory() throws Exception {
        String code = record.get(Typ);
        if (code != null) {
            return new CodeableConcept().setText(code);
        }
        warning("Typ empty for Record");
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
            return DateUtil.parseDateTimeType(record.get(Dokumentationsdatum));
        } catch (Exception e) {
            error("Can not parse Dokumentationsdatum for Record");
            return null;
        }
    }

}
