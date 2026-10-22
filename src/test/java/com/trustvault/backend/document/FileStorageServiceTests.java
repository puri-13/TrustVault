package com.trustvault.backend.document;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileStorageServiceTests {

    @TempDir
    Path storageDirectory;

    private FileStorageService fileStorageService;

    @BeforeEach
    void setUp() throws IOException {
        fileStorageService = new FileStorageService(storageDirectory.toString());
    }

    @Test
    void newlyInitializedStorageDirectoryIsReady() {
        assertTrue(fileStorageService.isStorageReady());
    }

    @Test
    void acceptsAllowedSignaturesWithoutTrustingMimeType() throws Exception {
        List<MockMultipartFile> files = List.of(
                file("document.pdf", "text/plain", "%PDF-1.7".getBytes(StandardCharsets.US_ASCII)),
                file("image.png", "application/pdf", new byte[] {
                        (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}),
                file("image.jpg", "text/plain", new byte[] {
                        (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01}),
                file("notes.txt", "application/octet-stream",
                        "TrustVault notes \u2713".getBytes(StandardCharsets.UTF_8)),
                file("report.docx", "text/plain", validDocx()));

        for (MockMultipartFile upload : files) {
            String storedName = fileStorageService.store(upload);
            assertTrue(Files.isRegularFile(
                    fileStorageService.getStoredFile(storedName)));
        }
    }

    @Test
    void rejectsMismatchedSignatureEvenWhenClientMimeClaimsPdf() throws Exception {
        MockMultipartFile upload = file(
                "fake.pdf", "application/pdf",
                "not a PDF".getBytes(StandardCharsets.UTF_8));

        assertThrows(IllegalArgumentException.class,
                () -> fileStorageService.store(upload));
        assertTrue(isStorageEmpty());
    }

    @Test
    void rejectsInvalidUtf8AndBinaryTextFiles() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> fileStorageService.store(file(
                        "invalid.txt", "text/plain", new byte[] {(byte) 0xC3, 0x28})));
        assertThrows(IllegalArgumentException.class,
                () -> fileStorageService.store(file(
                        "binary.txt", "text/plain", new byte[] {'a', 0, 'b'})));
        assertTrue(isStorageEmpty());
    }

    @Test
    void docxMustContainRequiredPackageEntries() throws Exception {
        assertDoesNotThrow(() -> fileStorageService.store(file(
                "valid.docx", "application/octet-stream", validDocx())));

        assertThrows(IllegalArgumentException.class,
                () -> fileStorageService.store(file(
                        "invalid.docx", "application/octet-stream",
                        "PK\u0003\u0004not a document".getBytes(StandardCharsets.US_ASCII))));
    }

    private MockMultipartFile file(String name, String contentType, byte[] content) {
        return new MockMultipartFile("file", name, contentType, content);
    }

    private byte[] validDocx() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("<Types/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write("<document/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return output.toByteArray();
    }

    private boolean isStorageEmpty() throws IOException {
        try (var files = Files.list(storageDirectory)) {
            return files.findAny().isEmpty();
        }
    }
}