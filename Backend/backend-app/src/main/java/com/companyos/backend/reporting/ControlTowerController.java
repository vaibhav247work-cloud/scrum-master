package com.companyos.backend.reporting;

import com.companyos.reporting.ControlTowerService;
import com.companyos.reporting.ControlTowerSummary;
import com.companyos.tenantcontext.TenantContextHolder;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/manager/control-tower")
public class ControlTowerController {
    private final ControlTowerService service;

    public ControlTowerController(ControlTowerService service) { this.service = service; }

    @GetMapping
    public ControlTowerSummary summary(@RequestParam(required = false)
                                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        return service.summary(TenantContextHolder.required().tenantId(), weekStart);
    }
}
