package de.uni_leipzig.life.csv2fhir.converter;

import static de.uni_leipzig.life.csv2fhir.converterFactory.AbteilungsfallConverterFactory.NeededColumns.Enddatum;
import static de.uni_leipzig.life.csv2fhir.converterFactory.AbteilungsfallConverterFactory.NeededColumns.Fachabteilung;
import static de.uni_leipzig.life.csv2fhir.converterFactory.AbteilungsfallConverterFactory.NeededColumns.Startdatum;

import java.util.Collections;
import java.util.List;

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

import de.uni_leipzig.imise.utils.CodeSystemMapper;
import de.uni_leipzig.life.csv2fhir.Converter;

/**
 * @author fheuschkel (29.10.2020), fmeinecke, AXS
 */
public class AbteilungsfallConverter extends Converter {

    /** Simple counter to generate unique identifier */
    static int n = 1;

    /**
     * Maps from human readable department description to the number code for
     * the department.
     */
    private final CodeSystemMapper departmentKeyMapper = new CodeSystemMapper("Fachabteilungsschluessel.map");

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
        return createCoding("https://www.medizininformatik-initiative.de/fhir/core/modul-fall/CodeSystem/Abteilungsfallklasse", "ub");
    }

    /**
     * @return
     * @throws Exception
     */
    private CodeableConcept convertServiceType() throws Exception {
        return createCodeableConcept(Fachabteilung, departmentKeyMapper);
    }

    /**
     * @return
     * @throws Exception
     */
    private List<EncounterLocationComponent> convertLocation() throws Exception {
        EncounterLocationComponent elc = new EncounterLocationComponent();
        Identifier i = new Identifier();
        i.setSystem("https://diz.mii.de/fhir/CodeSystem/TestOrganisationAbteilungen");
        i.setValue(record.get(Fachabteilung));
        Reference r = new Reference();
        r.setIdentifier(i);
        elc.setLocation(r);
        elc.setStatus(Encounter.EncounterLocationStatus.COMPLETED);
        elc.setPeriod(convertPeriod());
        return Collections.singletonList(elc);
    }

    /**
     * @return
     */
    private Period convertPeriod() {
        return createPeriod(Startdatum, Enddatum);
    }

}
