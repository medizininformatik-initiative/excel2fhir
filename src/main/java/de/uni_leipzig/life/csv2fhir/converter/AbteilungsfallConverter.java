package de.uni_leipzig.life.csv2fhir.converter;

import java.io.InputStream;
import java.net.URL;
import java.text.Normalizer;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.collections4.map.HashedMap;
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

/**
 * @author fmeinecke (29.10.2021), AXS
 */
@SuppressWarnings("serial")
class Fachabteilungsschluessel extends Properties {

    /** Path to the file with department keys and their textual description. */
    private static final URL DEPARTMENT_KEYS_FILE = ClassLoader.getSystemResource("Fachabteilungsschluessel.map");

    /** Cache to prevent normalizing same strings in again and again. */
    private static Map<Object, String> normalizedCache = new HashedMap<>();

    /**
     * A map from
     */
    public Fachabteilungsschluessel() {
        try (InputStream s = DEPARTMENT_KEYS_FILE.openStream()) {
            load(s);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public synchronized Object put(Object code, Object text) {
        String key = normalize(text);
        String value = String.valueOf(code);
        return super.put(key, value);
    }

    @Override
    public String get(Object keyText) {
        String key = String.valueOf(keyText);
        key = normalize(key);
        return (String) super.get(key);
    }

    /**
     * Replaces in the toString() string of the given object all special alpha
     * characters by they base form and removes all non alpha characters.<br>
     * ("Hämatologie und internistische Onkologie" -->
     * hamatologieundinternistischeonkologie)
     *
     * @param o
     * @return
     */
    private static String normalize(Object o) {
        String normalizedString = normalizedCache.get(o);
        if (normalizedString == null) {
            String s = String.valueOf(o);
            normalizedString = Normalizer.normalize(s, Normalizer.Form.NFD).toLowerCase().replaceAll("[^a-z]", "");
            normalizedCache.put(o, normalizedString);
        }
        return normalizedString;
    }

}

/**
 * @author fheuschkel (29.10.2020), fmeinecke
 */
public class AbteilungsfallConverter extends Converter {

    /** Simple counter to generate unique identifier */
    static int n = 1;

    /**
     * Maps from human readable department description to the number code for
     * the department.
     */
    static Fachabteilungsschluessel fachabteilungsschluessel = new Fachabteilungsschluessel();
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
    private Coding convertClass_() {
        return new Coding().setCode("ub").setSystem("https://www.medizininformatik-initiative.de/fhir/core/modul-fall/CodeSystem/Abteilungsfallklasse");
    }

    /**
     * @return
     * @throws Exception
     */
    private CodeableConcept convertServiceType() throws Exception {
        String text = record.get("Fachabteilung");
        if (text == null) {
            error("Fachabteilung empty for Record");
            return null;
        }
        String code = fachabteilungsschluessel.get(text);
        if (code == null) {
            code = text;
        }
        return new CodeableConcept().addCoding(new Coding()
                .setSystem("https://www.medizininformatik-initiative.de/fhir/core/CodeSystem/Fachabteilungsschluessel")
                .setCode(code)
                .setDisplay(text));
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
            return new Period()
                    .setStartElement(DateUtil.parseDateTimeType(record.get("Startdatum")))
                    .setEndElement(DateUtil.parseDateTimeType(record.get("Enddatum")));
        } catch (Exception e) {
            warning("Can not parse Startdatum or Enddatum for Record");
            return null;
        }
    }
}
