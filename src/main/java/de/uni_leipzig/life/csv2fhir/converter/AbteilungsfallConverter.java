package de.uni_leipzig.life.csv2fhir.converter;

import java.text.Normalizer;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Encounter.EncounterLocationComponent;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;

import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.utils.DateUtil;

class Fachabteilungsschluessel {

    /**
     *
     */
    static Map<String, String> dict = new HashMap<>();
    {
        add("0100", "Innere Medizin");
        add("0200", "Geriatrie");
        add("0300", "Kardiologie");
        add("0400", "Nephrologie");
        add("0500", "Hämatologie und internistische Onkologie");
        add("0600", "Endokrinologie");
        add("0700", "Gastroenterologie");
        add("0800", "Pneumologie");
        add("0900", "Rheumatologie");
        add("1000", "Padiatrie");
        add("1100", "Kinderkardiologie");
        add("1200", "Neonatologie");
        add("1300", "Kinderchirurgie");
        add("1400", "Lungen- und Bronchialheilkunde");
        add("1500", "Allgemeine Chirurgie");
        add("1600", "Unfallchirurgie");
        add("1700", "Neurochirurgie");
        add("1800", "Gefäßchirurgie");
        add("1900", "Plastische Chirurgie");
        add("2000", "Thoraxchirurgie");
        add("2100", "Herzchirurgie");
        add("2200", "Urologie");
        add("2300", "Orthopädie");
        add("2400", "Frauenheilkunde und Geburtshilfe");
        add("2500", "Geburtshilfe");
        add("2600", "Hals-, Nasen-, Ohrenheilkunde");
        add("2700", "Augenheilkunde");
        add("2800", "Neurologie");
        add("2900", "Allgemeine Psychiatrie");
        add("3000", "Kinder- und Jugendpsychiatrie");
        add("3100", "Psychosomatik/Psychotherapie");
        add("3200", "Nuklearmedizin");
        add("3300", "Strahlenheilkunde");
        add("3400", "Dermatologie");
        add("3500", "Zahn- und Kieferheilkunde, Mund- und Kieferchirurgie");
        add("3600", "Intensivmedizin");
        add("2316", "Orthopädie und Unfallchirurgie");
        add("2425", "Frauenheilkunde");
        add("3700", "Sonstige Fachabteilung");
    }

    /**
     * @param code
     * @param text
     */
    private static void add(String code, String text) {
        dict.put(normalize(text), code);
        //        System.out.println(normalize(text));
        //        dict.put(code,normalize(text));
    }

    /**
     * @param s
     * @return
     */
    private static String normalize(String s) {
        // "Hämatologie und internistische Onkologie" --> hamatologieundinternistischeonkologie
        return Normalizer.normalize(s, Normalizer.Form.NFD).toLowerCase().replaceAll("[^a-z]", "");
    }

    /**
     * @param text
     * @return
     */
    public String lookupCode(String text) {
        return dict.get(normalize(text));
    }

}

public class AbteilungsfallConverter extends Converter {

    // Simple counter to generate unique identifier
    static int n = 1;

    Fachabteilungsschluessel fachabteilungsschluessel = new Fachabteilungsschluessel();
    //https://www.tmf-ev.de/MII/FHIR/ModulFall/Terminologien.html
    //https://www.medizininformatik-initiative.de/fhir/core/ValueSet/Fachabteilungsschluessel

    String PROFILE = "https://www.medizininformatik-initiative.de/fhir/core/modul-fall/StructureDefinition/Encounter/Abteilungsfall";
    // https://simplifier.net/medizininformatikinitiative-modulfall/abteilungsfall-duplicate-2
    // https://simplifier.net/medizininformatikinitiative-modulfall/example-abteilungsfall

    /*
     * NotSupported : Terminology service failed while validating code ''
     * (system ''): Cannot retrieve valueset
     * 'https://www.medizininformatik-initiative.de/fhir/core/modul-fall/
     * ValueSet/Abteilungsfallklasse' Invalid : Instance count for
     * 'Encounter.serviceType.coding:fab' is 0, which is not within the
     * specified cardinality of 1..1 Invalid : Instance count for
     * 'Encounter.location' is 0, which is not within the specified cardinality
     * of 1..*
     */
    public AbteilungsfallConverter(CSVRecord record) throws Exception {
        super(record);
    }

    @Override
    public List<Resource> convert() throws Exception {
        Encounter encounter = new Encounter();
        encounter.setId(getEncounterId() + "-A-" + n++);
        encounter.setMeta(new Meta().addProfile(PROFILE));
        encounter.setStatus(Encounter.EncounterStatus.FINISHED);
        encounter.setClass_(convertClass_());
        encounter.setServiceType(convertServiceType());
        encounter.setSubject(getPatientReference());
        encounter.setPeriod(convertPeriod());
        //        encounter.setLocation(convertLocation()); bringt nichts
        encounter.setPartOf(getEncounterReference());
        return Collections.singletonList(encounter);
    }

    /**
     * @return
     */
    private static Coding convertClass_() {
        return new Coding().setCode("ub").setSystem(
                "https://www.medizininformatik-initiative.de/fhir/core/modul-fall/CodeSystem/Abteilungsfallklasse");
    }

    /**
     * @return
     * @throws Exception
     */
    private CodeableConcept convertServiceType() throws Exception {
        String text = record.get("Fachabteilung");
        if (text != null) {
            String code = fachabteilungsschluessel.lookupCode(text);
            if (code == null) {
                code = text;
            }
            return new CodeableConcept().addCoding(new Coding().setSystem(
                    "https://www.medizininformatik-initiative.de/fhir/core/CodeSystem/Fachabteilungsschluessel").setCode(code)
                    .setDisplay(text));
        }
        error("Fachabteilung empty for Record");
        return null;
    }

    /**
     * @return
     * @throws Exception
     */
    private List<EncounterLocationComponent> convertLocation() throws Exception {
        EncounterLocationComponent elc = new EncounterLocationComponent();
        Identifier i = new Identifier();
        i.setSystem("https://diz.mii.de/fhir/CodeSystem/TestOrganisationAbteilungen");
        i.setValue(record.get("Fachabteilung"));
        Reference r = new Reference();
        r.setIdentifier(i);
        elc.setLocation(r);
        elc.setStatus(Encounter.EncounterLocationStatus.COMPLETED);
        elc.setPeriod(convertPeriod());
        return Collections.singletonList(elc);
    }
    /**
     * @return
     * @throws Exception
     */
    private Period convertPeriod() throws Exception {
        try {
            return new Period().setStartElement(DateUtil.parseDateTimeType(record.get("Startdatum"))).setEndElement(DateUtil
                    .parseDateTimeType(record.get("Enddatum")));
        } catch (Exception e) {
            warning("Can not parse Startdatum or Enddatum for Record");
            return null;
        }
    }
}
