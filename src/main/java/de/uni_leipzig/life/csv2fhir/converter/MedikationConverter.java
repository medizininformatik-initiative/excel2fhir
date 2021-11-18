package de.uni_leipzig.life.csv2fhir.converter;

import static de.uni_leipzig.life.csv2fhir.converterFactory.MedikationConverterFactory.NeededColumns.ASK;
import static de.uni_leipzig.life.csv2fhir.converterFactory.MedikationConverterFactory.NeededColumns.ATC_Code;
import static de.uni_leipzig.life.csv2fhir.converterFactory.MedikationConverterFactory.NeededColumns.Anzahl_Dosen_pro_Tag;
import static de.uni_leipzig.life.csv2fhir.converterFactory.MedikationConverterFactory.NeededColumns.Darreichungsform;
import static de.uni_leipzig.life.csv2fhir.converterFactory.MedikationConverterFactory.NeededColumns.Einheit;
import static de.uni_leipzig.life.csv2fhir.converterFactory.MedikationConverterFactory.NeededColumns.Einzeldosis;
import static de.uni_leipzig.life.csv2fhir.converterFactory.MedikationConverterFactory.NeededColumns.FHIR_UserSelected;
import static de.uni_leipzig.life.csv2fhir.converterFactory.MedikationConverterFactory.NeededColumns.Medikationsplanart;
import static de.uni_leipzig.life.csv2fhir.converterFactory.MedikationConverterFactory.NeededColumns.PZN_Code;
import static de.uni_leipzig.life.csv2fhir.converterFactory.MedikationConverterFactory.NeededColumns.Therapieendedatum;
import static de.uni_leipzig.life.csv2fhir.converterFactory.MedikationConverterFactory.NeededColumns.Therapiestartdatum;
import static de.uni_leipzig.life.csv2fhir.converterFactory.MedikationConverterFactory.NeededColumns.Wirksubstanz_aus_Praeparat_Handelsname;
import static de.uni_leipzig.life.csv2fhir.converterFactory.MedikationConverterFactory.NeededColumns.Zeitstempel;
import static org.hl7.fhir.r4.model.MedicationStatement.MedicationStatementStatus.ACTIVE;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Dosage;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.Medication.MedicationIngredientComponent;
import org.hl7.fhir.r4.model.MedicationAdministration;
import org.hl7.fhir.r4.model.MedicationAdministration.MedicationAdministrationDosageComponent;
import org.hl7.fhir.r4.model.MedicationAdministration.MedicationAdministrationStatus;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Ratio;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.SimpleQuantity;
import org.hl7.fhir.r4.model.Type;

import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.Ucum;
import de.uni_leipzig.life.csv2fhir.utils.DateUtil;
import de.uni_leipzig.life.csv2fhir.utils.DecimalUtil;

/*
 * MedicationStatement bei "Vor Aufnahme" MedicationAdminstration sonst
 */
public class MedikationConverter extends Converter {

    /**  */
    String PROFILE_ADM = "https://www.medizininformatik-initiative.de/fhir/core/modul-medikation/StructureDefinition/MedicationAdministration";

    /**  */
    String PROFILE_STM = "https://www.medizininformatik-initiative.de/fhir/core/modul-medikation/StructureDefinition/MedicationStatement";

    /**  */
    String PROFILE_MED = "https://www.medizininformatik-initiative.de/fhir/core/modul-medikation/StructureDefinition/Medication";
    // https://simplifier.net/medizininformatikinitiative-modulmedikation/medication-duplicate-3

    /*
     * Invalid : Instance count for 'Medication.ingredient' is 0, which is not
     * within the specified cardinality of 1..*
     */
    static int n = 1;

    // Krude Lösung TODO
    /**  */
    static Set<String> medis = new HashSet<>();

    /**
     * @param record
     * @throws Exception
     */
    public MedikationConverter(CSVRecord record) throws Exception {
        super(record);
    }

    @Override
    public List<Resource> convert() throws Exception {
        List<Resource> l = new Vector<>();
        if (!medis.contains(getMedicationId())) {
            l.add(parseMedication());
            medis.add(getMedicationId());
        }
        if ("Vor Aufnahme".equals(record.get(Medikationsplanart))) {
            l.add(parseMedicationStatement());
        } else {
            l.add(parseMedicationAdministration());
        }
        return l;
    }

    /**
     * @return
     * @throws Exception
     */
    private MedicationAdministration parseMedicationAdministration() throws Exception {
        MedicationAdministration medicationAdministration = new MedicationAdministration();
        medicationAdministration.setMeta(new Meta().addProfile(PROFILE_ADM));
        medicationAdministration.setId(getEncounterId() + "-MA-" + n++);

        //        medicationAdministration.setStatus("completed");
        medicationAdministration.setStatus(MedicationAdministrationStatus.COMPLETED);
        // Set Reference
        medicationAdministration.setMedication(getMedicationReference());
        // and set CodeableConcept
        //        medicationAdministration.setMedication(convertMedicationCodeableConcept());
        medicationAdministration.setContext(getEncounterReference());
        medicationAdministration.setSubject(getPatientReference());
        //        medicationAdministration.setEffective(convertPeriod());
        medicationAdministration.setEffective(convertTimestamp());
        medicationAdministration.setDosage(convertDosageAdministration());
        return medicationAdministration;
    }

