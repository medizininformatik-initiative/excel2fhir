package de.uni_leipzig.life.csv2fhir.converter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.hl7.fhir.r4.model.Attachment;
import org.junit.Test;

public class DocumentReferenceConverterTest {

    @Test
    public void createAttachmentEmbedsReadableFiles() throws Exception {
        Path file = Files.createTempFile("document-reference", ".txt");
        Files.writeString(file, "test", StandardCharsets.UTF_8);

        Attachment attachment = DocumentReferenceConverter.createAttachment(file, true);

        assertEquals("test", new String(attachment.getData(), StandardCharsets.UTF_8));
        assertEquals(4, attachment.getSize());
        assertEquals(file.getFileName().toString(), attachment.getTitle());
        assertNull(attachment.getUrl());
    }

    @Test
    public void createAttachmentMarksMissingEmbeddedDataAsAbsent() throws Exception {
        Attachment attachment = DocumentReferenceConverter.createAttachment(null, true);

        assertNull(attachment.getData());
        assertTrue(attachment.getDataElement().hasExtension());
        assertNull(attachment.getSizeElement().getValue());
        assertTrue(attachment.getCreationElement().hasExtension());
        assertEquals("text", attachment.getContentType());
    }

    @Test
    public void createAttachmentKeepsUrlForMissingExternalFiles() throws Exception {
        Path file = Path.of("does-not-exist.pdf");

        Attachment attachment = DocumentReferenceConverter.createAttachment(file, false);

        assertTrue(attachment.hasUrl());
        assertNull(attachment.getSizeElement().getValue());
        assertTrue(attachment.getCreationElement().hasExtension());
        assertEquals("does-not-exist.pdf", attachment.getTitle());
    }

    @Test
    public void createAttachmentDoesNotReadDirectoriesAsEmbeddedFiles() throws Exception {
        Path directory = Files.createTempDirectory("document-reference");

        Attachment attachment = DocumentReferenceConverter.createAttachment(directory, true);

        assertNull(attachment.getData());
        assertTrue(attachment.getDataElement().hasExtension());
        assertNull(attachment.getSizeElement().getValue());
        assertTrue(attachment.getCreationElement().hasExtension());
    }
}
