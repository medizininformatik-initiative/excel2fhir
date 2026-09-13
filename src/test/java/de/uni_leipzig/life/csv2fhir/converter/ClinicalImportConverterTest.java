package de.uni_leipzig.life.csv2fhir.converter;

import static org.junit.Assert.*;
import java.io.*;
import java.util.*;
import org.apache.commons.csv.*;
import org.hl7.fhir.r4.model.*;
import org.junit.Test;
import de.uni_leipzig.life.csv2fhir.*;

public class ClinicalImportConverterTest {
    @Test public void laboratoryHasBothRequiredCategoryCodings() throws Exception {
        ConverterOptions options = new ConverterOptions("");
        Observation observation = (Observation)new ObservationLaboratoryConverter(
                row(Map.of("LOINC", "718-7", "Messwert", "14", "Einheit", "g/dL"),
                        ObservationLaboratoryConverter.ObservationLaboratory_Columns.values()),
                null, new ConverterResult(options), null, options).convertInternal().get(0);
        assertTrue(observation.getCategoryFirstRep().hasCoding(
                "http://terminology.hl7.org/CodeSystem/observation-category", "laboratory"));
        assertTrue(observation.getCategoryFirstRep().hasCoding("http://loinc.org", "26436-6"));
        assertEquals(2, observation.getCategoryFirstRep().getCoding().size());
    }
    private CSVRecord row(Map<String,String> values, Enum<?>[] columns) throws Exception {
        Map<String,String> data = new LinkedHashMap<>();
        data.put("Patient-ID", "patient"); data.put("Fall-Nr", "1");
        for (Enum<?> c : columns) data.put(c.toString(), "");
        data.putAll(values);
        StringWriter out = new StringWriter();
        try (CSVPrinter p = new CSVPrinter(out, CSVFormat.DEFAULT)) { p.printRecord(data.keySet()); p.printRecord(data.values()); }
        try (CSVParser p = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setNullString("").get().parse(new StringReader(out.toString()))) {
            return p.getRecords().get(0);
        }
    }
    @Test public void procedurePreservesOriginalCodingPeriodAndStatus() throws Exception {
        ConverterOptions options = new ConverterOptions("");
        var converter = new ProcedureConverter(row(Map.of("Prozedurencode", "00123", "Codesystem", DiagnosisValues.SNOMED,
                "Durchführungsbeginn", "2026-01-02T10:00:00Z", "Ende", "2026-01-02T10:30:00Z", "Status", "in-progress"),
                ProcedureConverter.Procedure_Columns.values()), null, new ConverterResult(options), null, options);
        Procedure p = (Procedure)converter.convertInternal().get(0);
        assertEquals("00123", p.getCode().getCodingFirstRep().getCode());
        assertEquals("http://snomed.info/sct", p.getCode().getCodingFirstRep().getSystem());
        assertEquals(Procedure.ProcedureStatus.INPROGRESS, p.getStatus());
        assertTrue(p.getPerformedPeriod().hasEnd()); assertFalse(p.hasCategory());
    }
    @Test public void componentsAttachToParentAndVitalSignsAreNotLaboratory() throws Exception {
        ConverterOptions options = new ConverterOptions(""); ConverterResult result = new ConverterResult(options);
        var columns = ObservationVitalSignsConverter.ObservationVitalSigns_Columns.values();
        var parent = new ObservationVitalSignsConverter(row(Map.of("Untersuchungscode", "85354-9", "Untersuchung ID", "bp",
                "Werttyp", "Komponenten", "Zeitstempel", "2026-01-02"), columns), null, result, null, options);
        Observation o = (Observation)parent.convertInternal().get(0);
        result.add(TableIdentifier.Klinische_Dokumentation, o);
        var component = new ObservationVitalSignsConverter(row(Map.of("Untersuchungscode", "8480-6", "Komponente von", "bp",
                "Werttyp", "Zahl", "Wert", "120", "Einheit", "mmHg", "Einheitencode", "mm[Hg]"), columns), null, result, null, options);
        assertTrue(component.convertInternal().isEmpty());
        assertEquals(1, o.getComponent().size());
        assertEquals("120", o.getComponentFirstRep().getValueQuantity().getValue().toPlainString());
        assertEquals("vital-signs", o.getCategoryFirstRep().getCodingFirstRep().getCode());
        assertFalse(o.hasMeta()); assertTrue(o.getId().length() <= 64);
        var orphan = new ObservationVitalSignsConverter(row(Map.of("Untersuchungscode", "8480-6", "Komponente von", "missing"), columns), null, result, null, options);
        assertThrows(IllegalArgumentException.class, () -> orphan.convertInternal());
    }
    @Test public void medicationKeepsDistinctProductsAndDoesNotTurnDoseFrequencyIntoDose() throws Exception {
        ConverterOptions options = new ConverterOptions(""); ConverterResult result = new ConverterResult(options);
        var values = new HashMap<>(Map.of("Präparatcode", "123", "Präparatcodesystem", "RxNorm", "Medikationstyp", "Verordnung",
                "Dokumentationszeitpunkt", "2026-01-02", "Einzeldosis", "2", "Dosiereinheit", "mg", "Dosen pro Tag", "3",
                "Wirkstoffcode", "!dar:unknown", "Wirkstoffcodesystem", DiagnosisValues.SNOMED));
        var converter = new MedicationConverter(row(values, MedicationConverter.Medication_Columns.values()), null, result, null, options);
        var first = converter.convertInternal(); Medication medication = (Medication)first.get(0);
        MedicationRequest request = (MedicationRequest)first.get(1);
        assertEquals("123", medication.getCode().getCodingFirstRep().getCode());
        assertEquals("2", request.getDosageInstructionFirstRep().getDoseAndRateFirstRep().getDoseQuantity().getValue().toPlainString());
        assertEquals(3, request.getDosageInstructionFirstRep().getTiming().getRepeat().getFrequency());
        assertFalse(medication.getIngredientFirstRep().hasStrength());
        values.put("Präparatcode", "456");
        var second = new MedicationConverter(row(values, MedicationConverter.Medication_Columns.values()), null, result, null, options).convertInternal();
        assertNotEquals(medication.getId(), second.get(0).getId());
    }
    @Test public void localProductPznPreservesLeadingZerosAndDoseSemantics() throws Exception {
        ConverterOptions options = new ConverterOptions("");
        var values = new HashMap<>(Map.of("Präparatcode", "00000000", "Präparatcodesystem", "PZN",
                "Medikationstyp", "Verordnung", "Dokumentationszeitpunkt", "2026-01-02",
                "Einzeldosis", "2", "Dosiereinheit", "mg", "Dosen pro Tag", "3",
                "Wirkstoffcode", "!dar:unknown", "Wirkstoffcodesystem", DiagnosisValues.SNOMED));
        var resources = new MedicationConverter(row(values, MedicationConverter.Medication_Columns.values()),
                null, new ConverterResult(options), null, options).convertInternal();
        Medication medication = (Medication) resources.get(0);
        MedicationRequest request = (MedicationRequest) resources.get(1);
        assertEquals("http://fhir.de/CodeSystem/ifa/pzn", medication.getCode().getCodingFirstRep().getSystem());
        assertEquals("00000000", medication.getCode().getCodingFirstRep().getCode());
        assertFalse(medication.getIngredientFirstRep().hasStrength());
        assertEquals("2", request.getDosageInstructionFirstRep().getDoseAndRateFirstRep().getDoseQuantity().getValue().toPlainString());
        assertEquals(3, request.getDosageInstructionFirstRep().getTiming().getRepeat().getFrequency());
    }
    @Test public void textLabResultUsesExplicitMissingCodingAndPreservesText() throws Exception {
        ConverterOptions options = new ConverterOptions("");
        Observation observation = (Observation)new ObservationLaboratoryConverter(
                row(Map.of("LOINC", "630-4", "Messwert", "Escherichia coli nachgewiesen", "Werttyp", "Text"),
                        ObservationLaboratoryConverter.ObservationLaboratory_Columns.values()),
                null, new ConverterResult(options), null, options).convertInternal().get(0);
        CodeableConcept value = observation.getValueCodeableConcept();
        assertEquals("Escherichia coli nachgewiesen", value.getText());
        Coding coding = value.getCodingFirstRep();
        assertFalse(coding.getCodeElement().hasValue());
        assertEquals("unknown", coding.getCodeElement().getExtensionByUrl(
                "http://hl7.org/fhir/StructureDefinition/data-absent-reason").getValue().primitiveValue());
        assertEquals("unknown", coding.getSystemElement().getExtensionByUrl(
                "http://hl7.org/fhir/StructureDefinition/data-absent-reason").getValue().primitiveValue());
    }

