package de.uni_leipzig.life.csv2fhir.converter;

import static org.junit.Assert.*;
import java.io.*;
import java.util.*;
import org.apache.commons.csv.*;
import org.hl7.fhir.r4.model.*;
import org.junit.Test;
import de.uni_leipzig.life.csv2fhir.*;

public class ClinicalImportConverterTest {
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
                "Dokumentationszeitpunkt", "2026-01-02T10:00:00Z", "Ende", "2026-01-02T10:30:00Z", "Status", "in-progress"),
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
        var parent = new ObservationVitalSignsConverter(row(Map.of("LOINC", "85354-9", "Untersuchung ID", "bp",
                "Werttyp", "Komponenten", "Zeitstempel", "2026-01-02"), columns), null, result, null, options);
        Observation o = (Observation)parent.convertInternal().get(0);
        result.add(TableIdentifier.Klinische_Dokumentation, o);
        var component = new ObservationVitalSignsConverter(row(Map.of("LOINC", "8480-6", "Komponente von", "bp",
                "Werttyp", "Zahl", "Wert", "120", "Einheit", "mmHg", "Einheitencode", "mm[Hg]"), columns), null, result, null, options);
        assertTrue(component.convertInternal().isEmpty());
        assertEquals(1, o.getComponent().size());
        assertEquals("120", o.getComponentFirstRep().getValueQuantity().getValue().toPlainString());
        assertEquals("vital-signs", o.getCategoryFirstRep().getCodingFirstRep().getCode());
        assertFalse(o.hasMeta()); assertTrue(o.getId().length() <= 64);
        var orphan = new ObservationVitalSignsConverter(row(Map.of("LOINC", "8480-6", "Komponente von", "missing"), columns), null, result, null, options);
        assertThrows(IllegalArgumentException.class, () -> orphan.convertInternal());
    }
    @Test public void medicationKeepsDistinctProductsAndDoesNotTurnDoseFrequencyIntoDose() throws Exception {
        ConverterOptions options = new ConverterOptions(""); ConverterResult result = new ConverterResult(options);
        var values = new HashMap<>(Map.of("Medikamentencode", "123", "Codesystem", "RxNorm", "Medikationstyp", "MedicationRequest",
                "Zeitstempel", "2026-01-02", "Einzeldosis", "2", "Einheit", "mg", "Anzahl Dosen pro Tag", "3",
                "Wirkstoffcode", "!dar:unknown", "Wirkstoffcodesystem", DiagnosisValues.SNOMED));
        var converter = new MedicationConverter(row(values, MedicationConverter.Medication_Columns.values()), null, result, null, options);
        var first = converter.convertInternal(); Medication medication = (Medication)first.get(0);
        MedicationRequest request = (MedicationRequest)first.get(1);
        assertEquals("123", medication.getCode().getCodingFirstRep().getCode());
        assertEquals("2", request.getDosageInstructionFirstRep().getDoseAndRateFirstRep().getDoseQuantity().getValue().toPlainString());
        assertEquals(3, request.getDosageInstructionFirstRep().getTiming().getRepeat().getFrequency());
        assertFalse(medication.getIngredientFirstRep().hasStrength());
        values.put("Medikamentencode", "456");
        var second = new MedicationConverter(row(values, MedicationConverter.Medication_Columns.values()), null, result, null, options).convertInternal();
        assertNotEquals(medication.getId(), second.get(0).getId());
    }
}
