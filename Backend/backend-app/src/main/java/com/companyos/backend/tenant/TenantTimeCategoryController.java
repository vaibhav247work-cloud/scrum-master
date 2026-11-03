package com.companyos.backend.tenant;

import com.companyos.tenantcontext.TenantContextHolder;
import com.companyos.time.TimeCategory;
import com.companyos.time.TimeCategoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenant/time-categories")
public class TenantTimeCategoryController {
    private final TimeCategoryService service;
    public TenantTimeCategoryController(TimeCategoryService service) { this.service = service; }

    @GetMapping
    public List<TimeCategory> list() { return service.list(TenantContextHolder.required().tenantId()); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TimeCategory create(@Valid @RequestBody CategoryRequest request) {
        return service.create(TenantContextHolder.required().tenantId(), request.categoryKey(), request.name());
    }

    @PatchMapping("/{categoryId}")
    public TimeCategory update(@PathVariable UUID categoryId, @Valid @RequestBody CategoryUpdateRequest request) {
        return service.update(TenantContextHolder.required().tenantId(), categoryId, request.name(), request.enabled());
    }

    public record CategoryRequest(@NotBlank @Size(max = 64) String categoryKey, @NotBlank @Size(max = 160) String name) { }
    public record CategoryUpdateRequest(@NotBlank @Size(max = 160) String name, boolean enabled) { }
}
