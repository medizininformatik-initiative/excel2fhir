package de.uni_leipzig.life.csv2fhir.converter;

import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.Ucum;
import de.uni_leipzig.life.csv2fhir.utils.DateUtil;
import de.uni_leipzig.life.csv2fhir.utils.DecimalUtil;
import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Dosage;
import org.hl7.fhir.r4.model.MedicationAdministration;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Ratio;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.SimpleQuantity;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.hl7.fhir.r4.model.MedicationStatement.MedicationStatementStatus.ACTIVE;

public class MedikationConverter implements Converter {

    private final CSVRecord record;

    public MedikationConverter(CSVRecord record) {
        this.record = record;
    }

    @Override
    public List<Resource> convert() throws Exception {
        if ("Administration".equals(record.get("FHIR_Resourcentyp"))) {
            return Collections.singletonList(convertMedicationAdministration());
        } else {
            return Collections.singletonList(parseMedicationStatement());
        }
    }

    private MedicationAdministration convertMedicationAdministration() throws Exception {
        MedicationAdministration medicationAdministration = new MedicationAdministration();
        medicationAdministration.setStatus("completed");
        medicationAdministration.setMedication(convertMedicationCodeableConcept());
        medicationAdministration.setSubject(convertSubject());
        medicationAdministration.setEffective(convertPeriod());
        medicationAdministration.setDosage(convertDosageComponent());
        return medicationAdministration;
    }

    private CodeableConcept convertMedicationCodeableConcept() {
        CodeableConcept concept = new CodeableConcept();
        concept.addCoding(getATCCoding());
        concept.addCoding(getPZNCoding());
        concept.addCoding(getASKCoding());
        concept.setText(record.get("Wirksubstanz aus Präparat/Handelsname"));
        return concept;
    }

    private Coding getATCCoding() {
        String atc = record.get("ATC Code");
        if (atc != null) {
            return new Coding()
                    .setSystem("http://fhir.de/CodeSystem/dimdi/atc")
                    .setCode(atc)
                    .setUserSelected("ATC".equals(record.get("FHIR_UserSelected")));
        } else {
            return null;
        }
    }

    private Coding getPZNCoding() {
        String pzn = record.get("PZN Code");
        if (pzn != null) {
            return new Coding()
                    .setSystem("http://fhir.de/CodeSystem/ifa/pzn")
                    .setCode(pzn)
                    .setUserSelected("PZN".equals(record.get("FHIR_UserSelected")));
        } else {
            return null;
        }
    }

    private Coding getASKCoding() {
        String ask = record.get("ASK");
        if (ask != null) {
            return new Coding()
                    .setSystem("http://fhir.de/CodeSystem/ask")
                    .setCode(ask)
                    .setUserSelected("ASK".equals(record.get("FHIR_UserSelected")));
        } else {
            return null;
        }
    }

    private Reference convertSubject() throws Exception {
        String patientId = record.get("Patient-ID");
        if (patientId != null) {
            return new Reference().setReference("Patient/" + patientId);
        } else {
            throw new Exception("Error on Medication: Patient-ID empty for Record: "
                    + record.getRecordNumber() + "!" + record.toString());
        }
    }

    private Period convertPeriod() throws Exception {
        try {
            Period  p = new Period().setStartElement(DateUtil.parseDateTimeType(record.get("Therapiestartdatum")));
            String end = record.get("Therapieendedatum");
            if (end != null && !end.isBlank()) {
                p.setEndElement(DateUtil.parseDateTimeType(end));
            }
            return p;
        } catch (Exception e) {
            throw new Exception("Error on Medication: Can not parse Therapiestartdatum or Therapieendedatum for Record: "
                    + record.getRecordNumber() + "! " + record.toString());
        }
    }

    private MedicationAdministration.MedicationAdministrationDosageComponent convertDosageComponent() throws Exception {
        return new MedicationAdministration.MedicationAdministrationDosageComponent()
                .setRate(getDoseRate());
    }

    private Ratio getDoseRate() throws Exception {
        String unit = record.get("Einheit");
        if (unit != null) {
            return new Ratio()
                    .setNumerator(new Quantity()
                            .setValue(getDosesPerDay().multiply(getDose()))
                            .setUnit(unit)
                            .setSystem("http://unitsofmeasure.org")
                            .setCode(unit))
                    .setDenominator(new Quantity()
                            .setValue(1)
                            .setUnit("day")
                            .setSystem("http://unitsofmeasure.org")
                            .setCode("d"));
        } else {
            throw new Exception("Error on Medication: Einheit empty for Record: "
                    + record.getRecordNumber() + "! " + record.toString());
        }
    }

    private BigDecimal getDosesPerDay() throws Exception {
        try {
            return DecimalUtil.parseDecimal(record.get("Anzahl Dosen pro Tag"));
        } catch (Exception e) {
            throw new Exception("Error on Medication: Anzahl Dosen pro Tag is not a numerical value for Record: "
                    + record.getRecordNumber() + "! " + record.toString());
        }
    }

    private BigDecimal getDose() throws Exception {
        try {
            return DecimalUtil.parseDecimal(record.get("Einzeldosis"));
        } catch (Exception e) {
            throw new Exception("Error on Medication: Einzeldosis is not a numerical value for Record: "
                    + record.getRecordNumber() + "! " + record.toString());
        }
    }

    private MedicationStatement parseMedicationStatement() throws Exception {
        MedicationStatement medicationStatement = new MedicationStatement();
        medicationStatement.setStatus(ACTIVE);
        medicationStatement.setMedication(convertMedicationCodeableConcept());
        medicationStatement.setSubject(convertSubject());
        medicationStatement.setEffective(convertPeriod());
        medicationStatement.setDateAssertedElement(convertTimestamp());
        medicationStatement.addDosage(convertDosage());
        return medicationStatement;
    }

    private DateTimeType convertTimestamp() throws Exception {
        try {
            return DateUtil.parseDateTimeType(record.get("Zeitstempel"));
        } catch (Exception e) {
            throw new Exception("Error on Medication: Can not parse timestamp for Record: "
                    + record.getRecordNumber() + "! " + record.toString());
        }
    }

    private Dosage convertDosage() throws Exception {
        String unit = getDoseUnit();
        String ucum,synonym;
        if (Ucum.isUcum(unit)) {
            ucum = unit;
            synonym = Ucum.ucum2human(unit); 
        } else  {
            ucum = Ucum.human2ucum(unit);
            synonym = unit;
        }

        return new Dosage()
                .addDoseAndRate(new Dosage.DosageDoseAndRateComponent()
                        .setDose(new SimpleQuantity()
                                .setValue(getDose())
                                .setUnit(synonym)
                                .setSystem("http://unitsofmeasure.org")
                                .setCode(ucum))
                        .setRate(new Quantity()
                                .setValue(getDosesPerDay())
                                .setUnit("day")
                                .setSystem("http://unitsofmeasure.org")
                                .setCode("d")));
    }

    private String getDoseUnit() throws Exception {
        String doseUnit = record.get("Einheit");
        if (doseUnit != null) {
            return doseUnit;
        } else {
            throw new Exception("Error on Medication: Einheit empty for Record: "
                    + record.getRecordNumber() + "! " + record.toString());
        }
    }
}
