package de.uni_leipzig.life.csv2fhir;

import java.util.List;

import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

import de.uni_leipzig.imise.utils.Sys;

/**
 * @author fheuschkel (02.11.2020)
 */
public abstract class Converter {

    /**  */
    final String pid;

    /**  */
    final String dizID;

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
        dizID = pid.toUpperCase().replaceAll("[^A-Z]", "");
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
        Sys.outm(1, 1, "Warning on " + getErrorMessageBody(msg));
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
        return createReference(Patient.class, getPatientId());
    }

    /**
     * @return
     * @throws Exception
     */
    protected String getDIZId() throws Exception {
        return dizID;
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
        return createReference(Encounter.class, getEncounterId());
    }

    /**
     * @param resourceClass
     * @param idBase
     */
    public static Reference createReference(Class<? extends Resource> resourceClass, String idBase) {
        return new Reference().setReference(resourceClass.getSimpleName() + "/" + idBase);
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
