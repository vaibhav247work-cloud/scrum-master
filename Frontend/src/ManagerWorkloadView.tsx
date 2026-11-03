import { FormEvent, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ResourceAllocationSummary, ResourceWorkloadMember, WorkScopeChangeRequest, TimeEntrySummary, TimesheetSummary, createResourceAllocation, decideTimeEntry, decideTimesheet, decideWorkScopeChange, getManagerWorkload, getPendingTimeEntries, getPendingTimesheets, getPendingWorkScopeChanges, getProjects, getResourceAllocations, getTenantUsers, ProjectSummary, TenantUser } from './api';

const demoMembers: ResourceWorkloadMember[] = [
  { userId: '1', email: 'ananya@example.com', displayName: 'Ananya Nair', status: 'ACTIVE', capacityMinutesPerWeek: 2400, loggedMinutesThisWeek: 2208, openWorkItemCount: 5, utilizationPercent: 92, loadStatus: 'AT_RISK', lastActivityAt: new Date().toISOString(), assignedWork: [], recentActivity: [] },
  { userId: '2', email: 'vikram@example.com', displayName: 'Vikram Kumar', status: 'ACTIVE', capacityMinutesPerWeek: 2400, loggedMinutesThisWeek: 1632, openWorkItemCount: 3, utilizationPercent: 68, loadStatus: 'BALANCED', lastActivityAt: new Date().toISOString(), assignedWork: [], recentActivity: [] },
  { userId: '3', email: 'sara@example.com', displayName: 'Sara Patel', status: 'ACTIVE', capacityMinutesPerWeek: 2400, loggedMinutesThisWeek: 1056, openWorkItemCount: 2, utilizationPercent: 44, loadStatus: 'BALANCED', lastActivityAt: new Date().toISOString(), assignedWork: [], recentActivity: [] },
];
const demoPendingTimeEntries: TimeEntrySummary[] = [
  { id: 'demo-time-approval', userId: 'demo-user', workItemId: null, projectId: null, sprintId: null, entryDate: new Date().toISOString().slice(0, 10), minutes: 90, description: 'Reviewed payment retry and rollback cases.', source: 'QUICK_HOURS', categoryKey: 'DEVELOPMENT', createdAt: new Date().toISOString(), approvalStatus: 'PENDING' },
];

