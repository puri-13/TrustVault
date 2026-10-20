
package com.trustvault.backend.document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
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

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<?> uploadDocument(
            @RequestParam @NotBlank @Size(max = 200) String name,
            @RequestParam @NotBlank @Size(max = 100) String category,
            @RequestParam("file") MultipartFile file) {

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
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "File upload failed"));
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
