package com.companyos.backend.integration;

import com.companyos.integration.CreateIntegrationConnectionCommand;
import com.companyos.integration.ExternalIntegrationEventSummary;
import com.companyos.integration.IngestIntegrationEventCommand;
import com.companyos.integration.IntegrationAuditService;
import com.companyos.integration.IntegrationConnectionSummary;
import com.companyos.tenantcontext.TenantContextHolder;
import com.companyos.backend.security.PublicEndpointRateLimiter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/integrations")
public class IntegrationController {
    private final IntegrationAuditService service;
    private final ObjectMapper objectMapper;
    private final PublicEndpointRateLimiter rateLimiter;

    public IntegrationController(IntegrationAuditService service, ObjectMapper objectMapper, PublicEndpointRateLimiter rateLimiter) {
        this.service = service;
        this.objectMapper = objectMapper;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/connections")
    public List<IntegrationConnectionSummary> connections() {
        return service.connections(TenantContextHolder.required().tenantId());
    }

    @PostMapping("/connections")
    @ResponseStatus(HttpStatus.CREATED)
    public IntegrationConnectionSummary create(@Valid @RequestBody ConnectionRequest request) {
        var context = TenantContextHolder.required();
        return service.createConnection(context.tenantId(), context.userId(), new CreateIntegrationConnectionCommand(
                request.connectionType(), request.name(), request.baseUrl(), request.externalAccount(), request.secret()));
    }

    @PostMapping("/connections/{connectionId}/events")
    public ExternalIntegrationEventSummary ingest(@PathVariable UUID connectionId,
                                                   @RequestHeader("X-Integration-Secret") String secret,
                                                   @Valid @RequestBody EventRequest request) {
        var context = TenantContextHolder.required();
        return service.ingestEvent(context.tenantId(), connectionId, secret, new IngestIntegrationEventCommand(
                request.sourceSystem(), request.eventType(), request.externalId(), request.repository(), request.branch(),
                request.environment(), request.sourceUrl(), request.occurredAt(), request.payload()));
    }

    @PostMapping("/webhooks/{connectionId}")
    public ExternalIntegrationEventSummary webhook(@PathVariable UUID connectionId,
                                                    @RequestHeader("X-Integration-Signature") String signature,
                                                    @RequestHeader("X-Integration-Timestamp") String timestamp,
                                                    @RequestBody String rawBody,
                                                    HttpServletRequest httpRequest) {
        rateLimiter.checkIntegrationWebhook(httpRequest, connectionId);
        try {
            EventRequest request = objectMapper.readValue(rawBody, EventRequest.class);
            return service.ingestSignedEvent(connectionId, signature, timestamp, rawBody,
                    new IngestIntegrationEventCommand(request.sourceSystem(), request.eventType(), request.externalId(),
                            request.repository(), request.branch(), request.environment(), request.sourceUrl(),
                            request.occurredAt(), request.payload()));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("request body is malformed", exception);
        }
    }

    public record ConnectionRequest(@NotBlank @Size(max = 32) String connectionType,
                                     @NotBlank @Size(max = 200) String name,
                                     @Size(max = 1000) String baseUrl,
                                     @Size(max = 240) String externalAccount,
                                     @NotBlank @Size(max = 4000) String secret) { }

    public record EventRequest(@NotBlank @Size(max = 32) String sourceSystem,
                               @NotBlank @Size(max = 100) String eventType,
                               @NotBlank @Size(max = 240) String externalId,
                               @Size(max = 500) String repository,
                               @Size(max = 500) String branch,
                               @Size(max = 200) String environment,
                               @Size(max = 2000) String sourceUrl,
                               Instant occurredAt,
                               @Size(max = 20000) String payload) { }
}
