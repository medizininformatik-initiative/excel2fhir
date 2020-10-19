package de.uni_leipzig.life.csv2fhir.converter;

import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.utils.DateUtil;
import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.*;

import java.util.Collections;
import java.util.List;

public class VersorgungsfallConverter implements Converter {

    private final CSVRecord record;

    public VersorgungsfallConverter(CSVRecord record) {
        this.record = record;
    }

    @Override
    public List<Resource> convert() throws Exception {
        Encounter encounter = new Encounter();
        encounter.setStatus(Encounter.EncounterStatus.FINISHED);//TODO
        // TODO encounter.setIdentifier(null);
        // TODO encounter.setDiagnosis(null);
        encounter.setSubject(convertEncounterIdReference());
        encounter.addReasonCode(convertReasonCode());
        encounter.setPeriod(convertPeriod());
        encounter.setClass_(convertClass());
        return Collections.singletonList(encounter);
    }

    private Reference convertEncounterIdReference() throws Exception {
        String patientId = record.get("Patient-ID");
        if (patientId != null) {
            return new Reference().setReference("Patient/" + patientId);
        } else {
            throw new Exception("Error on Versorgungsfall: Patient-ID empty for Record: "
                    + record.getRecordNumber() + "!" + record.toString());
        }
    }

    private CodeableConcept convertReasonCode() {
        String code = record.get("Versorgungsfallgrund (Aufnahmediagnose)");
        if (code != null) {
            return new CodeableConcept().addCoding(new Coding().setSystem("").setCode(code)).setText(code);
        } else {
           return null;
        }
    }

    private Period convertPeriod() throws Exception {
        try {
            return new Period()
                    .setStartElement(DateUtil.tryParseToDateTimeType(record.get("Startdatum")))
                    .setEndElement(DateUtil.tryParseToDateTimeType(record.get("Enddatum")));
        } catch (Exception e) {
            throw new Exception("Error on Versorgungsfall: Can not parse Startdatum or Enddatum for Record: "
                    + record.getRecordNumber() + "! " + record.toString());
        }
    }

    private Coding convertClass() throws Exception {
        String code = record.get("Versorgungsfallgrund (Aufnahmediagnose)");
        if (code != null) {
            return new Coding()
                    .setSystem("https://www.medizininformatik-initiative.de/fhir/core/modul-fall/CodeSystem/Versorgungsfallklasse")
                    .setCode(code);
        }else {
            throw new Exception("Error on Versorgungsfall: Versorgungsfallklasse empty for Record: "
                    + record.getRecordNumber() + "! " + record.toString());
        }
    }
}
