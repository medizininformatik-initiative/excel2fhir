package de.uni_leipzig.life.csv2fhir;


import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

import java.util.List;

public abstract class Converter {
    final String pid;
    protected final CSVRecord record;
    protected boolean kds = true;
    protected boolean kds_strict = true;
    
    public Converter(CSVRecord record) throws Exception {
        this.record = record;
        pid = parsePatientId();
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
    
    public String getPatientId() {
        return pid;
    }
    private String parsePatientId() throws Exception {
        String id = record.get("Patient-ID");
        if (id != null) {
            return id.replace("_", "-");
        } else {
            error("Patient-ID empty for Record");
            return null;
        }
    }

    protected Reference getPatientReference() throws Exception {
        return new Reference().setReference("Patient/" + getPatientId());
    } 

    protected String getDIZId() throws Exception {
        return getPatientId().toUpperCase().replaceAll("[^A-Z]", "");
    }
    protected String getEncounterId() throws Exception {
        return getPatientId() + "-E-1";
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
        return getPatientId() + "-C-" + id.hashCode();
    }

}
