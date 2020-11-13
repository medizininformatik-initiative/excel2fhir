package de.uni_leipzig.life.csv2fhir;


import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

import java.util.List;

public abstract class Converter {

    protected final CSVRecord record;
    
    public Converter(CSVRecord record) {
        this.record = record;
    }
    public abstract List<Resource> convert() throws Exception;

    protected void error(String msg) throws Exception {
        throw new Exception("Error on " + this.getClass().getSimpleName().replaceFirst("Converter", "") + ": " + msg + ":"
                + record.getRecordNumber() + "! " + record.toString());     
    }
    
    protected void warning(String msg) {
        System.out.println("Warning on " + this.getClass().getSimpleName().replaceFirst("Converter", "") + ": " + msg + ":"
                + record.getRecordNumber() + "! " + record.toString());     
    }   
    
    protected String parsePatientId() throws Exception {
        String id = record.get("Patient-ID");
        if (id != null) {
            return id;
        } else {
            error("Patient-ID empty for Record");
            return null;
        }
    }

    protected Reference convertSubject() throws Exception {
        String patientId = record.get("Patient-ID");
        if (patientId != null) {
            return new Reference().setReference("Patient/" + patientId);
        } else {
            error("Patient-ID empty for Record");
            return null;
        }
    }

    protected String getDIZId() throws Exception {
        return parsePatientId().replaceAll("[^A-Z]", "");
    }
    protected String getEncounterId() throws Exception {
        return parsePatientId() + "E-1";
    }
    protected Reference getEncounterReference() throws Exception {
        return new Reference().setReference("Encounter/" + getEncounterId());
    }
    protected String getDiagnoseId(String icd) throws Exception {
        String id;
        if (icd != null) {
            id = icd;
        } else {
            error("ICD empty");
            return null;
        }
        return parsePatientId() + "-C-" + id.hashCode();
    }

}
