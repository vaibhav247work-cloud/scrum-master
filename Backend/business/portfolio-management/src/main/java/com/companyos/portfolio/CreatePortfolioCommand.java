package com.companyos.portfolio;

public record CreatePortfolioCommand(String portfolioKey, String name, String description, String status) { }
