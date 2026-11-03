package com.companyos.demand;

public record CreateDemandCommand(
        String demandKey,
        String title,
        String description,
        String demandType,
        String priority) {
}
