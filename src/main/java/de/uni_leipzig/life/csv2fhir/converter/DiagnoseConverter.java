package de.uni_leipzig.life.csv2fhir.converter;

import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.utils.DateUtil;
import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.*;

import java.util.Collections;
import java.util.List;

public class DiagnoseConverter implements Converter {

    private final CSVRecord record;

    public DiagnoseConverter(CSVRecord record) {
        this.record = record;
    }

    @Override
    public List<Resource> convert() throws Exception {
        Condition condition = new Condition();
        condition.setSubject(convertConditionIdReference());
        condition.addNote(convertProcedureNote());
        condition.setCode(convertProcedureCode());
        condition.setRecordedDateElement(convertPeriod());
        // TODO Type ?
        return Collections.singletonList(condition);
    }

    private Reference convertConditionIdReference() throws Exception {
        String patientId = record.get("Patient-ID");
        if (patientId != null) {
            return new Reference().setReference("Patient/" + patientId);
        } else {
            throw new Exception("Error on Diagnose: Patient-ID empty for Record: "
                    + record.getRecordNumber() + "!" + record.toString());
        }
    }

    private Annotation convertProcedureNote() {
        String note = record.get("Bezeichner");
        if (note != null) {
            return new Annotation().setText(note);
        }
        return null;
    }

    private CodeableConcept convertProcedureCode() throws Exception {
        String code = record.get("ICD");
        if (code != null) {
            return new CodeableConcept().addCoding(new Coding().setSystem("http://fhir.de/CodeSystem/dimdi/icd-10-gm").setCode(code)).setText(code);
        } else {
            throw new Exception("Error on Diagnose: ICD empty for Record: "
                    + record.getRecordNumber() + "!" + record.toString());
        }
    }

    private DateTimeType convertPeriod() throws Exception {
        try {
            return DateUtil.tryParseToDateTimeType(record.get("Dokumentationsdatum"));
        } catch (Exception e) {
            throw new Exception("Error on Diagnose: Can not parse Dokumentationsdatum for Record: "
                    + record.getRecordNumber() + "! " + record.toString());
        }
    }
}
