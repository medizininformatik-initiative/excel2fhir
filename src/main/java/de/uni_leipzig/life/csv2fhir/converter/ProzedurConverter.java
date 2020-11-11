package de.uni_leipzig.life.csv2fhir.converter;

import java.util.Collections;
import java.util.List;

import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Procedure;
import org.hl7.fhir.r4.model.Resource;

import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.utils.DateUtil;

public class ProzedurConverter extends Converter {

    String PROFILE= "https://www.medizininformatik-initiative.de/fhir/core/modul-prozedur/StructureDefinition/Procedure";
    // https://simplifier.net/medizininformatikinitiative-modulprozeduren/prozedur  

    public ProzedurConverter(CSVRecord record) {
        super(record);
    }

    @Override
    public List<Resource> convert() throws Exception {
        Procedure procedure = new Procedure();
        procedure.setMeta(new Meta().addProfile(PROFILE));
//        procedure.addExtension(new Extension()
//                .setUrl("https://www.medizininformatik-initiative.de/fhir/core/modul-prozedur/StructureDefinition/procedure-recordedDate")
//                .setValue(convertRecordedDate()));
        procedure.setPerformed(convertRecordedDate());
        procedure.setStatus(Procedure.ProcedureStatus.COMPLETED);
        procedure.setCode(convertProcedureCode());
        procedure.setSubject(convertSubject());
        return Collections.singletonList(procedure);
    }

    private DateTimeType convertRecordedDate() throws Exception {
        try {
            return DateUtil.parseDateTimeType(record.get("Dokumentationsdatum"));
        } catch (Exception e) {
            error("Can not parse Dokumentationsdatum for Record");
            return null;
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
                    .setSystem("http://fhir.de/CodeSystem/dimdi/ops")
                    .setVersion("2020")     // just to be KDS compatible
                    .setCode(code);
        } else {
            error("Prozedurencode empty for Record");
            return null;
        }
    }


}

