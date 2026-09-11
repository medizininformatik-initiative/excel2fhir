package de.uni_leipzig.life.csv2fhir.converter;

import static de.uni_leipzig.life.csv2fhir.ConverterOptions.BooleanOption.SET_REFERENCE_FROM_CONDITION_TO_ENCOUNTER;
import static de.uni_leipzig.life.csv2fhir.ConverterOptions.BooleanOption.SET_REFERENCE_FROM_ENCOUNTER_TO_CONDITION;
import static de.uni_leipzig.life.csv2fhir.ConverterOptions.IntOption.START_ID_CONDITION;
import static de.uni_leipzig.life.csv2fhir.TableIdentifier.Diagnose;
import static de.uni_leipzig.life.csv2fhir.converter.ConditionConverter.Diagnosis_Columns.*;
import static org.apache.logging.log4j.util.Strings.isBlank;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Resource;

import de.uni_leipzig.imise.validate.FHIRValidator;
import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.ConverterOptions;
import de.uni_leipzig.life.csv2fhir.ConverterResult;
import de.uni_leipzig.life.csv2fhir.TableColumnIdentifier;
import de.uni_leipzig.life.csv2fhir.utils.DateUtil;

/** One diagnosis per row, with explicit coding and independently recorded clinical dates. */
public class ConditionConverter extends Converter {
    public enum Diagnosis_Columns implements TableColumnIdentifier {
        Bezeichner, Code, Codesystem, Zusatzcode, Zusatzcodesystem, Dokumentationszeitpunkt, Beginn, Ende,
        Klinischer_Status, Verifikationsstatus, Typ;

        @Override
        public String toString() {
            return name().replace('_', ' ');
        }
    }

    private static final String PROFILE =
            "https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose";

    public ConditionConverter(CSVRecord record, String previousRecordPID, ConverterResult result,
            FHIRValidator validator, ConverterOptions options) throws Exception {
        super(record, previousRecordPID, result, validator, options);
    }

    @Override
    protected List<Resource> convertInternal() throws Exception {
        Condition condition = new Condition();
        String encounterId = getEncounterId();
        String id = (isBlank(encounterId) ? getPatientId() : encounterId) + ResourceIdSuffix.CONDITION
                + result.getNextId(Diagnose, Condition.class, START_ID_CONDITION);
        condition.setId(id);
        condition.addIdentifier(new Identifier().setValue(id));
        condition.setMeta(new Meta().addProfile(PROFILE));
        CodeableConcept code = new CodeableConcept();
        addCoding(code, Code, Codesystem);
        addCoding(code, Zusatzcode, Zusatzcodesystem);
        if (!isBlank(get(Bezeichner))) {
            code.setText(get(Bezeichner));
        }
        if (!code.isEmpty()) {
            condition.setCode(code);
        }
        condition.setSubject(getPatientReference());
        condition.setRecordedDateElement(date(Dokumentationszeitpunkt));
        condition.setOnset(date(Beginn));
        condition.setAbatement(date(Ende));
        condition.setClinicalStatus(status(Klinischer_Status, DiagnosisValues.CLINICAL,
                "http://terminology.hl7.org/CodeSystem/condition-clinical"));
        condition.setVerificationStatus(status(Verifikationsstatus, DiagnosisValues.VERIFICATION,
                "http://terminology.hl7.org/CodeSystem/condition-ver-status"));
        if (result.getConverterOptions().is(SET_REFERENCE_FROM_CONDITION_TO_ENCOUNTER)) {
            condition.setEncounter(getEncounterReference());
        }
        if (!isValid(condition)) {
            return Collections.emptyList();
        }
        if (result.getConverterOptions().is(SET_REFERENCE_FROM_ENCOUNTER_TO_CONDITION) && !isBlank(encounterId)) {
            EncounterConverter.addDiagnosisToEncounter(result, encounterId, condition, get(Typ));
        }
        return Collections.singletonList(condition);
    }

    private void addCoding(CodeableConcept concept, Diagnosis_Columns codeColumn, Diagnosis_Columns systemColumn) {
        String value = get(codeColumn);
        if (isBlank(value)) {
            return;
        }
        Coding selection = DiagnosisValues.systems().get(get(systemColumn));
        if (selection == null) {
            throw new IllegalArgumentException(systemColumn + " requires an explicit supported selection");
        }
        Coding coding = selection.copy();
        Extension absent = DiagnosisValues.absentReason(value);
        if (absent == null) {
            coding.setCode(value);
        } else {
            coding.getCodeElement().addExtension(absent);
        }
        if (concept.getCoding().stream().anyMatch(c -> c.getSystem().equals(coding.getSystem()))) {
            throw new IllegalArgumentException("Two codings of the same system exceed the diagnosis profile slice");
        }
        concept.addCoding(coding);
    }

    private DateTimeType date(Diagnosis_Columns column) throws Exception {
        String value = get(column);
        if (isBlank(value)) {
            return null;
        }
        Extension absent = DiagnosisValues.absentReason(value);
        if (absent != null) {
            DateTimeType date = new DateTimeType();
            date.addExtension(absent);
            return date;
        }
        // Preserve FHIR precision and offsets for imported timestamps.
        if (value.matches("\\d{4}(-\\d{2}(-\\d{2})?)?(T.*)?")) {
            return new DateTimeType(value);
        }
        return DateUtil.parseDateTimeType(value);
    }

    private CodeableConcept status(Diagnosis_Columns column, Map<String, String> values, String system) {
        String value = get(column);
        if (isBlank(value)) {
            return null;
        }
        Extension absent = DiagnosisValues.absentReason(value);
        if (absent != null) {
            return (CodeableConcept) new CodeableConcept().addExtension(absent);
        }
        String code = values.get(value);
        if (code == null) {
            throw new IllegalArgumentException("Unknown " + column + ": " + value);
        }
        return new CodeableConcept(new Coding(system, code, null));
    }
}
