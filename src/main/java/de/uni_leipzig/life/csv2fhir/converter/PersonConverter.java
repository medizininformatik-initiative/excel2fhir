package de.uni_leipzig.life.csv2fhir.converter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.HumanName.NameUse;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.utils.DateUtil;

public class PersonConverter extends Converter {

    String PROFILE="https://www.medizininformatik-initiative.de/fhir/core/modul-person/StructureDefinition/Patient";
    // https://simplifier.net/MedizininformatikInitiative-ModulPerson/PatientIn
    
    public PersonConverter(CSVRecord record) {
        super(record);
    }

    @Override
    public List<Resource> convert() throws Exception {
        Patient patient = new Patient();
        String pid = parsePatientId();
        patient.setMeta(new Meta().addProfile(PROFILE));
        patient.setId(pid);
        patient.setIdentifier(Arrays.asList(new Identifier().setValue(pid)));
        patient.addName(parseName());
        patient.setGender(parseSex());
        patient.setBirthDateElement(parseBirthDate());
        patient.addAddress(parseAddress());
        patient.addGeneralPractitioner(parseHealthProvider());
        return Collections.singletonList(patient);
    }

    private HumanName parseName() {
        String forename = record.get("Vorname");
        String surname = record.get("Nachname");


        if (forename != null && surname != null) {
            HumanName humanName = new HumanName().setFamily(surname).setUse(NameUse.OFFICIAL);
            for (String name : forename.split(" ")) {
                humanName.addGiven(name);
            }
            return humanName;
        } else {
            warning("Vorname or Nachname empty for Record");
            return null;
        }
    }

    private Enumerations.AdministrativeGender parseSex() throws Exception {
        String sex = record.get("Geschlecht");
        if (sex != null) {
            if (sex.length() != 0) {
                switch (sex) {
                    case "m":
                    case "männlich":
                        return Enumerations.AdministrativeGender.MALE;
                    case "w":
                    case "weiblich":
                        return Enumerations.AdministrativeGender.FEMALE;
                    case "d":
                    case "divers":
                        return Enumerations.AdministrativeGender.OTHER;
                    default:
                        throw new Exception("Error on Patient: Geschlecht <"+ sex+ ">not parsable for Record: "
                                + record.getRecordNumber() + "! " + record.toString());
                }
            } else {
                error("Geschlecht empty for Record");
                return null;
            }
        } else {
            error("Geschlecht not found");
            return null;
        }
    }

    private DateType parseBirthDate() throws Exception {
        String birthday = record.get("Geburtsdatum");
        if (birthday != null) {
            try {
                return DateUtil.parseDateType(birthday);
            } catch (Exception e) {
                error("Can not parse birthday for Record");
                return null;
            }
        } else {
            error("Geburtsdatum empty for Record");
            return null;
        }
    }

    private Address parseAddress() {
        String address = record.get("Anschrift");
        if (address != null) {
            String[] addressSplitByComma = address.split(",");
            if (addressSplitByComma.length == 2) {
                String[] addressPlzAndCity = addressSplitByComma[1].split(" ");
                String plz = addressPlzAndCity[1];
                StringBuilder city = new StringBuilder();
                for (int i = 2; i < addressPlzAndCity.length; i++) {
                    city.append(addressPlzAndCity[i]);
                }
                return new Address().setCity(city.toString()).setPostalCode(plz).setText(address);
            } else {
                System.out.println("On Patient: Can not parse Address for Record: "
                        + record.getRecordNumber() + "! " + record.toString());
                return null;
            }
        } else {
            warning("On Patient: Anschrift empty for Record");
            return null;
        }
    }

    private Reference parseHealthProvider() throws Exception {
        String practitioner = record.get("Krankenkasse");
        if (practitioner != null) {
            if (practitioner.length() != 0) {
                return new Reference().setDisplay(practitioner);
            } else {
                error("Krankenkasse empty for Record");
                return null;
            }
        } else {
            warning("Column Krankenkasse not found!");
            return null;
        }
    }
}
