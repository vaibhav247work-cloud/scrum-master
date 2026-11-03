const apiBase = import.meta.env.VITE_API_BASE_URL ?? '';

export type CurrentUser = {
  tenantId: string;
  userId: string;
  tenantKey: string;
  email: string;
  displayName: string;
  authorities: string[];
};

export type WorkItemSummary = {
  id: string;
  workKey: string;
  projectId: string | null;
  sprintId: string | null;
  assigneeUserId: string | null;
  teamId?: string | null;
  title: string;
  description: string;
  workType: string;
  priority: string;
  status: string;
  createdAt: string;
  productId?: string | null;
  estimateMinutes?: number | null;
  completedAt?: string | null;
  parentWorkItemId?: string | null;
  acceptanceCriteria?: string;
  backlogPosition?: number | null;
};

export type WorkItemAssignmentHistory = {
  id: string;
  workItemId: string;
  previousAssigneeUserId: string | null;
  newAssigneeUserId: string | null;
  changedBy: string;
  reason: string;
  changedAt: string;
};

export type WorkScopeChangeRequest = {
  id: string;
  workItemId: string;
  projectId: string | null;
  requestedPriority: string | null;
  targetSprintId: string | null;
  requestedBy: string;
  emergency: boolean;
  reason: string;
  status: 'REQUESTED' | 'APPROVED' | 'REJECTED' | string;
  decidedBy: string | null;
  decisionNote: string;
  createdAt: string;
  decidedAt: string | null;
};

export type TimeEntrySummary = {
  id: string;
  userId: string;
  workItemId: string | null;
  projectId: string | null;
  sprintId: string | null;
  entryDate: string;
  minutes: number;
  description: string;
  source: string;
  categoryKey?: string;
  createdAt: string;
  approvalStatus?: 'PENDING' | 'APPROVED' | 'REJECTED' | string;
  approvedBy?: string | null;
  approvedAt?: string | null;
  approvalNote?: string;
};

export type TimeCategory = {
  id: string;
  categoryKey: string;
  name: string;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
};

export type TimesheetSummary = {
  id: string;
  userId: string;
  periodStart: string;
  periodEnd: string;
  cadence: 'DAILY' | 'WEEKLY' | 'MONTHLY' | string;
  status: 'OPEN' | 'SUBMITTED' | 'APPROVED' | 'REJECTED' | 'REOPENED' | string;
  totalMinutes: number;
  pendingMinutes: number;
  approvedMinutes: number;
  rejectedMinutes: number;
  entryCount: number;
  submittedAt?: string | null;
  submittedBy?: string | null;
  decidedAt?: string | null;
  decidedBy?: string | null;
  decisionNote: string;
};

export type WorkHubResponse = {
  workItems: WorkItemSummary[];
  recentTimeEntries: TimeEntrySummary[];
};

export type ResourceAssignedWork = {
  workItemId: string;
  workKey: string;
  projectId: string | null;
  sprintId: string | null;
  title: string;
  workType: string;
  priority: string;
  status: string;
  updatedAt: string;
};

export type ResourceActivity = {
  timeEntryId: string;
  workItemId: string | null;
  projectId: string | null;
  sprintId: string | null;
  entryDate: string;
  minutes: number;
  description: string;
  source: string;
  createdAt: string;
};

export type ResourceWorkloadMember = {
  userId: string;
  email: string;
  displayName: string;
  status: string;
  capacityMinutesPerWeek: number;
  loggedMinutesThisWeek: number;
  openWorkItemCount: number;
  utilizationPercent: number;
  loadStatus: 'AVAILABLE' | 'BALANCED' | 'AT_RISK' | 'OVERLOADED' | string;
  lastActivityAt: string | null;
  assignedWork: ResourceAssignedWork[];
  recentActivity: ResourceActivity[];
  teams?: ResourceTeamMembership[];
  plannedAllocationMinutesThisWeek?: number;
  actualVsPlannedPercent?: number;
  allocationStatus?: 'UNPLANNED' | 'UNDER_PLAN' | 'ON_TRACK' | 'OVER_PLAN' | string;
};

export type ResourceTeamMembership = {
  teamId: string;
  teamKey: string;
  teamName: string;
  membershipRole: string;
};

export type ResourceAllocationSummary = {
  id: string;
  userId: string;
  projectId: string;
  sprintId: string | null;
  teamId: string | null;
  startDate: string;
  endDate: string;
  plannedMinutesPerWeek: number;
  status: string;
  note: string;
  createdAt: string;
};

export type ControlTowerSummary = {
  generatedAt: string;
  activeProjects: number;
  activeSprints: number;
  openWorkItems: number;
  blockedDependencies: number;
  activeReleases: number;
  atRiskReleases: number;
  loggedMinutesThisWeek: number;
  activeMembers: number;
};

export type DeliveryReportRow = {
  projectId: string;
  projectKey: string;
  projectName: string;
  openWorkItems: number;
  completedWorkItems: number;
  loggedMinutes: number;
  activeReleases: number;
  atRiskReleases: number;
};

export type DeliveryForecastRow = {
  projectId: string;
  projectKey: string;
  projectName: string;
  plannedAllocationMinutes: number;
  actualLoggedMinutes: number;
  openWorkItems: number;
  completedWorkItems: number;
  completionPercent: number;
  activeSprints: number;
  activeReleases: number;
  atRiskReleases: number;
  blockedDependencies: number;
  forecastStatus: 'ON_TRACK' | 'AT_RISK' | 'OVER_PLAN' | 'COMPLETE' | 'NO_PLAN' | string;
  estimatedScopeMinutes: number;
  remainingEstimateMinutes: number;
  velocityMinutesPerWeek: number;
  projectedDaysRemaining: number | null;
  calibrationFactor: number;
  calibrationSampleSize: number;
};

export type ForecastCalibrationRow = {
  projectId: string;
  projectKey: string;
  projectName: string;
  sampleSize: number;
  estimatedMinutes: number;
  actualLoggedMinutes: number;
  calibrationFactor: number;
  accuracyPercent: number;
};

export type TenantUser = {
  userId: string;
  email: string;
  displayName: string;
  status: string;
};

export type TenantRole = {
  roleId: string;
  roleKey: string;
  displayName: string;
  systemRole: boolean;
};

export type TenantLifecycleSummary = {
  tenantId: string;
  tenantKey: string;
  displayName: string;
  status: string;
  suspendedAt: string | null;
  deletionScheduledAt: string | null;
  updatedAt: string;
};

