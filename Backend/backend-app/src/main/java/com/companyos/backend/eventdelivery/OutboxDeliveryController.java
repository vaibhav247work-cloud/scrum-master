package com.companyos.backend.eventdelivery;

import com.companyos.eventdelivery.OutboxDeliveryOperations;
import com.companyos.eventdelivery.OutboxDeliverySummary;
import com.companyos.security.contract.AuthorizationService;
import com.companyos.tenantcontext.TenantContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenant/outbox/deliveries")
public class OutboxDeliveryController {
    private final AuthorizationService authorization;
    private final OutboxDeliveryOperations operations;

    public OutboxDeliveryController(AuthorizationService authorization, OutboxDeliveryOperations operations) {
        this.authorization = authorization;
        this.operations = operations;
    }

    @GetMapping
    public List<OutboxDeliverySummary> list(
            @RequestParam(defaultValue = "DEAD_LETTER") String status,
            @RequestParam(defaultValue = "50") int limit) {
        authorization.requirePermission("integration.delivery.read");
        return operations.list(TenantContextHolder.required().tenantId(), status, limit);
    }

    @PostMapping("/{deliveryId}/replay")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void replay(@PathVariable UUID deliveryId) {
        authorization.requirePermission("integration.delivery.manage");
        var context = TenantContextHolder.required();
        if (!operations.replay(context.tenantId(), deliveryId, context.userId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Dead-letter delivery not found");
        }
    }
}
