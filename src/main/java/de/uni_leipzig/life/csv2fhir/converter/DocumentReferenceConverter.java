package de.uni_leipzig.life.csv2fhir.converter;

import static de.uni_leipzig.life.csv2fhir.ConverterOptions.IntOption.START_ID_DOCUMENT_REFERENCE;
import static de.uni_leipzig.life.csv2fhir.TableIdentifier.DocumentReference;
import static de.uni_leipzig.life.csv2fhir.converter.DocumentReferenceConverter.DocumentReference_Columns.Embed;
import static de.uni_leipzig.life.csv2fhir.converter.DocumentReferenceConverter.DocumentReference_Columns.URI;
import static java.util.Collections.singletonList;
import static org.apache.logging.log4j.util.Strings.isBlank;

import java.io.File;
import java.io.IOException;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Vector;

import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.Attachment;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.DocumentReference.DocumentReferenceContentComponent;
import org.hl7.fhir.r4.model.DocumentReference.DocumentReferenceContextComponent;
import org.hl7.fhir.r4.model.DocumentReference.ReferredDocumentStatus;
import org.hl7.fhir.r4.model.Enumerations.DocumentReferenceStatus;
import org.hl7.fhir.r4.model.Resource;

import de.uni_leipzig.imise.validate.FHIRValidator;
import de.uni_leipzig.life.csv2fhir.Converter;
import de.uni_leipzig.life.csv2fhir.ConverterOptions;
import de.uni_leipzig.life.csv2fhir.ConverterResult;
import de.uni_leipzig.life.csv2fhir.TableColumnIdentifier;

/**
 * @author FAM (24.07.2023)
 */
public class DocumentReferenceConverter extends Converter {

    /**
     *
     */
    public static enum DocumentReference_Columns implements TableColumnIdentifier {
        URI,
        Embed,
    }

    /**  */
    // No official profile available
    // Base is: http://hl7.org/fhir/R4/documentreference.html
    // String PROFILE = "https://www.medizininformatik-initiative.de/fhir/core/modul-prozedur/StructureDefinition/Document";
    // https://simplifier.net/medizininformatikinitiative-modulprozeduren/document

    /**
     * @param record
     * @param result
     * @param validator
     * @param options
     * @throws Exception
     */
    public DocumentReferenceConverter(CSVRecord record, ConverterResult result, FHIRValidator validator, ConverterOptions options) throws Exception {
        super(record, result, validator, options);
    }

    @Override
    protected List<Resource> convertInternal() throws Exception {
        DocumentReference documentReference = new DocumentReference();
        int nextId = result.getNextId(DocumentReference, DocumentReference.class, START_ID_DOCUMENT_REFERENCE);
        String encounterId = getEncounterId();
        String id = (isBlank(encounterId) ? getPatientId() : encounterId) + "-D-" + nextId;
        documentReference.setId(id);
        // No official profile available
        // documentReference.setMeta(new Meta().addProfile(PROFILE));
        documentReference.setSubject(getPatientReference());
        // Status of the Reference; always: "This is the current reference for this document."
        documentReference.setStatus(DocumentReferenceStatus.CURRENT);
        // Status of the underlying document; always: "final"
        documentReference.setDocStatus(ReferredDocumentStatus.FINAL);
        documentReference.setDate(new Date());

        // Example:
        // AD0101	Arztberichte
        // AD010104	Entlassungsbericht extern
        // AD010111	Ambulanzbrief
        //
        // 68609-7 Hospital Letter
        // 18842-5 Discharge summary

        // Coding loinc = createCoding("http://loinc.org", "68609-7");
        // CodeableConcept cc = new CodeableConcept();
        // loinc.setVersion("2.74");
        // loinc.setDisplay("Hospital Letter");

        Coding kdl = createCoding("http://dvmd.de/fhir/CodeSystem/kdl", "AD0101");
        kdl.setVersion("2023");
        kdl.setDisplay("Arztberichte");
        documentReference.setCategory(Collections.singletonList(new CodeableConcept(kdl)));

        kdl = createCoding("http://dvmd.de/fhir/CodeSystem/kdl", "AD010104");
        kdl.setVersion("2023");
        kdl.setDisplay("Entlassungsbericht extern");
        documentReference.setType(new CodeableConcept(kdl));

        List<CodeableConcept> cc = new Vector<>();
        Coding c1 = createCoding("http://terminology.hl7.org/CodeSystem/v3-Confidentiality", "L");
        c1.setVersion("4.0.1");
        c1.setDisplay("low");
        cc.add(new CodeableConcept(c1));

        Coding c2 = createCoding("http://terminology.hl7.org/CodeSystem/v3-ActReason", "HTEST");
        c2.setVersion("4.0.1");
        c2.setDisplay("test health data");
        cc.add(new CodeableConcept(c2));

        documentReference.setSecurityLabel(cc);

        DocumentReferenceContextComponent c = new DocumentReferenceContextComponent();
        c.setEncounter(getEncounterReferences());
        documentReference.setContext(c);

        boolean embed = isYesValue(get(Embed));
        Attachment a = createAttachment(Paths.get(get(URI)), embed);
        documentReference.setContent(Collections.singletonList(new DocumentReferenceContentComponent(a)));

        return singletonList(documentReference);
    }

    /**
     * Create Attachment from file
     *
     * @param file
     * @param embed if true embed file as binary else just add URL
     * @return new FHIR Attachment
     * @throws IOException
     */
    public static Attachment createAttachment(Path file, boolean embed) throws IOException {
        Attachment att = new Attachment();

        if (embed) {
            // Read unlimited
            byte[] bytes = Files.readAllBytes(file);
            att.setData(bytes);
            // optional
            att.setSize(bytes.length);
        } else {
            att.setUrl(file.toUri().toURL().toExternalForm());
            att.setSize((int) Files.size(file));
        }
        att.setContentType(getContentType(file.toFile()));
        // optional
        att.setTitle(file.getFileName().toString());
        // When attachment was first created
        // For now: take file creation time
        // Better (?): date, when document was written
        att.setCreation(new Date(((FileTime) Files.getAttribute(file, "creationTime")).toMillis()));
        return att;
    }

    /**
     * @param file
     * @return
     */
    public static String getContentType(File file) {
        return URLConnection.guessContentTypeFromName(file.getName());
    }

}
