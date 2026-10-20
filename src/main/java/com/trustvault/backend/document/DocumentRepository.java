package com.trustvault.backend.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByOwner_Id(Long ownerId);

    List<Document> findByCategoryIgnoreCase(String category);

    List<Document> findByNameContainingIgnoreCase(String name);

    Optional<Document> findByIdAndOwner_Id(Long id, Long ownerId);
}