    @Test public void partialAndMixedDosagesPreserveFactsWithoutInventedTiming() throws Exception {
        ConverterOptions options = new ConverterOptions("");
        var values = new HashMap<>(Map.of("Präparatcode", "123", "Präparatcodesystem", "RxNorm",
                "Medikationstyp", "Verordnung", "Dokumentationszeitpunkt", "2026-01-02", "Einzeldosis", "2"));
        values.put("Wirkstoffcode", "!dar:unknown");
        values.put("Wirkstoffcodesystem", DiagnosisValues.SNOMED);
        var columns = MedicationConverter.Medication_Columns.values();
        MedicationRequest request = (MedicationRequest)new MedicationConverter(row(values, columns), null,
                new ConverterResult(options), null, options).convertInternal().get(1);
        Dosage dosage = request.getDosageInstructionFirstRep();
        assertEquals("Einzeldosis: 2 (Einheit unbekannt)", dosage.getText());
        assertFalse(dosage.hasTiming());
        assertFalse(dosage.hasDoseAndRate());
        values.put("Dosierungstext", "Nach dem Essen");
        values.put("Dosen pro Tag", "3");
        values.put("Dosiereinheit", "mg");
        request = (MedicationRequest)new MedicationConverter(row(values, columns), null,
                new ConverterResult(options), null, options).convertInternal().get(1);
        dosage = request.getDosageInstructionFirstRep();
        assertEquals("Nach dem Essen; Einzeldosis: 2 mg; Dosen pro Tag: 3", dosage.getText());
        assertFalse(dosage.hasTiming());
        assertFalse(dosage.hasDoseAndRate());
    }

