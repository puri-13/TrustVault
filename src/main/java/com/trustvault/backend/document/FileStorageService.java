
package com.trustvault.backend.document;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class FileStorageService {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final long MAX_DOCX_EXPANDED_SIZE = 50L * 1024 * 1024;
    private static final int MAX_DOCX_ENTRIES = 1000;

    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("pdf", "txt", "png", "jpg", "jpeg", "docx");

    private final Path storageDirectory;

    public FileStorageService(
            @Value("${trustvault.storage-dir:${user.home}/TrustVault-Storage}")
            String storageDirectory) throws IOException {

        this.storageDirectory = Paths.get(storageDirectory)
                .toAbsolutePath()
                .normalize();

        Files.createDirectories(this.storageDirectory);
    }

    public String store(MultipartFile file) throws IOException {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                    "Please select a file");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                    "File size must not exceed 10 MB");
        }

        String originalName = file.getOriginalFilename();

        if (originalName == null || originalName.isBlank()
                || originalName.contains("/")
                || originalName.contains("\\")
                || originalName.contains("..")) {
            throw new IllegalArgumentException(
                    "Invalid filename");
        }

        int dot = originalName.lastIndexOf('.');

        if (dot <= 0 || dot == originalName.length() - 1) {
            throw new IllegalArgumentException(
                    "File must have an allowed extension");
        }

        String extension = originalName.substring(dot + 1)
                .toLowerCase(Locale.ROOT);

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException(
                    "Allowed types: PDF, TXT, PNG, JPG, JPEG, DOCX");
        }

        validateContent(file, extension);

        String storedName = UUID.randomUUID() + "." + extension;

        Path destination = storageDirectory.resolve(storedName)
                .normalize();

        if (!destination.getParent().equals(storageDirectory)) {
            throw new IllegalArgumentException(
                    "Invalid file path");
        }

        try (InputStream input = file.getInputStream()) {
            Files.copy(input, destination);
        } catch (IOException ex) {
            Files.deleteIfExists(destination);
            throw ex;
        }

        return storedName;
    }

    public boolean isStorageReady() {
        return Files.isDirectory(storageDirectory)
                && Files.isWritable(storageDirectory);
    }

    private void validateContent(MultipartFile file, String extension)
            throws IOException {
        switch (extension) {
            case "pdf" -> requireSignature(file, new byte[] {'%', 'P', 'D', 'F', '-'});
            case "png" -> requireSignature(file, new byte[] {
                    (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
            case "jpg", "jpeg" -> requireSignature(file, new byte[] {
                    (byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
            case "txt" -> validateText(file);
            case "docx" -> validateDocx(file);
            default -> throw new IllegalArgumentException(
                    "File content type is not supported");
        }
    }

    private void requireSignature(MultipartFile file, byte[] expected)
            throws IOException {
        try (InputStream input = file.getInputStream()) {
            byte[] actual = input.readNBytes(expected.length);
            if (!java.util.Arrays.equals(actual, expected)) {
                throw new IllegalArgumentException(
                        "File content does not match its filename extension");
            }
        }
    }

    private void validateText(MultipartFile file) throws IOException {
        byte[] content = file.getBytes();
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content));
        } catch (CharacterCodingException ex) {
            throw new IllegalArgumentException(
                    "Text files must contain valid UTF-8 text");
        }

        for (byte value : content) {
            if (value == 0) {
                throw new IllegalArgumentException(
                        "Text files must not contain binary data");
            }
        }
    }

    private void validateDocx(MultipartFile file) throws IOException {
        boolean hasContentTypes = false;
        boolean hasDocument = false;
        int entryCount = 0;
        long expandedSize = 0;
        byte[] buffer = new byte[8192];

        try (ZipInputStream zip = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > MAX_DOCX_ENTRIES) {
                    throw invalidDocx();
                }
                hasContentTypes |= "[Content_Types].xml".equals(entry.getName());
                hasDocument |= "word/document.xml".equals(entry.getName());

                int bytesRead;
                while ((bytesRead = zip.read(buffer)) != -1) {
                    expandedSize += bytesRead;
                    if (expandedSize > MAX_DOCX_EXPANDED_SIZE) {
                        throw invalidDocx();
                    }
                }
                zip.closeEntry();
            }
        } catch (java.util.zip.ZipException ex) {
            throw invalidDocx();
        }

        if (!hasContentTypes || !hasDocument) {
            throw invalidDocx();
        }
    }

    private IllegalArgumentException invalidDocx() {
        return new IllegalArgumentException(
                "DOCX file is not a valid document package");
    }

    /**
     * Resolves a stored filename safely and verifies that
     * the requested file exists and is readable.
     *
     * The caller must perform document authorization
     * before using this method.
     */
    public Path getStoredFile(String storedName) throws IOException {

        if (storedName == null || storedName.isBlank()) {
            throw new IOException("Invalid stored filename");
        }

        // Stored names are generated by this service as UUID filenames.
        // Reject path separators and unexpected filename formats.
        if (storedName.contains("/")
                || storedName.contains("\\")
                || storedName.contains("..")) {
            throw new IOException("Invalid stored filename");
        }

        Path filePath = storageDirectory.resolve(storedName)
                .normalize();

        if (!storageDirectory.equals(filePath.getParent())) {
            throw new IOException("Invalid file path");
        }

        if (!Files.isRegularFile(filePath)
                || !Files.isReadable(filePath)) {
            throw new IOException("Stored file not found");
        }

        return filePath;
    }

    public void deleteStoredFile(String storedName) {

        if (storedName == null || storedName.isBlank()) {
            return;
        }

        if (storedName.contains("/")
                || storedName.contains("\\")
                || storedName.contains("..")) {
            throw new IllegalArgumentException(
                    "Invalid stored filename");
        }

        Path filePath = storageDirectory.resolve(storedName)
                .normalize();

        if (!storageDirectory.equals(filePath.getParent())) {
            throw new IllegalArgumentException(
                    "Invalid file path");
        }

        try {
            Files.deleteIfExists(filePath);
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Could not delete stored file", ex);
        }
    }
}
