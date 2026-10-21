package com.trustvault.backend.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            String username,
            String action,
            String details) {

        AuditLog log = new AuditLog(
                username,
                action,
                details,
                LocalDateTime.now());

        auditLogRepository.saveAndFlush(log);
    }
}