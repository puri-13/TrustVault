
package com.trustvault.backend.document;

import com.trustvault.backend.user.AppUser;
import com.trustvault.backend.user.AppUserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import com.trustvault.backend.audit.AuditService;
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
            throw new IllegalArgumentException("Invalid document name");
        }

        if (category == null || category.isBlank()
                || category.trim().length() > 100) {
            throw new IllegalArgumentException("Invalid document category");
        }

        String storedName = fileStorageService.store(file);

        try {
            Document document = new Document();
            document.setName(name.trim());
            document.setCategory(category.trim());
            document.setStoragePath(storedName);
            document.setOwner(owner);
            document.setUploadedAt(LocalDateTime.now());

            Document savedDocument = documentRepository.save(document);

auditService.record(
        owner.getUsername(),
        "DOCUMENT_UPLOAD",
        "Uploaded document ID " + savedDocument.getId()
                + ", category " + savedDocument.getCategory());

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
    public Document getMyDocumentById(Long documentId) {
        AppUser user = getAuthenticatedUser();

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() ->
                        new NoSuchElementException("Document not found"));

        // Owners can access their own documents.
        if (document.getOwner().getId().equals(user.getId())) {
            return document;
        }

        // Other users must have READ or EDIT permission.
        DocumentPermission permission = permissionRepository
                .findByDocument_IdAndUser_Id(documentId, user.getId())
                .orElseThrow(() ->
                        new NoSuchElementException("Document not found"));

        String access = permission.getPermission()
                .toUpperCase(Locale.ROOT);

        if (!access.equals("READ") && !access.equals("EDIT")) {
            throw new NoSuchElementException("Document not found");
        }

        return document;
    }

    private AppUser getAuthenticatedUser() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        String username = authentication.getName();

        return appUserRepository.findByUsername(username)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Authenticated user not found"));
    }
}
