package com.companyos.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;
import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs against local MySQL and Redis only when explicitly enabled.
 *
 * PowerShell:
 * $env:COMPANYOS_LOCAL_INTEGRATION='true'
 * mvn -q -pl backend-app -am "-Dtest=LocalTenantIsolationIntegrationTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
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
class LocalTenantIsolationIntegrationTest {
    private static final String TEST_DATABASE = "company_os_api_tenant_isolation_test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    void tenantBCannotReadOrModifyTenantAProjectAndWorkItem() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String tenantAKey = "isolation-a-" + suffix;
        String tenantBKey = "isolation-b-" + suffix;
        String tenantAEmail = "owner-a-" + suffix + "@example.com";
        String tenantBEmail = "owner-b-" + suffix + "@example.com";
        String password = "StrongPassword1!";

        onboard(tenantAKey, tenantAEmail, "Tenant A");
        onboard(tenantBKey, tenantBEmail, "Tenant B");
        Auth tenantA = login(tenantAKey, tenantAEmail, password);
        Auth tenantB = login(tenantBKey, tenantBEmail, password);

        String projectId = createProject(tenantA, "ISO" + suffix);
        mockMvc.perform(get("/api/v1/projects").cookie(tenantA.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(projectId));

        mockMvc.perform(get("/api/v1/projects").cookie(tenantB.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        String workItemId = createWorkItem(tenantA, projectId);

        mockMvc.perform(get("/api/v1/work-items")
                        .param("projectId", projectId)
                        .cookie(tenantB.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(patch("/api/v1/work-items/{workItemId}", workItemId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(tenantB.session())
                        .content("{\"status\":\"DONE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("work item was not found"));

        mockMvc.perform(post("/api/v1/work-items")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(tenantB.session())
                        .content("{\"projectId\":\"" + projectId
                                + "\",\"title\":\"Cross tenant attempt\",\"workType\":\"TASK\",\"priority\":\"HIGH\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("The request conflicts with existing or tenant-scoped data"));
    }

    @Test
    void portfolioProgramAndInitiativeHierarchyRemainsTenantScoped() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String tenantAKey = "portfolio-a-" + suffix;
        String tenantBKey = "portfolio-b-" + suffix;
        String emailA = "portfolio-a-" + suffix + "@example.com";
        String emailB = "portfolio-b-" + suffix + "@example.com";
        onboard(tenantAKey, emailA, "Portfolio A", "[\"PORTFOLIO_PLANNING\"]");
        onboard(tenantBKey, emailB, "Portfolio B", "[\"PORTFOLIO_PLANNING\"]");
        Auth tenantA = login(tenantAKey, emailA, "StrongPassword1!");
        Auth tenantB = login(tenantBKey, emailB, "StrongPassword1!");

        MvcResult portfolio = mockMvc.perform(post("/api/v1/portfolios").with(csrf()).cookie(tenantA.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"portfolioKey\":\"VALUE" + suffix + "\",\"name\":\"Customer value\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PLANNING")).andReturn();
        String portfolioId = objectMapper.readTree(portfolio.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/v1/portfolios").cookie(tenantB.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/v1/portfolios/{portfolioId}/programs", portfolioId).cookie(tenantB.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(post("/api/v1/portfolios/{portfolioId}/programs", portfolioId).with(csrf())
                        .cookie(tenantB.session()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"programKey\":\"CROSS" + suffix + "\",\"name\":\"Cross tenant\"}"))
                .andExpect(status().isBadRequest());

        MvcResult program = mockMvc.perform(post("/api/v1/portfolios/{portfolioId}/programs", portfolioId).with(csrf())
                        .cookie(tenantA.session()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"programKey\":\"DELIVERY" + suffix + "\",\"name\":\"Delivery\"}"))
                .andExpect(status().isCreated()).andReturn();
        String programId = objectMapper.readTree(program.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/v1/portfolios/{portfolioId}/initiatives", portfolioId).with(csrf())
                        .cookie(tenantA.session()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"initiativeKey\":\"MOBILE" + suffix + "\",\"title\":\"Mobile checkout\",\"programId\":\"" + programId + "\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.programId").value(programId));
        mockMvc.perform(get("/api/v1/portfolios/{portfolioId}/initiatives", portfolioId).cookie(tenantB.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void integrationConnectionsAndEventsRemainTenantScopedAndDeduplicated() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String tenantAKey = "integration-a-" + suffix;
        String tenantBKey = "integration-b-" + suffix;
        String emailA = "integration-a-" + suffix + "@example.com";
        String emailB = "integration-b-" + suffix + "@example.com";
        onboard(tenantAKey, emailA, "Integration A", "[\"INTEGRATIONS\"]");
        onboard(tenantBKey, emailB, "Integration B", "[\"INTEGRATIONS\"]");
        Auth tenantA = login(tenantAKey, emailA, "StrongPassword1!");
        Auth tenantB = login(tenantBKey, emailB, "StrongPassword1!");

        MvcResult connection = mockMvc.perform(post("/api/v1/integrations/connections").with(csrf()).cookie(tenantA.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"connectionType\":\"CI_CD\",\"name\":\"Build pipeline\",\"baseUrl\":\"https://ci.example.com\",\"externalAccount\":\"checkout\",\"secret\":\"integration-secret\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.secretConfigured").value(true)).andReturn();
        String connectionId = objectMapper.readTree(connection.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/v1/integrations/connections").cookie(tenantB.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(post("/api/v1/integrations/connections/{connectionId}/events", connectionId).with(csrf())
                        .cookie(tenantB.session()).header("X-Integration-Secret", "integration-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceSystem\":\"CI_CD\",\"eventType\":\"BUILD_SUCCEEDED\",\"externalId\":\"build-1\",\"payload\":\"{}\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/integrations/connections/{connectionId}/events", connectionId).with(csrf())
                        .cookie(tenantA.session()).header("X-Integration-Secret", "integration-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceSystem\":\"CI_CD\",\"eventType\":\"BUILD_SUCCEEDED\",\"externalId\":\"build-1\",\"payload\":\"{}\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.duplicate").value(false));
        mockMvc.perform(post("/api/v1/integrations/connections/{connectionId}/events", connectionId).with(csrf())
                        .cookie(tenantA.session()).header("X-Integration-Secret", "integration-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceSystem\":\"CI_CD\",\"eventType\":\"BUILD_SUCCEEDED\",\"externalId\":\"build-1\",\"payload\":\"{}\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.duplicate").value(true));
    }

    @Test
    void tenantBCannotCrossTenantAdministrationReleaseDependencyTimeOrReportingData() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String tenantAKey = "module-a-" + suffix;
        String tenantBKey = "module-b-" + suffix;
        String tenantAEmail = "module-a-" + suffix + "@example.com";
        String tenantBEmail = "module-b-" + suffix + "@example.com";
        String password = "StrongPassword1!";
        String features = "[\"PROJECTS\",\"WORK_MANAGEMENT\",\"TIME_TRACKING\","
                + "\"RELEASE_MANAGEMENT\",\"DEPENDENCY_RISK\",\"RESOURCE_MANAGEMENT\",\"REPORTING\"]";

        onboard(tenantAKey, tenantAEmail, "Module Tenant A", features);
        onboard(tenantBKey, tenantBEmail, "Module Tenant B", features);
        Auth tenantA = login(tenantAKey, tenantAEmail, password);
        Auth tenantB = login(tenantBKey, tenantBEmail, password);

        String projectId = createProject(tenantA, "MOD" + suffix);
        String blockedWorkItemId = createWorkItem(tenantA, projectId);
        String dependencyTargetId = createWorkItem(tenantA, projectId);
        String releaseId = createRelease(tenantA, projectId, "REL" + suffix);
        mockMvc.perform(post("/api/v1/projects/{projectId}/releases/{releaseId}/work-items/{workItemId}",
                        projectId, releaseId, blockedWorkItemId)
                        .with(csrf()).cookie(tenantA.session()))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/projects/{projectId}/releases/{releaseId}/work-items/{workItemId}",
                        projectId, releaseId, dependencyTargetId)
                        .with(csrf()).cookie(tenantA.session()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/manager/reports/forecast")
                        .param("projectId", projectId)
                        .cookie(tenantA.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].estimatedScopeMinutes").value(240))
                .andExpect(jsonPath("$[0].remainingEstimateMinutes").value(240));

        mockMvc.perform(get("/api/v1/manager/reports/release-readiness")
                        .param("projectId", projectId)
                        .cookie(tenantA.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].estimatedScopeMinutes").value(240));

        mockMvc.perform(get("/api/v1/tenant/users").cookie(tenantB.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].email").value(tenantBEmail));

        mockMvc.perform(put("/api/v1/tenant/users/{userId}/roles", tenantA.userId())
                        .with(csrf())
                        .cookie(tenantB.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleKeys\":[\"COMPANY_ADMIN\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("The request conflicts with existing or tenant-scoped data"));

        mockMvc.perform(get("/api/v1/projects/{projectId}/releases", projectId)
                        .cookie(tenantB.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(post("/api/v1/projects/{projectId}/releases", projectId)
                        .with(csrf())
                        .cookie(tenantB.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"releaseKey\":\"CROSS" + suffix
                                + "\",\"name\":\"Cross tenant release\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("The request conflicts with existing or tenant-scoped data"));

        mockMvc.perform(post("/api/v1/projects/{projectId}/releases/{releaseId}/work-items/{workItemId}",
                        projectId, releaseId, blockedWorkItemId)
                        .with(csrf())
                        .cookie(tenantB.session()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("The request conflicts with existing or tenant-scoped data"));

        mockMvc.perform(get("/api/v1/work-items/{workItemId}/dependencies", blockedWorkItemId)
                        .cookie(tenantB.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(post("/api/v1/work-items/{workItemId}/dependencies", blockedWorkItemId)
                        .with(csrf())
                        .cookie(tenantB.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dependsOnWorkItemId\":\"" + dependencyTargetId
                                + "\",\"dependencyType\":\"BLOCKS\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("The request conflicts with existing or tenant-scoped data"));

        mockMvc.perform(get("/api/v1/time-entries").cookie(tenantB.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(post("/api/v1/time-entries")
                        .with(csrf())
                        .cookie(tenantB.session())
                        .header("Idempotency-Key", "cross-tenant-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workItemId\":\"" + blockedWorkItemId
                                + "\",\"entryDate\":\"2026-09-15\",\"minutes\":30"
                                + ",\"description\":\"Cross tenant attempt\",\"source\":\"QUICK_HOURS\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("The request conflicts with existing or tenant-scoped data"));

        mockMvc.perform(get("/api/v1/manager/reports/delivery")
                        .param("projectId", projectId)
                        .cookie(tenantB.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/v1/manager/reports/forecast")
                        .param("projectId", projectId)
                        .cookie(tenantB.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/v1/manager/reports/release-readiness")
                        .param("projectId", projectId)
                        .cookie(tenantB.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/v1/manager/control-tower").cookie(tenantB.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeProjects").value(0))
                .andExpect(jsonPath("$.activeReleases").value(0))
                .andExpect(jsonPath("$.openWorkItems").value(0));

        mockMvc.perform(get("/api/v1/manager/workload").cookie(tenantB.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].email").value(tenantBEmail));
    }

    @Test
    void timesheetPeriodApprovalLocksOnlyTheOwningTenantAndUserDate() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String tenantAKey = "sheet-a-" + suffix;
        String tenantBKey = "sheet-b-" + suffix;
        String tenantAEmail = "sheet-a-" + suffix + "@example.com";
        String tenantBEmail = "sheet-b-" + suffix + "@example.com";
        onboard(tenantAKey, tenantAEmail, "Timesheet A", "[\"TIME_TRACKING\"]");
        onboard(tenantBKey, tenantBEmail, "Timesheet B", "[\"TIME_TRACKING\"]");
        Auth tenantA = login(tenantAKey, tenantAEmail, "StrongPassword1!");
        Auth tenantB = login(tenantBKey, tenantBEmail, "StrongPassword1!");

        String policy = "{\"enabled\":true,\"expectedDailyMinutes\":480,\"allowOvertime\":true,"
                + "\"allowWeekend\":true,\"approvalEnabled\":true,\"approvalCadence\":\"WEEKLY\","
                + "\"autoApprove\":false,\"roundingIncrementMinutes\":1,\"roundingMode\":\"NEAREST\"}";
        mockMvc.perform(put("/api/v1/tenant/time-policy").with(csrf()).cookie(tenantA.session())
                        .contentType(MediaType.APPLICATION_JSON).content(policy))
                .andExpect(status().isNoContent());

        String entryDate = java.time.LocalDate.now().toString();
        mockMvc.perform(post("/api/v1/time-entries").with(csrf()).cookie(tenantA.session())
                        .header("Idempotency-Key", "sheet-entry-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entryDate\":\"" + entryDate + "\",\"minutes\":30,"
                                + "\"description\":\"Timesheet work\",\"source\":\"QUICK_HOURS\"}"))
                .andExpect(status().isCreated());

        MvcResult current = mockMvc.perform(get("/api/v1/me/timesheet").cookie(tenantA.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.entryCount").value(1))
                .andReturn();
        String timesheetId = objectMapper.readTree(current.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/v1/manager/timesheets/pending").cookie(tenantB.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(post("/api/v1/me/timesheet/{timesheetId}/submit", timesheetId)
                        .with(csrf()).cookie(tenantA.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUBMITTED"));
        mockMvc.perform(get("/api/v1/manager/timesheets/pending").cookie(tenantA.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(post("/api/v1/manager/timesheets/{timesheetId}/decision", timesheetId)
                        .with(csrf()).cookie(tenantA.session()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true,\"note\":\"Reviewed\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(post("/api/v1/time-entries").with(csrf()).cookie(tenantA.session())
                        .header("Idempotency-Key", "sheet-locked-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entryDate\":\"" + entryDate + "\",\"minutes\":15,"
                                + "\"description\":\"Late correction\",\"source\":\"QUICK_HOURS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("This date belongs to an approved timesheet and is locked"));
    }

    @Test
    void demandAndRequirementsReadinessRemainTenantScopedAndGateReadyStatus() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String tenantAKey = "demand-a-" + suffix;
        String tenantBKey = "demand-b-" + suffix;
        String emailA = "demand-a-" + suffix + "@example.com";
        String emailB = "demand-b-" + suffix + "@example.com";
        String features = "[\"DEMAND_INTAKE\",\"REQUIREMENTS_READINESS\"]";
        onboard(tenantAKey, emailA, "Demand A", features);
        onboard(tenantBKey, emailB, "Demand B", features);
        Auth tenantA = login(tenantAKey, emailA, "StrongPassword1!");
        Auth tenantB = login(tenantBKey, emailB, "StrongPassword1!");

        MvcResult demandResult = mockMvc.perform(post("/api/v1/demands").with(csrf()).cookie(tenantA.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"demandKey\":\"PAY" + suffix + "\",\"title\":\"Payment retry\","
                                + "\"description\":\"Retry failed payments\",\"demandType\":\"IDEA\",\"priority\":\"HIGH\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("SUBMITTED")).andReturn();
        String demandId = objectMapper.readTree(demandResult.getResponse().getContentAsString()).get("id").asText();
        mockMvc.perform(get("/api/v1/demands").cookie(tenantB.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(post("/api/v1/demands/{demandId}/decision", demandId).with(csrf()).cookie(tenantA.session())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"APPROVED\",\"note\":\"Accepted\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));

        MvcResult requirementResult = mockMvc.perform(post("/api/v1/requirements").with(csrf()).cookie(tenantA.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requirementKey\":\"REQ" + suffix + "\",\"demandId\":\"" + demandId
                                + "\",\"title\":\"Retry behavior\",\"description\":\"Define retry behavior\","
                                + "\"businessContext\":\"Reduce failed payment loss\",\"uxContext\":\"Show retry state\","
                                + "\"technicalContext\":\"Use idempotent retry policy\",\"acceptanceCriteria\":\"Retry is safe and visible\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("DRAFT")).andReturn();
        String requirementId = objectMapper.readTree(requirementResult.getResponse().getContentAsString()).get("id").asText();
        mockMvc.perform(get("/api/v1/requirements").cookie(tenantB.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(post("/api/v1/requirements/{requirementId}/checks", requirementId).with(csrf()).cookie(tenantA.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"checkKey\":\"DESIGN_REVIEW\",\"label\":\"Design reviewed\",\"required\":true,\"completed\":true,\"evidence\":\"Design note\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/requirements/{requirementId}/decision", requirementId).with(csrf()).cookie(tenantA.session())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"READY\",\"note\":\"Ready for development\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("READY"));
    }

    @Test
    void productsAndProductMembersRemainTenantScoped() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String tenantAKey = "product-a-" + suffix;
        String tenantBKey = "product-b-" + suffix;
        String emailA = "product-a-" + suffix + "@example.com";
        String emailB = "product-b-" + suffix + "@example.com";
        String features = "[\"PRODUCT_MANAGEMENT\"]";
        onboard(tenantAKey, emailA, "Product A", features);
        onboard(tenantBKey, emailB, "Product B", features);
        Auth tenantA = login(tenantAKey, emailA, "StrongPassword1!");
        Auth tenantB = login(tenantBKey, emailB, "StrongPassword1!");

        MvcResult productResult = mockMvc.perform(post("/api/v1/products").with(csrf()).cookie(tenantA.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productKey\":\"CHECKOUT" + suffix + "\",\"name\":\"Checkout\","
                                + "\"description\":\"Tenant A product\",\"featureKeys\":[\"PROJECTS\"]}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.productKey").value(("CHECKOUT" + suffix).toUpperCase()))
                .andReturn();
        String productId = objectMapper.readTree(productResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/v1/products").cookie(tenantB.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/v1/products/{productId}/members", productId).cookie(tenantB.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(put("/api/v1/products/{productId}/members/{userId}", productId, tenantB.userId())
                        .with(csrf()).cookie(tenantB.session()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessStatus\":\"ACTIVE\",\"productRole\":\"MEMBER\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/products/{productId}/members/{userId}", productId, tenantA.userId())
                        .with(csrf()).cookie(tenantA.session()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessStatus\":\"ACTIVE\",\"productRole\":\"PRODUCT_ADMIN\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(tenantA.userId().toString()));
        mockMvc.perform(get("/api/v1/products/{productId}/members", productId).cookie(tenantA.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void tenantBCannotCrossTenantSprintNotificationIntegrationOrOutboxData() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String tenantAKey = "remaining-a-" + suffix;
        String tenantBKey = "remaining-b-" + suffix;
        String tenantAEmail = "remaining-a-" + suffix + "@example.com";
        String tenantBEmail = "remaining-b-" + suffix + "@example.com";
        String password = "StrongPassword1!";
        String features = "[\"PROJECTS\",\"SCRUM\",\"WORK_MANAGEMENT\",\"NOTIFICATIONS\","
                + "\"INTEGRATIONS\",\"TELEGRAM_NOTIFICATIONS\"]";

        onboard(tenantAKey, tenantAEmail, "Remaining Tenant A", features);
        onboard(tenantBKey, tenantBEmail, "Remaining Tenant B", features);
        Auth tenantA = login(tenantAKey, tenantAEmail, password);
        Auth tenantB = login(tenantBKey, tenantBEmail, password);

        String projectId = createProject(tenantA, "REM" + suffix);
        String workItemId = createWorkItem(tenantA, projectId);
        String sprintId = createSprint(tenantA, projectId, "SPR" + suffix);
        UUID notificationId = seedNotification(tenantA);
        UUID deliveryId = seedDeadLetterDelivery(tenantA);

        mockMvc.perform(get("/api/v1/projects/{projectId}/sprints", projectId)
                        .cookie(tenantB.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(post("/api/v1/projects/{projectId}/sprints", projectId)
                        .with(csrf())
                        .cookie(tenantB.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sprintKey\":\"CROSS" + suffix
                                + "\",\"name\":\"Cross tenant sprint\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("The request conflicts with existing or tenant-scoped data"));

        mockMvc.perform(get("/api/v1/me/notifications").cookie(tenantA.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(notificationId.toString()))
                .andExpect(jsonPath("$[0].readAt").doesNotExist());

        mockMvc.perform(get("/api/v1/me/notifications").cookie(tenantB.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(patch("/api/v1/me/notifications/{notificationId}/read", notificationId)
                        .with(csrf())
                        .cookie(tenantB.session()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/me/notifications").cookie(tenantA.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].readAt").doesNotExist());

        mockMvc.perform(put("/api/v1/tenant/notification-channels/telegram")
                        .with(csrf())
                        .cookie(tenantA.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"botToken\":\"tenant-a-token\",\"chatId\":\"chat-a-"
                                + suffix + "\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/tenant/notification-channels/telegram")
                        .cookie(tenantB.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.configured").value(false));

        mockMvc.perform(put("/api/v1/tenant/notification-channels/telegram")
                        .with(csrf())
                        .cookie(tenantB.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/tenant/notification-channels/telegram")
                        .cookie(tenantA.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.configured").value(true));

        mockMvc.perform(get("/api/v1/tenant/outbox/deliveries")
                        .param("status", "DEAD_LETTER")
                        .cookie(tenantA.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(deliveryId.toString()));

        mockMvc.perform(get("/api/v1/tenant/outbox/deliveries")
                        .param("status", "DEAD_LETTER")
                        .cookie(tenantB.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(post("/api/v1/tenant/outbox/deliveries/{deliveryId}/replay", deliveryId)
                        .with(csrf())
                        .cookie(tenantB.session()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/projects/{projectId}/sprints", projectId)
                        .cookie(tenantA.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(sprintId));

        // Keep the source work item referenced so this test documents the complete project context.
        assertNotNull(workItemId);
    }

    @Test
    void limitedEmployeeCannotPerformAdministrativeActionsAndSuspensionRevokesSession() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String tenantKey = "authorization-" + suffix;
        String adminEmail = "authorization-admin-" + suffix + "@example.com";
        String employeeEmail = "authorization-employee-" + suffix + "@example.com";
        String password = "StrongPassword1!";

        onboard(tenantKey, adminEmail, "Authorization Tenant",
                "[\"PROJECTS\",\"WORK_MANAGEMENT\",\"TIME_TRACKING\"]");
        Auth admin = login(tenantKey, adminEmail, password);
        seedRole(admin, "EMPLOYEE", Set.of("project.read", "work.read", "time.entry.create", "time.entry.read"));

        Invitation invitation = invite(admin, employeeEmail, "Limited Employee", "EMPLOYEE");
        activate(invitation, password);
        Auth employee = login(tenantKey, employeeEmail, password);

        mockMvc.perform(get("/api/v1/projects").cookie(employee.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(post("/api/v1/projects")
                        .with(csrf())
                        .cookie(employee.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectKey\":\"EMP" + suffix
                                + "\",\"name\":\"Not allowed\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/tenant/users").cookie(employee.session()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/tenant/features").cookie(employee.session()))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/tenant/features/TIME_TRACKING")
                        .with(csrf())
                        .cookie(employee.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/time-entries")
                        .with(csrf())
                        .cookie(employee.session())
                        .header("Idempotency-Key", "employee-time-" + suffix)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entryDate\":\"" + java.time.LocalDate.now()
                                + "\",\"minutes\":30,\"description\":\"Employee work\","
                                + "\"source\":\"QUICK_HOURS\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/v1/tenant/features/TIME_TRACKING")
                        .with(csrf())
                        .cookie(admin.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/time-entries").cookie(employee.session()))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/tenant/users/{userId}/status", employee.userId())
                        .with(csrf())
                        .cookie(admin.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/projects").cookie(employee.session()))
                .andExpect(status().isUnauthorized());
    }

    private void onboard(String tenantKey, String email, String displayName) throws Exception {
        onboard(tenantKey, email, displayName, "[\"PROJECTS\",\"WORK_MANAGEMENT\"]");
    }

    private void onboard(String tenantKey, String email, String displayName, String enabledFeatures) throws Exception {
        mockMvc.perform(post("/api/v1/onboarding/tenants")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantKey\":\"" + tenantKey
                                + "\",\"legalName\":\"" + displayName
                                + " LLC\",\"displayName\":\"" + displayName
                                + "\",\"timezone\":\"Asia/Kolkata\",\"ownerEmail\":\"" + email
                                + "\",\"ownerDisplayName\":\"" + displayName
                                + " Owner\",\"ownerPassword\":\"StrongPassword1!\"," 
                                + "\"enabledFeatures\":" + enabledFeatures + "}"))
                .andExpect(status().isCreated());
    }

    private Auth login(String tenantKey, String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantKey\":\"" + tenantKey
                                + "\",\"email\":\"" + email
                                + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie session = result.getResponse().getCookie("company_os_session");
        assertNotNull(session);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return new Auth(tenantKey, UUID.fromString(body.get("tenantId").asText()),
                UUID.fromString(body.get("userId").asText()), session);
    }

    private String createProject(Auth auth, String projectKey) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(auth.session())
                        .content("{\"projectKey\":\"" + projectKey
                                + "\",\"name\":\"Tenant A Project\",\"description\":\"Isolation test\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("id").asText();
    }

    private String createWorkItem(Auth auth, String projectId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/work-items")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(auth.session())
                        .content("{\"projectId\":\"" + projectId
                                + "\",\"title\":\"Tenant A Work\",\"workType\":\"TASK\",\"priority\":\"HIGH\",\"estimateMinutes\":120}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("id").asText();
    }

    private String createRelease(Auth auth, String projectId, String releaseKey) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects/{projectId}/releases", projectId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(auth.session())
                        .content("{\"releaseKey\":\"" + releaseKey
                                + "\",\"name\":\"Tenant A Release\",\"description\":\"Isolation test\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("releaseId").asText();
    }

    private String createSprint(Auth auth, String projectId, String sprintKey) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects/{projectId}/sprints", projectId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(auth.session())
                        .content("{\"sprintKey\":\"" + sprintKey
                                + "\",\"name\":\"Tenant A Sprint\",\"goal\":\"Isolation test\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("id").asText();
    }

    private Invitation invite(Auth admin, String email, String displayName, String roleKey) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/tenant/users")
                        .with(csrf())
                        .cookie(admin.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email
                                + "\",\"displayName\":\"" + displayName
                                + "\",\"roleKeys\":[\"" + roleKey + "\"]}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return new Invitation(UUID.fromString(body.get("invitationId").asText()),
                body.get("activationToken").asText());
    }

    private void activate(Invitation invitation, String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/activate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invitationId\":\"" + invitation.invitationId()
                                + "\",\"activationToken\":\"" + invitation.activationToken()
                                + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isNoContent());
    }

    private void seedRole(Auth admin, String roleKey, Set<String> permissions) {
        UUID roleId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO tenant_role
                    (id, tenant_id, role_key, display_name, system_role, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, FALSE, 'ACTIVE', ?, ?)
                """, uuidBytes(roleId), uuidBytes(admin.tenantId()), roleKey,
                "Employee", Timestamp.from(now), Timestamp.from(now));
        for (String permission : permissions) {
            jdbcTemplate.update("""
                    INSERT INTO tenant_role_permission (tenant_id, role_id, permission_key)
                    VALUES (?, ?, ?)
                    """, uuidBytes(admin.tenantId()), uuidBytes(roleId), permission);
        }
    }

    private UUID seedNotification(Auth auth) {
        UUID sourceEventId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO outbox_event
                    (event_id, tenant_id, event_type, event_version, aggregate_type, aggregate_id,
                     actor_id, correlation_id, causation_id, occurred_at, payload_json, created_at)
                VALUES (?, ?, 'TEST_NOTIFICATION', 1, 'TEST', ?, ?, NULL, NULL, ?, '{}', ?)
                """, uuidBytes(sourceEventId), uuidBytes(auth.tenantId()), uuidBytes(notificationId),
                uuidBytes(auth.userId()), Timestamp.from(now), Timestamp.from(now));
        jdbcTemplate.update("""
                INSERT INTO in_app_notification
                    (id, tenant_id, recipient_user_id, source_event_id, notification_type,
                     title, body, read_at, created_at)
                VALUES (?, ?, ?, ?, 'TEST_NOTIFICATION', 'Tenant A notification',
                        'Tenant A private notification', NULL, ?)
                """, uuidBytes(notificationId), uuidBytes(auth.tenantId()), uuidBytes(auth.userId()),
                uuidBytes(sourceEventId), Timestamp.from(now));
        return notificationId;
    }

    private UUID seedDeadLetterDelivery(Auth auth) {
        UUID eventId = UUID.randomUUID();
        UUID deliveryId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO outbox_event
                    (event_id, tenant_id, event_type, event_version, aggregate_type, aggregate_id,
                     actor_id, correlation_id, causation_id, occurred_at, payload_json, created_at)
                VALUES (?, ?, 'TEST_DELIVERY', 1, 'TEST', ?, ?, NULL, NULL, ?, '{}', ?)
                """, uuidBytes(eventId), uuidBytes(auth.tenantId()), uuidBytes(deliveryId),
                uuidBytes(auth.userId()), Timestamp.from(now), Timestamp.from(now));
        jdbcTemplate.update("""
                INSERT INTO outbox_event_delivery
                    (id, event_id, tenant_id, consumer, status, attempt_count, next_attempt_at,
                     processed_at, last_error, created_at, updated_at)
                VALUES (?, ?, ?, 'test-consumer', 'DEAD_LETTER', 5, ?, NULL,
                        'test failure', ?, ?)
                """, uuidBytes(deliveryId), uuidBytes(eventId), uuidBytes(auth.tenantId()),
                Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
        return deliveryId;
    }

    private static byte[] uuidBytes(UUID value) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(value.getMostSignificantBits());
        buffer.putLong(value.getLeastSignificantBits());
        return buffer.array();
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private record Auth(String tenantKey, UUID tenantId, UUID userId, Cookie session) {
    }

    private record Invitation(UUID invitationId, String activationToken) {
    }

}
