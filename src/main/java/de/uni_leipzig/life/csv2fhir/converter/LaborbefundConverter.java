package de.uni_leipzig.life.csv2fhir.converter;

import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.utils.DateUtil;
import de.uni_leipzig.life.csv2fhir.utils.DecimalUtil;
import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;

// KDS Profile ObservationLab
// https://simplifier.net/guide/LaborbefundinderMedizininformatik-Initiative/Observation

public class LaborbefundConverter implements Converter {

    private final CSVRecord record;

    public LaborbefundConverter(CSVRecord record) {
        this.record = record;
    }

    @Override
    public List<Resource> convert() throws Exception {
        Observation observation = new Observation();
        //TODO observation.setIdentifier(new ArrayList<>());
        observation.setStatus(Observation.ObservationStatus.FINAL);
        //TODO observation.setCategory(new ArrayList<>());
        observation.setCode(parseObservationCode());
        observation.setSubject(parseObservationPatientId());
        observation.setEffective(parseObservationTimestamp());
        observation.setValue(parseObservationValue());
        return Collections.singletonList(observation);
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

    private Reference parseObservationPatientId() throws Exception {
        String patientId = record.get("Patient-ID");
        if (patientId != null) {
            return new Reference().setReference("Patient/" + patientId);
        } else {
            throw new Exception("Error on Observation: Patient-ID empty for Record: "
                    + record.getRecordNumber() + "! " + record.toString());
        }
    }

    private DateTimeType parseObservationTimestamp() throws Exception {
        String timestamp = record.get("Zeitstempel (Abnahme)");
        if (timestamp != null) {
            try {
                return DateUtil.parseDateTimeType(timestamp);
            } catch (DateTimeParseException eYear) {
                throw new Exception("Error on Observation: Can not parse Zeitstempel for Record: "
                        + record.getRecordNumber() + "! " + record.toString());
            }
        } else {
            throw new Exception("Error on Observation: Zeitstempel (Abnahme) empty for Record: "
                    + record.getRecordNumber() + "! " + record.toString());
        }
    }

    private Quantity parseObservationValue() throws Exception {
        try {
            return new Quantity().setValue(DecimalUtil.parseDecimal(record.get("Messwert")))
            		.setSystem("http://unitsofmeasure.org").setCode("UCUM").setUnit(record.get("Einheit"));
        } catch (Exception e) {
            throw new Exception("Error on Observation: Messwert is not a numerical value for Record: "
                    + record.getRecordNumber() + "! " + record.toString());
        }
    }
}
