package de.uni_leipzig.life.synthifhir.converter;

import java.util.*;
import java.util.function.Function;
import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.*;
import de.uni_leipzig.imise.validate.FHIRValidator;
import de.uni_leipzig.life.synthifhir.*;

/** A patient ID starts a plan; following rows contain independent activities. */
public class CarePlanConverter extends Converter {
    public enum Columns implements TableColumnIdentifier {
        Titel, Beschreibung, Planstatus, Absicht, Erstellungsdatum, Beginn, Ende,
        Kategorietext, Kategoriecode, Kategoriecodesystem, Kategorieversion,
        Aktivitätstext, Aktivitätscode, Aktivitätscodesystem, Aktivitätsversion,
        Aktivitätsstatus, Aktivitätsstatusgrund, Aktivitätsbeginn, Aktivitätsende, Aktivitätsort;
        @Override public boolean isMandatory() { return false; }
    }
    public static final List<String> PLAN_FIELDS = List.of("Fall-Nr", "Titel", "Beschreibung", "Planstatus", "Absicht",
            "Erstellungsdatum", "Beginn", "Ende", "Kategorietext", "Kategoriecode", "Kategoriecodesystem", "Kategorieversion");

    public static List<String> continuationErrors(Function<String, String> value) {
        if (present(value.apply("Patient-ID"))) return List.of();
        return PLAN_FIELDS.stream().filter(f -> present(value.apply(f)))
                .map(f -> f + ": plan fields require a Patient-ID on the same row").toList();
    }
    private static boolean present(String value) { return value != null && !value.isBlank(); }
    private String v(String field) { return hasColumn(field) && present(get(field)) ? get(field) : null; }
    private CodeableConcept concept(String prefix) {
        CodeableConcept code = ClinicalValues.concept(v(prefix + "code"), v(prefix + "codesystem"), v(prefix + "text"));
        if (v(prefix + "version") != null) CodingVersion.apply(code.getCodingFirstRep(), v(prefix + "version"));
        return code;
    }
    private final long rowNumber;
    public CarePlanConverter(CSVRecord row, String previous, ConverterResult result, FHIRValidator validator,
            ConverterOptions options) throws Exception { super(row, previous, result, validator, options); rowNumber = row.getRecordNumber(); }

    @Override protected List<Resource> convertInternal() throws Exception {
        boolean start = v("Patient-ID") != null;
        var errors = continuationErrors(this::v);
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("; ", errors));
        CarePlan plan = start ? new CarePlan() : result.currentCarePlan;
        if (plan == null || (!start && !getPatientReference().equalsDeep(plan.getSubject())))
            throw new IllegalArgumentException("Activity requires a preceding plan with Patient-ID");
        if (start) {
            plan.setId(ClinicalValues.resourceId(getPatientId(), "CarePlan", Long.toString(rowNumber)));
            plan.setSubject(getPatientReference()).setEncounter(getEncounterReference());
            plan.setTitle(v("Titel")).setDescription(v("Beschreibung"));
            plan.setStatusElement(InputCode.of(plan.getStatusElement(), v("Planstatus")));
            plan.setIntentElement(InputCode.of(plan.getIntentElement(), v("Absicht")));
            plan.setCreatedElement(ClinicalValues.date(v("Erstellungsdatum")));
            plan.setPeriod(new Period().setStartElement(ClinicalValues.date(v("Beginn"))).setEndElement(ClinicalValues.date(v("Ende"))));
            CodeableConcept category = concept("Kategorie");
            if (!category.isEmpty()) plan.addCategory(category);
        }
        var detail = new CarePlan.CarePlanActivityDetailComponent();
        detail.setCode(concept("Aktivitäts"));
        detail.setStatusElement(InputCode.of(detail.getStatusElement(), v("Aktivitätsstatus")));
        if (v("Aktivitätsstatusgrund") != null) detail.setStatusReason(new CodeableConcept().setText(v("Aktivitätsstatusgrund")));
        detail.setScheduled(new Period().setStartElement(ClinicalValues.date(v("Aktivitätsbeginn")))
                .setEndElement(ClinicalValues.date(v("Aktivitätsende"))));
        if (v("Aktivitätsort") != null) detail.setLocation(new Reference().setDisplay(v("Aktivitätsort")));
        if (!detail.isEmpty()) plan.addActivity().setDetail(detail);
        if (start) { result.currentCarePlan = plan; return List.of(plan); }
        return List.of();
    }
}
