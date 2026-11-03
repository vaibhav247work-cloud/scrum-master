package com.companyos.portfolio;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PortfolioManagementStore {
    List<PortfolioSummary> findPortfolios(UUID tenantId);
    PortfolioSummary createPortfolio(UUID tenantId, UUID actorId, CreatePortfolioCommand command, Instant now);
    List<ProgramSummary> findPrograms(UUID tenantId, UUID portfolioId);
    ProgramSummary createProgram(UUID tenantId, UUID actorId, UUID portfolioId, CreateProgramCommand command, Instant now);
    List<InitiativeSummary> findInitiatives(UUID tenantId, UUID portfolioId, UUID programId);
    InitiativeSummary createInitiative(UUID tenantId, UUID actorId, CreateInitiativeCommand command, Instant now);
}
