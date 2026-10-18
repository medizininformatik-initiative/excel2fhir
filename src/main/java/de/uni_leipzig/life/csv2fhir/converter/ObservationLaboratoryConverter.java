package de.uni_leipzig.life.csv2fhir.converter;

import static de.uni_leipzig.life.csv2fhir.ConverterOptions.IntOption.START_ID_OBSERVATION_LABORATORY;
import static de.uni_leipzig.life.csv2fhir.TableIdentifier.Laborbefund;
import static de.uni_leipzig.life.csv2fhir.converter.ObservationLaboratoryConverter.ObservationLaboratory_Columns.Einheit;
import static de.uni_leipzig.life.csv2fhir.converter.ObservationLaboratoryConverter.ObservationLaboratory_Columns.LOINC;
import static de.uni_leipzig.life.csv2fhir.converter.ObservationLaboratoryConverter.ObservationLaboratory_Columns.Messwert;
import static de.uni_leipzig.life.csv2fhir.converter.ObservationLaboratoryConverter.ObservationLaboratory_Columns.Parameter;
import static de.uni_leipzig.life.csv2fhir.converter.ObservationLaboratoryConverter.ObservationLaboratory_Columns.Zeitstempel_Abnahme;
import static de.uni_leipzig.life.csv2fhir.utils.DecimalUtil.parseComparator;
import static de.uni_leipzig.life.csv2fhir.utils.DecimalUtil.parseDecimal;

import java.util.Collections;
import java.util.List;

import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Resource;


import de.uni_leipzig.imise.validate.FHIRValidator;
import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.ConverterOptions;
import de.uni_leipzig.life.csv2fhir.ConverterResult;
import de.uni_leipzig.life.csv2fhir.TableColumnIdentifier;

/**
 * @author jheuschkel (19.10.2020), AXS (05.11.2021)
 */
public class ObservationLaboratoryConverter extends Converter {

    /**
     * toString() result of these enum values are the names of the columns in the
     * correspunding excel sheet.
     */
    public static enum ObservationLaboratory_Columns implements TableColumnIdentifier {
        LOINC,
        Parameter,
        Messwert,
        Einheit,
        Zeitstempel_Abnahme {
            @Override
            public String toString() {
                return "Zeitstempel (Abnahme)";
            }
        },
    }

    /**  */
    static final String PROFILE = "https://www.medizininformatik-initiative.de/fhir/core/modul-labor/StructureDefinition/ObservationLab";
    // https://simplifier.net/medizininformatikinitiative-modullabor/observationlab

    /**
     * @param record
     * @param previousRecordPID
     * @param result
     * @param validator
     * @param options
     * @throws Exception
     */
    public ObservationLaboratoryConverter(CSVRecord record, String previousRecordPID, ConverterResult result,
            FHIRValidator validator, ConverterOptions options) throws Exception {
        super(record, previousRecordPID, result, validator, options);
    }

    @Override
    protected List<Resource> convertInternal() throws Exception {
        return convertObservation(false);
    }

