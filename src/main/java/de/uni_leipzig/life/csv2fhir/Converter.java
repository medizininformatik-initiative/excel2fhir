package de.uni_leipzig.life.csv2fhir;

import java.util.List;

import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

import de.uni_leipzig.life.csv2fhir.utils.Sys;

/**
 * @author fheuschkel (02.11.2020)
 */
public abstract class Converter {

    /**  */
    final String pid;

    /**  */
    protected final CSVRecord record;

    /**  */
    protected boolean kds = true;

    /**  */
    protected boolean kds_strict = true;

    /**
     * @param record
     * @throws Exception
     */
    public Converter(CSVRecord record) throws Exception {
        this.record = record;
        pid = parsePatientId();
    }

    /**  */
    public abstract List<Resource> convert() throws Exception;

    /**
     * @param msg
     * @throws Exception
     */
    protected void error(String msg) throws Exception {
        throw new Exception("Error on " + getErrorMessageBody(msg));
    }

    /**
     * @param msg
     */
    protected void warning(String msg) {
        Sys.out1("Warning on " + getErrorMessageBody(msg));
    }

    /**
     * @param msg
     * @return
     */
    protected String getErrorMessageBody(String msg) {
        return getClass().getSimpleName().replaceFirst("Converter", "") + ": " + msg + ":" + record.getRecordNumber() + "! "
                + record.toString();
    }

    /**
     * @return
     */
    public String getPatientId() {
        return pid;
    }

    /**
     * @return
     * @throws Exception
     */
    private String parsePatientId() throws Exception {
        String id = record.get("Patient-ID");
        if (id != null) {
            return id.replace("_", "-");
        }
        error("Patient-ID empty for Record");
        return null;
    }

    /**
     * @return
     * @throws Exception
     */
    protected Reference getPatientReference() throws Exception {
        return new Reference().setReference("Patient/" + getPatientId());
    }

    /**
     * @return
     * @throws Exception
     */
    protected String getDIZId() throws Exception {
        return getPatientId().toUpperCase().replaceAll("[^A-Z]", "");
    }

    /**
     * @return
     * @throws Exception
     */
    protected String getEncounterId() throws Exception {
        return getPatientId() + "-E-1";
    }

    /**
     * @return
     * @throws Exception
     */
    protected Reference getEncounterReference() throws Exception {
        return new Reference().setReference("Encounter/" + getEncounterId());
    }

    /**
     * @param icd
     * @return
     * @throws Exception
     */
    protected String getDiagnoseId(String icd) throws Exception {
        String id;
        if (icd == null) {
            error("ICD empty");
            return null;
        }
        id = icd;
        return getPatientId() + "-C-" + id.hashCode();
    }

}
