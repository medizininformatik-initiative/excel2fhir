package de.uni_leipzig.life.csv2fhir.converter;

import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.utils.DateUtil;
import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

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
        // TODO encounter.setIdentifier(null);
        encounter.setStatus(Encounter.EncounterStatus.FINISHED);//TODO
        encounter.setClass_(convertClass());
        encounter.setSubject(convertSubject());
        encounter.setPeriod(convertPeriod());
        encounter.addReasonCode(convertReasonCode());
        // TODO encounter.setDiagnosis(null);

        return Collections.singletonList(encounter);
    }

    private Coding convertClass() throws Exception {
        String code = record.get("Versorgungsfallklasse");
        if (code != null) {
            return new Coding()
                    .setSystem("https://www.medizininformatik-initiative.de/fhir/core/modul-fall/CodeSystem/Versorgungsfallklasse")
                    .setCode(code);
        } else {
            throw new Exception("Error on Versorgungsfall: Versorgungsfallklasse empty for Record: "
                    + record.getRecordNumber() + "! " + record.toString());
        }
    }

    private Reference convertSubject() throws Exception {
        String patientId = record.get("Patient-ID");
        if (patientId != null) {
            return new Reference().setReference("Patient/" + patientId);
        } else {
            throw new Exception("Error on Versorgungsfall: Patient-ID empty for Record: "
                    + record.getRecordNumber() + "!" + record.toString());
        }
    }

    private Period convertPeriod() throws Exception {
        try {
            return new Period()
                    .setStartElement(DateUtil.parseDateTimeType(record.get("Startdatum")))
                    .setEndElement(DateUtil.parseDateTimeType(record.get("Enddatum")));
        } catch (Exception e) {
            throw new Exception("Error on Versorgungsfall: Can not parse Startdatum or Enddatum for Record: "
                    + record.getRecordNumber() + "! " + record.toString());
        }
    }

    private CodeableConcept convertReasonCode() {
        String code = record.get("Versorgungsfallgrund (Aufnahmediagnose)");
        if (code != null) {
            return new CodeableConcept().addCoding(new Coding()
                    .setSystem("2.25.13106415395318837456468900343666547797")
                    .setCode(code));
        } else {
            return null;
        }
    }


}