    protected List<Resource> convertObservation(boolean clinical) throws Exception {
        var table = clinical ? de.uni_leipzig.life.csv2fhir.TableIdentifier.Klinische_Dokumentation : Laborbefund;
        Enum<?> codeColumn = clinical ? ObservationVitalSignsConverter.ObservationVitalSigns_Columns.Untersuchungscode : LOINC;
        Enum<?> textColumn = clinical ? ObservationVitalSignsConverter.ObservationVitalSigns_Columns.Bezeichner : Parameter;
        Enum<?> valueColumn = clinical ? ObservationVitalSignsConverter.ObservationVitalSigns_Columns.Wert : Messwert;
        Enum<?> unitColumn = clinical ? ObservationVitalSignsConverter.ObservationVitalSigns_Columns.Einheit : Einheit;
        Enum<?> dateColumn = clinical ? ObservationVitalSignsConverter.ObservationVitalSigns_Columns.Zeitstempel : Zeitstempel_Abnahme;
        String category = ClinicalValues.get(this, ClinicalValues.Column.Kategorie);
        String sourceId = ClinicalValues.get(this, ClinicalValues.Column.Untersuchung_ID);
        String id = sourceId == null ? (getEncounterReference() == null ? getPatientId() : getEncounterId())
                + (clinical ? ResourceIdSuffix.OBSERVATION_VITALSIGNS : ResourceIdSuffix.OBSERVATION_LABORATORY)
                + result.getNextId(table, Observation.class, clinical
                    ? de.uni_leipzig.life.csv2fhir.ConverterOptions.IntOption.START_ID_OBSERVATION_VITAL_SIGNS
                    : START_ID_OBSERVATION_LABORATORY) : ClinicalValues.resourceId(getPatientId(), "Observation", sourceId);
        String system = ClinicalValues.get(this, ClinicalValues.Column.Codesystem);
        CodeableConcept code = ClinicalValues.concept(get(codeColumn), system, get(textColumn));
        Coding additionalCode = ClinicalValues.coding(ClinicalValues.get(this, ClinicalValues.Column.Zusatzcode),
                ClinicalValues.get(this, ClinicalValues.Column.Zusatzcodesystem));
        if (additionalCode != null && code.getCoding().stream().noneMatch(c -> c.equalsDeep(additionalCode))) {
            code.addCoding(additionalCode);
        }
        String kind = ClinicalValues.get(this, ClinicalValues.Column.Werttyp);
        String raw = get(valueColumn);
        org.hl7.fhir.r4.model.Type value = null;
        if (kind == null || kind.equals("Zahl")) {
            Quantity quantity = parseObservationValue(valueColumn, unitColumn);
            if (!quantity.isEmpty()) value = quantity;
        } else if (kind.equals("Text")) {
            if (raw != null && !raw.isBlank()) {
                value = new org.hl7.fhir.r4.model.StringType(raw);
            }
        } else if (kind.equals("Code")) {
            value = ClinicalValues.concept(ClinicalValues.get(this, ClinicalValues.Column.Wertcode),
                    ClinicalValues.get(this, ClinicalValues.Column.Wertcodesystem), raw);
        } else if (kind.equals("Ja/Nein")) {
            if (raw != null && !raw.isBlank()) {
                if (!"true".equals(raw) && !"false".equals(raw)) throw new IllegalArgumentException("Boolean must be true or false");
                value = new org.hl7.fhir.r4.model.BooleanType(Boolean.parseBoolean(raw));
            }
        } else if (!kind.equals("Komponenten") && !kind.equals("Fehlend")) {
            throw new IllegalArgumentException("Unknown value type: " + kind);
        }
        CodeableConcept absent = null;
        if ("Fehlend".equals(kind) && raw != null && !raw.isBlank()) {
            org.hl7.fhir.r4.model.Extension extension = DiagnosisValues.absentReason(raw);
            if (extension == null) throw new IllegalArgumentException("Missing explicit data absent reason");
            absent = new CodeableConcept(new Coding("http://terminology.hl7.org/CodeSystem/data-absent-reason",
                    extension.getValue().primitiveValue(), null));
        }
        String parentId = ClinicalValues.get(this, ClinicalValues.Column.Komponente_von);
        if (parentId != null) {
            Observation parent = result.get(table, Observation.class, ClinicalValues.resourceId(getPatientId(), "Observation", parentId));
            if (parent == null) throw new IllegalArgumentException("Component parent must precede component: " + parentId);
            parent.addComponent().setCode(code).setValue(value).setDataAbsentReason(absent);
            return Collections.emptyList();
        }
        Observation observation = new Observation();
        observation.setId(id);
        observation.setSubject(getPatientReference());
        observation.setEncounter(getEncounterReference());
        if (category != null) observation.addCategory(ClinicalValues.concept(category,
                "http://terminology.hl7.org/CodeSystem/observation-category", null));
        if ("laboratory".equals(category)) {
            observation.setMeta(new Meta().addProfile(PROFILE));
        }
        String status = ClinicalValues.get(this, ClinicalValues.Column.Status);
        observation.setStatusElement(InputCode.of(observation.getStatusElement(), status));
        observation.setEffective(ClinicalValues.date(get(dateColumn)));
        String issued = ClinicalValues.get(this, ClinicalValues.Column.Ausgabezeitpunkt);
        if (issued != null) observation.setIssuedElement(ClinicalValues.instant(issued));
        observation.setCode(code);
        observation.setValue(value);
        observation.setDataAbsentReason(absent);
        return Collections.singletonList(observation);
    }

    /**
     * @param valueColumnIdentifier
     * @param unitColumnIdentifier
     * @return
     * @throws Exception
     */
    public Quantity parseObservationValue(Enum<?> valueColumnIdentifier, Enum<?> unitColumnIdentifier)
            throws Exception {
        Quantity quantity = new Quantity();
        String valueString = get(valueColumnIdentifier);
        if (valueString != null && !valueString.isBlank()) {
            String comparator = parseComparator(valueString);
            if (comparator != null) {
                quantity.setComparator(Quantity.QuantityComparator.fromCode(comparator));
                valueString = valueString.trim().substring(comparator.length());
            }
            quantity.setValue(parseDecimal(valueString));
        }
        String unit = get(unitColumnIdentifier);
        if (unit != null && !unit.isBlank()) quantity.setUnit(unit);
        quantity.setCode(ClinicalValues.get(this, ClinicalValues.Column.Einheitencode));
        quantity.setSystem(ClinicalValues.get(this, ClinicalValues.Column.Einheitencodesystem));
        return quantity;
    }

}
