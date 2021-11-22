package de.uni_leipzig.life.csv2fhir.converter;

import static de.uni_leipzig.life.csv2fhir.Converter.EmptyRecordValueErrorLevel.ERROR;
import static de.uni_leipzig.life.csv2fhir.TableIdentifier.Klinische_Dokumentation;
import static de.uni_leipzig.life.csv2fhir.converterFactory.KlinischeDokumentationConverterFactory.NeededColumns.Bezeichner;
import static de.uni_leipzig.life.csv2fhir.converterFactory.KlinischeDokumentationConverterFactory.NeededColumns.Einheit;
import static de.uni_leipzig.life.csv2fhir.converterFactory.KlinischeDokumentationConverterFactory.NeededColumns.LOINC;
import static de.uni_leipzig.life.csv2fhir.converterFactory.KlinischeDokumentationConverterFactory.NeededColumns.Patient_ID;
import static de.uni_leipzig.life.csv2fhir.converterFactory.KlinischeDokumentationConverterFactory.NeededColumns.Wert;
import static de.uni_leipzig.life.csv2fhir.converterFactory.KlinischeDokumentationConverterFactory.NeededColumns.Zeitstempel;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.Ucum;
import de.uni_leipzig.life.csv2fhir.utils.DateUtil;
import de.uni_leipzig.life.csv2fhir.utils.DecimalUtil;

public class KlinischeDokumentationConverter extends Converter {

    /** Simple counter to generate unique identifier */
    static int n = 1;

    /**
     * @param record
     * @throws Exception
     */
    public KlinischeDokumentationConverter(CSVRecord record) throws Exception {
        super(record);
    }

    @Override
    public List<Resource> convert() throws Exception {
        Observation observation = new Observation();
        observation.setId(getEncounterId() + "-OK-" + n++);
        observation.setStatus(Observation.ObservationStatus.FINAL);
        observation.setCode(parseObservationCode());
        observation.setSubject(parseObservationPatientId());
        observation.setEncounter(getEncounterReference());
        observation.setEffective(parseObservationTimestamp());
        observation.setValue(parseObservationValue());
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
    private CodeableConcept parseObservationCode() throws Exception {
        return createCodeableConcept("http://loinc.org", LOINC, Bezeichner, ERROR);
    }

    /**
     * @return
     * @throws Exception
     */
    private Reference parseObservationPatientId() throws Exception {
        String patientId = record.get(Patient_ID);
        if (patientId != null) {
            return new Reference().setReference("Patient/" + patientId);
        }
        error("Patient-ID empty for Record");
        return null;
    }

    /**
     * @return
     * @throws Exception
     */
    private DateTimeType parseObservationTimestamp() throws Exception {
        String timestamp = record.get(Zeitstempel);
        if (timestamp != null) {
            try {
                return DateUtil.parseDateTimeType(timestamp);
            } catch (Exception eYear) {
                error("Can not parse Zeitstempel");
                return null;
            }
        }
        error("Zeitstempel (Abnahme) empty for Record");
        return null;
    }

    /**
     * @return
     * @throws Exception
     */
    private Quantity parseObservationValue() throws Exception {
        BigDecimal messwert;
        try {
            messwert = DecimalUtil.parseDecimal(record.get(Wert));
        } catch (Exception e) {
            error("Wert is not a numerical value for Record");
            return null;
        }
        String unit = record.get(Einheit);
        if (unit == null || unit.isEmpty()) {
            error("Einheit is empty for Record");
            return null;
        }

        String ucum, synonym;
        if (Ucum.isUcum(unit)) {
            ucum = unit;
            synonym = Ucum.ucum2human(unit);
        } else {
            ucum = Ucum.human2ucum(unit);
            synonym = unit;
        }
        if (ucum.isEmpty()) {
            return new Quantity().setValue(messwert).setUnit(synonym);
        }
        return new Quantity().setValue(messwert).setSystem("http://unitsofmeasure.org").setCode(ucum).setUnit(synonym);
    }

}
