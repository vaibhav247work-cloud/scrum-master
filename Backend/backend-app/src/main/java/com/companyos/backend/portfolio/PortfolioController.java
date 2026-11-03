package com.companyos.backend.portfolio;

import com.companyos.portfolio.CreateInitiativeCommand;
import com.companyos.portfolio.CreatePortfolioCommand;
import com.companyos.portfolio.CreateProgramCommand;
import com.companyos.portfolio.InitiativeSummary;
import com.companyos.portfolio.PortfolioManagementService;
import com.companyos.portfolio.PortfolioSummary;
import com.companyos.portfolio.ProgramSummary;
import com.companyos.tenantcontext.TenantContextHolder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/portfolios")
public class PortfolioController {
    private final PortfolioManagementService service;

    public PortfolioController(PortfolioManagementService service) { this.service = service; }

    @GetMapping
    public List<PortfolioSummary> portfolios() {
        return service.portfolios(TenantContextHolder.required().tenantId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PortfolioSummary createPortfolio(@Valid @RequestBody PortfolioRequest request) {
        var context = TenantContextHolder.required();
        return service.createPortfolio(context.tenantId(), context.userId(),
                new CreatePortfolioCommand(request.portfolioKey(), request.name(), request.description(), request.status()));
    }

    @GetMapping("/{portfolioId}/programs")
    public List<ProgramSummary> programs(@PathVariable UUID portfolioId) {
        return service.programs(TenantContextHolder.required().tenantId(), portfolioId);
    }

    @PostMapping("/{portfolioId}/programs")
    @ResponseStatus(HttpStatus.CREATED)
    public ProgramSummary createProgram(@PathVariable UUID portfolioId, @Valid @RequestBody ProgramRequest request) {
        var context = TenantContextHolder.required();
        return service.createProgram(context.tenantId(), context.userId(), portfolioId,
                new CreateProgramCommand(request.programKey(), request.name(), request.description(), request.status()));
    }

    @GetMapping("/{portfolioId}/initiatives")
    public List<InitiativeSummary> initiatives(@PathVariable UUID portfolioId,
                                               @RequestParam(required = false) UUID programId) {
        return service.initiatives(TenantContextHolder.required().tenantId(), portfolioId, programId);
    }

    @PostMapping("/{portfolioId}/initiatives")
    @ResponseStatus(HttpStatus.CREATED)
    public InitiativeSummary createInitiative(@PathVariable UUID portfolioId,
                                              @Valid @RequestBody InitiativeRequest request) {
        var context = TenantContextHolder.required();
        return service.createInitiative(context.tenantId(), context.userId(), new CreateInitiativeCommand(
                portfolioId, request.programId(), request.productId(), request.initiativeKey(), request.title(),
                request.description(), request.priority(), request.targetDate()));
    }

    public record PortfolioRequest(@NotBlank @Size(max = 48) String portfolioKey,
                                   @NotBlank @Size(max = 200) String name,
                                   @Size(max = 4000) String description,
                                   @Size(max = 32) String status) { }

    public record ProgramRequest(@NotBlank @Size(max = 48) String programKey,
                                 @NotBlank @Size(max = 200) String name,
                                 @Size(max = 4000) String description,
                                 @Size(max = 32) String status) { }

    public record InitiativeRequest(@NotBlank @Size(max = 48) String initiativeKey,
                                    @NotBlank @Size(max = 240) String title,
                                    @Size(max = 4000) String description,
                                    @Size(max = 16) String priority,
                                    UUID programId,
                                    UUID productId,
                                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate) { }
}
