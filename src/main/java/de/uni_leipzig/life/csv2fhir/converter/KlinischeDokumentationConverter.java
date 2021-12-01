package de.uni_leipzig.life.csv2fhir.converter;

import static com.google.common.base.Strings.isNullOrEmpty;
import static de.uni_leipzig.life.csv2fhir.BundleFuntions.createReference;
import static de.uni_leipzig.life.csv2fhir.Converter.EmptyRecordValueErrorLevel.ERROR;
import static de.uni_leipzig.life.csv2fhir.TableIdentifier.Klinische_Dokumentation;
import static de.uni_leipzig.life.csv2fhir.Ucum.human2ucum;
import static de.uni_leipzig.life.csv2fhir.Ucum.ucum2human;
import static de.uni_leipzig.life.csv2fhir.converter.LaborbefundConverter.createUnknownDataAbsentReason;
import static de.uni_leipzig.life.csv2fhir.converterFactory.KlinischeDokumentationConverterFactory.KlinischeDokumentation_Columns.Bezeichner;
import static de.uni_leipzig.life.csv2fhir.converterFactory.KlinischeDokumentationConverterFactory.KlinischeDokumentation_Columns.Einheit;
import static de.uni_leipzig.life.csv2fhir.converterFactory.KlinischeDokumentationConverterFactory.KlinischeDokumentation_Columns.LOINC;
import static de.uni_leipzig.life.csv2fhir.converterFactory.KlinischeDokumentationConverterFactory.KlinischeDokumentation_Columns.Patient_ID;
import static de.uni_leipzig.life.csv2fhir.converterFactory.KlinischeDokumentationConverterFactory.KlinischeDokumentation_Columns.Wert;
import static de.uni_leipzig.life.csv2fhir.converterFactory.KlinischeDokumentationConverterFactory.KlinischeDokumentation_Columns.Zeitstempel;
import static de.uni_leipzig.life.csv2fhir.utils.DateUtil.parseDateTimeType;
import static de.uni_leipzig.life.csv2fhir.utils.DecimalUtil.parseDecimal;
import static org.hl7.fhir.r4.model.Observation.ObservationStatus.FINAL;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

import de.uni_leipzig.imise.FHIRValidator;
import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.ConverterResult;
import de.uni_leipzig.life.csv2fhir.Ucum;

public class KlinischeDokumentationConverter extends Converter {

    /**
     * @param record
     * @param result
     * @param validator
     * @throws Exception
     */
    public KlinischeDokumentationConverter(CSVRecord record, ConverterResult result, FHIRValidator validator) throws Exception {
        super(record, result, validator);
    }

    @Override
    public List<Resource> convert() throws Exception {
        Observation observation = new Observation();
        int nextId = result.getNextId(Klinische_Dokumentation, Observation.class);
        observation.setId(getEncounterId() + "-OK-" + nextId);
        observation.setStatus(FINAL);
        observation.setCode(createCodeableConcept("http://loinc.org", LOINC, Bezeichner, ERROR));
        observation.setSubject(parseObservationPatientId());
        observation.setEncounter(getEncounterReference()); // if null then observation is invalid
        observation.setEffective(parseObservationTimestamp());
        //set value or value absent reason
        Quantity observationValue = parseObservationValue();
        if (observationValue != null) {
            observation.setValue(observationValue);
        } else {
            observation.setDataAbsentReason(createUnknownDataAbsentReason());
        }
        return Collections.singletonList(observation);
    }

    @Override
    protected Enum<?> getPatientIDColumnIdentifier() {
        return Klinische_Dokumentation.getPIDColumnIdentifier();
    }

    /**
     * @return
     * @throws Exception
     */
    private Reference parseObservationPatientId() throws Exception {
        String patientId = get(Patient_ID);
        if (!isNullOrEmpty(patientId)) {
            return createReference(Patient.class, patientId);
        }
        error(Patient_ID + " empty for Record");
        return null;
    }

    /**
     * @return
     * @throws Exception
     */
    private DateTimeType parseObservationTimestamp() throws Exception {
        String timestamp = get(Zeitstempel);
        if (timestamp != null) {
            try {
                return parseDateTimeType(timestamp);
            } catch (Exception eYear) {
                error("Can not parse Zeitstempel");
                return null;
            }
        }
        error(Zeitstempel + " empty for Record");
        return null;
    }

    /**
     * @return
     * @throws Exception
     */
    private Quantity parseObservationValue() throws Exception {
        BigDecimal messwert;
        try {
            messwert = parseDecimal(get(Wert));
        } catch (Exception e) {
            error(Wert + " is not a numerical value for Record");
            return null;
        }
        String unit = get(Einheit);
        if (isNullOrEmpty(unit)) {
            error(Einheit + " is empty for Record");
            return null;
        }
        boolean isUcum = Ucum.isUcum(unit);
        String ucum = isUcum ? unit : human2ucum(unit);
        String synonym = isUcum ? ucum2human(unit) : unit;

        Quantity quantity = new Quantity()
                .setSystem("http://unitsofmeasure.org")
                .setValue(messwert);

        if (!ucum.isEmpty()) {
            quantity.setCode(ucum)
                    .setUnit(synonym);
        }
        return quantity;
    }

}
