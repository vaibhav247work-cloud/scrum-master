package com.companyos.backend;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "security.session.cookie-secure=false",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureMockMvc
class TenantAuthenticationIntegrationTest {
    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("company_os")
            .withUsername("company_os")
            .withPassword("change-me");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine")
            .withExposedPorts(6379);

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Test
    void registersLogsInAndResolvesVerifiedTenantContext() throws Exception {
        String csrfToken = csrfToken();
        String request = """
                {
                  "tenantKey":"acme-integration",
                  "legalName":"Acme Integration LLC",
                  "displayName":"Acme Integration",
                  "timezone":"Asia/Kolkata",
                  "ownerEmail":"owner@acme-integration.example",
                  "ownerDisplayName":"Integration Owner",
                  "ownerPassword":"StrongPassword1!",
                  "enabledFeatures":["PROJECTS"]
                }
                """;

        var registration = mockMvc.perform(post("/api/v1/onboarding/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", csrfToken)
                        .cookie(new jakarta.servlet.http.Cookie("XSRF-TOKEN", csrfToken))
                        .content(request))
                .andExpect(status().isCreated())
                .andReturn();

        String loginCsrf = csrfToken();
        var login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-XSRF-TOKEN", loginCsrf)
                        .cookie(new jakarta.servlet.http.Cookie("XSRF-TOKEN", loginCsrf))
                        .content("""
                                {"tenantKey":"acme-integration","email":"owner@acme-integration.example","password":"StrongPassword1!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("company_os_session"))
                .andReturn();

        var sessionCookie = login.getResponse().getCookie("company_os_session");
        mockMvc.perform(get("/api/v1/me").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantKey").value("acme-integration"))
                .andExpect(jsonPath("$.displayName").value("Integration Owner"));

        mockMvc.perform(get("/api/v1/tenant/users").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("owner@acme-integration.example"));
    }

    private String csrfToken() throws Exception {
        return mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andReturn()
                .getResponse()
                .getCookie("XSRF-TOKEN")
                .getValue();
    }
}
