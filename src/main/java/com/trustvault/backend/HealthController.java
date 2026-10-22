package com.trustvault.backend;

import com.trustvault.backend.document.FileStorageService;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final JdbcTemplate jdbcTemplate;
    private final FileStorageService fileStorageService;

    public HealthController(
            JdbcTemplate jdbcTemplate,
            FileStorageService fileStorageService) {
        this.jdbcTemplate = jdbcTemplate;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @GetMapping("/live")
    public Map<String, String> liveness() {
        return health();
    }

    @GetMapping("/ready")
    public ResponseEntity<Map<String, String>> readiness() {
        boolean databaseReady;
        try {
            databaseReady = Integer.valueOf(1).equals(
                    jdbcTemplate.queryForObject("SELECT 1", Integer.class));
        } catch (DataAccessException ex) {
            databaseReady = false;
        }

        boolean storageReady = fileStorageService.isStorageReady();
        boolean ready = databaseReady && storageReady;
        Map<String, String> status = Map.of(
                "status", ready ? "UP" : "DOWN",
                "database", databaseReady ? "UP" : "DOWN",
                "storage", storageReady ? "UP" : "DOWN");

        return ResponseEntity.status(
                ready ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(status);
    }
}