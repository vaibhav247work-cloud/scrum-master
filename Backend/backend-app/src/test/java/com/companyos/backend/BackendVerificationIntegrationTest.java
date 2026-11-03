package com.companyos.backend;

import com.companyos.idempotency.IdempotencyRecord;
import com.companyos.idempotency.IdempotencyStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Local MySQL/Redis verification for backend contracts that are too important
 * to leave covered only by mocks. Run with COMPANYOS_LOCAL_INTEGRATION=true.
 */
@Tag("local-integration")
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
class BackendVerificationIntegrationTest {
    private static final String TEST_DATABASE = "company_os_backend_verification_test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private IdempotencyStore idempotencyStore;

    @DynamicPropertySource
    static void localInfrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () ->
                "jdbc:mysql://localhost:3306/" + TEST_DATABASE + "?createDatabaseIfNotExist=true&serverTimezone=UTC");
        registry.add("spring.datasource.username", () -> env("COMPANYOS_LOCAL_DB_USER", "root"));
        registry.add("spring.datasource.password", () -> env("COMPANYOS_LOCAL_DB_PASSWORD", "root"));
        registry.add("spring.data.redis.host", () -> env("COMPANYOS_LOCAL_REDIS_HOST", "localhost"));
        registry.add("spring.data.redis.port", () -> Integer.parseInt(env("COMPANYOS_LOCAL_REDIS_PORT", "6379")));
    }

    @Test
    void protectedApiFailsClosedAndStateChangesRequireCsrf() throws Exception {
        mockMvc.perform(get("/api/v1/projects"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/me/work-hub"))
                .andExpect(status().isUnauthorized());

        String suffix = suffix();
        String email = "csrf-" + suffix + "@example.com";
        onboard("csrf-" + suffix, email, "CSRF Tenant", "[\"PROJECTS\"]");
        Auth owner = login("csrf-" + suffix, email);

        mockMvc.perform(post("/api/v1/projects")
                        .cookie(owner.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectKey\":\"CSRF" + suffix + "\",\"name\":\"Missing CSRF\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void idempotencyIsRedisBackedTenantUserScopedAndPayloadSafe() throws Exception {
        String suffix = suffix();
        String tenantAKey = "idem-a-" + suffix;
        String tenantBKey = "idem-b-" + suffix;
        String emailA = "idem-a-" + suffix + "@example.com";
        String emailB = "idem-b-" + suffix + "@example.com";
        onboard(tenantAKey, emailA, "Idempotency A", "[\"TIME_TRACKING\"]");
        onboard(tenantBKey, emailB, "Idempotency B", "[\"TIME_TRACKING\"]");
        Auth tenantA = login(tenantAKey, emailA);
        Auth tenantB = login(tenantBKey, emailB);
        String key = "same-key-" + suffix;
        String body = "{\"entryDate\":\"" + LocalDate.now()
                + "\",\"minutes\":30,\"description\":\"same request\",\"source\":\"QUICK_HOURS\"}";

        MvcResult first = mockMvc.perform(post("/api/v1/time-entries")
                        .with(csrf()).cookie(tenantA.session())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replay", "false"))
                .andReturn();
        String firstId = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/v1/time-entries")
                        .with(csrf()).cookie(tenantA.session())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replay", "true"))
                .andExpect(jsonPath("$.id").value(firstId));

        mockMvc.perform(post("/api/v1/time-entries")
                        .with(csrf()).cookie(tenantA.session())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("\"minutes\":30", "\"minutes\":31")))
                .andExpect(status().isConflict());

        // The same key is independent in another tenant/user scope.
        mockMvc.perform(post("/api/v1/time-entries")
                        .with(csrf()).cookie(tenantB.session())
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replay", "false"));
    }

    @Test
    void everySupportedTimeSourceWritesOneCanonicalEntryAndOneOutboxEvent() throws Exception {
        String suffix = suffix();
        String tenantKey = "sources-" + suffix;
        String email = "sources-" + suffix + "@example.com";
        onboard(tenantKey, email, "Time Sources", "[\"TIME_TRACKING\"]");
        Auth auth = login(tenantKey, email);
        String[] sources = {"QUICK_HOURS", "TIMER", "START_END", "BULK", "SUGGESTED"};

        for (int index = 0; index < sources.length; index++) {
            MvcResult result = mockMvc.perform(post("/api/v1/time-entries")
                            .with(csrf()).cookie(auth.session())
                            .header("Idempotency-Key", "source-" + sources[index].toLowerCase() + "-" + suffix)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"entryDate\":\"" + LocalDate.now()
                                    + "\",\"minutes\":" + (index + 1)
                                    + ",\"description\":\"source test\",\"source\":\"" + sources[index] + "\"}"))
                    .andExpect(status().isCreated())
                    .andReturn();
            JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
            UUID entryId = UUID.fromString(body.get("id").asText());
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM time_entry WHERE tenant_id = ? AND id = ?",
                    Integer.class, uuidBytes(auth.tenantId()), uuidBytes(entryId)));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM outbox_event WHERE tenant_id = ? AND event_type = 'TIME_ENTRY_CREATED' AND aggregate_id = ?",
                    Integer.class, uuidBytes(auth.tenantId()), uuidBytes(entryId)));
        }
    }

    @Test
    void redisIdempotencyReservationsAreAtomicAndUseTenantScopedNamespaces() {
        String suffix = suffix();
        String scopeA = "tenant-a:" + suffix + ":TIME_ENTRY";
        String scopeB = "tenant-b:" + suffix + ":TIME_ENTRY";
        String key = "redis-key-" + suffix;
        Duration ttl = Duration.ofSeconds(15);

        assertTrue(idempotencyStore.reserve(scopeA, key, "hash-a", ttl));
        assertFalse(idempotencyStore.reserve(scopeA, key, "hash-a", ttl));
        IdempotencyRecord processing = idempotencyStore.find(scopeA, key).orElseThrow();
        assertTrue(processing.processing());

        idempotencyStore.complete(scopeA, key, "hash-a", "{\"ok\":true}", ttl);
        IdempotencyRecord completed = idempotencyStore.find(scopeA, key).orElseThrow();
        assertTrue(completed.completed());
        assertEquals("{\"ok\":true}", completed.responseJson());

        assertTrue(idempotencyStore.reserve(scopeB, key, "hash-b", ttl));
        String releasedKey = "release-" + suffix;
        assertTrue(idempotencyStore.reserve(scopeA, releasedKey, "hash-release", ttl));
        idempotencyStore.release(scopeA, releasedKey, "hash-release");
        assertTrue(idempotencyStore.find(scopeA, releasedKey).isEmpty());
        idempotencyStore.release(scopeB, key, "hash-b");
    }

    @Test
    void concurrentRedisReservationsHaveExactlyOneWinner() throws Exception {
        String suffix = suffix();
        String scope = "concurrent:" + suffix + ":TIME_ENTRY";
        String key = "same-request-" + suffix;
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<Boolean>> attempts = new ArrayList<>();
            for (int index = 0; index < 8; index++) {
                attempts.add(executor.submit(() -> idempotencyStore.reserve(
                        scope, key, "same-hash", Duration.ofSeconds(15))));
            }
            long winners = 0;
            for (Future<Boolean> attempt : attempts) {
                if (attempt.get()) winners++;
            }
            assertEquals(1, winners);
        } finally {
            idempotencyStore.release(scope, key, "same-hash");
            executor.shutdownNow();
        }
    }

    @Test
    void preparedStatementsTreatSqlLikeInputAsDataAndValidationRejectsUnsafeRequests() throws Exception {
        String suffix = suffix();
        String tenantKey = "input-" + suffix;
        String email = "input-" + suffix + "@example.com";
        onboard(tenantKey, email, "Input Tenant", "[\"PROJECTS\",\"WORK_MANAGEMENT\",\"TIME_TRACKING\"]");
        Auth auth = login(tenantKey, email);
        String projectId = createProject(auth, "SAFE" + suffix);

        mockMvc.perform(post("/api/v1/work-items")
                        .with(csrf()).cookie(auth.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"" + projectId
                                + "\",\"title\":\"x' OR '1'='1\",\"workType\":\"TASK\",\"priority\":\"HIGH\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("x' OR '1'='1"));

        mockMvc.perform(post("/api/v1/work-items")
                        .with(csrf()).cookie(auth.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Request validation failed"));

        mockMvc.perform(post("/api/v1/time-entries")
                        .with(csrf()).cookie(auth.session())
                        .header("Idempotency-Key", "invalid-source-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"minutes\":30,\"source\":\"DROP_TABLE\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/work-items")
                        .param("projectId", "' OR 1=1 --")
                        .cookie(auth.session()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Request is malformed"));
    }

    @Test
    void tenantFirstIndexesExistForHotOperationalQueries() {
        assertIndex("project", "ix_project_tenant_status_created");
        assertIndex("sprint", "ix_sprint_tenant_project_status");
        assertIndex("work_item", "ix_work_item_tenant_project");
        assertIndex("work_item", "ix_work_item_tenant_sprint");
        assertIndex("time_entry", "ix_time_entry_tenant_user_date");
        assertIndex("outbox_event_delivery", "ix_outbox_delivery_claim");
    }

    @Test
    void outboxDeliveryCannotCrossTenantsOrDuplicateOneConsumer() {
        UUID eventId = UUID.randomUUID();
        UUID eventTenantId = UUID.randomUUID();
        UUID wrongTenantId = UUID.randomUUID();
        UUID deliveryId = UUID.randomUUID();
        Instant now = java.time.Instant.now();

        jdbcTemplate.update("""
                INSERT INTO outbox_event
                    (event_id, tenant_id, event_type, event_version, aggregate_type, aggregate_id,
                     actor_id, correlation_id, causation_id, occurred_at, payload_json, created_at)
                VALUES (?, ?, 'TEST_ISOLATION', 1, 'TEST', ?, NULL, NULL, NULL, ?, '{}', ?)
                """, uuidBytes(eventId), uuidBytes(eventTenantId), uuidBytes(UUID.randomUUID()),
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO outbox_event_delivery
                    (id, event_id, tenant_id, consumer, status, attempt_count, next_attempt_at,
                     processed_at, last_error, created_at, updated_at)
                VALUES (?, ?, ?, 'test.consumer', 'PENDING', 0, ?, NULL, NULL, ?, ?)
                """, uuidBytes(deliveryId), uuidBytes(eventId), uuidBytes(wrongTenantId),
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(now), java.sql.Timestamp.from(now)));

        jdbcTemplate.update("""
                INSERT INTO outbox_event_delivery
                    (id, event_id, tenant_id, consumer, status, attempt_count, next_attempt_at,
                     processed_at, last_error, created_at, updated_at)
                VALUES (?, ?, ?, 'test.consumer', 'PENDING', 0, ?, NULL, NULL, ?, ?)
                """, uuidBytes(deliveryId), uuidBytes(eventId), uuidBytes(eventTenantId),
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
                INSERT INTO outbox_event_delivery
                    (id, event_id, tenant_id, consumer, status, attempt_count, next_attempt_at,
                     processed_at, last_error, created_at, updated_at)
                VALUES (?, ?, ?, 'test.consumer', 'PENDING', 0, ?, NULL, NULL, ?, ?)
                """, uuidBytes(UUID.randomUUID()), uuidBytes(eventId), uuidBytes(eventTenantId),
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(now), java.sql.Timestamp.from(now)));
    }

    private void assertIndex(String table, String expectedIndex) {
        Set<String> indexes = new HashSet<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList("SHOW INDEX FROM " + table)) {
            indexes.add(String.valueOf(row.get("Key_name")));
        }
        assertTrue(indexes.contains(expectedIndex),
                () -> table + " is missing expected index " + expectedIndex + "; found " + indexes);
    }

    private void onboard(String tenantKey, String email, String displayName, String features) throws Exception {
        mockMvc.perform(post("/api/v1/onboarding/tenants")
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantKey\":\"" + tenantKey
                                + "\",\"legalName\":\"" + displayName
                                + " LLC\",\"displayName\":\"" + displayName
                                + "\",\"timezone\":\"Asia/Kolkata\",\"ownerEmail\":\"" + email
                                + "\",\"ownerDisplayName\":\"" + displayName
                                + " Owner\",\"ownerPassword\":\"StrongPassword1!\",\"enabledFeatures\":"
                                + features + "}"))
                .andExpect(status().isCreated());
    }

    private Auth login(String tenantKey, String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantKey\":\"" + tenantKey
                                + "\",\"email\":\"" + email
                                + "\",\"password\":\"StrongPassword1!\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie session = result.getResponse().getCookie("company_os_session");
        assertNotNull(session);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return new Auth(UUID.fromString(body.get("tenantId").asText()),
                UUID.fromString(body.get("userId").asText()), session);
    }

    private String createProject(Auth auth, String projectKey) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects")
                        .with(csrf()).cookie(auth.session()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectKey\":\"" + projectKey
                                + "\",\"name\":\"Prepared ' project\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private static byte[] uuidBytes(UUID value) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(value.getMostSignificantBits());
        buffer.putLong(value.getLeastSignificantBits());
        return buffer.array();
    }

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private record Auth(UUID tenantId, UUID userId, Cookie session) {
    }
}