    /**
     * @return
     * @throws Exception
     */
    private Medication parseMedication() throws Exception {
        Medication medication = new Medication();
        medication.setMeta(new Meta().addProfile(PROFILE_MED));
        medication.setId(getMedicationId());
        medication.setIdentifier(Collections.singletonList(new Identifier().setValue(getMedicationId())));
        medication.setCode(convertMedicationCodeableConcept());
        medication.setIngredient(Collections.singletonList(getIngredient()));
        return medication;
    }

    /**
     * @return
     */
    private MedicationIngredientComponent getIngredient() {
        MedicationIngredientComponent m = new MedicationIngredientComponent();
        try {
            Coding askCoding = getASKCoding();
            CodeableConcept askCodeableConcept = new CodeableConcept(askCoding);
            m.setItem(askCodeableConcept);
        } catch (Exception e) {
            warning("cannot set ATC");
        }
        try {
            m.setStrength(getDoseRate());
        } catch (Exception e) {
            warning("cannot set strength");
        }
        return m;
    }

    /**
     * @return
     */
    private CodeableConcept convertMedicationCodeableConcept() {
        CodeableConcept concept = new CodeableConcept();
        concept.addCoding(createCoding("http://fhir.de/CodeSystem/ifa/pzn", PZN_Code, FHIR_UserSelected, "PZN"));
        concept.addCoding(createCoding("http://fhir.de/CodeSystem/dimdi/atc", ATC_Code, FHIR_UserSelected, "ATC"));
        concept.setText(record.get(Wirksubstanz_aus_Praeparat_Handelsname));
        return concept;
    }

    /**
     * @return
     * @throws Exception
     */
    private String getMedicationId() throws Exception {
        String id;
        String atc = record.get(ATC_Code);
        if (atc != null) {
            id = atc;
        } else {
            String txt = record.get(Wirksubstanz_aus_Praeparat_Handelsname);
            if (txt == null) {
                error("ATC and Wirksubstanz aus Präparat/Handelsname empty");
                return null;
            }
            warning("ATC empty");
            id = txt;
        }
        //        return getDIZId() + "-M-" + id.hashCode();
        // Dumm, jede Medikation wird für jeden Patienten wiederholt... es dafür steht jedes bundle für sich
        return getPatientId() + "-M-" + id.hashCode();
    }

    /**
     * @return
     * @throws Exception
     */
    private Reference getMedicationReference() throws Exception {
        return new Reference().setReference("Medication/" + getMedicationId());
    }

    /**
     * @return
     */
    private Coding getASKCoding() {
        String askCodeSystem = "http://fhir.de/CodeSystem/ask";
        Coding askCoding = createCoding(askCodeSystem, ASK, FHIR_UserSelected, "ASK");
        if (askCoding == null) {
            // fake to be KDS conform
            warning("no ask code");
            askCoding = createCoding(askCodeSystem, null, "no code defined");
        }
        return askCoding;
    }

    /**
     * @param codeSystem
     * @param codeColumnName
     * @param selectedIndicatorColumnName
     * @param selectedConditionValue
     * @return a new Coding with the given code system and code from the column
     *         with the codeColumnName or <code>null</code> if the code is
     *         missing.
     */
    public Coding createCoding(String codeSystem, Enum<?> codeColumnName, Enum<?> selectedIndicatorColumnName, String selectedConditionValue) {
        String code = record.get(codeColumnName);
        if (code != null) {
            String selectedColumnValue = record.get(selectedIndicatorColumnName);
            boolean selected = selectedConditionValue.equals(selectedColumnValue);
            return createCoding(codeSystem, code).setUserSelected(selected);
        }
        return null;
    }

    //    /*
    //     * Wenn kein start/end vorhanden, dann nehme einfach mal an "start unbekannt", "end = Zeitstempel/heute"
    //     */
    //    private  Type convertDateTime() throws Exception {
    //        try {
    //
    //            String e = record.get("Zeitstempel");
    //            if (!StringUtils.isBlank(e)) {
    //                return DateUtil.parseDateTimeType(e);
    //                // Period with only end date
    //                // DateTimeType end = DateUtil.parseDateTimeType(e);
    //                //return new Period().setEndElement(end);
    //            }
    //        } catch (Exception e) {
    //            error("Can not parse Zeitstempel");
    //        }
    //        return null;
    //    }