    @Test public void germanAddressNameBecomesIsoCodeWithoutChangingWorkbookConvention() throws Exception {
        ConverterOptions options = new ConverterOptions("");
        Patient patient = (Patient)new PatientConverter(row(Map.of("Land", "DE", "Bundesland", "Hamburg",
                "Geburtsdatum", "2000-01-01"), PatientConverter.Person_Columns.values()), null,
                new ConverterResult(options), null, options).convertInternal().get(0);
        assertEquals("DE-HH", patient.getAddressFirstRep().getState());
        assertEquals("http://hl7.org/fhir/StructureDefinition/data-absent-reason",
                Converter.DATA_ABSENT_REASON_UNKNOWN.getUrl());
    }

    @Test public void insurerIsNeverEmittedAsGeneralPractitioner() throws Exception {
        ConverterOptions options = new ConverterOptions("");
        Patient patient = (Patient)new PatientConverter(row(Map.of("Krankenkasse", "AOK",
                "Geburtsdatum", "2000-01-01"), PatientConverter.Person_Columns.values()), null,
                new ConverterResult(options), null, options).convertInternal().get(0);
        assertFalse(patient.hasGeneralPractitioner());
    }

    @Test public void structuredAddressSurvivesMissingCountryAndEmptyAddressUsesDar() throws Exception {
        ConverterOptions options = new ConverterOptions("");
        var values = new HashMap<>(Map.of("Straße", "Musterstraße 7", "Postleitzahl", "01234",
                "Ort", "Beispielort", "Geburtsdatum", "2000-01-01"));
        var columns = PatientConverter.Person_Columns.values();
        Patient patient = (Patient)new PatientConverter(row(values, columns), null,
                new ConverterResult(options), null, options).convertInternal().get(0);
        Address address = patient.getAddressFirstRep();
        assertEquals("Musterstraße 7", address.getLine().get(0).getValue());
        assertEquals("01234", address.getPostalCode());
        assertEquals("Beispielort", address.getCity());
        assertFalse(address.hasCountry());
        assertFalse(address.hasExtension());
        values.keySet().removeAll(List.of("Straße", "Postleitzahl", "Ort"));
        patient = (Patient)new PatientConverter(row(values, columns), null,
                new ConverterResult(options), null, options).convertInternal().get(0);
        assertEquals("unknown", patient.getAddressFirstRep().getExtensionByUrl(
                "http://hl7.org/fhir/StructureDefinition/data-absent-reason").getValue().primitiveValue());
    }

