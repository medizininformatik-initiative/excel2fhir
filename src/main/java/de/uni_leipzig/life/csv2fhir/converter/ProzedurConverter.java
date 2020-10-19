package de.uni_leipzig.life.csv2fhir.converter;

import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.utils.DateUtil;
import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.*;

import java.util.Collections;
import java.util.List;

public class ProzedurConverter implements Converter {

    private final CSVRecord record;

    public ProzedurConverter(CSVRecord record) {
        this.record = record;
    }

    @Override
    public List<Resource> convert() throws Exception {
        Procedure procedure = new Procedure();
        procedure.setStatus(Procedure.ProcedureStatus.COMPLETED);//TODO
        procedure.setSubject(convertProcedureIdReference());
        procedure.addNote(convertProcedureNote());
        procedure.setCode(convertProcedureCode());
        procedure.setPerformed(convertPeriod());
        return Collections.singletonList(procedure);
    }

    private Reference convertProcedureIdReference() throws Exception {
        String patientId = record.get("Patient-ID");
        if (patientId != null) {
            return new Reference().setReference("Patient/" + patientId);
        } else {
            throw new Exception("Error on Procedure: Patient-ID empty for Record: "
                    + record.getRecordNumber() + "!" + record.toString());
        }
    }

    private Annotation convertProcedureNote() {
        String note = record.get("Prozesdurentext");
        if (note != null) {
            return new Annotation().setText(note);
        }
        return null;
    }

    private CodeableConcept convertProcedureCode() throws Exception {
        String code = record.get("Prozedurencode");
        if (code != null) {
            return new CodeableConcept().addCoding(new Coding().setSystem("http://snomed.info/sct").setCode(code)).setText(code);
        } else {
            throw new Exception("Error on Procedure: Prozedurencode empty for Record: "
                    + record.getRecordNumber() + "!" + record.toString());
        }
    }

    private DateTimeType convertPeriod() throws Exception {
        try {
            return DateUtil.tryParseToDateTimeType(record.get("Dokumentationsdatum"));
        } catch (Exception e) {
            throw new Exception("Error on Procedure: Can not parse Dokumentationsdatum for Record: "
                    + record.getRecordNumber() + "! " + record.toString());
        }
    }
}
