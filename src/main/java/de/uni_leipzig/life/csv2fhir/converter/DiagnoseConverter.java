package de.uni_leipzig.life.csv2fhir.converter;

import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.utils.DateUtil;
import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

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
        condition.addCategory(convertCategory());
        condition.setCode(convertProcedureCode());
        condition.setSubject(convertSubject());
        condition.setRecordedDateElement(convertRecordedDate());
        return Collections.singletonList(condition);
    }

    private CodeableConcept convertCategory() throws Exception {
        String code = record.get("Typ");
        if (code != null) {
            return new CodeableConcept().setText(code);
        } else {
            throw new Exception("Error on Diagnose: Typ empty for Record: "
                    + record.getRecordNumber() + "!" + record.toString());
        }
    }

    private CodeableConcept convertProcedureCode() throws Exception {
        return new CodeableConcept()
                .addCoding(getCoding())
                .setText(record.get("Bezeichner"));
    }

    private Coding getCoding() throws Exception {
        String code = record.get("ICD");
        if (code != null) {
            return new Coding()
                    .setSystem("http://fhir.de/CodeSystem/dimdi/icd-10-gm")
                    .setCode(code);
        } else {
            throw new Exception("Error on Diagnose: ICD empty for Record: "
                    + record.getRecordNumber() + "!" + record.toString());
        }
    }

    private Reference convertSubject() throws Exception {
        String patientId = record.get("Patient-ID");
        if (patientId != null) {
            return new Reference().setReference("Patient/" + patientId);
        } else {
            throw new Exception("Error on Diagnose: Patient-ID empty for Record: "
                    + record.getRecordNumber() + "!" + record.toString());
        }
    }

    private DateTimeType convertRecordedDate() throws Exception {
        try {
            return DateUtil.parseDateTimeType(record.get("Dokumentationsdatum"));
        } catch (Exception e) {
            throw new Exception("Error on Diagnose: Can not parse Dokumentationsdatum for Record: "
                    + record.getRecordNumber() + "! " + record.toString());
        }
    }
}
