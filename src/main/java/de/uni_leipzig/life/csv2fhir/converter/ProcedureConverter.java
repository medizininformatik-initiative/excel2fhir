package de.uni_leipzig.life.csv2fhir.converter;

import static de.uni_leipzig.life.csv2fhir.BundleFunctions.getEncounterDate;
import static de.uni_leipzig.life.csv2fhir.ConverterOptions.BooleanOption.SET_REFERENCE_FROM_ENCOUNTER_TO_PROCEDURE_CONDITION;
import static de.uni_leipzig.life.csv2fhir.ConverterOptions.BooleanOption.SET_REFERENCE_FROM_PROCEDURE_CONDITION_TO_ENCOUNTER;
import static de.uni_leipzig.life.csv2fhir.ConverterOptions.IntOption.START_ID_PROCEDURE;
import static de.uni_leipzig.life.csv2fhir.TableIdentifier.Prozedur;
import static de.uni_leipzig.life.csv2fhir.converter.ProcedureConverter.Procedure_Columns.Durchführungsbeginn;
import static de.uni_leipzig.life.csv2fhir.converter.ProcedureConverter.Procedure_Columns.Prozedurencode;
import static de.uni_leipzig.life.csv2fhir.converter.ProcedureConverter.Procedure_Columns.Prozedurentext;
import static java.util.Collections.singletonList;
import static org.apache.logging.log4j.util.Strings.isBlank;

import java.util.Collections;
import java.util.List;

import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Procedure;
import org.hl7.fhir.r4.model.Resource;

import de.uni_leipzig.imise.validate.FHIRValidator;
import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.ConverterOptions;
import de.uni_leipzig.life.csv2fhir.ConverterResult;
import de.uni_leipzig.life.csv2fhir.TableColumnIdentifier;
import de.uni_leipzig.life.csv2fhir.utils.TerminologyVersionUtil;

/**
 * @author jheuschkel (19.10.2020), AXS (05.11.2021)
 */
public class ProcedureConverter extends Converter {

    /**
     * toString() result of these enum values are the names of the columns in the
     * correspunding excel sheet.
     */
    public static enum Procedure_Columns implements TableColumnIdentifier {
        Prozedurentext,
        Prozedurencode,
        Durchführungsbeginn,
    }

    /**  */
    String PROFILE = "https://www.medizininformatik-initiative.de/fhir/core/modul-prozedur/StructureDefinition/Procedure";
    // https://simplifier.net/medizininformatikinitiative-modulprozeduren/prozedur

    /**
     * @param record
     * @param previousRecordPID
     * @param result
     * @param validator
     * @param options
     * @throws Exception
     */
    public ProcedureConverter(CSVRecord record, String previousRecordPID, ConverterResult result,
            FHIRValidator validator, ConverterOptions options) throws Exception {
        super(record, previousRecordPID, result, validator, options);
    }

    @Override
    protected List<Resource> convertInternal() throws Exception {
        Procedure procedure = new Procedure();
        int nextId = result.getNextId(Prozedur, Procedure.class, START_ID_PROCEDURE);
        String encounterId = getEncounterId();
        String id = (isBlank(encounterId) ? getPatientId() : encounterId) + ResourceIdSuffix.PROCEDURE + nextId;
        procedure.setId(id);
        procedure.setMeta(new Meta().addProfile(PROFILE));
        // procedure.addExtension(new Extension()
        // .setUrl("https://www.medizininformatik-initiative.de/fhir/core/modul-prozedur/StructureDefinition/procedure-recordedDate")
        // .setValue(convertRecordedDate()));
        var start = ClinicalValues.date(get(Durchführungsbeginn));
        var end = ClinicalValues.date(ClinicalValues.get(this, ClinicalValues.Column.Ende));
        procedure.setPerformed(end == null ? start : new org.hl7.fhir.r4.model.Period().setStartElement(start).setEndElement(end));
        String status = ClinicalValues.get(this, ClinicalValues.Column.Status);
        procedure.setStatus(status == null ? Procedure.ProcedureStatus.COMPLETED : Procedure.ProcedureStatus.fromCode(status));
        Coding category = convertSnomedCategory();
        if (category != null) procedure.setCategory(new CodeableConcept(category));
        procedure.setCode(convertProcedureCode());
        procedure.setSubject(getPatientReference());

        // enable this to get the reference from condition to encounter. This is
        // optional
        // but it creates a circle, because the encounter has also a reference list to
        // all
        // diagnosis. This is false by default.
        ConverterOptions converterOptions = result.getConverterOptions();
        if (converterOptions.is(SET_REFERENCE_FROM_PROCEDURE_CONDITION_TO_ENCOUNTER)) {
            procedure.setEncounter(getEncounterReference());
        }

        if (converterOptions.is(SET_REFERENCE_FROM_ENCOUNTER_TO_PROCEDURE_CONDITION)) { // default is true
            // now add an the encounter a reference to this procedure as diagnosis (Yes
            // thats the logic of KDS!?)
            if (!isBlank(encounterId)) {
                EncounterConverter.addDiagnosisToEncounter(result, encounterId, procedure);
            }
        }
        return singletonList(procedure);
    }

    /**
     * @return
     * @throws Exception
     */
    private CodeableConcept convertProcedureCode() throws Exception {
        String selection = ClinicalValues.get(this, ClinicalValues.Column.Codesystem);
        if (selection != null) {
            CodeableConcept code = ClinicalValues.concept(get(Prozedurencode), selection, get(Prozedurentext));
            Coding extra = ClinicalValues.coding(ClinicalValues.get(this, ClinicalValues.Column.Zusatzcode),
                    ClinicalValues.get(this, ClinicalValues.Column.Zusatzcodesystem));
            if (extra != null) code.addCoding(extra);
            return code;
        }
        Coding procedureCoding = createCoding("http://fhir.de/CodeSystem/bfarm/ops", Prozedurencode);
        if (procedureCoding != null) {
            procedureCoding.setVersion(TerminologyVersionUtil
                    .getOpsVersion(getEncounterDate(result, getPatientId(), getEncounterId())));
            return createCodeableConcept(procedureCoding, Prozedurentext);
        }
        return null;
    }

    /**
     * @return
     * @throws Exception
     */
    private Coding convertSnomedCategory() throws Exception {
        String explicit = ClinicalValues.get(this, ClinicalValues.Column.Kategorie);
        if (explicit != null) return ClinicalValues.coding(explicit, DiagnosisValues.SNOMED);
        String selection = ClinicalValues.get(this, ClinicalValues.Column.Codesystem);
        if (selection != null && !selection.startsWith("OPS ")) return null;
        String code = get(Prozedurencode);
        String display = null;
        if (code != null && !code.isBlank()) {
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
                return null;
            }
        }
        return createCoding("http://snomed.info/sct", code, display);
    }

}
