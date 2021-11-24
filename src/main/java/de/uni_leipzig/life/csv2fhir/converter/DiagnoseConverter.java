package de.uni_leipzig.life.csv2fhir.converter;

import static de.uni_leipzig.life.csv2fhir.Converter.EmptyRecordValueErrorLevel.ERROR;
import static de.uni_leipzig.life.csv2fhir.TableIdentifier.Diagnose;
import static de.uni_leipzig.life.csv2fhir.converterFactory.DiagnoseConverterFactory.NeededColumns.Bezeichner;
import static de.uni_leipzig.life.csv2fhir.converterFactory.DiagnoseConverterFactory.NeededColumns.Dokumentationsdatum;
import static de.uni_leipzig.life.csv2fhir.converterFactory.DiagnoseConverterFactory.NeededColumns.ICD;
import static de.uni_leipzig.life.csv2fhir.converterFactory.DiagnoseConverterFactory.NeededColumns.Patient_ID;
import static de.uni_leipzig.life.csv2fhir.converterFactory.DiagnoseConverterFactory.NeededColumns.Typ;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.TableIdentifier;
import de.uni_leipzig.life.csv2fhir.converterFactory.DiagnoseConverterFactory;
import de.uni_leipzig.life.csv2fhir.utils.DateUtil;

public class DiagnoseConverter extends Converter {

    /** Simple counter to generate unique identifier */
    static int n = 1;

    /**  */
    String PROFILE = "https://www.medizininformatik-initiative.de/fhir/core/modul-diagnose/StructureDefinition/Diagnose";
    // https://simplifier.net/medizininformatikinitiative-moduldiagnosen/diagnose

    /**
     * @param record
     * @throws Exception
     */
    public DiagnoseConverter(CSVRecord record) throws Exception {
        super(record);
    }

    @Override
    public List<Resource> convert() throws Exception {
        Condition condition = new Condition();
        // Nicht im Profil, aber notwendig für Fall Aufnahmediagnose
        String id = getDiagnoseId();
        condition.setId(id);
        condition.setIdentifier(Collections.singletonList(new Identifier().setValue(id)));
        condition.setMeta(new Meta().addProfile(PROFILE));
        //        condition.addCategory(convertCategory());
        condition.setCode(convertCode());
        condition.setSubject(getPatientReference());
        condition.setRecordedDateElement(convertRecordedDate());

        //now add an the encounter a reference to this procedure as diagnosis (Yes thats the logic of KDS!?)
        String encounterId = getEncounterId();
        String diagnosisUseIdentifier = record.get(DiagnoseConverterFactory.NeededColumns.Typ);
        VersorgungsfallConverter.addDiagnosisToEncounter(encounterId, condition, diagnosisUseIdentifier);

        return Collections.singletonList(condition);
    }

    @Override
    protected Enum<?> getPatientIDColumnIdentifier() {
        return Diagnose.getPIDColumnIdentifier();
    }

    /**
     * @return
     * @throws Exception
     */
    private String getDiagnoseId() throws Exception {
        String icd = record.get(ICD);
        if (icd == null) {
            error("ICD empty");
            return null;
        }
        return getPatientId() + "-CD-" + n++;
    }

    /**
     * @return
     * @throws Exception
     */
    private CodeableConcept convertCategory() throws Exception {
        String code = record.get(Typ);
        if (code != null) {
            return new CodeableConcept().setText(code);
        }
        warning(Typ + " empty for Record");
        return null;
    }

    /**
     * @return
     * @throws Exception
     */
    private CodeableConcept convertCode() throws Exception {
        //Coding icdCoding = createCoding("http://fhir.de/CodeSystem/dimdi/icd-10-gm", ICD, ERROR); //this should be valid but the Validator only acceps dimdi
        Coding icdCoding = createCoding("http://fhir.de/CodeSystem/dimdi/icd-10-gm", ICD, ERROR);
        if (icdCoding != null) {
            icdCoding.setVersion("2020"); // just to be KDS compatible
            return createCodeableConcept(icdCoding, Bezeichner);
        }
        return null;
    }

    /**
     * @return
     * @throws Exception
     */
    private DateTimeType convertRecordedDate() throws Exception {
        try {
            String date = record.get(Dokumentationsdatum);
            return DateUtil.parseDateTimeType(date);
        } catch (Exception e) {
            //extract a date from an encounter
            String pid = record.get(Patient_ID);
            DateTimeType encounterDate = getEncounterDate(pid);
            if (encounterDate != null) {
                warning("Can not parse " + Dokumentationsdatum + " for Record. Extract date from encounter. " + record);
                return encounterDate;
            }
        }
        error("Can not parse " + Dokumentationsdatum + " for Record");
        return null;
    }

    /**
     * @param pid Patient ID
     * @return all encounters for the patient with the given id
     */
    public static Collection<Encounter> getEncounters(TableIdentifier encounterSource, String pid) {
        Collection<Encounter> result = new ArrayList<>();
        if (pid != null) {
            for (Resource resource : encounterSource.getResources()) {
                if (resource instanceof Encounter) {
                    Encounter encounter = (Encounter) resource;
                    Reference patientReference = encounter.getSubject();
                    String encounterPID = patientReference.getReference();
                    if (encounterPID == null) {
                        continue;
                    }
                    String basePID = getBaseId(pid);
                    String baseEncounterPID = getBaseId(encounterPID);
                    if (baseEncounterPID.equals(basePID)) {
                        result.add(encounter);
                    }
                }
            }
        }
        return result;
    }

    /**
     * @param pid patient ID
     * @return an extracteda date from one encounter of the corresponding
     *         patient with the given id
     */
    public static DateTimeType getEncounterDate(String pid) {
        Collection<Encounter> mainEncounters = getEncounters(TableIdentifier.Versorgungsfall, pid);
        DateTimeType encounterDate = getEncounterDate(mainEncounters);
        Collection<Encounter> subEncounters = null;
        if (encounterDate == null) {
            subEncounters = getEncounters(TableIdentifier.Abteilungsfall, pid);
            encounterDate = getEncounterDate(subEncounters);
        }
        return encounterDate;
    }

    /**
     * @param encounters
     * @return the first date entry found in one of the encounters
     */
    public static DateTimeType getEncounterDate(Collection<Encounter> encounters) {
        for (Encounter encounter : encounters) {
            Period period = encounter.getPeriod();
            if (period != null) {
                DateTimeType date = period.getStartElement(); //must exists to be valid!
                if (date != null) {
                    return date;
                }
            }
        }
        return null;
    }

}
