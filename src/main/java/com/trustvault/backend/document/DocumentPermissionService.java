
package com.trustvault.backend.document;

import com.trustvault.backend.audit.AuditService;
import com.trustvault.backend.user.AppUser;
import com.trustvault.backend.user.AppUserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.NoSuchElementException;

@Service
public class DocumentPermissionService {

    private final DocumentRepository documentRepository;
    private final DocumentPermissionRepository permissionRepository;
    private final AppUserRepository appUserRepository;
    private final AuditService auditService;

    public DocumentPermissionService(
            DocumentRepository documentRepository,
            DocumentPermissionRepository permissionRepository,
            AppUserRepository appUserRepository,
            AuditService auditService) {

        this.documentRepository = documentRepository;
        this.permissionRepository = permissionRepository;
        this.appUserRepository = appUserRepository;
        this.auditService = auditService;
    }

    @Transactional
    public DocumentPermission grantPermission(
            Long documentId, String username, String permission) {

        AppUser owner = getAuthenticatedUser();

        // Only the document owner can grant access.
        Document document = documentRepository
                .findByIdAndOwner_Id(documentId, owner.getId())
                .orElseThrow(() ->
                        new NoSuchElementException("Document not found"));

        AppUser targetUser = appUserRepository.findByUsername(username)
                .orElseThrow(() ->
                        new NoSuchElementException("User not found"));

        String normalizedPermission =
                permission.trim().toUpperCase(Locale.ROOT);

        if (!normalizedPermission.equals("READ")
                && !normalizedPermission.equals("EDIT")) {
            throw new IllegalArgumentException(
                    "Permission must be READ or EDIT");
        }

        DocumentPermission documentPermission =
                permissionRepository
                        .findByDocument_IdAndUser_Id(
                                documentId, targetUser.getId())
                        .orElseGet(DocumentPermission::new);

        documentPermission.setDocument(document);
        documentPermission.setUser(targetUser);
        documentPermission.setPermission(normalizedPermission);
        documentPermission.setGrantedAt(LocalDateTime.now());

        DocumentPermission savedPermission =
                permissionRepository.save(documentPermission);

        auditService.record(
                owner.getUsername(),
                "PERMISSION_GRANTED",
                "Document ID " + documentId
                        + ": granted " + normalizedPermission
                        + " access to user "
                        + targetUser.getUsername());

        return savedPermission;
    }

    @Transactional
    public void revokePermission(Long documentId, String username) {

        AppUser owner = getAuthenticatedUser();

        // Only the document owner can revoke access.
        documentRepository.findByIdAndOwner_Id(
                documentId, owner.getId())
                .orElseThrow(() ->
                        new NoSuchElementException("Document not found"));

        AppUser targetUser = appUserRepository.findByUsername(username)
                .orElseThrow(() ->
                        new NoSuchElementException("User not found"));

        boolean permissionExisted = permissionRepository
                .findByDocument_IdAndUser_Id(
                        documentId, targetUser.getId())
                .isPresent();

        permissionRepository.deleteByDocument_IdAndUser_Id(
                documentId, targetUser.getId());

        if (permissionExisted) {
            auditService.record(
                    owner.getUsername(),
                    "PERMISSION_REVOKED",
                    "Document ID " + documentId
                            + ": revoked access for user "
                            + targetUser.getUsername());
        }
    }

    private AppUser getAuthenticatedUser() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        return appUserRepository
                .findByUsername(authentication.getName())
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Authenticated user not found"));
    }
}
