package de.uni_leipzig.life.csv2fhir.converter;

import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.utils.DateUtil;
import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.*;
import java.util.Collections;
import java.util.List;

public class AbteilungsfallConverter implements Converter {

    private final CSVRecord record;

    public AbteilungsfallConverter(CSVRecord record) {
        this.record = record;
    }

    @Override
    public List<Resource> convert() throws Exception {
        Encounter encounter = new Encounter();
        // TODO encounter.setIdentifier(null);
        encounter.setStatus(Encounter.EncounterStatus.FINISHED);//TODO
        // TODO encounter.setClass_(null);
        encounter.setServiceType(convertServiceType());
        encounter.setSubject(convertSubject());
        encounter.setPeriod(convertPeriod());
        // TODO encounter.setDiagnosis(null);
        // TODO encounter.addLocation(null);
        return Collections.singletonList(encounter);
    }

    private CodeableConcept convertServiceType() throws Exception {
        String code = record.get("Fachabteilung");
        if (code != null) {
            return new CodeableConcept().addCoding(new Coding()
                    .setSystem("https://www.medizininformatik-initiative.de/fhir/core/CodeSystem/Fachabteilungsschluessel")
                    .setCode(code))
                    .setText(code);
        } else {
            throw new Exception("Error on Abteilungsfall: Fachabteilung empty for Record: "
                    + record.getRecordNumber() + "! " + record.toString());
        }
    }

    private Reference convertSubject() throws Exception {
        String patientId = record.get("Patient-ID");
        if (patientId != null) {
            return new Reference().setReference("Patient/" + patientId);
        } else {
            throw new Exception("Error on Abteilungsfall: Patient-ID empty for Record: "
                    + record.getRecordNumber() + "!" + record.toString());
        }
    }

    private Period convertPeriod() throws Exception {
        try {
            return new Period()
                    .setStartElement(DateUtil.parseDateTimeType(record.get("Startdatum")))
                    .setEndElement(DateUtil.parseDateTimeType(record.get("Enddatum")));
        } catch (Exception e) {
            throw new Exception("Error on Abteilungsfall: Can not parse Startdatum or Enddatum for Record: "
                    + record.getRecordNumber() + "! " + record.toString());
        }
    }
}