export type TenantDataExport = {
  exportVersion: number;
  exportedAt: string;
  tenant: { tenantId: string; tenantKey: string; displayName: string; status: string };
  data: Record<string, unknown[]>;
  limits: { maxRowsPerTable: number; sensitiveCredentialFieldsOmitted: boolean };
};

export type RetentionPolicy = {
  tenantId: string;
  enabled: boolean;
  operationMode: 'PURGE' | 'PREVIEW_ONLY' | string;
  activityRetentionDays: number;
  notificationRetentionDays: number;
  deliveryRetentionDays: number;
  auditRetentionDays: number;
  policyVersion: number;
  updatedAt: string | null;
};
export type RetentionCleanupSummary = {
  tenantId: string; status: string; notificationsEligible: number; deliveriesEligible: number;
  outboxEventsEligible: number; auditEventsEligible: number; notificationsRemoved: number;
  deliveriesRemoved: number; outboxEventsRemoved: number; auditEventsRemoved: number;
  legalHoldActive: boolean; executedAt: string;
};
export type RetentionHold = { id: string; holdKey: string; reason: string; active: boolean; createdAt: string; releasedAt: string | null };

export type OrganizationUnit = {
  id: string;
  parentUnitId: string | null;
  unitKey: string;
  name: string;
  unitType: string;
  managerUserId: string | null;
  status: string;
  createdAt: string;
};

export type TeamSummary = {
  id: string;
  parentUnitId: string | null;
  teamKey: string;
  name: string;
  managerUserId: string | null;
  status: string;
  memberCount: number;
  createdAt: string;
};

export type TeamMember = {
  userId: string;
  email: string;
  displayName: string;
  membershipRole: string;
  status: string;
  joinedAt: string;
  leftAt: string | null;
};

export type FeatureEntitlement = {
  featureKey: string;
  enabled: boolean;
};

export type ProductSummary = {
  id: string; productKey: string; name: string; description: string; status: string;
  featureKeys: string[]; memberCount: number; createdAt: string; updatedAt: string;
};
export type ProductMemberSummary = {
  productId: string; userId: string; displayName: string; email: string;
  accessStatus: string; productRole: string; updatedAt: string;
};
export function getProducts(): Promise<ProductSummary[]> { return request<ProductSummary[]>('/api/v1/products'); }
export async function createProduct(input: { productKey: string; name: string; description?: string; featureKeys?: string[] }): Promise<ProductSummary> {
  const token = await csrfToken();
  return request<ProductSummary>('/api/v1/products', { method: 'POST', headers: { 'X-XSRF-TOKEN': token }, body: JSON.stringify(input) });
}
export async function updateProductFeatures(productId: string, featureKeys: string[]): Promise<ProductSummary> {
  const token = await csrfToken();
  return request<ProductSummary>(`/api/v1/products/${productId}/features`, { method: 'PUT', headers: { 'X-XSRF-TOKEN': token }, body: JSON.stringify({ featureKeys }) });
}
export function getProductMembers(productId: string): Promise<ProductMemberSummary[]> { return request<ProductMemberSummary[]>(`/api/v1/products/${productId}/members`); }
export async function setProductMember(productId: string, userId: string, input: { accessStatus: string; productRole?: string }): Promise<ProductMemberSummary> {
  const token = await csrfToken();
  return request<ProductMemberSummary>(`/api/v1/products/${productId}/members/${userId}`, { method: 'PUT', headers: { 'X-XSRF-TOKEN': token }, body: JSON.stringify(input) });
}

export type PortfolioSummary = {
  id: string; portfolioKey: string; name: string; description: string; status: string;
  ownerUserId: string | null; createdAt: string; updatedAt: string;
};
export type ProgramSummary = {
  id: string; portfolioId: string; programKey: string; name: string; description: string; status: string;
  ownerUserId: string | null; createdAt: string; updatedAt: string;
};
export type InitiativeSummary = {
  id: string; portfolioId: string; programId: string | null; productId: string | null; initiativeKey: string;
  title: string; description: string; priority: string; status: string; targetDate: string | null;
  ownerUserId: string | null; createdAt: string; updatedAt: string;
};
export function getPortfolios(): Promise<PortfolioSummary[]> { return request<PortfolioSummary[]>('/api/v1/portfolios'); }
export async function createPortfolio(input: { portfolioKey: string; name: string; description?: string; status?: string }): Promise<PortfolioSummary> {
  const token = await csrfToken();
  return request<PortfolioSummary>('/api/v1/portfolios', { method: 'POST', headers: { 'X-XSRF-TOKEN': token }, body: JSON.stringify(input) });
}
export function getPrograms(portfolioId: string): Promise<ProgramSummary[]> { return request<ProgramSummary[]>(`/api/v1/portfolios/${portfolioId}/programs`); }
export async function createProgram(portfolioId: string, input: { programKey: string; name: string; description?: string; status?: string }): Promise<ProgramSummary> {
  const token = await csrfToken();
  return request<ProgramSummary>(`/api/v1/portfolios/${portfolioId}/programs`, { method: 'POST', headers: { 'X-XSRF-TOKEN': token }, body: JSON.stringify(input) });
}
export function getInitiatives(portfolioId: string, programId?: string): Promise<InitiativeSummary[]> {
  const query = programId ? `?programId=${encodeURIComponent(programId)}` : '';
  return request<InitiativeSummary[]>(`/api/v1/portfolios/${portfolioId}/initiatives${query}`);
}
export async function createInitiative(portfolioId: string, input: { initiativeKey: string; title: string; description?: string; priority?: string; programId?: string; productId?: string; targetDate?: string }): Promise<InitiativeSummary> {
  const token = await csrfToken();
  return request<InitiativeSummary>(`/api/v1/portfolios/${portfolioId}/initiatives`, { method: 'POST', headers: { 'X-XSRF-TOKEN': token }, body: JSON.stringify(input) });
}

export type ImportJobSummary = {
  id: string; jobType: string; status: string; totalRows: number; processedRows: number;
  acceptedRows: number; rejectedRows: number; errorCount: number; failureReason: string;
  requestedBy: string; createdAt: string; updatedAt: string; completedAt: string | null;
};
export type WorkItemImportRow = {
  sourceKey?: string; projectId?: string; sprintId?: string; assigneeUserId?: string; title: string;
  description?: string; workType?: string; priority?: string; teamId?: string; estimateMinutes?: number; acceptanceCriteria?: string;
};
export async function importWorkItems(rows: WorkItemImportRow[], dryRun = false): Promise<ImportJobSummary> {
  const token = await csrfToken();
  const key = `work-import-${crypto.randomUUID()}`;
  return request<ImportJobSummary>(`/api/v1/migrations/work-items?dryRun=${dryRun}`, { method: 'POST', headers: { 'X-XSRF-TOKEN': token, 'Idempotency-Key': key }, body: JSON.stringify({ rows }) });
}
export function getImportJob(jobId: string): Promise<ImportJobSummary> { return request<ImportJobSummary>(`/api/v1/migrations/jobs/${jobId}`); }

