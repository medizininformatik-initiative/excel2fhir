package de.uni_leipzig.life.csv2fhir.converter;

import static de.uni_leipzig.life.csv2fhir.TableIdentifier.Person;
import static de.uni_leipzig.life.csv2fhir.converter.PersonConverter.Person_Columns.Anschrift;
import static de.uni_leipzig.life.csv2fhir.converter.PersonConverter.Person_Columns.Geburtsdatum;
import static de.uni_leipzig.life.csv2fhir.converter.PersonConverter.Person_Columns.Geschlecht;
import static de.uni_leipzig.life.csv2fhir.converter.PersonConverter.Person_Columns.Krankenkasse;
import static de.uni_leipzig.life.csv2fhir.converter.PersonConverter.Person_Columns.Nachname;
import static de.uni_leipzig.life.csv2fhir.converter.PersonConverter.Person_Columns.Vorname;
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
import de.uni_leipzig.life.csv2fhir.ConverterResult;
import de.uni_leipzig.life.csv2fhir.TableColumnIdentifier;

/**
 * @author jheuschkel (19.10.2020), AXS (05.11.2021)
 */
public class PersonConverter extends Converter {

    /**
     *
     */
    public static enum Person_Columns implements TableColumnIdentifier {
        Vorname,
        Nachname,
        Anschrift,
        Geburtsdatum,
        Geschlecht,
        Krankenkasse
    }

    /**  */
    String PROFILE = "https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/Patient";
    // @see https://simplifier.net/MedizininformatikInitiative-ModulPerson/PatientIn

    /**
     * @param record
     * @param result
     * @param validator
     * @throws Exception
     */
    public PersonConverter(CSVRecord record, ConverterResult result, FHIRValidator validator) throws Exception {
        super(record, result, validator);
    }

    /**
     * Resets the static index counter
     */
    public static void reset() {
        //no static counter to reset at the moment
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
        patient.addGeneralPractitioner(parseHealthProvider());
        //        String resourceAsJson = OutputFileType.JSON.getParser().setPrettyPrint(true).encodeResourceToString(patient); // for debug
        //        Sys.out1(resourceAsJson);
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
        //for unknown reasons a Data Absent Reason in name is not valid -> so we set dummy names
        if (Strings.isNullOrEmpty(surname)) {
            surname = "DUMMY_SURNAME";
        }
        if (Strings.isNullOrEmpty(surname)) { //should never happen while we must set the dummy name to be valid
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
                    throw new Exception("Error on " + Person + ": " + Geschlecht + " <" + gender + "> not parsable for Record: " + this);
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
        String address = get(Anschrift);
        Address addressResource;
        if (address != null) {
            addressResource = new Address();
            String[] addressSplitByComma = address.split(",");
            if (addressSplitByComma.length == 2) {
                String[] addressPlzAndCity = addressSplitByComma[1].split(" ");
                String plz = addressPlzAndCity[1];
                StringBuilder city = new StringBuilder();
                for (int i = 2; i < addressPlzAndCity.length; i++) {
                    city.append(addressPlzAndCity[i]);
                }
                List<StringType> l = Collections.singletonList(new StringType(addressSplitByComma[0]));
                addressResource.setCity(city.toString()).setPostalCode(plz).setLine(l);
            } else {
                // "12345 ORT"
                String[] addressPlzAndCity = address.split(" ");
                if (addressPlzAndCity.length == 2) {
                    String plz = addressPlzAndCity[0];
                    String city = addressPlzAndCity[1];
                    addressResource.setCity(city).setPostalCode(plz).setText(address);
                } else {
                    addressResource.setText(address);
                }
            }
            return addressResource.setType(AddressType.BOTH).setCountry("DE");
        }
        warning("On " + Person + ": " + Anschrift + " empty. " + this);
        return getDataAbsentAddress(); //needed to be KDS compliant
    }

    /**
     * @return an address filled with UNKNOWN data absent reasons
     */
    public Address getDataAbsentAddress() {
        Address address = new Address();
        address.addExtension(DATA_ABSENT_REASON_UNKNOWN);
        //address.getCityElement().addExtension(getUnknownDataAbsentReason());
        //address.getPostalCodeElement().addExtension(getUnknownDataAbsentReason());
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
