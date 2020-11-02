package de.uni_leipzig.life.csv2fhir.converter;

import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.utils.DateUtil;
import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Procedure;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

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
        procedure.addExtension(new Extension()
                .setUrl("https://www.medizininformatik-initiative.de/fhir/core/modul-prozedur/StructureDefinition/procedure-recordedDate")
                .setValue(convertRecordedDate()));
        procedure.setStatus(Procedure.ProcedureStatus.COMPLETED);//TODO
        procedure.setCode(convertProcedureCode());
        procedure.setSubject(convertSubject());
        // TODO procedure.setPerformed(null);
        return Collections.singletonList(procedure);
    }

    private DateTimeType convertRecordedDate() throws Exception {
        try {
            return DateUtil.parseDateTimeType(record.get("Dokumentationsdatum"));
        } catch (Exception e) {
            throw new Exception("Error on Procedure: Can not parse Dokumentationsdatum for Record: "
                    + record.getRecordNumber() + "! " + record.toString());
        }
    }

    private CodeableConcept convertProcedureCode() throws Exception {
        return new CodeableConcept()
                .addCoding(getCode())
                .setText(record.get("Prozedurentext"));
    }

    private Coding getCode() throws Exception {
        String code = record.get("Prozedurencode");
        if (code != null) {
            return new Coding()
                    .setSystem("http://snomed.info/sct")
                    .setCode(code);
        } else {
            throw new Exception("Error on Procedure: Prozedurencode empty for Record: "
                    + record.getRecordNumber() + "!" + record.toString());
        }
    }

    private Reference convertSubject() throws Exception {
        String patientId = record.get("Patient-ID");
        if (patientId != null) {
            return new Reference().setReference("Patient/" + patientId);
        } else {
            throw new Exception("Error on Procedure: Patient-ID empty for Record: "
                    + record.getRecordNumber() + "!" + record.toString());
        }
    }
}