export type IntegrationConnectionSummary = { id: string; connectionType: string; name: string; status: string; baseUrl: string; externalAccount: string; secretConfigured: boolean; lastEventAt: string | null; lastError: string; createdAt: string; updatedAt: string };
export type IntegrationEventSummary = { id: string; connectionId: string; sourceSystem: string; eventType: string; externalId: string; status: string; repository: string; branch: string; environment: string; sourceUrl: string; occurredAt: string; receivedAt: string; duplicate: boolean };
export function getIntegrationConnections(): Promise<IntegrationConnectionSummary[]> { return request<IntegrationConnectionSummary[]>('/api/v1/integrations/connections'); }
export async function createIntegrationConnection(input: { connectionType: string; name: string; baseUrl?: string; externalAccount?: string; secret: string }): Promise<IntegrationConnectionSummary> { const token = await csrfToken(); return request<IntegrationConnectionSummary>('/api/v1/integrations/connections', { method: 'POST', headers: { 'X-XSRF-TOKEN': token }, body: JSON.stringify(input) }); }
export async function ingestIntegrationEvent(connectionId: string, secret: string, input: { sourceSystem: string; eventType: string; externalId: string; repository?: string; branch?: string; environment?: string; sourceUrl?: string; payload?: string }): Promise<IntegrationEventSummary> { const token = await csrfToken(); return request<IntegrationEventSummary>(`/api/v1/integrations/connections/${connectionId}/events`, { method: 'POST', headers: { 'X-XSRF-TOKEN': token, 'X-Integration-Secret': secret }, body: JSON.stringify(input) }); }

export type TimePolicy = {
  enabled: boolean;
  expectedDailyMinutes: number;
  allowOvertime: boolean;
  allowWeekend: boolean;
  approvalEnabled: boolean;
  approvalCadence: 'DAILY' | 'WEEKLY' | 'MONTHLY' | string;
  autoApprove: boolean;
  roundingIncrementMinutes: 1 | 5 | 10 | 15 | 30;
  roundingMode: 'NEAREST' | 'UP' | 'DOWN';
};

export type ForecastPolicy = {
  enabled: boolean;
  historicalCalibrationEnabled: boolean;
  releaseAtRiskDays: number;
  releaseAtRiskCompletionPercent: number;
  projectOverPlanPercent: number;
};

export type ProjectSummary = {
  id: string;
  projectKey: string;
  name: string;
  description: string;
  status: string;
  createdAt: string;
  productId?: string | null;
};

export type SprintSummary = {
  id: string;
  projectId: string;
  sprintKey: string;
  name: string;
  goal: string;
  startDate: string | null;
  endDate: string | null;
  status: string;
  createdAt: string;
};

export type ReleaseSummary = {
  releaseId: string;
  projectId: string;
  releaseKey: string;
  name: string;
  description: string;
  targetDate: string | null;
  status: string;
  createdAt: string;
};

export type ReleaseReadinessRow = {
  releaseId: string;
  projectId: string;
  projectKey: string;
  releaseKey: string;
  releaseName: string;
  targetDate: string | null;
  releaseStatus: string;
  totalWorkItems: number;
  completedWorkItems: number;
  openWorkItems: number;
  blockedWorkItems: number;
  completionPercent: number;
  daysToTarget: number | null;
  readinessStatus: string;
  estimatedScopeMinutes: number;
  remainingEstimateMinutes: number;
  velocityMinutesPerWeek: number;
  projectedDaysRemaining: number | null;
};

export type DependencySummary = {
  dependencyId: string;
  blockedWorkItemId: string;
  dependsOnWorkItemId: string;
  dependencyType: string;
  status: string;
  createdAt: string;
};

export type NotificationSummary = {
  id: string;
  type: string;
  title: string;
  body: string;
  readAt: string | null;
  createdAt: string;
};

export type TelegramChannel = {
  enabled: boolean;
  configured: boolean;
  chatId: string | null;
  updatedAt: string | null;
};

type LoginRequest = {
  tenantKey: string;
  email: string;
  password: string;
};

export type TenantOnboardingRequest = {
  tenantKey: string;
  legalName: string;
  displayName: string;
  timezone: string;
  ownerEmail: string;
  ownerDisplayName: string;
  ownerPassword: string;
  enabledFeatures: string[];
};

