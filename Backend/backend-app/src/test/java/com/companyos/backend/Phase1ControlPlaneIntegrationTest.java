package com.companyos.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EnabledIfEnvironmentVariable(named = "COMPANYOS_LOCAL_INTEGRATION", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "security.session.cookie-secure=false",
        "security.rate-limit.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "outbox.notification.enabled=false",
        "security.invitation.expose-token=true",
        "notification.secret-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
@AutoConfigureMockMvc
class Phase1ControlPlaneIntegrationTest {
    private static final String TEST_DATABASE = "company_os_api_tenant_isolation_test";
    private static final String PASSWORD = "StrongPassword1!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void localInfrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:mysql://localhost:3306/" + TEST_DATABASE + "?createDatabaseIfNotExist=true&serverTimezone=UTC");
        registry.add("spring.datasource.username", () -> env("COMPANYOS_LOCAL_DB_USER", "root"));
        registry.add("spring.datasource.password", () -> env("COMPANYOS_LOCAL_DB_PASSWORD", "root"));
        registry.add("spring.data.redis.host", () -> env("COMPANYOS_LOCAL_REDIS_HOST", "localhost"));
        registry.add("spring.data.redis.port", () -> Integer.parseInt(env("COMPANYOS_LOCAL_REDIS_PORT", "6379")));
    }

    @Test
    void boardConfigurationAndMigrationImportAreTenantBoundAndIdempotent() throws Exception {
        String suffix = suffix();
        Auth auth = onboardAndLogin("control-" + suffix, "control-" + suffix + "@example.com",
                "[\"PROJECTS\",\"WORK_MANAGEMENT\",\"PLANNING_WORKFLOWS\",\"MIGRATION_IMPORT\"]");
        String projectId = createProject(auth, "CTRL" + suffix);

        mockMvc.perform(put("/api/v1/projects/{projectId}/board-config", projectId).with(csrf())
                        .cookie(auth.session()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grouping\":\"STATUS\",\"swimlane\":\"ASSIGNEE\","
                                + "\"cardFields\":[\"TITLE\",\"PRIORITY\"],\"wipLimits\":{\"TODO\":5}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grouping").value("STATUS"))
                .andExpect(jsonPath("$.swimlane").value("ASSIGNEE"));

        String importBody = "{\"rows\":[{\"sourceKey\":\"legacy-" + suffix
                + "\",\"projectId\":\"" + projectId
                + "\",\"title\":\"Imported work item\",\"workType\":\"TASK\",\"priority\":\"HIGH\"}]}";
        MvcResult first = mockMvc.perform(post("/api/v1/migrations/work-items").param("dryRun", "false")
                        .with(csrf()).cookie(auth.session()).header("Idempotency-Key", "import-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON).content(importBody))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("COMPLETED")).andReturn();
        String jobId = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/v1/migrations/work-items").param("dryRun", "false")
                        .with(csrf()).cookie(auth.session()).header("Idempotency-Key", "import-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON).content(importBody))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(jobId));
        mockMvc.perform(get("/api/v1/migrations/jobs/{jobId}", jobId).cookie(auth.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void signedWebhookRetentionAndLifecycleControlsRemainTenantBound() throws Exception {
        String suffix = suffix();
        Auth auth = onboardAndLogin("ops-" + suffix, "ops-" + suffix + "@example.com",
                "[\"PROJECTS\",\"INTEGRATIONS\",\"RETENTION_MANAGEMENT\"]");

        MvcResult connection = mockMvc.perform(post("/api/v1/integrations/connections").with(csrf()).cookie(auth.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"connectionType\":\"WEBHOOK\",\"name\":\"Ops webhook\","
                                + "\"baseUrl\":\"\",\"externalAccount\":\"ops\",\"secret\":\"integration-secret\"}"))
                .andExpect(status().isCreated()).andReturn();
        String connectionId = objectMapper.readTree(connection.getResponse().getContentAsString()).get("id").asText();
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String body = "{\"sourceSystem\":\"WEBHOOK\",\"eventType\":\"TASK_COMPLETED\","
                + "\"externalId\":\"hook-" + suffix + "\",\"payload\":\"{}\"}";
        String signature = "sha256=" + hmac("integration-secret", timestamp + "." + body);

        mockMvc.perform(post("/api/v1/integrations/webhooks/{connectionId}", connectionId)
                        .header("X-Integration-Timestamp", timestamp).header("X-Integration-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.duplicate").value(false));
        mockMvc.perform(post("/api/v1/integrations/webhooks/{connectionId}", connectionId)
                        .header("X-Integration-Timestamp", timestamp).header("X-Integration-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.duplicate").value(true));

        mockMvc.perform(get("/api/v1/tenant/retention-policy").cookie(auth.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
        mockMvc.perform(put("/api/v1/tenant/retention-policy").with(csrf()).cookie(auth.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"operationMode\":\"PREVIEW_ONLY\","
                                + "\"activityRetentionDays\":120,\"notificationRetentionDays\":90,"
                                + "\"deliveryRetentionDays\":90,\"auditRetentionDays\":365}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true));
        mockMvc.perform(get("/api/v1/tenant/retention-policy/preview").cookie(auth.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.legalHoldActive").value(false));
        mockMvc.perform(post("/api/v1/tenant/retention-policy/holds").with(csrf()).cookie(auth.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"holdKey\":\"INVESTIGATION-" + suffix + "\",\"reason\":\"Preserve evidence\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
        mockMvc.perform(get("/api/v1/tenant/retention-policy/preview").cookie(auth.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.legalHoldActive").value(true));

        String export = mockMvc.perform(get("/api/v1/tenant/lifecycle/export").cookie(auth.session()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(export.contains("secret_ciphertext"));
        assertFalse(export.contains("integration-secret"));
        mockMvc.perform(post("/api/v1/tenant/lifecycle/status").with(csrf()).cookie(auth.session())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DELETION_SCHEDULED\",\"reason\":\"Test window\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DELETION_SCHEDULED"));
        mockMvc.perform(post("/api/v1/tenant/lifecycle/status").with(csrf()).cookie(auth.session())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\",\"reason\":\"Cancelled\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        mockMvc.perform(post("/api/v1/tenant/lifecycle/status").with(csrf()).cookie(auth.session())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SUSPENDED\",\"reason\":\"Test suspension\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUSPENDED"));
        mockMvc.perform(get("/api/v1/projects").cookie(auth.session())).andExpect(status().isUnauthorized());
    }

    private Auth onboardAndLogin(String tenantKey, String email, String features) throws Exception {
        mockMvc.perform(post("/api/v1/onboarding/tenants").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantKey\":\"" + tenantKey + "\",\"legalName\":\"Control LLC\","
                                + "\"displayName\":\"Control\",\"timezone\":\"Asia/Kolkata\","
                                + "\"ownerEmail\":\"" + email + "\",\"ownerDisplayName\":\"Owner\","
                                + "\"ownerPassword\":\"" + PASSWORD + "\",\"enabledFeatures\":" + features + "}"))
                .andExpect(status().isCreated());
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantKey\":\"" + tenantKey + "\",\"email\":\"" + email
                                + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn();
        Cookie session = result.getResponse().getCookie("company_os_session");
        assertNotNull(session);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return new Auth(session);
    }

    private String createProject(Auth auth, String key) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects").with(csrf()).cookie(auth.session())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"projectKey\":\"" + key
                                + "\",\"name\":\"Control project\"}"))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private static String hmac(String secret, String material) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(material.getBytes(StandardCharsets.UTF_8)));
    }

    private static String suffix() { return UUID.randomUUID().toString().replace("-", "").substring(0, 12); }
    private static String env(String key, String fallback) { String value = System.getenv(key); return value == null || value.isBlank() ? fallback : value; }
    private record Auth(Cookie session) { }
}
