package de.uni_leipzig.life.csv2fhir.converter;

import static de.uni_leipzig.life.csv2fhir.BundleFunctions.getEncounterDate;
import static de.uni_leipzig.life.csv2fhir.ConverterOptions.BooleanOption.SET_REFERENCE_FROM_CONDITION_TO_ENCOUNTER;
import static de.uni_leipzig.life.csv2fhir.ConverterOptions.BooleanOption.SET_REFERENCE_FROM_ENCOUNTER_TO_CONDITION;
import static de.uni_leipzig.life.csv2fhir.ConverterOptions.IntOption.START_ID_CONDITION;
import static de.uni_leipzig.life.csv2fhir.TableIdentifier.Diagnose;
import static de.uni_leipzig.life.csv2fhir.converter.ConditionConverter.Diagnosis_Columns.Bezeichner;
import static de.uni_leipzig.life.csv2fhir.converter.ConditionConverter.Diagnosis_Columns.Dokumentationsdatum;
import static de.uni_leipzig.life.csv2fhir.converter.ConditionConverter.Diagnosis_Columns.ICD;
import static de.uni_leipzig.life.csv2fhir.converter.ConditionConverter.Diagnosis_Columns.Typ;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.logging.log4j.util.Strings.isBlank;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Resource;

import de.uni_leipzig.imise.validate.FHIRValidator;
import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.ConverterOptions;
import de.uni_leipzig.life.csv2fhir.ConverterResult;
import de.uni_leipzig.life.csv2fhir.TableColumnIdentifier;
import de.uni_leipzig.life.csv2fhir.utils.DateUtil;

/**
 * @author jheuschkel (19.10.2020), AXS (05.11.2021)
 */
public class ConditionConverter extends Converter {

    /**
     * toString() result of these enum values are the names of the columns in
     * the correspunding excel sheet.
     */
    public static enum Diagnosis_Columns implements TableColumnIdentifier {
        Bezeichner,
        ICD,
        Dokumentationsdatum,
        Typ
    }

    /**
     * Patterns to find valid ICD 10 Codes in a given String. They are sorted
     * from the strongest to the weakest pattern.
     */
    public static final Pattern[] VALID_ICD10_GM_PATTERNS = {
            Pattern.compile("[A-Z][0-9]{2}(\\.[0-9][0-9]|\\.[0-9]|\\.){0,1}"),
            //            Pattern.compile("[A-Z][0-9][0-9]\\.[0-9][0-9]"),
            //            Pattern.compile("[A-Z][0-9][0-9]\\.[0-9]"),
            //            Pattern.compile("[A-Z][0-9][0-9]\\."),
            //            Pattern.compile("[A-Z][0-9][0-9]"),
    };

    /**  */
    String PROFILE = "https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose";
    // https://simplifier.net/medizininformatikinitiative-moduldiagnosen/diagnose

    /**
     * @param record
     * @param result
     * @param validator
     * @param options
     * @throws Exception
     */
    public ConditionConverter(CSVRecord record, ConverterResult result, FHIRValidator validator, ConverterOptions options) throws Exception {
        super(record, result, validator, options);
    }

    @Override
    protected List<Resource> convertInternal() throws Exception {
        List<Resource> conditions = new ArrayList<>();
        //it is possible that there ist more than on code in the cell
        List<String> icdCodes = parseICDCodes();
        if (!isEmpty(icdCodes)) {
            for (int i = 0; i < icdCodes.size(); i++) {
                String icdCode = icdCodes.get(i);
                Condition condition = new Condition();
                // Nicht im Profil, aber notwendig für Fall Aufnahmediagnose
                String id = getDiagnoseId(i);
                condition.setId(id);
                condition.setIdentifier(Collections.singletonList(new Identifier().setValue(id)));
                condition.setMeta(new Meta().addProfile(PROFILE));
                //        condition.addCategory(convertCategory());
                condition.setCode(convertCode(icdCode));
                condition.setSubject(getPatientReference());
                condition.setRecordedDateElement(convertRecordedDate());

                //enable this to get the reference from condition to encounter. This is optional
                //but it creates a circle, because the encounter has also a reference list to all
                //diagnosis. This is false by default.
                ConverterOptions converterOptions = result.getConverterOptions();
                if (converterOptions.is(SET_REFERENCE_FROM_CONDITION_TO_ENCOUNTER)) {
                    condition.setEncounter(getEncounterReference());
                }

                if (isValid(condition)) { //check validity before adding the refence from encounter to this
                    //usually this is true by default
                    if (converterOptions.is(SET_REFERENCE_FROM_ENCOUNTER_TO_CONDITION)) {
                        //now add an the encounter a reference to this procedure as diagnosis (Yes thats the logic of KDS!?)
                        String encounterId = getEncounterId();
                        //encounterId is optional
                        if (!isBlank(encounterId)) {
                            String diagnosisUseIdentifier = get(Typ);
                            EncounterLevel1Converter.addDiagnosisToEncounter(result, encounterId, condition, diagnosisUseIdentifier);
                        }
                    }
                    conditions.add(condition);
                }
            }
        }
        return conditions;
    }

    /**
     * @return
     * @throws Exception
     */
    private List<String> parseICDCodes() throws Exception {
        String code = get(ICD);
        if (isBlank(code)) {
            error(ICD + " empty for record");
            return null;
        }
        code = code.trim().toUpperCase();
        List<String> icdCodes = extractICDCodes(code);
        return icdCodes;
    }

    /**
     * @param sourceString
     * @return
     */
    private static List<String> extractICDCodes(String sourceString) {
        List<String> icdCodes = new ArrayList<>();
        for (Pattern pattern : VALID_ICD10_GM_PATTERNS) {
            Matcher matcher = pattern.matcher(sourceString);
            while (matcher.find()) {
                int start = matcher.start();
                int end = matcher.end();
                String icdCode = sourceString.substring(start, end);
                if (icdCode.endsWith(".")) {
                    icdCode = icdCode.substring(0, icdCode.length() - 1);
                }
                icdCodes.add(icdCode);
                sourceString = StringUtils.remove(sourceString, icdCode);
                matcher = pattern.matcher(sourceString);
            }
        }
        return icdCodes;
    }

    /**
     * @param nextIDOffset
     * @return
     * @throws Exception
     */
    private String getDiagnoseId(int nextIDOffset) throws Exception {
        String icd = get(ICD);
        if (icd == null) {
            error("ICD empty");
            return null;
        }
        int nextId = result.getNextId(Diagnose, Condition.class, START_ID_CONDITION) + nextIDOffset;
        String encounterId = getEncounterId();
        return (isBlank(encounterId) ? getPatientId() : encounterId) + ResourceIdSuffix.CONDITION + nextId;
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
     * @param icdCode
     * @return
     * @throws Exception
     */
    private CodeableConcept convertCode(String icdCode) throws Exception {
        Coding icdCoding = createCoding("http://fhir.de/CodeSystem/bfarm/icd-10-gm", icdCode);
        icdCoding.setVersion("2020"); // just to be KDS compatible
        return createCodeableConcept(icdCoding, Bezeichner);
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

}
