
package com.trustvault.backend.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentPermissionRepository
        extends JpaRepository<DocumentPermission, Long> {

    List<DocumentPermission> findByDocument_Id(Long documentId);

    Optional<DocumentPermission> findByDocument_IdAndUser_Id(
            Long documentId, Long userId);

    boolean existsByDocument_IdAndUser_IdAndPermissionIgnoreCase(
            Long documentId, Long userId, String permission);

    void deleteByDocument_IdAndUser_Id(
            Long documentId, Long userId);
}
