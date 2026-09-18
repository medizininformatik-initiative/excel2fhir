package de.uni_leipzig.life.csv2fhir.converter;

import java.util.*;
import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.*;
import de.uni_leipzig.imise.validate.FHIRValidator;
import de.uni_leipzig.life.csv2fhir.*;

/** Explicit human input sheets for clinical events without a previous converter. */
public abstract class ClinicalEventConverter extends Converter {
    public enum Columns implements TableColumnIdentifier {
        Eintrag_ID, Bezeichner, Code, Codesystem, Zeitpunkt, Ende, Status, Absicht,
        Primärquelle, Ausgabezeitpunkt, Ergebnisse, Beschreibung, Aktivitätscodes;
        @Override public String toString() { return name().replace('_', ' '); }
        @Override public boolean isMandatory() { return false; }
    }
    private final String type;
    protected ClinicalEventConverter(String type, CSVRecord row, String pid, ConverterResult result,
            FHIRValidator validator, ConverterOptions options) throws Exception {
        super(row, pid, result, validator, options); this.type = type;
    }
    private String v(Columns c) { String v = get(c); return v == null || v.isBlank() ? null : v; }
    private CodeableConcept code() { return ClinicalValues.concept(v(Columns.Code), v(Columns.Codesystem), v(Columns.Bezeichner)); }
    @Override protected List<Resource> convertInternal() throws Exception {
        String sourceId = v(Columns.Eintrag_ID);
        if (sourceId == null) throw new IllegalArgumentException("Eintrag ID required");
        String id = ClinicalValues.resourceId(getPatientId(), type, sourceId);
        DateTimeType time = ClinicalValues.date(v(Columns.Zeitpunkt));
        Resource resource;
        switch (type) {
        case "Immunization":
            Immunization vaccine = new Immunization(); vaccine.setPatient(getPatientReference());
            vaccine.setEncounter(getEncounterReference()); vaccine.setVaccineCode(code()); vaccine.setOccurrence(time);
            if (v(Columns.Status) != null) vaccine.setStatus(Immunization.ImmunizationStatus.fromCode(v(Columns.Status)));
            if (v(Columns.Primärquelle) != null) {
                if (!List.of("true", "false").contains(v(Columns.Primärquelle))) throw new IllegalArgumentException("Primärquelle: true or false");
                vaccine.setPrimarySource(Boolean.parseBoolean(v(Columns.Primärquelle)));
            }
            resource = vaccine; break;
        case "DiagnosticReport":
            DiagnosticReport report = new DiagnosticReport(); report.setSubject(getPatientReference());
            report.setEncounter(getEncounterReference()); report.setCode(code()); report.setEffective(time);
            if (v(Columns.Status) != null) report.setStatus(DiagnosticReport.DiagnosticReportStatus.fromCode(v(Columns.Status)));
            if (v(Columns.Ausgabezeitpunkt) != null) report.setIssuedElement(new InstantType(v(Columns.Ausgabezeitpunkt)));
            if (v(Columns.Ergebnisse) != null) for (String ref : v(Columns.Ergebnisse).split(";"))
                report.addResult(new Reference("Observation/" + ClinicalValues.resourceId(getPatientId(), "Observation", ref)));
            if (v(Columns.Beschreibung) != null) report.setConclusion(v(Columns.Beschreibung));
            resource = report; break;
        case "CarePlan":
            CarePlan plan = new CarePlan(); plan.setSubject(getPatientReference()); plan.setEncounter(getEncounterReference());
            if (!code().isEmpty()) plan.addCategory(code());
            plan.setPeriod(new Period().setStartElement(time).setEndElement(ClinicalValues.date(v(Columns.Ende))));
            if (v(Columns.Status) != null) plan.setStatus(CarePlan.CarePlanStatus.fromCode(v(Columns.Status)));
            if (v(Columns.Absicht) != null) plan.setIntent(CarePlan.CarePlanIntent.fromCode(v(Columns.Absicht)));
            if (v(Columns.Beschreibung) != null) plan.setDescription(v(Columns.Beschreibung));
            if (v(Columns.Aktivitätscodes) != null) for (String activity : v(Columns.Aktivitätscodes).split(";"))
                plan.addActivity().getDetail().setCode(ClinicalValues.concept(activity, DiagnosisValues.SNOMED, null))
                    .setStatus(CarePlan.CarePlanActivityStatus.UNKNOWN);
            resource = plan; break;
        default: throw new IllegalArgumentException(type);
        }
        resource.setId(id);
        return Collections.singletonList(resource);
    }
    public static class Vaccine extends ClinicalEventConverter {
        public Vaccine(CSVRecord r, String p, ConverterResult c, FHIRValidator v, ConverterOptions o) throws Exception { super("Immunization",r,p,c,v,o); }
    }
    public static class Report extends ClinicalEventConverter {
        public Report(CSVRecord r, String p, ConverterResult c, FHIRValidator v, ConverterOptions o) throws Exception { super("DiagnosticReport",r,p,c,v,o); }
    }
    public static class Plan extends ClinicalEventConverter {
        public Plan(CSVRecord r, String p, ConverterResult c, FHIRValidator v, ConverterOptions o) throws Exception { super("CarePlan",r,p,c,v,o); }
    }
}
