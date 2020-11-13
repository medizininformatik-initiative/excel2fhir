package de.uni_leipzig.life.csv2fhir.converter;

import java.util.Collections;
import java.util.List;

import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Resource;

import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.utils.DateUtil;

public class DiagnoseConverter extends Converter {

    String PROFILE="https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose";
    // https://simplifier.net/medizininformatikinitiative-moduldiagnosen/diagnose

    public DiagnoseConverter(CSVRecord record) {
        super(record);
    }

    @Override
    public List<Resource> convert() throws Exception {
        Condition condition = new Condition();
        // Nicht im Profil, aber notwendig für Fall Aufnahmediagnose 
        condition.setId(getDiagnoseId());
        condition.setIdentifier(Collections.singletonList(new Identifier().setValue(getDiagnoseId())));
        condition.setMeta(new Meta().addProfile(PROFILE));
        condition.addCategory(convertCategory());
        condition.setCode(convertProcedureCode());
        condition.setSubject(convertSubject());
        condition.setRecordedDateElement(convertRecordedDate());
        return Collections.singletonList(condition);
    }

    private String getDiagnoseId() throws Exception {
        return getDiagnoseId(record.get("ICD"));
    }
    private CodeableConcept convertCategory() throws Exception {
        String code = record.get("Typ");
        if (code != null) {
            return new CodeableConcept().setText(code);
        } else {
            error("Typ empty for Record");                  
            return null;
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
                    .setVersion("2020")     // just to be KDS compatible
                    .setCode(code);

        } else {
            error("ICD empty for Record");
            return null;
        }
    }

    private DateTimeType convertRecordedDate() throws Exception {
        try {
            return DateUtil.parseDateTimeType(record.get("Dokumentationsdatum"));
        } catch (Exception e) {
            error("Can not parse Dokumentationsdatum for Record");
            return null;
        }
    }
}
