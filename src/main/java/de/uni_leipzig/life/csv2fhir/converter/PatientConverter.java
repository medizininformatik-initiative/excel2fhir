package de.uni_leipzig.life.csv2fhir.converter;

import static de.uni_leipzig.life.csv2fhir.TableIdentifier.Person;

import static de.uni_leipzig.life.csv2fhir.converter.PatientConverter.Person_Columns.Geburtsdatum;
import static de.uni_leipzig.life.csv2fhir.converter.PatientConverter.Person_Columns.Geschlecht;
import static de.uni_leipzig.life.csv2fhir.converter.PatientConverter.Person_Columns.Krankenkasse;
import static de.uni_leipzig.life.csv2fhir.converter.PatientConverter.Person_Columns.Nachname;
import static de.uni_leipzig.life.csv2fhir.converter.PatientConverter.Person_Columns.Vorname;
import static org.hl7.fhir.r4.model.Enumerations.AdministrativeGender.FEMALE;
import static org.hl7.fhir.r4.model.Enumerations.AdministrativeGender.MALE;
import static org.hl7.fhir.r4.model.Enumerations.AdministrativeGender.OTHER;
import static org.hl7.fhir.r4.model.Enumerations.AdministrativeGender.UNKNOWN;

import java.util.Collections;
import java.util.List;

import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.Address.AddressType;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.HumanName.NameUse;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Identifier.IdentifierUse;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;

import com.google.common.base.Strings;

import de.uni_leipzig.imise.validate.FHIRValidator;
import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.ConverterOptions;
import de.uni_leipzig.life.csv2fhir.ConverterResult;
import de.uni_leipzig.life.csv2fhir.TableColumnIdentifier;

/**
 * @author jheuschkel (19.10.2020), AXS (05.11.2021)
 */
public class PatientConverter extends Converter {

    /**
     * toString() result of these enum values are the names of the columns in the
     * correspunding excel sheet.
     */
    public static enum Person_Columns implements TableColumnIdentifier {
        Vorname,
        Nachname,
        Geburtsdatum,
        Geschlecht,
        Krankenkasse
    }

    // ISO 3166-2:DE, as bound by the German address profile. Keep human-readable Excel values.
    private static final java.util.Map<String, String> GERMAN_STATES = java.util.Map.ofEntries(
            java.util.Map.entry("Baden-Württemberg", "DE-BW"), java.util.Map.entry("Bayern", "DE-BY"),
            java.util.Map.entry("Berlin", "DE-BE"), java.util.Map.entry("Brandenburg", "DE-BB"),
            java.util.Map.entry("Bremen", "DE-HB"), java.util.Map.entry("Hamburg", "DE-HH"),
            java.util.Map.entry("Hessen", "DE-HE"), java.util.Map.entry("Mecklenburg-Vorpommern", "DE-MV"),
            java.util.Map.entry("Niedersachsen", "DE-NI"), java.util.Map.entry("Nordrhein-Westfalen", "DE-NW"),
            java.util.Map.entry("Rheinland-Pfalz", "DE-RP"), java.util.Map.entry("Saarland", "DE-SL"),
            java.util.Map.entry("Sachsen", "DE-SN"), java.util.Map.entry("Sachsen-Anhalt", "DE-ST"),
            java.util.Map.entry("Schleswig-Holstein", "DE-SH"), java.util.Map.entry("Thüringen", "DE-TH"));

    /**  */
    String PROFILE = "https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/Patient";
    // @see
    // https://simplifier.net/MedizininformatikInitiative-ModulPerson/PatientIn

    /**
     * @param record
     * @param previousRecordPID
     * @param result
     * @param validator
     * @param options
     * @throws Exception
     */
    public PatientConverter(CSVRecord record, String previousRecordPID, ConverterResult result, FHIRValidator validator,
            ConverterOptions options) throws Exception {
        super(record, previousRecordPID, result, validator, options);
    }

    @Override
    protected List<Resource> convertInternal() throws Exception {
        Patient patient = new Patient();
        patient.setMeta(new Meta().addProfile(PROFILE));
        patient.setId(getPatientId());
        patient.setIdentifier(parseIdentifier());
        patient.addName(parseName());
        patient.setGender(parseGender());
        patient.setBirthDateElement(parseDate(Geburtsdatum));
        patient.addAddress(parseAddress());
        patient.setDeceased(ClinicalValues.date(ClinicalValues.get(this, ClinicalValues.Column.Sterbezeitpunkt)));
        patient.addGeneralPractitioner(parseHealthProvider());
        // String resourceAsJson =
        // OutputFileType.JSON.getParser().setPrettyPrint(true).encodeResourceToString(patient);
        // // for debug
        // Sys.out1(resourceAsJson);
        return Collections.singletonList(patient);
    }

