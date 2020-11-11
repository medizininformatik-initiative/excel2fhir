package de.uni_leipzig.life.csv2fhir.converter;

import static org.hl7.fhir.r4.model.MedicationStatement.MedicationStatementStatus.ACTIVE;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Dosage;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.MedicationAdministration;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Ratio;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.SimpleQuantity;

import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.Ucum;
import de.uni_leipzig.life.csv2fhir.utils.DateUtil;
import de.uni_leipzig.life.csv2fhir.utils.DecimalUtil;

public class MedikationConverter extends Converter {

    String PROFILE_ADM= "https://www.medizininformatik-initiative.de/fhir/core/modul-medikation/StructureDefinition/MedicationAdministration";
    
    String PROFILE_STM= "https://www.medizininformatik-initiative.de/fhir/core/modul-medikation/StructureDefinition/MedicationStatement";
    
    String PROFILE_MED= "https://www.medizininformatik-initiative.de/fhir/core/modul-medikation/StructureDefinition/Medication";
    // https://simplifier.net/medizininformatikinitiative-modulmedikation/medication-duplicate-3
    /*
     * Invalid : Instance count for 'Medication.ingredient' is 0, which is not within the specified cardinality of 1..*
     */

    // Krude Lösung TODO
    static Set<String> medis = new HashSet<String>();

    public MedikationConverter(CSVRecord record) {
        super(record);
    }

    @Override
    public List<Resource> convert() throws Exception {
        List<Resource> l = new Vector<>();
        if (!medis.contains(getMedicationId())) {
            l.add(parseMedication());
            medis.add(getMedicationId());
        }
        if ("Administration".equals(record.get("FHIR_Resourcentyp"))) {
            l.add(convertMedicationAdministration());
        } else {
            l.add(parseMedicationStatement());
        }
        return l;
    }

    private MedicationAdministration convertMedicationAdministration() throws Exception {
        MedicationAdministration medicationAdministration = new MedicationAdministration();
        medicationAdministration.setMeta(new Meta().addProfile(PROFILE_ADM));

        medicationAdministration.setStatus("completed");
        medicationAdministration.setMedication(getMedicationReference());
        //        medicationAdministration.setMedication(convertMedicationCodeableConcept());
        medicationAdministration.setSubject(convertSubject());
        medicationAdministration.setEffective(convertPeriod());
        medicationAdministration.setDosage(convertDosageComponent());
        return medicationAdministration;
    }

    private Medication parseMedication() throws Exception {
        Medication medication = new Medication();
        medication.setMeta(new Meta().addProfile(PROFILE_MED));
        medication.setId(getMedicationId());
        medication.setIdentifier(Collections.singletonList(new Identifier().setValue(getMedicationId())));
        medication.setCode(convertMedicationCodeableConcept());
        return medication;
    }
    private CodeableConcept convertMedicationCodeableConcept() {
        CodeableConcept concept = new CodeableConcept();
        concept.addCoding(getATCCoding());
        concept.addCoding(getPZNCoding());
        concept.addCoding(getASKCoding());
        concept.setText(record.get("Wirksubstanz aus Präparat/Handelsname"));
        return concept;
    }

    private String getMedicationId() throws Exception {
        String atc = record.get("ATC Code");
        if (atc != null) {
            return getDIZId() + "-" + atc;
        } else {
            error("ATC empty");
            return null;
        }
    }


    private Reference getMedicationReference() throws Exception {
        String atc = record.get("ATC Code");
        if (atc != null) {
            return new Reference().setReference("Medication/" + getMedicationId());
        } else {
            error("ATC empty");
            return null;
        }
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

    private Period convertPeriod() throws Exception {
        try {
            Period  p = new Period().setStartElement(DateUtil.parseDateTimeType(record.get("Therapiestartdatum")));
            String end = record.get("Therapieendedatum");
            if (end != null && !end.isBlank()) {
                p.setEndElement(DateUtil.parseDateTimeType(end));
            }
            return p;
        } catch (Exception e) {
            error("Can not parse Therapiestartdatum or Therapieendedatum");
            
            return null;
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
            error("Einheit empty for Record");
            return null;
        }
    }

    private BigDecimal getDosesPerDay() throws Exception {
        try {
            return DecimalUtil.parseDecimal(record.get("Anzahl Dosen pro Tag"));
        } catch (Exception e) {
            error("Anzahl Dosen pro Tag is not a numerical value for Record");
            return null;
        }
    }

    private BigDecimal getDose() throws Exception {
        try {
            return DecimalUtil.parseDecimal(record.get("Einzeldosis"));
        } catch (Exception e) {
            error("Einzeldosis is not a numerical value for Record");
            return null;
        }
    }

    private MedicationStatement parseMedicationStatement() throws Exception {
        MedicationStatement medicationStatement = new MedicationStatement();
        medicationStatement.setStatus(ACTIVE);
        medicationStatement.setMeta(new Meta().addProfile(PROFILE_STM));

        medicationStatement.setMedication(getMedicationReference());

        //        medicationStatement.setMedication(convertMedicationCodeableConcept());
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
            error("Can not parse timestamp");
            return null;
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
            error("Einheit empty");
            return null;
        }
    }
}