export function ManagerWorkloadView({ demoMode }: { demoMode: boolean }) {
  const queryClient = useQueryClient();
  const [selected, setSelected] = useState<ResourceWorkloadMember | null>(null);
  const [filter, setFilter] = useState<WorkloadFilter>('ALL');
  const [sort, setSort] = useState<WorkloadSort>('LOAD_DESC');
  const query = useQuery({ queryKey: ['manager-workload'], queryFn: () => getManagerWorkload(), enabled: !demoMode });
  const allocationQuery = useQuery({ queryKey: ['resource-allocations'], queryFn: () => getResourceAllocations(), enabled: !demoMode });
  const usersQuery = useQuery({ queryKey: ['tenant-users'], queryFn: () => getTenantUsers(), enabled: !demoMode });
  const projectsQuery = useQuery({ queryKey: ['projects'], queryFn: () => getProjects(), enabled: !demoMode });
  const scopeChangesQuery = useQuery({ queryKey: ['pending-scope-changes'], queryFn: () => getPendingWorkScopeChanges(), enabled: !demoMode });
  const timeApprovalQuery = useQuery({ queryKey: ['pending-time-entries'], queryFn: getPendingTimeEntries, enabled: !demoMode });
  const timesheetApprovalQuery = useQuery({ queryKey: ['pending-timesheets'], queryFn: getPendingTimesheets, enabled: !demoMode });
  const [allocationUserId, setAllocationUserId] = useState('');
  const [allocationProjectId, setAllocationProjectId] = useState('');
  const [allocationStartDate, setAllocationStartDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [allocationEndDate, setAllocationEndDate] = useState(() => new Date(Date.now() + 6 * 86400000).toISOString().slice(0, 10));
  const [allocationMinutes, setAllocationMinutes] = useState(1200);
  const [allocationNote, setAllocationNote] = useState('');
  const allocationMutation = useMutation({ mutationFn: (input: { userId: string; projectId: string; startDate: string; endDate: string; plannedMinutesPerWeek: number; note: string }) => createResourceAllocation(input), onSuccess: async () => { await queryClient.invalidateQueries({ queryKey: ['resource-allocations'] }); setAllocationUserId(''); setAllocationProjectId(''); setAllocationNote(''); } });
  const scopeDecisionMutation = useMutation({ mutationFn: ({ requestId, approve }: { requestId: string; approve: boolean }) => decideWorkScopeChange(requestId, { approve, decisionNote: approve ? 'Approved from manager workspace.' : 'Rejected from manager workspace.' }), onSuccess: () => queryClient.invalidateQueries({ queryKey: ['pending-scope-changes'] }) });
  const timeDecisionMutation = useMutation({ mutationFn: ({ entryId, approved }: { entryId: string; approved: boolean }) => decideTimeEntry(entryId, { approved, note: approved ? 'Approved from manager workspace.' : 'Rejected from manager workspace.' }), onSuccess: () => queryClient.invalidateQueries({ queryKey: ['pending-time-entries'] }) });
  const timesheetDecisionMutation = useMutation({ mutationFn: ({ timesheetId, approved }: { timesheetId: string; approved: boolean }) => decideTimesheet(timesheetId, { approved, note: approved ? 'Approved from manager workspace.' : 'Rejected from manager workspace.' }), onSuccess: () => queryClient.invalidateQueries({ queryKey: ['pending-timesheets'] }) });
  const members = demoMode ? demoMembers : query.data ?? [];
  const atRisk = members.filter((member) => member.loadStatus === 'AT_RISK' || member.loadStatus === 'OVERLOADED').length;
  const available = members.filter((member) => member.loadStatus === 'AVAILABLE').length;
  const average = members.length ? Math.round(members.reduce((sum, member) => sum + member.utilizationPercent, 0) / members.length) : 0;
  const visibleMembers = members.filter((member) => matchesFilter(member, filter)).sort((left, right) => compareMembers(left, right, sort));
  const pendingTimeEntries = demoMode ? demoPendingTimeEntries : timeApprovalQuery.data ?? [];
  const pendingTimesheets = demoMode ? [] : timesheetApprovalQuery.data ?? [];
  function submitAllocation(event: FormEvent) { event.preventDefault(); if (!demoMode && allocationUserId && allocationProjectId) allocationMutation.mutate({ userId: allocationUserId, projectId: allocationProjectId, startDate: allocationStartDate, endDate: allocationEndDate, plannedMinutesPerWeek: allocationMinutes, note: allocationNote.trim() }); }
  return <>
    <PageHeader eyebrow="MANAGER WORKSPACE" title="My Team" description="Capacity, current work, and recent activity for your authorized team." />
    {query.isError && <div className="inline-error">Could not load manager workload. Check your permissions or backend connection.</div>}
    <div className="metric-grid team-metrics"><div className="metric-card blue"><span>Average utilization</span><strong>{average}%</strong><small>Selected team members</small></div><div className="metric-card rose"><span>Needs attention</span><strong>{atRisk}</strong><small>At risk or overloaded</small></div><div className="metric-card cyan"><span>Available</span><strong>{available}</strong><small>Ready for new work</small></div><div className="metric-card violet"><span>People tracked</span><strong>{members.length}</strong><small>Active tenant members</small></div></div>
    <div className="section-row"><div><h2 className="section-title">Team members</h2><p className="section-subtitle">The signal combines configured capacity, actual logged time, and open assigned work.</p></div><span className="status-pill neutral">{query.isLoading ? 'Syncing…' : `${visibleMembers.length} shown`}</span></div>
    <div className="panel workload-toolbar" aria-label="Workload filters"><div className="workload-filters" role="group" aria-label="Filter team members"><button className={filter === 'ALL' ? 'filter-button active-filter' : 'filter-button'} aria-pressed={filter === 'ALL'} onClick={() => setFilter('ALL')}>Everyone <span>{members.length}</span></button><button className={filter === 'NEEDS_ATTENTION' ? 'filter-button active-filter' : 'filter-button'} aria-pressed={filter === 'NEEDS_ATTENTION'} onClick={() => setFilter('NEEDS_ATTENTION')}>Needs attention <span>{atRisk}</span></button><button className={filter === 'AVAILABLE' ? 'filter-button active-filter' : 'filter-button'} aria-pressed={filter === 'AVAILABLE'} onClick={() => setFilter('AVAILABLE')}>Available <span>{available}</span></button></div><label className="workload-sort">Sort by<select value={sort} onChange={(event) => setSort(event.target.value as WorkloadSort)}><option value="LOAD_DESC">Highest load</option><option value="OPEN_WORK_DESC">Most open work</option><option value="ACTIVITY_DESC">Recent activity</option><option value="NAME_ASC">Name</option></select></label></div>
    {!demoMode && <section className="panel allocation-panel"><div className="panel-head"><div><div className="eyebrow">CAPACITY PLANNING</div><h3>Planned allocations</h3><p className="section-subtitle">Reserve weekly capacity against a project before work is assigned.</p></div><span className="status-pill neutral">{allocationQuery.isLoading ? 'Loading…' : `${(allocationQuery.data ?? []).length} active`}</span></div><form className="allocation-form" onSubmit={submitAllocation}><select required value={allocationUserId} onChange={(event) => setAllocationUserId(event.target.value)} aria-label="Allocation employee"><option value="">Employee</option>{(usersQuery.data ?? []).filter((user) => user.status === 'ACTIVE').map((user) => <option key={user.userId} value={user.userId}>{user.displayName}</option>)}</select><select required value={allocationProjectId} onChange={(event) => setAllocationProjectId(event.target.value)} aria-label="Allocation project"><option value="">Project</option>{(projectsQuery.data ?? []).map((project) => <option key={project.id} value={project.id}>{project.name}</option>)}</select><input required type="date" value={allocationStartDate} onChange={(event) => setAllocationStartDate(event.target.value)} aria-label="Allocation start date" /><input required type="date" value={allocationEndDate} onChange={(event) => setAllocationEndDate(event.target.value)} aria-label="Allocation end date" /><input required type="number" min="1" max="10080" value={allocationMinutes} onChange={(event) => setAllocationMinutes(Number(event.target.value))} aria-label="Planned minutes per week" /><input maxLength={1000} value={allocationNote} onChange={(event) => setAllocationNote(event.target.value)} placeholder="Planning note" aria-label="Allocation note" /><button className="primary-button" disabled={allocationMutation.isPending}>{allocationMutation.isPending ? 'Saving…' : 'Reserve capacity'}</button></form>{allocationMutation.isError && <div className="inline-error">{(allocationMutation.error as Error).message}</div>}<AllocationList allocations={allocationQuery.data ?? []} users={usersQuery.data ?? []} projects={projectsQuery.data ?? []} /></section>}
    {!demoMode && <ScopeChangeInbox requests={scopeChangesQuery.data ?? []} loading={scopeChangesQuery.isLoading} pending={scopeDecisionMutation.isPending} onDecision={(requestId, approve) => scopeDecisionMutation.mutate({ requestId, approve })} />}
    <TimeApprovalInbox entries={pendingTimeEntries} loading={!demoMode && timeApprovalQuery.isLoading} pending={timeDecisionMutation.isPending || demoMode} onDecision={(entryId, approved) => timeDecisionMutation.mutate({ entryId, approved })} />
    {!demoMode && <TimesheetApprovalInbox timesheets={pendingTimesheets} loading={timesheetApprovalQuery.isLoading} pending={timesheetDecisionMutation.isPending} onDecision={(timesheetId, approved) => timesheetDecisionMutation.mutate({ timesheetId, approved })} />}
    <div className="member-grid">{query.isLoading ? <div className="panel empty-state">Loading workload…</div> : visibleMembers.length ? visibleMembers.map((member) => <MemberCard key={member.userId} member={member} onSelect={() => setSelected(member)} />) : <div className="panel empty-state">No team members match this view.</div>}</div>
    {selected && <MemberDetail member={selected} onClose={() => setSelected(null)} />}
  </>;
}

type WorkloadFilter = 'ALL' | 'NEEDS_ATTENTION' | 'AVAILABLE';
type WorkloadSort = 'LOAD_DESC' | 'OPEN_WORK_DESC' | 'ACTIVITY_DESC' | 'NAME_ASC';

function matchesFilter(member: ResourceWorkloadMember, filter: WorkloadFilter) {
  if (filter === 'NEEDS_ATTENTION') return member.loadStatus === 'AT_RISK' || member.loadStatus === 'OVERLOADED';
  if (filter === 'AVAILABLE') return member.loadStatus === 'AVAILABLE';
  return true;
}

function compareMembers(left: ResourceWorkloadMember, right: ResourceWorkloadMember, sort: WorkloadSort) {
  if (sort === 'OPEN_WORK_DESC') return right.openWorkItemCount - left.openWorkItemCount;
  if (sort === 'ACTIVITY_DESC') return dateValue(right.lastActivityAt) - dateValue(left.lastActivityAt);
  if (sort === 'NAME_ASC') return left.displayName.localeCompare(right.displayName);
  return right.utilizationPercent - left.utilizationPercent;
}

function dateValue(value: string | null) { return value ? Date.parse(value) || 0 : 0; }

function MemberCard({ member, onSelect }: { member: ResourceWorkloadMember; onSelect: () => void }) {
  const tone = member.loadStatus === 'OVERLOADED' ? 'rose' : member.loadStatus === 'AT_RISK' ? 'violet' : 'blue';
  const fill = member.utilizationPercent > 100 ? 100 : member.utilizationPercent;
  const planned = member.plannedAllocationMinutesThisWeek ?? 0;
  const variance = member.actualVsPlannedPercent ?? 0;
  return <div className="member-card"><div className="member-head"><div className={`member-avatar ${tone}`}>{initials(member.displayName)}</div><div><strong>{member.displayName}</strong><span>{member.email}</span></div><span className={`load-status ${member.loadStatus.toLowerCase()}`}>{member.loadStatus.replace('_', ' ')}</span></div><div className="load-row"><span>Current load</span><strong>{member.utilizationPercent}%</strong></div><div className="load-bar"><div className={`load-fill ${member.utilizationPercent >= 100 ? 'danger' : member.utilizationPercent >= 80 ? 'warning' : 'healthy'}`} style={{ width: `${fill}%` }} /></div><div className="member-meta"><span>{member.openWorkItemCount} open tasks</span><span>{formatMinutes(member.loggedMinutesThisWeek)} logged</span></div><div className="plan-variance"><span>Actual vs planned</span><strong>{planned ? `${variance}% · ${member.allocationStatus?.replace('_', ' ')}` : 'No allocation'}</strong></div>{member.teams?.length ? <div className="member-teams">{member.teams.map((team) => <span className="status-pill neutral" key={team.teamId}>{team.teamName}</span>)}</div> : null}<button className="outline-button" onClick={onSelect}>View work & activity →</button></div>;
}

function MemberDetail({ member, onClose }: { member: ResourceWorkloadMember; onClose: () => void }) {
  return <div className="panel member-detail"><div className="panel-head"><div><div className="eyebrow">MEMBER DETAIL</div><h3>{member.displayName}</h3></div><button className="more-button" onClick={onClose}>×</button></div><div className="detail-columns"><div><strong>Assigned work</strong>{member.assignedWork.length ? member.assignedWork.map((work) => <div className="detail-row" key={work.workItemId}><span>{work.workKey}</span><strong>{work.title}</strong></div>) : <p className="muted">No work details loaded.</p>}</div><div><strong>Recent activity</strong>{member.recentActivity.length ? member.recentActivity.slice(0, 5).map((activity) => <div className="detail-row" key={activity.timeEntryId}><span>{activity.entryDate} · {formatMinutes(activity.minutes)}</span><strong>{activity.description || 'Time logged'}</strong></div>) : <p className="muted">No recent activity.</p>}</div></div></div>;
}

function AllocationList({ allocations, users, projects }: { allocations: ResourceAllocationSummary[]; users: TenantUser[]; projects: ProjectSummary[] }) {
  if (!allocations.length) return <div className="empty-state allocation-empty">No active allocations for this planning window.</div>;
  return <div className="allocation-list">{allocations.map((allocation) => <div className="allocation-row" key={allocation.id}><div><strong>{users.find((user) => user.userId === allocation.userId)?.displayName ?? allocation.userId.slice(0, 8)}</strong><span>{projects.find((project) => project.id === allocation.projectId)?.name ?? allocation.projectId.slice(0, 8)} · {allocation.startDate} to {allocation.endDate}</span></div><strong>{formatMinutes(allocation.plannedMinutesPerWeek)}/week</strong></div>)}</div>;
}

function ScopeChangeInbox({ requests, loading, pending, onDecision }: { requests: WorkScopeChangeRequest[]; loading: boolean; pending: boolean; onDecision: (requestId: string, approve: boolean) => void }) {
  return <section className="panel scope-inbox"><div className="panel-head"><div><div className="eyebrow">DECISION QUEUE</div><h3>Scope-change approvals</h3><p className="section-subtitle">Review emergency priority or sprint-scope changes before they affect delivery data.</p></div><span className="status-pill warning-pill">{loading ? 'Loading…' : `${requests.length} pending`}</span></div>{loading ? <div className="empty-state">Loading requests…</div> : !requests.length ? <div className="empty-state">No pending scope-change requests.</div> : <div className="scope-request-list">{requests.map((request) => <div className="scope-request-row" key={request.id}><div><div className="scope-request-title"><strong>{request.requestedPriority ? `${request.requestedPriority} priority` : 'Sprint scope change'}</strong>{request.emergency && <span className="risk-value">Emergency</span>}</div><span>{request.reason}</span><small>Requested {new Date(request.createdAt).toLocaleString()}</small></div><div className="quick-log-actions"><button className="primary-button" disabled={pending} onClick={() => onDecision(request.id, true)}>Approve</button><button className="filter-button" disabled={pending} onClick={() => onDecision(request.id, false)}>Reject</button></div></div>)}</div>}</section>;
}

function TimeApprovalInbox({ entries, loading, pending, onDecision }: { entries: TimeEntrySummary[]; loading: boolean; pending: boolean; onDecision: (entryId: string, approved: boolean) => void }) {
  return <section className="panel scope-inbox"><div className="panel-head"><div><div className="eyebrow">TIME GOVERNANCE</div><h3>Time-entry approvals</h3><p className="section-subtitle">Review employee hours before they feed approved timesheets and management reporting.</p></div><span className="status-pill warning-pill">{loading ? 'Loading…' : `${entries.length} pending`}</span></div>{loading ? <div className="empty-state">Loading time entries…</div> : !entries.length ? <div className="empty-state">No pending time entries.</div> : <div className="scope-request-list">{entries.map((entry) => <div className="scope-request-row" key={entry.id}><div><div className="scope-request-title"><strong>{formatMinutes(entry.minutes)} · {entry.categoryKey ?? 'GENERAL'}</strong><span>{entry.entryDate}</span></div><span>{entry.description || 'No description provided'}</span><small>{entry.source.replace('_', ' ')}</small></div><div className="quick-log-actions"><button className="primary-button" disabled={pending} onClick={() => onDecision(entry.id, true)}>Approve</button><button className="filter-button" disabled={pending} onClick={() => onDecision(entry.id, false)}>Reject</button></div></div>)}</div>}</section>;
}

function TimesheetApprovalInbox({ timesheets, loading, pending, onDecision }: { timesheets: TimesheetSummary[]; loading: boolean; pending: boolean; onDecision: (timesheetId: string, approved: boolean) => void }) {
  return <section className="panel scope-inbox"><div className="panel-head"><div><div className="eyebrow">PERIOD GOVERNANCE</div><h3>Timesheet approvals</h3><p className="section-subtitle">Approve a complete employee period after reviewing its total, pending, and rejected hours.</p></div><span className="status-pill warning-pill">{loading ? 'Loading…' : `${timesheets.length} pending`}</span></div>{loading ? <div className="empty-state">Loading timesheets…</div> : !timesheets.length ? <div className="empty-state">No submitted timesheets.</div> : <div className="scope-request-list">{timesheets.map((timesheet) => <div className="scope-request-row" key={timesheet.id}><div><div className="scope-request-title"><strong>{timesheet.periodStart} to {timesheet.periodEnd}</strong><span>{formatMinutes(timesheet.totalMinutes)}</span></div><span>{timesheet.entryCount} entries · {formatMinutes(timesheet.pendingMinutes)} pending</span><small>{timesheet.cadence} period</small></div><div className="quick-log-actions"><button className="primary-button" disabled={pending} onClick={() => onDecision(timesheet.id, true)}>Approve period</button><button className="filter-button" disabled={pending} onClick={() => onDecision(timesheet.id, false)}>Reject</button></div></div>)}</div>}</section>;
}

function PageHeader({ eyebrow, title, description }: { eyebrow: string; title: string; description: string }) { return <div className="page-header"><div><div className="eyebrow">{eyebrow}</div><h1>{title}</h1><p>{description}</p></div></div>; }
function initials(name: string) { return name.split(' ').map((part) => part[0]).join('').slice(0, 2).toUpperCase(); }
function formatMinutes(minutes: number) { return minutes >= 60 ? `${Math.floor(minutes / 60)}h ${minutes % 60 ? `${minutes % 60}m` : ''}`.trim() : `${minutes}m`; }
