package de.uni_leipzig.life.csv2fhir.converter;

import java.util.Collections;
import java.util.List;
import java.util.Vector;

import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Encounter.DiagnosisComponent;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.utils.DateUtil;

public class VersorgungsfallConverter extends Converter {

    String PROFILE= "https://www.medizininformatik-initiative.de/fhir/core/modul-fall/StructureDefinition/Encounter/Versorgungsfall";
    // https://simplifier.net/medizininformatikinitiative-modulfall/versorgungsfall-duplicate-2
    // https://simplifier.net/medizininformatikinitiative-modulfall/example-versorgungsfall

    /*
     * NotSupported : Terminology service failed while validating code '' (system ''): Cannot retrieve valueset 'https://www.medizininformatik-initiative.de/fhir/core/modul-fall/ValueSet/Versorgungsfallklasse'
     */
    
    /*
     * Schwierigkeit: die Aufnahmediagnosen nicht unbedingt identisch mit der Hauptdiagnosen
     * Diagnose haben typisch keinen verpflichtenden Identifier; hier wird konstruiert...
     *  
     */
   public VersorgungsfallConverter(CSVRecord record) throws Exception {
        super(record);
    }

    @Override
    public List<Resource> convert() throws Exception {
        Encounter encounter = new Encounter();
        encounter.setMeta(new Meta().addProfile(PROFILE));
        encounter.setId(getEncounterId());
        encounter.setIdentifier(convertIdentifier());
        encounter.setStatus(Encounter.EncounterStatus.FINISHED);//TODO
        encounter.setClass_(convertClass());
        encounter.setSubject(getPatientReference());
        encounter.setPeriod(convertPeriod());
    
        // encounter.addReasonCode(convertReasonCode());
        encounter.setDiagnosis(convertDiagnosis());

        return Collections.singletonList(encounter);
    }

    private List<DiagnosisComponent> convertDiagnosis() throws Exception {
        String codes = record.get("Versorgungsfallgrund (Aufnahmediagnose)");
        CodeableConcept c = new CodeableConcept();
        c.addCoding().setCode("AD").setSystem("http://terminology.hl7.org/CodeSystem/diagnosis-role").setDisplay("Admission diagnosis");
        if (codes != null) {
            String codeArr[] = codes.trim().split("\\s*\\+\\s*");
            List<DiagnosisComponent> ld = new Vector<>();
            for (String icd : codeArr) {
                ld.add(new DiagnosisComponent().setUse(c).setCondition(new Reference().setReference("Condition/" + getDiagnoseId(icd))));
            }
            return ld;
        } else {
            warning("Versorgungsfallgrund (Aufnahmediagnose) empty");
            return null;           
        }
    }
    protected Reference getPatientReference() throws Exception {
        String patientId = record.get("Patient-ID");
        if (patientId != null) {
            return new Reference().setReference("Patient/" + patientId);
        } else {
            error("Patient-ID empty");
            return null;
        }
    }

    private List<Identifier> convertIdentifier() throws Exception {
        // generierte Encounternummer
        String id = getEncounterId();

        CodeableConcept d = new CodeableConcept();
        d.addCoding()
        .setCode("VN")
        .setSystem("http://terminology.hl7.org/CodeSystem/v2-0203");
        Reference r = new Reference().setIdentifier(new Identifier()
                .setValue(getDIZId())
                .setSystem("https://www.medizininformatik-initiative.de/fhir/core/NamingSystem/org-identifier"));
        return Collections.singletonList(new Identifier().setValue(id).setSystem("Generated").setAssigner(r).setType(d));

    }

       
    private Coding convertClass() throws Exception {
        String code = record.get("Versorgungsfallklasse");
        if (code != null) {
            return new Coding()
                    .setSystem("https://www.medizininformatik-initiative.de/fhir/core/modul-fall/CodeSystem/Versorgungsfallklasse")
                    .setCode(code);
        } else {
            error("Versorgungsfallklasse empty");
            return null;
        }
    }

    private Period convertPeriod() throws Exception {
        try {
            return new Period()
                    .setStartElement(DateUtil.parseDateTimeType(record.get("Startdatum")))
                    .setEndElement(DateUtil.parseDateTimeType(record.get("Enddatum")));
        } catch (Exception e) {
            error("Can not parse Startdatum or Enddatum");
            return null;
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
