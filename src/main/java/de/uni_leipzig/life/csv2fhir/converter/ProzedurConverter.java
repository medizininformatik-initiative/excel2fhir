package de.uni_leipzig.life.csv2fhir.converter;

import static de.uni_leipzig.life.csv2fhir.Converter.EmptyRecordValueErrorLevel.ERROR;
import static de.uni_leipzig.life.csv2fhir.converterFactory.ProzedurConverterFactory.NeededColumns.Dokumentationsdatum;
import static de.uni_leipzig.life.csv2fhir.converterFactory.ProzedurConverterFactory.NeededColumns.Patient_ID;
import static de.uni_leipzig.life.csv2fhir.converterFactory.ProzedurConverterFactory.NeededColumns.Prozedurencode;
import static de.uni_leipzig.life.csv2fhir.converterFactory.ProzedurConverterFactory.NeededColumns.Prozedurentext;

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

    /**  */
    String PROFILE = "https://www.medizininformatik-initiative.de/fhir/core/modul-prozedur/StructureDefinition/Procedure";
    // https://simplifier.net/medizininformatikinitiative-modulprozeduren/prozedur

    /**
     * @param record
     * @throws Exception
     */
    public ProzedurConverter(CSVRecord record) throws Exception {
        super(record);
    }

    /** Simple counter to generate unique identifier */
    static int n = 1;

    @Override
    public List<Resource> convert() throws Exception {
        Procedure procedure = new Procedure();
        procedure.setId(getEncounterId() + "-P-" + n++);
        //        procedure.addExtension(new Extension()
        //                .setUrl("https://www.medizininformatik-initiative.de/fhir/core/modul-prozedur/StructureDefinition/procedure-recordedDate")
        //                .setValue(convertRecordedDate()));
        procedure.setPerformed(convertRecordedDate());
        procedure.setStatus(Procedure.ProcedureStatus.COMPLETED);
        procedure.setCategory(new CodeableConcept(convertSnomedCategory()));
        procedure.setCode(convertProcedureCode());
        procedure.setSubject(getPatientReference());
        procedure.setEncounter(getEncounterReference());
        if (kds) {
            procedure.setMeta(new Meta().addProfile(PROFILE));
        } else if (kds_strict) {
            return null;
        }
        return Collections.singletonList(procedure);
    }

    @Override
    protected Enum<?> getPatientIDColumnIdentifier() {
        return Patient_ID;
    }

    /**
     * @return
     * @throws Exception
     */
    private DateTimeType convertRecordedDate() throws Exception {
        try {
            return DateUtil.parseDateTimeType(record.get(Dokumentationsdatum));
        } catch (Exception e) {
            error("Can not parse Dokumentationsdatum for Record");
            return null;
        }
    }

    /**
     * @return
     * @throws Exception
     */
    private CodeableConcept convertProcedureCode() throws Exception {
        Coding procedureCoding = createCoding("http://fhir.de/CodeSystem/bfarm/ops", Prozedurencode, ERROR);
        if (procedureCoding != null) {
            procedureCoding.setVersion("2020"); // just to be KDS compatible
            return createCodeableConcept(procedureCoding, Prozedurentext);
        }
        return null;
    }

    /**
     * @return
     * @throws Exception
     */
    private Coding convertSnomedCategory() throws Exception {
        String code = record.get(Prozedurencode);
        if (code != null) {
            String display;
            switch (code.charAt(0)) {
            case '1':
                code = "103693007";
                display = "Diagnostic procedure";
                break;
            case '3':
                code = "363679005";
                display = "Imaging";
                break;
            case '5':
                code = "387713003";
                display = "Surgical procedure";
                break;
            case '6':
                code = "18629005";
                display = "Administration of medicine";
                break;
            case '8':
                code = "277132007";
                display = "Therapeutic procedure";
                break;
            case '9':
                code = "394841004";
                display = "Other category";
                break;
            default:
                kds = false;
                return null;
            }
            return createCoding("http://snomed.info/sct", code, display);
        }
        kds = false;
        return null;
    }

}
