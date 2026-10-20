
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
import java.util.NoSuchElementException;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final AppUserRepository appUserRepository;
    private final FileStorageService fileStorageService;

    public DocumentService(
            DocumentRepository documentRepository,
            AppUserRepository appUserRepository,
            FileStorageService fileStorageService) {
        this.documentRepository = documentRepository;
        this.appUserRepository = appUserRepository;
        this.fileStorageService = fileStorageService;
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

            return documentRepository.save(document);
        } catch (RuntimeException ex) {
            // Remove the saved file if its database record cannot be saved.
            fileStorageService.deleteStoredFile(storedName);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public List<Document> getMyDocuments() {
        AppUser owner = getAuthenticatedUser();
        return documentRepository.findByOwner_Id(owner.getId());
    }

    @Transactional(readOnly = true)
    public Document getMyDocumentById(Long documentId) {
        AppUser owner = getAuthenticatedUser();

        return documentRepository
                .findByIdAndOwner_Id(documentId, owner.getId())
                .orElseThrow(() ->
                        new NoSuchElementException("Document not found"));
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
