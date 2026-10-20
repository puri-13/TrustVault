
package com.trustvault.backend.document;

import com.trustvault.backend.user.AppUser;
import com.trustvault.backend.user.AppUserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final AppUserRepository appUserRepository;

    public DocumentService(
            DocumentRepository documentRepository,
            AppUserRepository appUserRepository) {
        this.documentRepository = documentRepository;
        this.appUserRepository = appUserRepository;
    }

    @Transactional
    public Document createDocument(
            String name,
            String category,
            String storagePath) {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        String username = authentication.getName();

        AppUser owner = appUserRepository.findByUsername(username)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Authenticated user not found"));

        if (!"OWNER".equals(owner.getRole())) {
            throw new IllegalArgumentException(
                    "Only document owners can create documents");
        }

        if (name == null || name.isBlank()
                || name.trim().length() > 200) {
            throw new IllegalArgumentException("Invalid document name");
        }

        if (category == null || category.isBlank()
                || category.trim().length() > 100) {
            throw new IllegalArgumentException(
                    "Invalid document category");
        }

        if (storagePath == null || storagePath.isBlank()
                || storagePath.length() > 500) {
            throw new IllegalArgumentException(
                    "Invalid storage path");
        }

        Document document = new Document();
        document.setName(name.trim());
        document.setCategory(category.trim());
        document.setStoragePath(storagePath);
        document.setOwner(owner);
        document.setUploadedAt(LocalDateTime.now());

        return documentRepository.save(document);
    }

    @Transactional(readOnly = true)
    public List<Document> getMyDocuments() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        String username = authentication.getName();

        AppUser owner = appUserRepository.findByUsername(username)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Authenticated user not found"));

        return documentRepository.findByOwner_Id(owner.getId());
    }

    // Retrieve a document only if it belongs to the logged-in user.
    @Transactional(readOnly = true)
    public Document getMyDocumentById(Long documentId) {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        String username = authentication.getName();

        AppUser owner = appUserRepository.findByUsername(username)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Authenticated user not found"));

        return documentRepository
                .findByIdAndOwner_Id(documentId, owner.getId())
                .orElseThrow(() ->
                        new NoSuchElementException("Document not found"));
    }
}
