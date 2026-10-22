package com.trustvault.backend;

import com.trustvault.backend.document.FileStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthControllerTests {

    @Test
    void readinessDoesNotExposeDatabaseFailureDetails() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class))
                .thenThrow(new DataAccessResourceFailureException(
                        "database host and credentials must stay private"));
        when(fileStorageService.isStorageReady()).thenReturn(true);
        HealthController controller = new HealthController(
                jdbcTemplate, fileStorageService);

        var response = controller.readiness();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("DOWN", response.getBody().get("status"));
        assertEquals("DOWN", response.getBody().get("database"));
        assertEquals("UP", response.getBody().get("storage"));
        assertEquals(3, response.getBody().size());
    }

    @Test
    void readinessReturnsUnavailableWhenStorageIsNotWritable() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class))
                .thenReturn(1);
        when(fileStorageService.isStorageReady()).thenReturn(false);
        HealthController controller = new HealthController(
                jdbcTemplate, fileStorageService);

        var response = controller.readiness();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("UP", response.getBody().get("database"));
        assertEquals("DOWN", response.getBody().get("storage"));
    }
}