
package com.trustvault.backend.document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    public record DocumentResponse(
            Long id,
            String name,
            String category,
            String owner,
            java.time.LocalDateTime uploadedAt
    ) {
    }

    public record SharedDocumentResponse(
            Long id,
            String name,
            String category,
            String owner,
            String permission,
            java.time.LocalDateTime uploadedAt
    ) {
    }

    // Upload a document
    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<?> uploadDocument(
            @RequestParam
            @NotBlank
            @Size(max = 200)
            String name,

            @RequestParam
            @NotBlank
            @Size(max = 100)
            String category,

            @RequestParam("file")
            MultipartFile file) {

        try {
            Document document = documentService.uploadDocument(
                    name, category, file);

            return ResponseEntity.status(HttpStatus.CREATED).body(
                    new DocumentResponse(
                            document.getId(),
                            document.getName(),
                            document.getCategory(),
                            document.getOwner().getUsername(),
                            document.getUploadedAt()
                    )
            );

        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", ex.getMessage())
            );

        } catch (IOException ex) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "File upload failed"));
        }
    }

    // Get documents owned by the logged-in user
    @GetMapping
    public List<DocumentResponse> getMyDocuments() {
        return documentService.getMyDocuments()
                .stream()
                .map(document -> new DocumentResponse(
                        document.getId(),
                        document.getName(),
                        document.getCategory(),
                        document.getOwner().getUsername(),
                        document.getUploadedAt()
                ))
                .toList();
    }

    // Get documents shared with the logged-in user.
    // This route must be before /{id}.
    @GetMapping("/shared-with-me")
    public List<SharedDocumentResponse> getSharedWithMe() {
        return documentService.getSharedWithMe()
                .stream()
                .map(permission -> {
                    Document document = permission.getDocument();

                    return new SharedDocumentResponse(
                            document.getId(),
                            document.getName(),
                            document.getCategory(),
                            document.getOwner().getUsername(),
                            permission.getPermission(),
                            document.getUploadedAt()
                    );
                })
                .toList();
    }

    // Download a document after ownership/permission checks.
    @GetMapping("/{id}/download")
    public ResponseEntity<?> downloadDocument(
            @PathVariable Long id) {

        try {
            DocumentService.DownloadedDocument file =
                    documentService.downloadDocument(id);

            ByteArrayResource resource =
                    new ByteArrayResource(file.content());

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\""
                                    + safeDownloadFilename(file.filename())
                                    + "\"")
                    .contentLength(file.content().length)
                    .body(resource);

        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    Map.of("error", "Document not found")
            );

        } catch (IOException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    Map.of("error", "Stored file not found")
            );
        }
    }

    // Get one document, subject to access checks
    @GetMapping("/{id}")
    public ResponseEntity<?> getMyDocumentById(
            @PathVariable Long id) {

        try {
            Document document =
                    documentService.getMyDocumentById(id);

            return ResponseEntity.ok(
                    new DocumentResponse(
                            document.getId(),
                            document.getName(),
                            document.getCategory(),
                            document.getOwner().getUsername(),
                            document.getUploadedAt()
                    )
            );

        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    Map.of("error", "Document not found")
            );
        }
    }

    private String safeDownloadFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "document";
        }

        return filename.replaceAll("[^a-zA-Z0-9._ -]", "_");
    }
}
