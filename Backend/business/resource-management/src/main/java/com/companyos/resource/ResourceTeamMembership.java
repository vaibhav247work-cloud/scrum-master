package com.companyos.resource;

import java.util.UUID;

public record ResourceTeamMembership(UUID teamId, String teamKey, String teamName, String membershipRole) { }
