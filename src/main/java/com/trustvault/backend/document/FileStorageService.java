
package com.trustvault.backend.document;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;

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
            throw new IllegalArgumentException("Please select a file");
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
            throw new IllegalArgumentException("Invalid filename");
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

        String storedName = UUID.randomUUID() + "." + extension;
        Path destination = storageDirectory.resolve(storedName)
                .normalize();

        if (!destination.getParent().equals(storageDirectory)) {
            throw new IllegalArgumentException("Invalid file path");
        }

        try (InputStream input = file.getInputStream()) {
            Files.copy(input, destination);
        } catch (IOException ex) {
            Files.deleteIfExists(destination);
            throw ex;
        }

        return storedName;
    }

    public void deleteStoredFile(String storedName) {
        if (storedName == null || storedName.isBlank()) {
            return;
        }

        Path filePath = storageDirectory.resolve(storedName)
                .normalize();

        if (!filePath.getParent().equals(storageDirectory)) {
            throw new IllegalArgumentException("Invalid file path");
        }

        try {
            Files.deleteIfExists(filePath);
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Could not delete stored file", ex);
        }
    }
}
