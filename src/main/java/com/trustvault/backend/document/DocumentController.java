
package com.trustvault.backend.document;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    public record CreateDocumentRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 100) String category,
            @NotBlank @Size(max = 500) String storagePath
    ) {
    }

    public record DocumentResponse(
            Long id,
            String name,
            String category,
            String owner,
            java.time.LocalDateTime uploadedAt
    ) {
    }

    @PostMapping
    public ResponseEntity<?> createDocument(
            @Valid @RequestBody CreateDocumentRequest request) {

        try {
            Document document = documentService.createDocument(
                    request.name(),
                    request.category(),
                    request.storagePath()
            );

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
        }
    }

    @GetMapping
    public List<DocumentResponse> getMyDocuments() {
        return documentService.getMyDocuments().stream()
                .map(document -> new DocumentResponse(
                        document.getId(),
                        document.getName(),
                        document.getCategory(),
                        document.getOwner().getUsername(),
                        document.getUploadedAt()
                ))
                .toList();
    }

    // Get a document only if it belongs to the logged-in user.
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
}