    /**
     * @return
     * @throws Exception
     */
    private List<Identifier> parseIdentifier() throws Exception {
        Identifier identifier = new Identifier()
                .setSystem("https://" + getDIZId() + ".de/pid")
                .setValue(getPatientId())
                .setUse(IdentifierUse.USUAL)
                .setType(createCodeableConcept("http://terminology.hl7.org/CodeSystem/v2-0203", "MR"));
        return Collections.singletonList(identifier);
    }

    /**
     * @return
     */
    private HumanName parseName() {
        String forename = get(Vorname);
        String surname = get(Nachname);
        HumanName humanName = new HumanName();
        // for unknown reasons a Data Absent Reason in name is not valid -> so we set
        // dummy names
        if (Strings.isNullOrEmpty(surname)) {
            surname = "DUMMY_SURNAME";
        }
        if (Strings.isNullOrEmpty(surname)) { // should never happen while we must set the dummy name to be valid
            warning("Empty " + Nachname + " -> Create Data Absent Reason \"unknown\"");
            StringType familyElement = humanName.getFamilyElement();
            familyElement.addExtension(DATA_ABSENT_REASON_UNKNOWN);
        } else {
            humanName.setFamily(surname).setUse(NameUse.OFFICIAL);
        }
        // same dummy name reason here
        if (Strings.isNullOrEmpty(forename)) {
            forename = "DUMMY_NAME";
        }
        if (Strings.isNullOrEmpty(forename)) {
            warning("Empty " + Vorname + " -> Create Data Absent Reason \"unknown\"");
            StringType givenElement = humanName.addGivenElement();
            givenElement.addExtension(DATA_ABSENT_REASON_UNKNOWN);
        } else {
            for (String name : forename.split(" ")) {
                humanName.addGiven(name);
            }
        }
        return humanName;
    }

    /**
     * @return
     * @throws Exception
     */
    private AdministrativeGender parseGender() throws Exception {
        String gender = get(Geschlecht);
        if (gender != null) {
            if (gender.length() != 0) {
                switch (gender) {
                case "m":
                case "male":
                case "männlich":
                    return MALE;
                case "w":
                case "weiblich":
                case "female":
                case "f":
                    return FEMALE;
                case "d":
                case "divers":
                case "other":
                case "unbestimmt":
                    return OTHER;
                case "x":
                case "unbekannt":
                case "unknown":
                    return UNKNOWN;
                default:
                    throw new Exception("Error on " + Person + ": " + Geschlecht + " <" + gender
                            + "> not parsable for Record: " + this);
                }
            }
            warning("Geschlecht empty for Record");
            return UNKNOWN;
        }
        warning("Geschlecht not found");
        return UNKNOWN;
    }

    /**
     * @return
     */
    private Address parseAddress() {
        Address address = new Address();
        String street = ClinicalValues.get(this, ClinicalValues.Column.Straße);
        if (street != null) address.addLine(street);
        address.setPostalCode(ClinicalValues.get(this, ClinicalValues.Column.Postleitzahl));
        address.setCity(ClinicalValues.get(this, ClinicalValues.Column.Ort));
        String country = ClinicalValues.get(this, ClinicalValues.Column.Land);
        address.setCountry(country);
        String state = ClinicalValues.get(this, ClinicalValues.Column.Bundesland);
        address.setState("DE".equals(country) ? GERMAN_STATES.getOrDefault(state == null ? "" : state, state) : state);
        return address.isEmpty() ? getDataAbsentAddress() : address.setType(AddressType.BOTH);
    }

    /**
     * @return an address filled with UNKNOWN data absent reasons
     */
    public Address getDataAbsentAddress() {
        Address address = new Address();
        address.addExtension(DATA_ABSENT_REASON_UNKNOWN);
        // address.getCityElement().addExtension(getUnknownDataAbsentReason());
        // address.getPostalCodeElement().addExtension(getUnknownDataAbsentReason());
        return address;
    }

    /**
     * @return
     * @throws Exception
     */
    private Reference parseHealthProvider() throws Exception {
        String practitioner = get(Krankenkasse);
        if (!Strings.isNullOrEmpty(practitioner)) {
            return new Reference().setDisplay(practitioner);
        }
        info(Krankenkasse + " empty for Record");
        return null;
    }
}
