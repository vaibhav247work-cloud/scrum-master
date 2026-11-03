package com.companyos.quality;

import java.util.List;
import java.util.UUID;

public interface QualityOperationsStore {
    QualityGateSummary createGate(UUID tenantId, UUID actorId, CreateQualityGateCommand command);
    List<QualityGateSummary> findGates(UUID tenantId, UUID projectId, UUID releaseId);
    QualityGateSummary decideGate(UUID tenantId, UUID actorId, UUID gateId, String status, String evidence);
    IncidentSummary createIncident(UUID tenantId, UUID actorId, CreateIncidentCommand command);
    List<IncidentSummary> findIncidents(UUID tenantId, String status);
    IncidentSummary updateIncident(UUID tenantId, UUID actorId, UUID incidentId, String status, String mitigation, String rootCause);
}
