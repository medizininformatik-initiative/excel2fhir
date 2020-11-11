package de.uni_leipzig.life.csv2fhir.converter;

import java.util.Collections;
import java.util.List;

import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Resource;

import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.utils.DateUtil;

    
public class AbteilungsfallConverter extends Converter {

    String PROFILE= "https://www.medizininformatik-initiative.de/fhir/core/modul-fall/StructureDefinition/Encounter/Abteilungsfall";
    // https://simplifier.net/medizininformatikinitiative-modulfall/abteilungsfall-duplicate-2
    // https://simplifier.net/medizininformatikinitiative-modulfall/example-abteilungsfall
    
    /*
     * NotSupported : Terminology service failed while validating code '' (system ''): Cannot retrieve valueset 'https://www.medizininformatik-initiative.de/fhir/core/modul-fall/ValueSet/Abteilungsfallklasse'
     * Invalid : Instance count for 'Encounter.serviceType.coding:fab' is 0, which is not within the specified cardinality of 1..1
     * Invalid : Instance count for 'Encounter.location' is 0, which is not within the specified cardinality of 1..*
     */
    public AbteilungsfallConverter(CSVRecord record) {
        super(record);
    }

    @Override
    public List<Resource> convert() throws Exception {
        Encounter encounter = new Encounter();  
        encounter.setMeta(new Meta().addProfile(PROFILE));
   
        encounter.setStatus(Encounter.EncounterStatus.FINISHED);
        encounter.setClass_(convertClass_());   
        encounter.setServiceType(convertServiceType());
        encounter.setSubject(convertSubject());
        encounter.setPeriod(convertPeriod());
        encounter.setPartOf(getEncounterReference());
        
        //TODO encounter.setLocation(theLocation)
        return Collections.singletonList(encounter);
    }

    private Coding convertClass_() {
        return new Coding().setCode("ub").setSystem("https://www.medizininformatik-initiative.de/fhir/core/modul-fall/CodeSystem/Abteilungsfallklasse");
    }
    
    private CodeableConcept convertServiceType() throws Exception {
        String code = record.get("Fachabteilung");
        if (code != null) {
            return new CodeableConcept().addCoding(new Coding()
                    .setSystem("https://www.medizininformatik-initiative.de/fhir/core/CodeSystem/Fachabteilungsschluessel")
                    .setCode(code))
                    .setText(code);
        } else {
            error("Fachabteilung empty for Record");
            return null;
        }
    }

    private Period convertPeriod() throws Exception {
        try {
            return new Period()
                    .setStartElement(DateUtil.parseDateTimeType(record.get("Startdatum")))
                    .setEndElement(DateUtil.parseDateTimeType(record.get("Enddatum")));
        } catch (Exception e) {
            error("Can not parse Startdatum or Enddatum for Record");
            return null;
        }
    }
}
