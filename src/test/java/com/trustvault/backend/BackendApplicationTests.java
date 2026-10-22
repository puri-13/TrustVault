package com.trustvault.backend;

import com.trustvault.backend.audit.AuditLogRepository;
import com.trustvault.backend.document.Document;
import com.trustvault.backend.document.DocumentPermissionRepository;
import com.trustvault.backend.document.DocumentRepository;
import com.trustvault.backend.document.FileStorageService;
import com.trustvault.backend.user.AppUser;
import com.trustvault.backend.user.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import java.nio.charset.StandardCharsets;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.springframework.dao.DataIntegrityViolationException;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BackendApplicationTests {

    private static final String OWNER = "owner-test";
    private static final String VIEWER = "viewer-test";
    private static final String PASSWORD = "correct-horse-19";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentPermissionRepository permissionRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private FileStorageService fileStorageService;

    @BeforeEach
    void clearDatabaseAndTestFiles() throws Exception {
        permissionRepository.deleteAll();
        documentRepository.findAll().forEach(document ->
                fileStorageService.deleteStoredFile(document.getStoragePath()));
        documentRepository.deleteAll();
        auditLogRepository.deleteAll();
        appUserRepository.deleteAll();
    }

    @Test
    void registrationValidatesFieldsRejectsDuplicatesAndHashesPassword()
            throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"new-owner\",\"password\":\"strong-pass-19\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("OWNER"));

        AppUser savedUser = appUserRepository.findByUsername("new-owner")
                .orElseThrow();
        org.junit.jupiter.api.Assertions.assertNotEquals(
                "strong-pass-19", savedUser.getPassword());
        org.junit.jupiter.api.Assertions.assertTrue(
                passwordEncoder.matches("strong-pass-19", savedUser.getPassword()));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"new-owner\",\"password\":\"strong-pass-19\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"x\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/api/health/live"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/api/health/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.database").value("UP"))
                .andExpect(jsonPath("$.storage").value("UP"));
    }

    @Test
    void basicAuthenticationRejectsAnonymousAndInvalidCredentials()
            throws Exception {
        register(OWNER);

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/documents")
                        .with(httpBasic(OWNER, "wrong-password")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/documents")
                        .with(httpBasic(OWNER, PASSWORD)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/auth/me")
                        .with(httpBasic(OWNER, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(OWNER))
                .andExpect(jsonPath("$.role").value("OWNER"));
    }

    @Test
    void sharingAllowsReadAndRevocationDeniesMetadataAndDownload()
            throws Exception {
        register(OWNER);
        register(VIEWER);
        long documentId = uploadDocument(OWNER);

        mockMvc.perform(get("/api/documents/{id}", documentId)
                        .with(httpBasic(VIEWER, PASSWORD)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/documents/{id}/permissions", documentId)
                        .with(httpBasic(OWNER, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + VIEWER
                                + "\",\"permission\":\"READ\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.permission").value("READ"));

        mockMvc.perform(get("/api/documents/{id}", documentId)
                        .with(httpBasic(VIEWER, PASSWORD)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/documents/{id}/download", documentId)
                        .with(httpBasic(VIEWER, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().bytes("test document".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(delete("/api/documents/{id}/permissions", documentId)
                        .param("username", VIEWER)
                        .with(httpBasic(OWNER, PASSWORD)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/documents/{id}/download", documentId)
                        .with(httpBasic(VIEWER, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    @Test
    void permissionChangesRequireOwnershipAndUploadRejectsInvalidFiles()
            throws Exception {
        register(OWNER);
        register("other-owner");
        register(VIEWER);
        long documentId = uploadDocument(OWNER);

        mockMvc.perform(post("/api/documents/{id}/permissions", documentId)
                        .with(httpBasic("other-owner", PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + VIEWER
                                + "\",\"permission\":\"READ\"}"))
                .andExpect(status().isNotFound());

        MockMultipartFile invalidFile = new MockMultipartFile(
                "file", "payload.exe", MediaType.APPLICATION_OCTET_STREAM_VALUE,
                "not allowed".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/documents")
                        .file(invalidFile)
                        .param("name", "Invalid")
                        .param("category", "Test")
                        .with(httpBasic(OWNER, PASSWORD)))
                .andExpect(status().isBadRequest());

        MockMultipartFile mismatchedContent = new MockMultipartFile(
                "file", "fake.pdf", MediaType.APPLICATION_PDF_VALUE,
                "not a PDF".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/documents")
                        .file(mismatchedContent)
                        .param("name", "Mismatched content")
                        .param("category", "Test")
                        .with(httpBasic(OWNER, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        MockMultipartFile oversizedFile = new MockMultipartFile(
                "file", "large.txt", MediaType.TEXT_PLAIN_VALUE,
                new byte[10 * 1024 * 1024 + 1]);
        mockMvc.perform(multipart("/api/documents")
                        .file(oversizedFile)
                        .param("name", "Oversized")
                        .param("category", "Test")
                        .with(httpBasic(OWNER, PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void auditEndpointRequiresAdminRole() throws Exception {
        register(OWNER);
        AppUser admin = new AppUser();
        admin.setUsername("admin-test");
        admin.setPassword(passwordEncoder.encode(PASSWORD));
        admin.setRole("ADMIN");
        appUserRepository.save(admin);

        mockMvc.perform(get("/api/audit"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/audit")
                        .with(httpBasic(OWNER, PASSWORD)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/audit")
                        .with(httpBasic("admin-test", PASSWORD)))
                .andExpect(status().isOk());
    }

        @Test
        void databaseEnforcesUniqueUsernames() throws Exception {
                register("unique-user");

                AppUser duplicate = new AppUser();
                duplicate.setUsername("unique-user");
                duplicate.setPassword(passwordEncoder.encode(PASSWORD));
                duplicate.setRole("OWNER");

                assertThrows(DataIntegrityViolationException.class,
                                () -> appUserRepository.saveAndFlush(duplicate));
        }

        @Test
        void corsAllowsConfiguredLocalFrontendOrigin() throws Exception {
                mockMvc.perform(options("/api/documents")
                                                .header("Origin", "http://localhost:5173")
                                                .header("Access-Control-Request-Method", "POST")
                                                .header("Access-Control-Request-Headers", "authorization,content-type"))
                                .andExpect(status().isOk())
                                .andExpect(header().string(
                                                "Access-Control-Allow-Origin", "http://localhost:5173"));
        }

    private void register(String username) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username
                                + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated());
    }

    private long uploadDocument(String username) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "document.txt", MediaType.TEXT_PLAIN_VALUE,
                "test document".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/documents")
                        .file(file)
                        .param("name", "Test document")
                        .param("category", "Test")
                        .with(httpBasic(username, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn();
        AppUser owner = appUserRepository.findByUsername(username)
                .orElseThrow();
        return documentRepository.findByOwner_Id(owner.getId())
                .get(0).getId();
    }
}