export type TenantOnboardingResponse = {
  tenantId: string;
  ownerId: string;
  tenantKey: string;
  ownerEmail: string;
  enabledFeatures: string[];
  tenantStatus: string;
  ownerStatus: string;
};

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${apiBase}${path}`, {
    credentials: 'include',
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(init?.headers ?? {}),
    },
  });

  if (!response.ok) {
    const problem = await response.json().catch(() => undefined) as { detail?: string } | undefined;
    throw new Error(problem?.detail ?? `Request failed (${response.status})`);
  }
  return response.status === 204 ? (undefined as T) : response.json() as Promise<T>;
}

export function getCurrentUser(): Promise<CurrentUser> {
  return request<CurrentUser>('/api/v1/me');
}

export function getNotifications(limit = 25): Promise<NotificationSummary[]> {
  return request<NotificationSummary[]>(`/api/v1/me/notifications?limit=${limit}`);
}

export async function markNotificationRead(notificationId: string): Promise<void> {
  const token = await csrfToken();
  await request(`/api/v1/me/notifications/${notificationId}/read`, { method: 'PATCH', headers: { 'X-XSRF-TOKEN': token } });
}

export function getTenantUsers(): Promise<TenantUser[]> {
  return request<TenantUser[]>('/api/v1/tenant/users');
}

export function getTenantRoles(): Promise<TenantRole[]> {
  return request<TenantRole[]>('/api/v1/tenant/users/roles');
}

export function getTenantLifecycle(): Promise<TenantLifecycleSummary> {
  return request<TenantLifecycleSummary>('/api/v1/tenant/lifecycle');
}

export async function updateTenantLifecycle(status: string, reason?: string): Promise<TenantLifecycleSummary> {
  const token = await csrfToken();
  return request<TenantLifecycleSummary>('/api/v1/tenant/lifecycle/status', {
    method: 'POST', headers: { 'X-XSRF-TOKEN': token }, body: JSON.stringify({ status, reason }),
  });
}

export function exportTenantData(): Promise<TenantDataExport> {
  return request<TenantDataExport>('/api/v1/tenant/lifecycle/export');
}

export function getRetentionPolicy(): Promise<RetentionPolicy> { return request<RetentionPolicy>('/api/v1/tenant/retention-policy'); }
export async function updateRetentionPolicy(policy: Omit<RetentionPolicy, 'tenantId' | 'policyVersion' | 'updatedAt'>): Promise<RetentionPolicy> {
  const token = await csrfToken();
  return request<RetentionPolicy>('/api/v1/tenant/retention-policy', { method: 'PUT', headers: { 'X-XSRF-TOKEN': token }, body: JSON.stringify(policy) });
}
export function previewRetentionCleanup(): Promise<RetentionCleanupSummary> { return request<RetentionCleanupSummary>('/api/v1/tenant/retention-policy/preview'); }
export async function executeRetentionCleanup(): Promise<RetentionCleanupSummary> { const token = await csrfToken(); return request<RetentionCleanupSummary>('/api/v1/tenant/retention-policy/cleanup', { method: 'POST', headers: { 'X-XSRF-TOKEN': token } }); }
export function getRetentionHolds(): Promise<RetentionHold[]> { return request<RetentionHold[]>('/api/v1/tenant/retention-policy/holds'); }
export async function createRetentionHold(input: { holdKey: string; reason: string }): Promise<RetentionHold> { const token = await csrfToken(); return request<RetentionHold>('/api/v1/tenant/retention-policy/holds', { method: 'POST', headers: { 'X-XSRF-TOKEN': token }, body: JSON.stringify(input) }); }
export async function releaseRetentionHold(holdId: string): Promise<RetentionHold> { const token = await csrfToken(); return request<RetentionHold>(`/api/v1/tenant/retention-policy/holds/${holdId}/release`, { method: 'POST', headers: { 'X-XSRF-TOKEN': token } }); }

export function getTenantFeatures(): Promise<FeatureEntitlement[]> {
  return request<FeatureEntitlement[]>('/api/v1/tenant/features');
}

export function getOrganizationUnits(): Promise<OrganizationUnit[]> {
  return request<OrganizationUnit[]>('/api/v1/tenant/organization/units');
}

export async function createOrganizationUnit(input: { parentUnitId?: string; unitKey: string; name: string; unitType: string; managerUserId?: string }): Promise<OrganizationUnit> {
  const token = await csrfToken();
  return request<OrganizationUnit>('/api/v1/tenant/organization/units', { method: 'POST', headers: { 'X-XSRF-TOKEN': token }, body: JSON.stringify(input) });
}

export function getTeams(): Promise<TeamSummary[]> {
  return request<TeamSummary[]>('/api/v1/tenant/organization/teams');
}

export async function createTeam(input: { parentUnitId?: string; teamKey: string; name: string; managerUserId?: string }): Promise<TeamSummary> {
  const token = await csrfToken();
  return request<TeamSummary>('/api/v1/tenant/organization/teams', { method: 'POST', headers: { 'X-XSRF-TOKEN': token }, body: JSON.stringify(input) });
}

export function getTeamMembers(teamId: string): Promise<TeamMember[]> {
  return request<TeamMember[]>(`/api/v1/tenant/organization/teams/${teamId}/members`);
}

export async function addTeamMember(teamId: string, input: { userId: string; membershipRole: string }): Promise<TeamMember> {
  const token = await csrfToken();
  return request<TeamMember>(`/api/v1/tenant/organization/teams/${teamId}/members`, { method: 'POST', headers: { 'X-XSRF-TOKEN': token }, body: JSON.stringify(input) });
}

export async function removeTeamMember(teamId: string, userId: string): Promise<void> {
  const token = await csrfToken();
  await request(`/api/v1/tenant/organization/teams/${teamId}/members/${userId}`, { method: 'DELETE', headers: { 'X-XSRF-TOKEN': token } });
}

export async function inviteTenantUser(input: { email: string; displayName: string; roleKeys: string[] }) {
  const token = await csrfToken();
  return request('/api/v1/tenant/users', { method: 'POST', headers: { 'X-XSRF-TOKEN': token }, body: JSON.stringify(input) });
}

export async function updateTenantUserStatus(userId: string, status: string): Promise<void> {
  const token = await csrfToken();
  await request(`/api/v1/tenant/users/${userId}/status`, { method: 'PATCH', headers: { 'X-XSRF-TOKEN': token }, body: JSON.stringify({ status }) });
}

export async function updateTenantFeature(featureKey: string, enabled: boolean): Promise<void> {
  const token = await csrfToken();
  await request(`/api/v1/tenant/features/${encodeURIComponent(featureKey)}`, { method: 'PUT', headers: { 'X-XSRF-TOKEN': token }, body: JSON.stringify({ enabled }) });
}

export function getTimePolicy(): Promise<TimePolicy> {
  return request<TimePolicy>('/api/v1/tenant/time-policy');
}

export function getForecastPolicy(): Promise<ForecastPolicy> {
  return request<ForecastPolicy>('/api/v1/tenant/forecast-policy');
}

export async function updateForecastPolicy(policy: ForecastPolicy): Promise<void> {
  const token = await csrfToken();
  await request<void>('/api/v1/tenant/forecast-policy', { method: 'PUT', headers: { 'X-XSRF-TOKEN': token }, body: JSON.stringify(policy) });
}

export async function updateTimePolicy(input: TimePolicy): Promise<void> {
  const token = await csrfToken();
  await request('/api/v1/tenant/time-policy', {
    method: 'PUT',
    headers: { 'X-XSRF-TOKEN': token },
    body: JSON.stringify(input),
  });
}

export function getTelegramChannel(): Promise<TelegramChannel> {
  return request<TelegramChannel>('/api/v1/tenant/notification-channels/telegram');
}

export async function updateTelegramChannel(input: { enabled: boolean; botToken?: string; chatId?: string }): Promise<void> {
  const token = await csrfToken();
  await request('/api/v1/tenant/notification-channels/telegram', { method: 'PUT', headers: { 'X-XSRF-TOKEN': token }, body: JSON.stringify(input) });
}

async function csrfToken(): Promise<string> {
  const response = await request<{ token: string }>('/api/v1/auth/csrf');

  // CookieCsrfTokenRepository stores the authoritative token in the readable
  // XSRF-TOKEN cookie. The response body may contain a request-handler token
  // representation, so state-changing requests must prefer the cookie value.
  const cookieToken = document.cookie
    .split(';')
    .map((cookie) => cookie.trim())
    .find((cookie) => cookie.startsWith('XSRF-TOKEN='))
    ?.slice('XSRF-TOKEN='.length);

  return cookieToken ? decodeURIComponent(cookieToken) : response.token;
}

export async function login(credentials: LoginRequest): Promise<CurrentUser> {
  const token = await csrfToken();
  await request('/api/v1/auth/login', {
    method: 'POST',
    headers: { 'X-XSRF-TOKEN': token },
    body: JSON.stringify(credentials),
  });
  return getCurrentUser();
}

export async function onboardTenant(input: TenantOnboardingRequest): Promise<TenantOnboardingResponse> {
  const token = await csrfToken();
  return request<TenantOnboardingResponse>('/api/v1/onboarding/tenants', {
    method: 'POST',
    headers: { 'X-XSRF-TOKEN': token },
    body: JSON.stringify(input),
  });
}

export async function logout(): Promise<void> {
  const token = await csrfToken();
  await request('/api/v1/auth/logout', {
    method: 'DELETE',
    headers: { 'X-XSRF-TOKEN': token },
  });
}

export function getWorkHub(): Promise<WorkHubResponse> {
  return request<WorkHubResponse>('/api/v1/me/work-hub');
}

export type CreateTimeEntryRequest = {
  workItemId?: string;
  projectId?: string;
  sprintId?: string;
  entryDate: string;
  minutes: number;
  description: string;
  source: 'QUICK_HOURS' | 'TIMER' | 'START_END' | 'BULK' | 'SUGGESTED';
  categoryKey?: string;
  idempotencyKey?: string;
};

export async function createTimeEntry(entry: CreateTimeEntryRequest): Promise<TimeEntrySummary> {
  const token = await csrfToken();
  const idempotencyKey = entry.idempotencyKey ?? crypto.randomUUID();
  return request<TimeEntrySummary>('/api/v1/time-entries', {
    method: 'POST',
    headers: { 'X-XSRF-TOKEN': token, 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify(entry),
  });
}

export function getPendingTimeEntries(): Promise<TimeEntrySummary[]> {
  return request<TimeEntrySummary[]>('/api/v1/manager/time-entries/pending');
}

export function getTimeCategories(): Promise<TimeCategory[]> {
  return request<TimeCategory[]>('/api/v1/tenant/time-categories');
}

export async function createTimeCategory(input: { categoryKey: string; name: string }): Promise<TimeCategory> {
  const token = await csrfToken();
  return request<TimeCategory>('/api/v1/tenant/time-categories', {
    method: 'POST', headers: { 'X-XSRF-TOKEN': token }, body: JSON.stringify(input),
  });
}

export async function updateTimeCategory(categoryId: string, input: { name: string; enabled: boolean }): Promise<TimeCategory> {
  const token = await csrfToken();
  return request<TimeCategory>(`/api/v1/tenant/time-categories/${categoryId}`, {
    method: 'PATCH', headers: { 'X-XSRF-TOKEN': token }, body: JSON.stringify(input),
  });
}

export async function decideTimeEntry(entryId: string, input: { approved: boolean; note?: string }): Promise<TimeEntrySummary> {
  const token = await csrfToken();
  return request<TimeEntrySummary>(`/api/v1/manager/time-entries/${entryId}/approval`, {
    method: 'POST',
    headers: { 'X-XSRF-TOKEN': token },
    body: JSON.stringify(input),
  });
}

export function getCurrentTimesheet(): Promise<TimesheetSummary> {
  return request<TimesheetSummary>('/api/v1/me/timesheet');
}

export async function submitTimesheet(timesheetId: string): Promise<TimesheetSummary> {
  const token = await csrfToken();
  return request<TimesheetSummary>(`/api/v1/me/timesheet/${timesheetId}/submit`, {
    method: 'POST', headers: { 'X-XSRF-TOKEN': token },
  });
}

export function getPendingTimesheets(): Promise<TimesheetSummary[]> {
  return request<TimesheetSummary[]>('/api/v1/manager/timesheets/pending');
}

export async function decideTimesheet(timesheetId: string, input: { approved: boolean; note?: string }): Promise<TimesheetSummary> {
  const token = await csrfToken();
  return request<TimesheetSummary>(`/api/v1/manager/timesheets/${timesheetId}/decision`, {
    method: 'POST', headers: { 'X-XSRF-TOKEN': token }, body: JSON.stringify(input),
  });
}

export async function reopenTimesheet(timesheetId: string, note = ''): Promise<TimesheetSummary> {
  const token = await csrfToken();
  return request<TimesheetSummary>(`/api/v1/manager/timesheets/${timesheetId}/reopen`, {
    method: 'POST', headers: { 'X-XSRF-TOKEN': token }, body: JSON.stringify({ note }),
  });
}

export type CreateWorkItemRequest = {
  projectId?: string;
  sprintId?: string;
  assigneeUserId?: string;
  title: string;
  description: string;
  workType: string;
  priority: string;
  teamId?: string;
  estimateMinutes?: number;
  parentWorkItemId?: string;
  acceptanceCriteria?: string;
};

export async function createWorkItem(input: CreateWorkItemRequest): Promise<WorkItemSummary> {
  const token = await csrfToken();
  return request<WorkItemSummary>('/api/v1/work-items', {
    method: 'POST',
    headers: { 'X-XSRF-TOKEN': token },
    body: JSON.stringify(input),
  });
}

export async function updateWorkItem(workItemId: string, input: { status?: string; description?: string; assigneeUserId?: string; teamId?: string; assignmentReason?: string; estimateMinutes?: number; parentWorkItemId?: string; acceptanceCriteria?: string }): Promise<WorkItemSummary> {
  const token = await csrfToken();
  return request<WorkItemSummary>(`/api/v1/work-items/${workItemId}`, {
    method: 'PATCH',
    headers: { 'X-XSRF-TOKEN': token },
    body: JSON.stringify(input),
  });
}

export async function reorderWorkItem(workItemId: string, targetPosition: number): Promise<WorkItemSummary> {
  const token = await csrfToken();
  return request<WorkItemSummary>(`/api/v1/work-items/${workItemId}/rank`, {
    method: 'PATCH',
    headers: { 'X-XSRF-TOKEN': token },
    body: JSON.stringify({ targetPosition }),
  });
}

export function getWorkItemAssignmentHistory(workItemId: string): Promise<WorkItemAssignmentHistory[]> {
  return request<WorkItemAssignmentHistory[]>(`/api/v1/work-items/${workItemId}/assignment-history`);
}

export function getWorkScopeChangeRequests(workItemId: string): Promise<WorkScopeChangeRequest[]> {
  return request<WorkScopeChangeRequest[]>(`/api/v1/work-items/${workItemId}/scope-change-requests`);
}

export async function requestWorkScopeChange(workItemId: string, input: { requestedPriority?: string; targetSprintId?: string; emergency: boolean; reason: string }): Promise<WorkScopeChangeRequest> {
  const token = await csrfToken();
  return request<WorkScopeChangeRequest>(`/api/v1/work-items/${workItemId}/scope-change-requests`, { method: 'POST', headers: { 'X-XSRF-TOKEN': token }, body: JSON.stringify(input) });
}

export async function decideWorkScopeChange(requestId: string, input: { approve: boolean; decisionNote: string }): Promise<WorkScopeChangeRequest> {
  const token = await csrfToken();
  return request<WorkScopeChangeRequest>(`/api/v1/work-items/scope-change-requests/${requestId}/decision`, { method: 'POST', headers: { 'X-XSRF-TOKEN': token }, body: JSON.stringify(input) });
}

export function getPendingWorkScopeChanges(): Promise<WorkScopeChangeRequest[]> {
  return request<WorkScopeChangeRequest[]>('/api/v1/manager/scope-change-requests');
}

export function getWorkItems(filters?: { projectId?: string; sprintId?: string; assigneeUserId?: string; teamId?: string }): Promise<WorkItemSummary[]> {
  const params = new URLSearchParams();
  if (filters?.projectId) params.set('projectId', filters.projectId);
  if (filters?.sprintId) params.set('sprintId', filters.sprintId);
  if (filters?.assigneeUserId) params.set('assigneeUserId', filters.assigneeUserId);
  if (filters?.teamId) params.set('teamId', filters.teamId);
  const query = params.toString();
  return request<WorkItemSummary[]>(`/api/v1/work-items${query ? `?${query}` : ''}`);
}

export function getManagerWorkload(weekStart?: string): Promise<ResourceWorkloadMember[]> {
  const query = weekStart ? `?weekStart=${encodeURIComponent(weekStart)}` : '';
  return request<ResourceWorkloadMember[]>(`/api/v1/manager/workload${query}`);
}

export function getResourceAllocations(weekStart?: string): Promise<ResourceAllocationSummary[]> {
  const query = weekStart ? `?weekStart=${encodeURIComponent(weekStart)}` : '';
  return request<ResourceAllocationSummary[]>(`/api/v1/manager/workload/allocations${query}`);
}

export async function createResourceAllocation(input: { userId: string; projectId: string; sprintId?: string; teamId?: string; startDate: string; endDate: string; plannedMinutesPerWeek: number; note: string }): Promise<ResourceAllocationSummary> {
  const token = await csrfToken();
  return request<ResourceAllocationSummary>('/api/v1/manager/workload/allocations', { method: 'POST', headers: { 'X-XSRF-TOKEN': token }, body: JSON.stringify(input) });
}

export function getControlTower(weekStart?: string): Promise<ControlTowerSummary> {
  const query = weekStart ? `?weekStart=${encodeURIComponent(weekStart)}` : '';
  return request<ControlTowerSummary>(`/api/v1/manager/control-tower${query}`);
}

export function getDeliveryReport(filters?: { from?: string; to?: string; projectId?: string }): Promise<DeliveryReportRow[]> {
  const params = new URLSearchParams();
  if (filters?.from) params.set('from', filters.from);
  if (filters?.to) params.set('to', filters.to);
  if (filters?.projectId) params.set('projectId', filters.projectId);
  const query = params.toString();
  return request<DeliveryReportRow[]>(`/api/v1/manager/reports/delivery${query ? `?${query}` : ''}`);
}

export function getDeliveryForecast(filters?: { from?: string; to?: string; projectId?: string }): Promise<DeliveryForecastRow[]> {
  const params = new URLSearchParams();
  if (filters?.from) params.set('from', filters.from);
  if (filters?.to) params.set('to', filters.to);
  if (filters?.projectId) params.set('projectId', filters.projectId);
  const query = params.toString();
  return request<DeliveryForecastRow[]>(`/api/v1/manager/reports/forecast${query ? `?${query}` : ''}`);
}

export function getForecastCalibration(filters?: { from?: string; to?: string; projectId?: string }): Promise<ForecastCalibrationRow[]> {
  const params = new URLSearchParams();
  if (filters?.from) params.set('from', filters.from);
  if (filters?.to) params.set('to', filters.to);
  if (filters?.projectId) params.set('projectId', filters.projectId);
  const query = params.toString();
  return request<ForecastCalibrationRow[]>(`/api/v1/manager/reports/forecast-calibration${query ? `?${query}` : ''}`);
}

export async function downloadDeliveryReportCsv(filters?: { from?: string; to?: string; projectId?: string }): Promise<void> {
  const params = new URLSearchParams();
  if (filters?.from) params.set('from', filters.from);
  if (filters?.to) params.set('to', filters.to);
  if (filters?.projectId) params.set('projectId', filters.projectId);
  const query = params.toString();
  const response = await fetch(`${apiBase}/api/v1/manager/reports/delivery.csv${query ? `?${query}` : ''}`, { credentials: 'include' });
  if (!response.ok) throw new Error(`Export failed (${response.status})`);
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = 'delivery-report.csv';
  anchor.click();
  URL.revokeObjectURL(url);
}

export function getProjects(): Promise<ProjectSummary[]> {
  return request<ProjectSummary[]>('/api/v1/projects');
}

export type ProjectTeamSummary = { projectId: string; teamId: string; relationshipRole: string; createdAt: string };
export type SprintTeamSummary = { sprintId: string; teamId: string; relationshipRole: string; createdAt: string };

export function getProjectTeams(projectId: string): Promise<ProjectTeamSummary[]> {
  return request<ProjectTeamSummary[]>(`/api/v1/projects/${projectId}/teams`);
}

export async function addProjectTeam(projectId: string, input: { teamId: string; relationshipRole?: string }): Promise<ProjectTeamSummary> {
  const token = await csrfToken();
  return request<ProjectTeamSummary>(`/api/v1/projects/${projectId}/teams`, { method: 'POST', headers: { 'X-XSRF-TOKEN': token }, body: JSON.stringify(input) });
}

export async function removeProjectTeam(projectId: string, teamId: string): Promise<void> {
  const token = await csrfToken();
  await request(`/api/v1/projects/${projectId}/teams/${teamId}`, { method: 'DELETE', headers: { 'X-XSRF-TOKEN': token } });
}

export type CreateProjectRequest = {
  projectKey: string;
  name: string;
  description: string;
  productId?: string;
};

export async function createProject(input: CreateProjectRequest): Promise<ProjectSummary> {
  const token = await csrfToken();
  return request<ProjectSummary>('/api/v1/projects', {
    method: 'POST',
    headers: { 'X-XSRF-TOKEN': token },
    body: JSON.stringify(input),
  });
}

export function getSprints(projectId: string): Promise<SprintSummary[]> {
  return request<SprintSummary[]>(`/api/v1/projects/${projectId}/sprints`);
}

export type CreateSprintRequest = {
  sprintKey: string;
  name: string;
  goal: string;
  startDate: string;
  endDate: string;
};

export async function createSprint(projectId: string, input: CreateSprintRequest): Promise<SprintSummary> {
  const token = await csrfToken();
  return request<SprintSummary>(`/api/v1/projects/${projectId}/sprints`, {
    method: 'POST',
    headers: { 'X-XSRF-TOKEN': token },
    body: JSON.stringify(input),
  });
}

export function getSprintTeams(projectId: string, sprintId: string): Promise<SprintTeamSummary[]> {
  return request<SprintTeamSummary[]>(`/api/v1/projects/${projectId}/sprints/${sprintId}/teams`);
}

export type SprintProgressSummary = {
  sprintId: string;
  totalWorkItems: number;
  todoWorkItems: number;
  inProgressWorkItems: number;
  blockedWorkItems: number;
  completedWorkItems: number;
  cancelledWorkItems: number;
  carryOverWorkItems: number;
};

export function getSprintProgress(projectId: string, sprintId: string): Promise<SprintProgressSummary> {
  return request<SprintProgressSummary>(`/api/v1/projects/${projectId}/sprints/${sprintId}/summary`);
}

export async function updateSprintStatus(projectId: string, sprintId: string, status: string, completionNote = ''): Promise<SprintSummary> {
  const token = await csrfToken();
  return request<SprintSummary>(`/api/v1/projects/${projectId}/sprints/${sprintId}`, { method: 'PATCH', headers: { 'X-XSRF-TOKEN': token }, body: JSON.stringify({ status, completionNote }) });
}

export type SprintCarryOverResult = { sourceSprintId: string; targetSprintId: string; movedWorkItemCount: number; remainingWorkItemCount: number };

export async function carryOverSprint(projectId: string, sourceSprintId: string, targetSprintId: string, workItemIds?: string[]): Promise<SprintCarryOverResult> {
  const token = await csrfToken();
  return request<SprintCarryOverResult>(`/api/v1/projects/${projectId}/sprints/${sourceSprintId}/carry-over`, { method: 'POST', headers: { 'X-XSRF-TOKEN': token }, body: JSON.stringify({ targetSprintId, workItemIds }) });
}

export async function addSprintTeam(projectId: string, sprintId: string, input: { teamId: string; relationshipRole?: string }): Promise<SprintTeamSummary> {
  const token = await csrfToken();
  return request<SprintTeamSummary>(`/api/v1/projects/${projectId}/sprints/${sprintId}/teams`, { method: 'POST', headers: { 'X-XSRF-TOKEN': token }, body: JSON.stringify(input) });
}

export function getReleases(projectId: string): Promise<ReleaseSummary[]> {
  return request<ReleaseSummary[]>(`/api/v1/projects/${projectId}/releases`);
}

export function getReleaseReadiness(projectId?: string): Promise<ReleaseReadinessRow[]> {
  const query = projectId ? `?projectId=${encodeURIComponent(projectId)}` : '';
  return request<ReleaseReadinessRow[]>(`/api/v1/manager/reports/release-readiness${query}`);
}

export async function createRelease(projectId: string, input: { releaseKey: string; name: string; description: string; targetDate: string }): Promise<ReleaseSummary> {
  const token = await csrfToken();
  return request<ReleaseSummary>(`/api/v1/projects/${projectId}/releases`, { method: 'POST', headers: { 'X-XSRF-TOKEN': token }, body: JSON.stringify(input) });
}

export function getProjectWorkItems(projectId: string): Promise<WorkItemSummary[]> {
  return request<WorkItemSummary[]>(`/api/v1/work-items?projectId=${encodeURIComponent(projectId)}`);
}

export function getDependencies(workItemId: string): Promise<DependencySummary[]> {
  return request<DependencySummary[]>(`/api/v1/work-items/${workItemId}/dependencies`);
}

export async function createDependency(workItemId: string, input: { dependsOnWorkItemId: string; dependencyType: string }): Promise<DependencySummary> {
  const token = await csrfToken();
  return request<DependencySummary>(`/api/v1/work-items/${workItemId}/dependencies`, { method: 'POST', headers: { 'X-XSRF-TOKEN': token }, body: JSON.stringify(input) });
}

export type DemandSummary = {
  id: string;
  demandKey: string;
  title: string;
  description: string;
  demandType: 'CLIENT' | 'INTERNAL' | 'IDEA' | 'INCIDENT' | string;
  priority: 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT' | string;
  status: string;
  requestedBy: string;
  ownerUserId: string | null;
  decidedBy: string | null;
  decisionNote: string;
  decidedAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export type ReadinessCheck = {
  id: string;
  checkKey: string;
  label: string;
  required: boolean;
  completed: boolean;
  evidence: string;
  updatedAt: string;
};

export type RequirementSummary = {
  id: string;
  requirementKey: string;
  demandId: string | null;
  title: string;
  description: string;
  businessContext: string;
  uxContext: string;
  technicalContext: string;
  acceptanceCriteria: string;
  status: string;
  reviewNote: string;
  reviewedAt: string | null;
  createdAt: string;
  updatedAt: string;
  checks: ReadinessCheck[];
};

export type WorkflowStatusDefinition = { key: string; label: string; terminal: boolean; position: number };
export type WorkflowTransitionDefinition = { fromStatus: string; toStatus: string };
export type WorkflowSummary = {
  id: string;
  workflowKey: string;
  name: string;
  methodology: string;
  status: string;
  statuses: WorkflowStatusDefinition[];
  transitions: WorkflowTransitionDefinition[];
  createdAt: string;
};

export type BoardConfiguration = {
  projectId: string;
  grouping: 'STATUS' | 'ASSIGNEE' | 'TEAM' | 'PRIORITY' | string;
  swimlane: 'NONE' | 'ASSIGNEE' | 'TEAM' | 'PARENT' | string;
  cardFields: string[];
  wipLimits: Record<string, number>;
  updatedAt: string | null;
};

export function getDemands(status?: string): Promise<DemandSummary[]> {
  return request<DemandSummary[]>(`/api/v1/demands${status ? `?status=${encodeURIComponent(status)}` : ''}`);
}

export async function createDemand(input: { demandKey: string; title: string; description: string; demandType: string; priority: string }): Promise<DemandSummary> {
  const token = await csrfToken();
  return request<DemandSummary>('/api/v1/demands', { method: 'POST', headers: { 'X-XSRF-TOKEN': token }, body: JSON.stringify(input) });
}

export async function decideDemand(demandId: string, input: { status: string; ownerUserId?: string; note?: string }): Promise<DemandSummary> {
  const token = await csrfToken();
  return request<DemandSummary>(`/api/v1/demands/${demandId}/decision`, { method: 'POST', headers: { 'X-XSRF-TOKEN': token }, body: JSON.stringify(input) });
}

export function getRequirements(status?: string): Promise<RequirementSummary[]> {
  return request<RequirementSummary[]>(`/api/v1/requirements${status ? `?status=${encodeURIComponent(status)}` : ''}`);
}

export async function createRequirement(input: { requirementKey: string; demandId?: string; title: string; description: string; businessContext: string; uxContext: string; technicalContext: string; acceptanceCriteria: string }): Promise<RequirementSummary> {
  const token = await csrfToken();
  return request<RequirementSummary>('/api/v1/requirements', { method: 'POST', headers: { 'X-XSRF-TOKEN': token }, body: JSON.stringify(input) });
}

export async function updateRequirementCheck(requirementId: string, input: { checkKey: string; label: string; required: boolean; completed: boolean; evidence: string }): Promise<RequirementSummary> {
  const token = await csrfToken();
  return request<RequirementSummary>(`/api/v1/requirements/${requirementId}/checks`, { method: 'POST', headers: { 'X-XSRF-TOKEN': token }, body: JSON.stringify(input) });
}

export async function decideRequirement(requirementId: string, input: { status: string; note?: string }): Promise<RequirementSummary> {
  const token = await csrfToken();
  return request<RequirementSummary>(`/api/v1/requirements/${requirementId}/decision`, { method: 'POST', headers: { 'X-XSRF-TOKEN': token }, body: JSON.stringify(input) });
}

export function getWorkflows(): Promise<WorkflowSummary[]> {
  return request<WorkflowSummary[]>('/api/v1/workflows');
}

export async function createWorkflow(input: { workflowKey: string; name: string; methodology: string; statuses: WorkflowStatusDefinition[]; transitions: WorkflowTransitionDefinition[] }): Promise<WorkflowSummary> {
  const token = await csrfToken();
  return request<WorkflowSummary>('/api/v1/workflows', { method: 'POST', headers: { 'X-XSRF-TOKEN': token }, body: JSON.stringify(input) });
}

export async function assignProjectWorkflow(projectId: string, workflowId: string): Promise<void> {
  const token = await csrfToken();
  await request(`/api/v1/workflows/projects/${projectId}`, { method: 'PUT', headers: { 'X-XSRF-TOKEN': token }, body: JSON.stringify({ workflowId }) });
}

export function getBoardConfiguration(projectId: string): Promise<BoardConfiguration> {
  return request<BoardConfiguration>(`/api/v1/projects/${projectId}/board-config`);
}

export async function updateBoardConfiguration(projectId: string, input: { grouping: string; swimlane: string; cardFields: string[]; wipLimits: Record<string, number> }): Promise<BoardConfiguration> {
  const token = await csrfToken();
  return request<BoardConfiguration>(`/api/v1/projects/${projectId}/board-config`, { method: 'PUT', headers: { 'X-XSRF-TOKEN': token }, body: JSON.stringify(input) });
}

export type QualityGateSummary = { id: string; projectId: string | null; releaseId: string | null; gateKey: string; gateType: string; status: string; required: boolean; evidence: string; decidedBy: string | null; decidedAt: string | null; createdAt: string; updatedAt: string };
export type IncidentSummary = { id: string; projectId: string | null; releaseId: string | null; incidentKey: string; title: string; description: string; severity: string; status: string; ownerUserId: string | null; mitigation: string; rootCause: string; openedAt: string; updatedAt: string };

export function getQualityGates(filters?: { projectId?: string; releaseId?: string }): Promise<QualityGateSummary[]> {
  const params = new URLSearchParams(); if (filters?.projectId) params.set('projectId', filters.projectId); if (filters?.releaseId) params.set('releaseId', filters.releaseId);
  const query = params.toString(); return request<QualityGateSummary[]>(`/api/v1/quality/gates${query ? `?${query}` : ''}`);
}

export async function createQualityGate(input: { projectId?: string; releaseId?: string; gateKey: string; gateType: string; required: boolean }): Promise<QualityGateSummary> {
  const token = await csrfToken(); return request<QualityGateSummary>('/api/v1/quality/gates', { method: 'POST', headers: { 'X-XSRF-TOKEN': token }, body: JSON.stringify(input) });
}

export async function decideQualityGate(gateId: string, input: { status: string; evidence: string }): Promise<QualityGateSummary> {
  const token = await csrfToken(); return request<QualityGateSummary>(`/api/v1/quality/gates/${gateId}/decision`, { method: 'POST', headers: { 'X-XSRF-TOKEN': token }, body: JSON.stringify(input) });
}

export function getIncidents(status?: string): Promise<IncidentSummary[]> { return request<IncidentSummary[]>(`/api/v1/quality/incidents${status ? `?status=${encodeURIComponent(status)}` : ''}`); }

export async function createIncident(input: { projectId?: string; releaseId?: string; incidentKey: string; title: string; description: string; severity: string; ownerUserId?: string }): Promise<IncidentSummary> {
  const token = await csrfToken(); return request<IncidentSummary>('/api/v1/quality/incidents', { method: 'POST', headers: { 'X-XSRF-TOKEN': token }, body: JSON.stringify(input) });
}

export async function updateIncident(incidentId: string, input: { status: string; mitigation: string; rootCause: string }): Promise<IncidentSummary> {
  const token = await csrfToken(); return request<IncidentSummary>(`/api/v1/quality/incidents/${incidentId}`, { method: 'PATCH', headers: { 'X-XSRF-TOKEN': token }, body: JSON.stringify(input) });
}
