
package com.trustvault.backend.document;

import com.trustvault.backend.audit.AuditService;
import com.trustvault.backend.user.AppUser;
import com.trustvault.backend.user.AppUserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final AppUserRepository appUserRepository;
    private final FileStorageService fileStorageService;
    private final DocumentPermissionRepository permissionRepository;
    private final AuditService auditService;

    public DocumentService(
            DocumentRepository documentRepository,
            AppUserRepository appUserRepository,
            FileStorageService fileStorageService,
            DocumentPermissionRepository permissionRepository,
            AuditService auditService) {

        this.documentRepository = documentRepository;
        this.appUserRepository = appUserRepository;
        this.fileStorageService = fileStorageService;
        this.permissionRepository = permissionRepository;
        this.auditService = auditService;
    }

    @Transactional
    public Document uploadDocument(
            String name,
            String category,
            MultipartFile file) throws IOException {

        AppUser owner = getAuthenticatedUser();

        if (!"OWNER".equals(owner.getRole())) {
            throw new IllegalArgumentException(
                    "Only document owners can upload documents");
        }

        if (name == null || name.isBlank()
                || name.trim().length() > 200) {
            throw new IllegalArgumentException(
                    "Invalid document name");
        }

        if (category == null || category.isBlank()
                || category.trim().length() > 100) {
            throw new IllegalArgumentException(
                    "Invalid document category");
        }

        String storedName = fileStorageService.store(file);

        try {
            Document document = new Document();
            document.setName(name.trim());
            document.setCategory(category.trim());
            document.setStoragePath(storedName);
            document.setOwner(owner);
            document.setUploadedAt(LocalDateTime.now());

            Document savedDocument =
                    documentRepository.save(document);

            auditService.record(
                    owner.getUsername(),
                    "DOCUMENT_UPLOAD",
                    "Uploaded document ID "
                            + savedDocument.getId()
                            + ", category "
                            + savedDocument.getCategory());

            return savedDocument;

        } catch (RuntimeException ex) {
            fileStorageService.deleteStoredFile(storedName);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public List<Document> getMyDocuments() {
        AppUser user = getAuthenticatedUser();
        return documentRepository.findByOwner_Id(user.getId());
    }

    @Transactional(readOnly = true)
    public List<DocumentPermission> getSharedWithMe() {
        AppUser user = getAuthenticatedUser();
        return permissionRepository.findByUser_Id(user.getId());
    }

    @Transactional(readOnly = true)
    public Document getMyDocumentById(Long documentId) {
        AppUser user = getAuthenticatedUser();

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() ->
                        new NoSuchElementException(
                                "Document not found"));

        // The owner can access their own document.
        if (document.getOwner().getId().equals(user.getId())) {
            return document;
        }

        // Other users must have READ or EDIT permission.
        DocumentPermission permission = permissionRepository
                .findByDocument_IdAndUser_Id(
                        documentId, user.getId())
                .orElseThrow(() ->
                        new NoSuchElementException(
                                "Document not found"));

        String access = permission.getPermission()
                .toUpperCase(Locale.ROOT);

        if (!access.equals("READ") && !access.equals("EDIT")) {
            throw new NoSuchElementException(
                    "Document not found");
        }

        return document;
    }

    /**
     * Downloads a document only after checking the user's access.
     * The stored file path is never supplied by the client.
     */
    @Transactional
    public DownloadedDocument downloadDocument(Long documentId)
            throws IOException {

        // Verify document ownership or READ/EDIT permission.
        Document document = getMyDocumentById(documentId);
        AppUser user = getAuthenticatedUser();

        Path filePath = fileStorageService.getStoredFile(
                document.getStoragePath());

        byte[] content = Files.readAllBytes(filePath);

        // Record successful downloads.
        auditService.record(
                user.getUsername(),
                "DOCUMENT_DOWNLOAD",
                "Downloaded document ID " + document.getId());

        // Use the stored file's extension in the download filename.
        String downloadFilename = document.getName();
        String storedFilename = filePath.getFileName().toString();

        int extensionIndex = storedFilename.lastIndexOf('.');

        if (extensionIndex > 0
                && extensionIndex < storedFilename.length() - 1) {

            String extension =
                    storedFilename.substring(extensionIndex);

            downloadFilename += extension;
        }

        return new DownloadedDocument(
                downloadFilename,
                content);
    }

    public record DownloadedDocument(
            String filename,
            byte[] content) {
    }

    private AppUser getAuthenticatedUser() {
        Authentication authentication =
                SecurityContextHolder.getContext()
                        .getAuthentication();

        String username = authentication.getName();

        return appUserRepository.findByUsername(username)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Authenticated user not found"));
    }
}
