package de.uni_leipzig.life.csv2fhir.converter;

import static de.uni_leipzig.life.csv2fhir.BundleFunctions.createReference;
import static de.uni_leipzig.life.csv2fhir.TableIdentifier.Medikation;
import static de.uni_leipzig.life.csv2fhir.utils.DecimalUtil.parseDecimal;
import static org.apache.logging.log4j.util.Strings.isBlank;
import java.util.*;
import java.nio.charset.StandardCharsets;
import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.*;
import de.uni_leipzig.imise.validate.FHIRValidator;
import de.uni_leipzig.life.csv2fhir.*;
import de.uni_leipzig.life.csv2fhir.ConverterOptions.IntOption;

/** Explicit product, ingredient, dosage and event-time inputs. */
public class MedicationConverter extends Converter {
    public enum Medication_Columns implements TableColumnIdentifier {
        Medikationstyp, Präparatbezeichnung, Präparatcode, Präparatcodesystem,
        ATC_Code, ATC_Version, Darreichungsform, Wirkstoffcode, Wirkstoffcodesystem,
        Status, Absicht, Dokumentationszeitpunkt, Beginn, Ende, Einzeldosis, Dosiereinheit,
        Dosen_pro_Tag, Dosierungstext;
        @Override public String toString() {
            return name().startsWith("ATC_") ? name().replace('_', '-') : name().replace('_', ' ');
        }
    }
    private static final String PROFILE = "https://www.medizininformatik-initiative.de/fhir/core/modul-medikation/StructureDefinition/";
    public MedicationConverter(CSVRecord record, String previousRecordPID, ConverterResult result,
            FHIRValidator validator, ConverterOptions options) throws Exception {
        super(record, previousRecordPID, result, validator, options);
    }
    private String value(String key) { return MedicationValues.value(this::get, key); }
    @Override protected List<Resource> convertInternal() throws Exception {
        List<String> errors = MedicationValues.errors(this::get);
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("; ", errors));
        List<Resource> resources = new ArrayList<>();
        if (result.get(Medikation, Medication.class, getMedicationId()) == null) resources.add(medication());
        String type = value("Medikationstyp"), status = value("Status");
        Reference medication = createReference(Medication.class, getMedicationId());
        if (MedicationValues.REQUEST.equals(type)) {
            MedicationRequest r = new MedicationRequest();
            r.setMeta(new Meta().addProfile(PROFILE + "MedicationRequest"));
            r.setId(createId(MedicationRequest.class));
            r.setSubject(getPatientReference()).setEncounter(getEncounterReference()).setMedication(medication);
            r.setStatus(MedicationRequest.MedicationRequestStatus.fromCode(status == null ? "active" : status));
            r.setIntent(MedicationRequest.MedicationRequestIntent.fromCode(value("Absicht") == null ? "order" : value("Absicht")));
            r.setAuthoredOnElement(ClinicalValues.date(value("Dokumentationszeitpunkt")));
            Dosage dose = dosage();
            if (!dose.isEmpty()) r.addDosageInstruction(dose);
            resources.add(r);
        } else if (MedicationValues.ADMINISTRATION.equals(type)) {
            MedicationAdministration r = new MedicationAdministration();
            r.setMeta(new Meta().addProfile(PROFILE + "MedicationAdministration"));
            r.setId(createId(MedicationAdministration.class));
            r.setSubject(getPatientReference()).setContext(getEncounterReference()).setMedication(medication);
            r.setStatus(MedicationAdministration.MedicationAdministrationStatus.fromCode(status == null ? "completed" : status));
            r.setEffective(effective());
            var dose = new MedicationAdministration.MedicationAdministrationDosageComponent();
            if (value("Einzeldosis") != null) dose.setDose(quantity());
            // Administration has no daily timing element. Keep the supplied facts in text.
            if (value("Dosierungstext") != null || value("Dosen pro Tag") != null ||
                    (value("Einzeldosis") != null && value("Dosiereinheit") == null)) dose.setText(doseText());
            if (!dose.isEmpty()) r.setDosage(dose);
            resources.add(r);
        } else {
            MedicationStatement r = new MedicationStatement();
            r.setMeta(new Meta().addProfile(PROFILE + "MedicationStatement"));
            r.setId(createId(MedicationStatement.class));
            r.setSubject(getPatientReference()).setContext(getEncounterReference()).setMedication(medication);
            r.setStatus(MedicationStatement.MedicationStatementStatus.fromCode(status == null ? "active" : status));
            r.setEffective(effective());
            r.setDateAssertedElement(ClinicalValues.date(value("Dokumentationszeitpunkt")));
            Dosage dose = dosage();
            if (!dose.isEmpty()) r.addDosage(dose);
            resources.add(r);
        }
        return resources;
    }
    private Medication medication() {
        Medication r = new Medication();
        r.setMeta(new Meta().addProfile(PROFILE + "Medication"));
        r.setId(getMedicationId());
        r.addIdentifier().setValue(getMedicationId());
        CodeableConcept code = ClinicalValues.concept(value("Präparatcode"), value("Präparatcodesystem"), value("Präparatbezeichnung"));
        if (value("ATC-Code") != null) {
            Coding atc = new Coding().setSystem("http://fhir.de/CodeSystem/bfarm/atc").setVersion(value("ATC-Version"));
            Extension absent = DiagnosisValues.absentReason(value("ATC-Code"));
            if (absent == null) atc.setCode(value("ATC-Code")); else atc.getCodeElement().addExtension(absent);
            code.addCoding(atc);
        }
        r.setCode(code);
        if (value("Darreichungsform") != null) r.setForm(new CodeableConcept().setText(value("Darreichungsform")));
        r.addIngredient().setItem(ClinicalValues.concept(value("Wirkstoffcode"), value("Wirkstoffcodesystem"), null));
        return r;
    }
    private String getMedicationId() {
        // Length-prefix each product fact to avoid collisions, including ATC version and ingredient system.
        StringBuilder key = new StringBuilder();
        for (String column : List.of("Präparatbezeichnung", "Präparatcode", "Präparatcodesystem", "ATC-Code", "ATC-Version",
                "Darreichungsform", "Wirkstoffcode", "Wirkstoffcodesystem")) {
            String part = Objects.toString(value(column), "");
            key.append(part.length()).append(':').append(part);
        }
        return "Medication-" + UUID.nameUUIDFromBytes(key.toString().getBytes(StandardCharsets.UTF_8));
    }
    private Type effective() throws Exception {
        DateTimeType start = ClinicalValues.date(value("Beginn"));
        return value("Ende") == null ? start : new Period().setStartElement(start).setEndElement(ClinicalValues.date(value("Ende")));
    }
    private Quantity quantity() throws Exception {
        Extension absent = DiagnosisValues.absentReason(value("Einzeldosis"));
        Quantity q = getUcumQuantity(absent == null ? parseDecimal(value("Einzeldosis")) : null, value("Dosiereinheit"), null);
        if (absent != null) q.getValueElement().setExtension(List.of(absent));
        return q;
    }
    private String doseText() {
        List<String> facts = new ArrayList<>();
        if (value("Dosierungstext") != null) facts.add(value("Dosierungstext"));
        if (value("Einzeldosis") != null) facts.add("Einzeldosis: " + value("Einzeldosis") +
                (value("Dosiereinheit") == null ? " (Einheit unbekannt)" : " " + value("Dosiereinheit")));
        if (value("Dosen pro Tag") != null) facts.add("Dosen pro Tag: " + value("Dosen pro Tag"));
        return String.join("; ", facts);
    }
    private Dosage dosage() throws Exception {
        Dosage d = new Dosage();
        String frequency = value("Dosen pro Tag");
        boolean integral = frequency != null && parseDecimal(frequency).stripTrailingZeros().scale() <= 0
                && parseDecimal(frequency).compareTo(java.math.BigDecimal.valueOf(Integer.MAX_VALUE)) <= 0;
        if (value("Dosierungstext") == null && value("Einzeldosis") != null && value("Dosiereinheit") != null && integral) {
            d.addDoseAndRate().setDose(quantity());
            d.getTiming().getRepeat().setFrequency(parseDecimal(frequency).intValueExact()).setPeriod(1).setPeriodUnit(Timing.UnitsOfTime.D);
        } else if (!doseText().isEmpty()) d.setText(doseText());
        return d;
    }
    private <T extends Resource> String createId(Class<T> resourceType) throws Exception {
        String encounterID = getEncounterId();
        String superID = isBlank(encounterID) ? getPatientId() : encounterID;
        IntOption startID = null;
        String suffix = null;
        if (resourceType.isAssignableFrom(MedicationRequest.class)) {
            startID = IntOption.START_ID_MEDICATION_REQUEST;
            suffix = ResourceIdSuffix.MEDICATION_REQUEST.toString();
        } else if (resourceType.isAssignableFrom(MedicationAdministration.class)) {
            startID = IntOption.START_ID_MEDICATION_ADMINISTRATION;
            suffix = ResourceIdSuffix.MEDICATION_ADMINISTRATION.toString();
        } else if (resourceType.isAssignableFrom(MedicationStatement.class)) {
            startID = IntOption.START_ID_MEDICATION_STATEMENT;
            suffix = ResourceIdSuffix.MEDICATION_STATEMENT.toString();
        }
        int nextIDNumber = result.getNextId(Medikation, resourceType, startID);
        return superID + suffix + nextIDNumber;
    }

}
