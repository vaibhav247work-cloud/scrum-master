package com.companyos.portfolio;

import java.time.LocalDate;
import java.util.UUID;

public record CreateInitiativeCommand(UUID portfolioId, UUID programId, UUID productId, String initiativeKey,
                                      String title, String description, String priority, LocalDate targetDate) { }
