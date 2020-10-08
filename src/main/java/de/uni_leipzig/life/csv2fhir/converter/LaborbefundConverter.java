package de.uni_leipzig.life.csv2fhir.converter;

import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.utils.DateUtil;
import de.uni_leipzig.life.csv2fhir.utils.DecimalUtil;
import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.*;
import java.math.BigDecimal;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LaborbefundConverter implements Converter {

    private final CSVRecord record;

    public LaborbefundConverter(CSVRecord record) {
        this.record = record;
    }

    @Override
    public List<Resource> convert() throws Exception {
        Observation observation = new Observation();
        observation.setIdentifier(new ArrayList<>());//TODO
        observation.setStatus(Observation.ObservationStatus.FINAL);
        observation.setCategory(new ArrayList<>()); //TODO
        observation.setSubject(parseObservationPatientId());
        observation.setCode(parseObservationCode());
        observation.setValue(parseObservationValue());
        observation.setEffective(parseObservationTimestamp());
        return Collections.singletonList(observation);
    }

    private Reference parseObservationPatientId() throws Exception {
        String patientId = record.get("Patient-ID");
        if (patientId != null) {
            return new Reference().setReference("Patient/" + patientId);
        } else {
            throw new Exception("Error on Observation: Patient-ID empty for Record: "
                    + record.getRecordNumber() + "! " + record.toString());
        }
    }

    private CodeableConcept parseObservationCode() throws Exception {
        String code = record.get("LOINC");
        if (code != null) {
            return new CodeableConcept().addCoding(new Coding()
                    .setSystem("http://loinc.org")
                    .setCode(code))
                    .setText(record.get("Parameter"));
        } else {
            throw new Exception("Error on Observation: LOINC empty for Record: "
                    + record.getRecordNumber() + "! " + record.toString());
        }
    }

    private Quantity parseObservationValue() throws Exception {
        try {
            return new Quantity().setValue(DecimalUtil.matchesDecimal(record.get("Messwert"))).setUnit(record.get("Einheit"));
        } catch (Exception e){
        throw new Exception("Error on Medication: Einzeldosis is not a numerical value for Record: "
                + record.getRecordNumber() + "! " + record.toString());
        }
    }

    private DateTimeType parseObservationTimestamp() throws Exception {
        String timestamp = record.get("Zeitstempel (Abnahme)");
        if (timestamp != null) {
            try {
                return DateUtil.tryParseToDateTimeType(timestamp);
            } catch (DateTimeParseException eYear) {
                throw new Exception("Error on Observation: Can not parse Zeitstempel for Record: "
                        + record.getRecordNumber() + "! " + record.toString());
            }
        } else {
            throw new Exception("Error on Observation: Zeitstempel (Abnahme) empty for Record: "
                    + record.getRecordNumber() + "! " + record.toString());
        }
    }
}