    /**
     * In den Testdaten leider häufig falsch / täglich wiederholt genutzt
     *
     * @return
     * @throws Exception
     */
    private Type convertPeriod() throws Exception {
        try {
            String s = record.get(Therapiestartdatum);
            String e = record.get(Therapieendedatum);
            if (StringUtils.isBlank(s)) {
                if (StringUtils.isBlank(e)) {
                    // no date given
                    error("cannot administer without effective date");
                    return null;
                }
                // Period with only end date
                DateTimeType end = DateUtil.parseDateTimeType(e);
                return new Period().setEndElement(end);
            }
            DateTimeType start = DateUtil.parseDateTimeType(s);
            if (StringUtils.isBlank(e) || e.equals(s)) {
                // Just a single day
                return start;
            }
            DateTimeType end = DateUtil.parseDateTimeType(e);
            // complete Period
            return new Period().setStartElement(start).setEndElement(end);
        } catch (Exception e) {
            error("Can not parse Therapiestartdatum or Therapieendedatum");
        }
        return null;
    }

    /**
     * @return
     * @throws Exception
     */
    private Ratio getDoseRate() throws Exception {
        String unit = record.get(Einheit);
        if (unit != null) {
            return new Ratio().setNumerator(
                    new Quantity().setValue(getDose())
                            .setUnit(unit).setSystem("http://unitsofmeasure.org")
                            .setCode(Ucum.human2ucum(unit)))
                    .setDenominator(
                            new Quantity().setValue(new BigDecimal(1))
                                    .setSystem("http://XXX")
                                    .setCode(record.get(Darreichungsform)));
        }
        error("Einheit empty for Record");
        return null;
    }

    /**
     * @return
     * @throws Exception
     */
    private BigDecimal getDosesPerDay() throws Exception {
        try {
            return DecimalUtil.parseDecimal(record.get(Anzahl_Dosen_pro_Tag));
        } catch (Exception e) {
            error("Anzahl Dosen pro Tag is not a numerical value for Record");
            return null;
        }
    }

    /**
     * @return
     * @throws Exception
     */
    private BigDecimal getDose() throws Exception {
        try {
            return DecimalUtil.parseDecimal(record.get(Einzeldosis));
        } catch (Exception e) {
            error("Einzeldosis is not a numerical value for Record");
            return null;
        }
    }

    /**
     * @return
     * @throws Exception
     */
    private MedicationStatement parseMedicationStatement() throws Exception {
        MedicationStatement medicationStatement = new MedicationStatement();
        medicationStatement.setId(getEncounterId() + "-MS-" + n++);
        medicationStatement.setStatus(ACTIVE);
        medicationStatement.setMeta(new Meta().addProfile(PROFILE_STM));

        medicationStatement.setMedication(getMedicationReference());

        //        medicationStatement.setMedication(convertMedicationCodeableConcept());
        medicationStatement.setContext(getEncounterReference());
        medicationStatement.setSubject(getPatientReference());
        //        Type p = convertPeriod(); in den Testdaten meist so nicht richtig
        Type p = convertTimestamp();
        medicationStatement.setEffective(p);
        //        medicationStatement.setDateAssertedElement(convertTimestamp());
        medicationStatement.addDosage(convertDosageStatement());
        return medicationStatement;
    }

    /**
     * @return
     * @throws Exception
     */
    private DateTimeType convertTimestamp() throws Exception {
        try {
            return DateUtil.parseDateTimeType(record.get(Zeitstempel));
        } catch (Exception e) {
            error("Can not parse timestamp");
            return null;
        }
    }

    /**
     * @return
     * @throws Exception
     */
    private Quantity convertQuantity() throws Exception {
        BigDecimal value = null;
        String doseCount = record.get(Anzahl_Dosen_pro_Tag);
        try {
            value = DecimalUtil.parseDecimal(doseCount);
        } catch (Exception e) {
            warning("no dose defined");
            return new SimpleQuantity().setUnit(doseCount);
        }
        String ucum = "1"; // see https://ucum.org/ucum.html#section-Examples-for-some-Non-Units.
        String synonym = record.get(Darreichungsform);
        return new SimpleQuantity().setValue(value).setUnit(synonym).setSystem("http://unitsofmeasure.org").setCode(ucum);
    }

    /**
     * @return
     * @throws Exception
     */
    private MedicationAdministrationDosageComponent convertDosageAdministration() throws Exception {
        return new MedicationAdministrationDosageComponent().setDose(convertQuantity());
    }

    /**
     * @return
     * @throws Exception
     */
    private Dosage convertDosageStatement() throws Exception {
        Dosage d = new Dosage();
        d.addDoseAndRate().setDose(convertQuantity());
        return d;
    }

    /**
     * @return
     * @throws Exception
     */
    private String getDoseUnit() throws Exception {
        String doseUnit = record.get("Einheit");
        if (doseUnit != null) {
            return doseUnit;
        }
        error("Einheit empty");
        return null;
    }
}
