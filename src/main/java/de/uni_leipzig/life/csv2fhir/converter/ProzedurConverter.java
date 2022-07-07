package de.uni_leipzig.life.csv2fhir.converter;

import static de.uni_leipzig.life.csv2fhir.TableIdentifier.Prozedur;
import static de.uni_leipzig.life.csv2fhir.converterFactory.ProcedureConverterFactory.Procedure_Columns.Dokumentationsdatum;
import static de.uni_leipzig.life.csv2fhir.converterFactory.ProcedureConverterFactory.Procedure_Columns.Prozedurencode;
import static de.uni_leipzig.life.csv2fhir.converterFactory.ProcedureConverterFactory.Procedure_Columns.Prozedurentext;
import static java.util.Collections.singletonList;

import java.util.Collections;
import java.util.List;

import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Procedure;
import org.hl7.fhir.r4.model.Resource;

import de.uni_leipzig.imise.FHIRValidator;
import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.ConverterResult;
import de.uni_leipzig.life.csv2fhir.TableColumnIdentifier;
import de.uni_leipzig.life.csv2fhir.converterFactory.ProcedureConverterFactory.Procedure_Columns;

public class ProzedurConverter extends Converter {

    /**  */
    String PROFILE = "https://www.medizininformatik-initiative.de/fhir/core/modul-prozedur/StructureDefinition/Procedure";
    // https://simplifier.net/medizininformatikinitiative-modulprozeduren/prozedur

    /**
     * @param record
     * @param result
     * @param validator
     * @throws Exception
     */
    public ProzedurConverter(CSVRecord record, ConverterResult result, FHIRValidator validator) throws Exception {
        super(record, result, validator);
    }

    @Override
    public List<Resource> convert() throws Exception {
        Procedure procedure = new Procedure();
        int nextId = result.getNextId(Prozedur, Procedure.class);
        procedure.setId(getEncounterId() + "-P-" + nextId);
        //        procedure.addExtension(new Extension()
        //                .setUrl("https://www.medizininformatik-initiative.de/fhir/core/modul-prozedur/StructureDefinition/procedure-recordedDate")
        //                .setValue(convertRecordedDate()));
        procedure.setPerformed(parseDateTimeType(Dokumentationsdatum));
        procedure.setStatus(Procedure.ProcedureStatus.COMPLETED);
        procedure.setCategory(new CodeableConcept(convertSnomedCategory()));
        procedure.setCode(convertProcedureCode());
        procedure.setSubject(getPatientReference());

        if (kds) {
            procedure.setMeta(new Meta().addProfile(PROFILE));
        } else if (kds_strict) {
            return Collections.emptyList();
        }

        if (!isValid(procedure)) {
            return Collections.emptyList();
        }
        //now add an the encounter a reference to this procedure as diagnosis (Yes thats the logic of KDS!?)
        String encounterId = getEncounterId();
        EncounterLevel1Converter.addDiagnosisToEncounter(result, encounterId, procedure);

        return singletonList(procedure);
    }

    @Override
    protected Enum<?> getPatientIDColumnIdentifier() {
        return Prozedur.getPIDColumnIdentifier();
    }

    /**
     * @return
     * @throws Exception
     */
    private CodeableConcept convertProcedureCode() throws Exception {
        Coding procedureCoding = createCoding("http://fhir.de/CodeSystem/bfarm/ops", Prozedurencode);
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
        String code = get(Prozedurencode);
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

    @Override
    protected TableColumnIdentifier getMainEncounterNumberColumnIdentifier() {
        return Procedure_Columns.Versorgungsfall_Nr;
    }

}