    @Test public void medicationTimeMatrixRejectsMisplacedDatesAndUnknownTypes() throws Exception {
        var values = new HashMap<>(Map.of("Medikationstyp", "Verordnung", "Präparatcode", "123", "Präparatcodesystem", "RxNorm",
                "Wirkstoffcode", "!dar:unknown", "Wirkstoffcodesystem", DiagnosisValues.SNOMED));
        assertTrue(MedicationValues.errors(values::get).isEmpty());
        values.put("Beginn", "2026-01-01");
        assertFalse(MedicationValues.errors(values::get).isEmpty());
        values.put("Medikationstyp", "Verabreichung");
        values.put("Status", "draft");
        assertFalse(MedicationValues.errors(values::get).isEmpty());
        values.put("Status", "completed");
        values.put("Ende", "2025-12-31");
        assertFalse(MedicationValues.errors(values::get).isEmpty());
        values.remove("Ende"); values.put("Beginn", "!dar:unknown");
        assertTrue(MedicationValues.errors(values::get).isEmpty());
        ConverterOptions options = new ConverterOptions("");
        var resources = new MedicationConverter(row(values, MedicationConverter.Medication_Columns.values()), null,
                new ConverterResult(options), null, options).convertInternal();
        MedicationAdministration administration = (MedicationAdministration)resources.get(1);
        assertEquals("unknown", administration.getEffectiveDateTimeType().getExtensionFirstRep().getValue().primitiveValue());
        values.put("Medikationstyp", "Vertippt");
        assertThrows(IllegalArgumentException.class, () -> new MedicationConverter(row(values, MedicationConverter.Medication_Columns.values()),
                null, new ConverterResult(options), null, options).convertInternal());
    }
    @Test public void medicationCombinesProductAtcAndIngredientAndDoesNotTruncateFrequency() throws Exception {
        ConverterOptions options = new ConverterOptions("");
        var values = new HashMap<String,String>();
        values.put("Medikationstyp", "Medikationsaussage"); values.put("Beginn", "2026-01-01");
        values.put("Ende", "2026-01-10"); values.put("Dokumentationszeitpunkt", "2026-01-11");
        values.put("Präparatcode", "00000000"); values.put("Präparatcodesystem", "PZN");
        values.put("ATC-Code", "N06AA09"); values.put("ATC-Version", "2025");
        values.put("Wirkstoffcode", "!dar:unknown"); values.put("Wirkstoffcodesystem", "ASK");
        values.put("Einzeldosis", "2"); values.put("Dosiereinheit", "mg"); values.put("Dosen pro Tag", "0.5");
        var resources = new MedicationConverter(row(values, MedicationConverter.Medication_Columns.values()), null,
                new ConverterResult(options), null, options).convertInternal();
        Medication medication = (Medication)resources.get(0);
        assertEquals(2, medication.getCode().getCoding().size());
        assertEquals("2025", medication.getCode().getCoding().get(1).getVersion());
        assertFalse(medication.getIngredientFirstRep().hasStrength());
        MedicationStatement statement = (MedicationStatement)resources.get(1);
        assertEquals("2026-01-10", statement.getEffectivePeriod().getEndElement().getValueAsString());
        assertEquals("2026-01-11", statement.getDateAssertedElement().getValueAsString());
        assertEquals("Einzeldosis: 2 mg; Dosen pro Tag: 0.5", statement.getDosageFirstRep().getText());
        assertFalse(statement.getDosageFirstRep().hasTiming());
        values.put("ATC-Version", "2026");
        var changed = new MedicationConverter(row(values, MedicationConverter.Medication_Columns.values()), null,
                new ConverterResult(options), null, options).convertInternal();
        assertNotEquals(medication.getId(), changed.get(0).getId());
    }
    @Test public void laboratoryRejectsBooleanButClinicalDocumentationKeepsIt() throws Exception {
        ConverterOptions options = new ConverterOptions("");
        var values = new HashMap<>(Map.of("LOINC", "1234-5", "Messwert", "true", "Werttyp", "Ja/Nein"));
        assertThrows(IllegalArgumentException.class, () -> new ObservationLaboratoryConverter(row(values,
                ObservationLaboratoryConverter.ObservationLaboratory_Columns.values()), null,
                new ConverterResult(options), null, options).convertInternal());
        values.put("Untersuchungscode", "1234-5"); values.put("Wert", "true");
        var resources = new ObservationVitalSignsConverter(row(values, ObservationVitalSignsConverter.ObservationVitalSigns_Columns.values()),
                null, new ConverterResult(options), null, options).convertInternal();
        assertTrue(((Observation)resources.get(0)).getValueBooleanType().booleanValue());
    }
}
