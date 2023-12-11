package de.uni_leipzig.life.csv2fhir.converter;

import static de.uni_leipzig.life.csv2fhir.ConverterOptions.IntOption.START_ID_DOCUMENT_REFERENCE;
import static de.uni_leipzig.life.csv2fhir.TableIdentifier.DocumentReference;
import static de.uni_leipzig.life.csv2fhir.converter.DocumentReferenceConverter.DocumentReference_Columns.Dateipfad;
import static de.uni_leipzig.life.csv2fhir.converter.DocumentReferenceConverter.DocumentReference_Columns.Embed;
import static java.util.Collections.singletonList;
import static org.apache.logging.log4j.util.Strings.isBlank;

import java.io.File;
import java.io.IOException;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.Date;
import java.util.List;

import org.apache.commons.csv.CSVRecord;
import org.hl7.fhir.r4.model.Attachment;
import org.hl7.fhir.r4.model.CodeableConcept;
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
 * @author FAM (24.07.2023), AXS (06.08.23)
 */
public class DocumentReferenceConverter extends Converter {

    /**
     * toString() result of these enum values are the names of the columns in
     * the correspunding excel sheet.
     */
    public static enum DocumentReference_Columns implements TableColumnIdentifier {
        Dateipfad,
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
        String id = (isBlank(encounterId) ? getPatientId() : encounterId) + ResourceIdSuffix.DOCUMENT_REFERENCE + nextId;
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

        //TODO: Enable DocumentResource category filling by excel input data
        CodeableConcept category = createCodeableConcept("http://dvmd.de/fhir/CodeSystem/kdl", "AD0101", "Arztberichte", null, "2023");
        documentReference.setCategory(singletonList(category));

        //TODO: Enable DocumentResource type filling by excel input data
        CodeableConcept type = createCodeableConcept("http://dvmd.de/fhir/CodeSystem/kdl", "AD010104", "Entlassungsbericht extern", null, "2023");
        documentReference.setType(type);

        //TODO: Enable DocumentResource securityLabel filling by excel input data
        CodeableConcept securityLabel1 = createCodeableConcept("http://terminology.hl7.org/CodeSystem/v3-Confidentiality", "L", "low", null, "4.0.1");
        CodeableConcept securityLabel2 = createCodeableConcept("http://terminology.hl7.org/CodeSystem/v3-ActReason", "HTEST", "test health data", null, "4.0.1");
        documentReference.setSecurityLabel(List.of(securityLabel1, securityLabel2));

        DocumentReferenceContextComponent context = new DocumentReferenceContextComponent();
        context.setEncounter(getEncounterReferences());
        documentReference.setContext(context);

        boolean embed = isYesValue(get(Embed));
        String filePath = get(Dateipfad);
        if (!isBlank(filePath)) {
            Attachment attachment = createAttachment(Paths.get(filePath), embed);
            documentReference.setContent(singletonList(new DocumentReferenceContentComponent(attachment)));
        }

        return singletonList(documentReference);
    }

    /**
     * Create Attachment from file
     *
     * @param path path to file
     * @param embed if <code>true</code> embed file as binary else just add URL
     * @return new FHIR Attachment
     * @throws IOException
     */
    public static Attachment createAttachment(Path path, boolean embed) throws IOException {
        Attachment attachment = new Attachment();
        File file = path.toFile();
        if (embed) {
            if (file.canRead()) {
                // Read unlimited
                byte[] bytes = Files.readAllBytes(path);
                attachment.setData(bytes);
                // optional
                attachment.setSize(bytes.length);
            } else {
                attachment.getDataElement().addExtension(DATA_ABSENT_REASON_ERROR);
                attachment.getSizeElement().addExtension(DATA_ABSENT_REASON_UNKNOWN);
            }
        } else {
            attachment.setUrl(path.toUri().toURL().toExternalForm());
            attachment.setSize((int) Files.size(path));
        }
        attachment.setContentType(getContentType(file));
        // optional
        attachment.setTitle(path.getFileName().toString());
        // When attachment was first created
        // For now: take file creation time
        // Better (?): date, when document was written
        if (file.canRead()) {
            attachment.setCreation(new Date(((FileTime) Files.getAttribute(path, "creationTime")).toMillis()));
        } else {
            attachment.getCreationElement().addExtension(DATA_ABSENT_REASON_UNKNOWN);
        }
        return attachment;
    }

    /**
     * @param file
     * @return
     */
    public static String getContentType(File file) {
        return URLConnection.guessContentTypeFromName(file.getName());
    }

}
