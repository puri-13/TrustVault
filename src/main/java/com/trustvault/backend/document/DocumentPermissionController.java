
package com.trustvault.backend.document;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/documents/{documentId}/permissions")
public class DocumentPermissionController {

    private final DocumentPermissionService permissionService;
    private final DocumentRepository documentRepository;
    private final DocumentPermissionRepository permissionRepository;
    private final com.trustvault.backend.user.AppUserRepository appUserRepository;

    public DocumentPermissionController(
            DocumentPermissionService permissionService,
            DocumentRepository documentRepository,
            DocumentPermissionRepository permissionRepository,
            com.trustvault.backend.user.AppUserRepository appUserRepository) {
        this.permissionService = permissionService;
        this.documentRepository = documentRepository;
        this.permissionRepository = permissionRepository;
        this.appUserRepository = appUserRepository;
    }

    public record PermissionResponse(
            String username,
            String permission,
            java.time.LocalDateTime grantedAt) {
    }

    @PostMapping
    public ResponseEntity<?> grantPermission(
            @PathVariable Long documentId,
            @Valid @RequestBody PermissionRequest request) {
        try {
            DocumentPermission permission =
                    permissionService.grantPermission(
                            documentId,
                            request.username(),
                            request.permission());

            return ResponseEntity.status(HttpStatus.CREATED).body(
                    new PermissionResponse(
                            permission.getUser().getUsername(),
                            permission.getPermission(),
                            permission.getGrantedAt()));

        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    @DeleteMapping
    public ResponseEntity<?> revokePermission(
            @PathVariable Long documentId,
            @RequestParam String username) {
        try {
            permissionService.revokePermission(documentId, username);
            return ResponseEntity.ok(
                    Map.of("message", "Permission revoked"));

        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> listPermissions(
            @PathVariable Long documentId) {
        try {
            String currentUsername = org.springframework.security.core.context
                    .SecurityContextHolder.getContext()
                    .getAuthentication().getName();

            var currentUser = appUserRepository
                    .findByUsername(currentUsername)
                    .orElseThrow(() ->
                            new NoSuchElementException("User not found"));

            documentRepository.findByIdAndOwner_Id(
                    documentId, currentUser.getId())
                    .orElseThrow(() ->
                            new NoSuchElementException("Document not found"));

            List<PermissionResponse> results =
                    permissionRepository.findByDocument_Id(documentId)
                            .stream()
                            .map(p -> new PermissionResponse(
                                    p.getUser().getUsername(),
                                    p.getPermission(),
                                    p.getGrantedAt()))
                            .toList();

            return ResponseEntity.ok(results);

        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", ex.getMessage()));
        }
    }
}
