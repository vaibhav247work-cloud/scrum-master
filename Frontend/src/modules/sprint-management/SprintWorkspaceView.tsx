import { FormEvent, useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { BoardConfiguration, createTimeEntry, createWorkItem, getBoardConfiguration, getTenantFeatures, getTeams, getTimeCategories, getWorkItems, reorderWorkItem, updateBoardConfiguration, WorkItemSummary, updateWorkItem } from '../../api';
import { PageHeader } from '../../shared/components/PageHeader';
import { ActiveTimer, elapsedMinutes, formatDuration, readTimer, timerKey } from '../time-tracking/activeTimer';

const demoItems: WorkItemSummary[] = [
  { id: 'demo-checkout-api', workKey: 'WEB-142', projectId: 'demo-commerce', sprintId: 'demo-sprint', assigneeUserId: 'demo-user', title: 'Complete checkout API contract', description: 'Finalize validation and error responses for payment confirmation.', workType: 'STORY', priority: 'HIGH', status: 'IN_PROGRESS', createdAt: new Date().toISOString() },
  { id: 'demo-tax', workKey: 'WEB-143', projectId: 'demo-commerce', sprintId: 'demo-sprint', assigneeUserId: null, title: 'Resolve tax calculation dependency', description: 'Waiting for the finance service contract.', workType: 'TASK', priority: 'URGENT', status: 'BLOCKED', createdAt: new Date().toISOString() },
];

export function SprintWorkspaceView({ demoMode, projectId, sprintId, projectName, sprintName, userId, onBack }: {
  demoMode: boolean;
  projectId: string;
  sprintId: string;
  projectName: string;
  sprintName: string;
  userId?: string;
  onBack: () => void;
}) {
  const queryClient = useQueryClient();
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [showCreate, setShowCreate] = useState(false);
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [acceptanceCriteria, setAcceptanceCriteria] = useState('');
  const [workType, setWorkType] = useState('TASK');
  const [priority, setPriority] = useState('MEDIUM');
  const [estimateMinutes, setEstimateMinutes] = useState(0);
  const [teamId, setTeamId] = useState('');
  const [showTimeEntry, setShowTimeEntry] = useState(false);
  const [timeWorkItemId, setTimeWorkItemId] = useState('');
  const [minutes, setMinutes] = useState(30);
  const [timeDescription, setTimeDescription] = useState('');
  const [timeSource, setTimeSource] = useState<'QUICK_HOURS' | 'TIMER'>('QUICK_HOURS');
  const [timeCategoryKey, setTimeCategoryKey] = useState('GENERAL');
  const [notice, setNotice] = useState('');
  const [showBoardConfig, setShowBoardConfig] = useState(false);
  const [boardConfig, setBoardConfig] = useState<BoardConfiguration>({ projectId, grouping: 'STATUS', swimlane: 'NONE', cardFields: ['KEY', 'TITLE', 'PRIORITY', 'ASSIGNEE'], wipLimits: {}, updatedAt: null });
  const [activeTimer, setActiveTimer] = useState<ActiveTimer | null>(() => readTimer(userId));
  const [timerNow, setTimerNow] = useState(() => Date.now());
  const query = useQuery({
    queryKey: ['work-items', 'sprint', sprintId],
    queryFn: () => getWorkItems({ sprintId }),
    enabled: !demoMode,
  });
  const featuresQuery = useQuery({ queryKey: ['tenant-features'], queryFn: () => getTenantFeatures(), enabled: !demoMode });
  const categoriesQuery = useQuery({ queryKey: ['tenant-time-categories'], queryFn: getTimeCategories, enabled: !demoMode });
  const boardConfigQuery = useQuery({ queryKey: ['board-config', projectId], queryFn: () => getBoardConfiguration(projectId), enabled: !demoMode });
  const organizationEnabled = !demoMode && Boolean(featuresQuery.data?.some((feature) => feature.featureKey === 'ORGANIZATION_WORKFORCE' && feature.enabled));
  const teamsQuery = useQuery({ queryKey: ['organization-teams'], queryFn: () => getTeams(), enabled: organizationEnabled });
  const items = demoMode ? demoItems : query.data ?? [];
  const timeCategories = demoMode ? [{ id: 'demo-general', categoryKey: 'GENERAL', name: 'General work', enabled: true }] : (categoriesQuery.data ?? []).filter((category) => category.enabled);
  const filteredItems = useMemo(
    () => statusFilter === 'ALL' ? items : items.filter((item) => item.status === statusFilter),
    [items, statusFilter],
  );
  const createMutation = useMutation({
    mutationFn: () => createWorkItem({ projectId, sprintId, teamId: teamId || undefined, title: title.trim(), description: description.trim(), acceptanceCriteria: acceptanceCriteria.trim() || undefined, workType, priority, ...(estimateMinutes ? { estimateMinutes } : {}) }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['work-items', 'sprint', sprintId] });
      setShowCreate(false);
      setTitle('');
      setDescription('');
      setAcceptanceCriteria('');
      setWorkType('TASK');
      setPriority('MEDIUM');
      setEstimateMinutes(0);
      setTeamId('');
    },
  });
  const statusMutation = useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) => updateWorkItem(id, { status }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['work-items', 'sprint', sprintId] }),
  });
  const rankMutation = useMutation({
    mutationFn: ({ id, targetPosition }: { id: string; targetPosition: number }) => reorderWorkItem(id, targetPosition),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['work-items', 'sprint', sprintId] }),
  });
  const timeMutation = useMutation({
    mutationFn: () => createTimeEntry({ workItemId: timeWorkItemId, entryDate: new Date().toISOString().slice(0, 10), minutes, description: timeDescription.trim(), source: timeSource, categoryKey: timeCategoryKey }),
    onSuccess: () => {
      setNotice('Time saved to your central timesheet.');
      setShowTimeEntry(false);
      setTimeWorkItemId('');
      setMinutes(30);
      setTimeDescription('');
      setTimeCategoryKey('GENERAL');
    },
  });
  const boardConfigMutation = useMutation({ mutationFn: () => updateBoardConfiguration(projectId, { grouping: boardConfig.grouping, swimlane: boardConfig.swimlane, cardFields: boardConfig.cardFields, wipLimits: boardConfig.wipLimits }), onSuccess: async (saved) => { setBoardConfig(saved); await queryClient.invalidateQueries({ queryKey: ['board-config', projectId] }); setNotice('Board configuration saved for this project.'); } });

  useEffect(() => {
    if (!activeTimer) {
      localStorage.removeItem(timerKey(userId));
      return;
    }
    localStorage.setItem(timerKey(userId), JSON.stringify(activeTimer));
  }, [activeTimer, userId]);

  useEffect(() => { if (boardConfigQuery.data) setBoardConfig(boardConfigQuery.data); }, [boardConfigQuery.data]);

  useEffect(() => {
    if (!activeTimer) return;
    const interval = window.setInterval(() => setTimerNow(Date.now()), 1000);
    return () => window.clearInterval(interval);
  }, [activeTimer]);

  function submit(event: FormEvent) {
    event.preventDefault();
    if (!demoMode && title.trim()) createMutation.mutate();
  }

  function openTimeEntry(workItemId: string) {
    setTimeWorkItemId(workItemId);
    setTimeSource('QUICK_HOURS');
    setShowTimeEntry(true);
    setNotice('');
  }

  function startTimer(workItemId: string) {
    if (activeTimer) {
      setNotice(activeTimer.workItemId === workItemId ? 'This timer is already running.' : 'Stop the active timer before starting another one.');
      return;
    }
    setActiveTimer({ workItemId, startedAt: Date.now() });
    setNotice('Timer started. It remains available if you move between work screens.');
  }

  function stopTimer() {
    if (!activeTimer) return;
    setActiveTimer(null);
    setTimeWorkItemId(activeTimer.workItemId);
    setTimeSource('TIMER');
    setMinutes(elapsedMinutes(activeTimer.startedAt));
    setShowTimeEntry(true);
    setNotice('Timer stopped. Add a description and save the recorded time.');
  }

  function submitTime(event: FormEvent) {
    event.preventDefault();
    setNotice('');
    if (demoMode) {
      setNotice('Demo mode: this entry will be sent when the backend session is connected.');
      setShowTimeEntry(false);
      return;
    }
    if (timeWorkItemId && timeDescription.trim()) timeMutation.mutate();
  }

  return <>
    <PageHeader eyebrow="SPRINT WORKSPACE" title={sprintName} description={`${projectName} · One connected view for planning, execution, and actual work.`} action="Back to sprints" onAction={onBack} />
    {notice && <div className="inline-notice" role="status">{notice}</div>}
    {activeTimer && <div className="panel active-timer-panel"><div><div className="eyebrow">TIMER RUNNING</div><strong>{items.find((item) => item.id === activeTimer.workItemId)?.title ?? 'Work from another screen'}</strong><span>Active for {formatDuration(timerNow - activeTimer.startedAt)}</span></div><button type="button" className="primary-button" onClick={stopTimer}>Stop timer</button></div>}
    <div className="panel workspace-toolbar"><div><div className="eyebrow">BOARD CONFIGURATION</div><strong>Shape this project board</strong><span className="section-subtitle">Choose grouping, swimlanes, visible card fields, and optional status WIP limits without changing workflow code.</span></div><button type="button" className="filter-button" onClick={() => setShowBoardConfig((value) => !value)}>{showBoardConfig ? 'Close settings' : 'Board settings'}</button></div>
    {showBoardConfig && <div className="panel quick-log-panel workspace-create"><label>Group cards by <select value={boardConfig.grouping} onChange={(event) => setBoardConfig({ ...boardConfig, grouping: event.target.value })}><option value="STATUS">Status</option><option value="ASSIGNEE">Assignee</option><option value="TEAM">Team</option><option value="PRIORITY">Priority</option></select></label><label>Swimlanes <select value={boardConfig.swimlane} onChange={(event) => setBoardConfig({ ...boardConfig, swimlane: event.target.value })}><option value="NONE">None</option><option value="ASSIGNEE">Assignee</option><option value="TEAM">Team</option><option value="PARENT">Parent item</option></select></label><label className="workspace-description">Visible card fields <input value={boardConfig.cardFields.join(', ')} onChange={(event) => setBoardConfig({ ...boardConfig, cardFields: event.target.value.split(',').map((value) => value.trim().toUpperCase()).filter(Boolean) })} placeholder="KEY, TITLE, PRIORITY, ASSIGNEE" /></label><label className="workspace-description">WIP limits (JSON, optional) <input value={JSON.stringify(boardConfig.wipLimits)} onChange={(event) => { try { const value = JSON.parse(event.target.value); if (value && typeof value === 'object' && !Array.isArray(value)) setBoardConfig({ ...boardConfig, wipLimits: value }); } catch { /* keep last valid JSON */ } }} placeholder='{"IN_PROGRESS": 4}' /></label>{boardConfigMutation.isError && <div className="inline-error">{(boardConfigMutation.error as Error).message}</div>}<div className="quick-log-actions"><button type="button" className="primary-button" disabled={demoMode || boardConfigMutation.isPending || boardConfigQuery.isLoading} onClick={() => boardConfigMutation.mutate()}>{boardConfigMutation.isPending ? 'Saving…' : demoMode ? 'Demo mode' : 'Save board configuration'}</button></div></div>}
    <div className="metric-grid workspace-metrics">
      <Metric label="Total work" value={String(items.length)} detail="Sprint scope" tone="blue" />
      <Metric label="In progress" value={String(items.filter((item) => item.status === 'IN_PROGRESS').length)} detail="Active now" tone="cyan" />
      <Metric label="Blocked" value={String(items.filter((item) => item.status === 'BLOCKED').length)} detail="Needs attention" tone="rose" />
      <Metric label="Done" value={String(items.filter((item) => item.status === 'DONE').length)} detail="Completed scope" tone="violet" />
    </div>
    {showCreate && <form className="panel quick-log-panel workspace-create" onSubmit={submit}>
      <div><div className="eyebrow">ADD TO SPRINT</div><h3>Create work without leaving the workspace</h3><p className="section-subtitle">The project and sprint context are applied automatically.</p></div>
      <label>Title <input required maxLength={240} value={title} onChange={(event) => setTitle(event.target.value)} placeholder="Implement payment retry flow" /></label>
      <label>Type <select value={workType} onChange={(event) => setWorkType(event.target.value)}><option>TASK</option><option>BUG</option><option>STORY</option></select></label>
      <label>Priority <select value={priority} onChange={(event) => setPriority(event.target.value)}><option>LOW</option><option>MEDIUM</option><option>HIGH</option><option>URGENT</option></select></label>
      <label>Estimate (minutes, optional) <input type="number" min="0" max="10080" value={estimateMinutes} onChange={(event) => setEstimateMinutes(Number(event.target.value))} /></label>
      {organizationEnabled && <label>Owning team <select value={teamId} onChange={(event) => setTeamId(event.target.value)}><option value="">No team selected</option>{(teamsQuery.data ?? []).map((team) => <option key={team.id} value={team.id}>{team.name}</option>)}</select></label>}
      <label className="workspace-description">Description <input maxLength={4000} value={description} onChange={(event) => setDescription(event.target.value)} placeholder="Context, acceptance criteria, or handoff notes" /></label>
      <label className="workspace-description">Acceptance criteria <textarea maxLength={8000} value={acceptanceCriteria} onChange={(event) => setAcceptanceCriteria(event.target.value)} placeholder="Given… When… Then…" /></label>
      {createMutation.isError && <div className="inline-error">{(createMutation.error as Error).message}</div>}
      <div className="quick-log-actions"><button type="button" className="filter-button" onClick={() => setShowCreate(false)}>Cancel</button><button className="primary-button" disabled={createMutation.isPending || demoMode}>{createMutation.isPending ? 'Creating…' : demoMode ? 'Demo mode' : 'Create work item'}</button></div>
    </form>}
    {showTimeEntry && <form className="panel quick-log-panel workspace-create" onSubmit={submitTime}>
      <div><div className="eyebrow">CENTRAL TIME ENTRY</div><h3>Log time without leaving the sprint</h3><p className="section-subtitle">Project, sprint, and work item context will be saved together.</p></div>
      <label>Work item <select required value={timeWorkItemId} onChange={(event) => setTimeWorkItemId(event.target.value)}><option value="">Select work item</option>{items.map((item) => <option key={item.id} value={item.id}>{item.workKey} / {item.title}</option>)}</select></label>{timeCategories.length > 0 && <label>Time category <select value={timeCategoryKey} onChange={(event) => setTimeCategoryKey(event.target.value)}>{timeCategories.map((category) => <option key={category.categoryKey} value={category.categoryKey}>{category.name}</option>)}</select></label>}
      <label>Minutes <input required type="number" min="1" max="1440" value={minutes} onChange={(event) => setMinutes(Number(event.target.value))} /></label>
      <label className="workspace-description">Description <input required maxLength={4000} value={timeDescription} onChange={(event) => setTimeDescription(event.target.value)} placeholder="What changed or was completed?" /></label>
      {timeMutation.isError && <div className="inline-error">{(timeMutation.error as Error).message}</div>}
      <div className="quick-log-actions"><button type="button" className="filter-button" onClick={() => setShowTimeEntry(false)}>Cancel</button><button className="primary-button" disabled={timeMutation.isPending || demoMode}>{timeMutation.isPending ? 'Saving…' : demoMode ? 'Demo mode' : 'Save time'}</button></div>
    </form>}
    <div className="panel workspace-toolbar"><div className="workload-filters" role="group" aria-label="Filter sprint work"><button className={`filter-button ${statusFilter === 'ALL' ? 'active-filter' : ''}`} onClick={() => setStatusFilter('ALL')}>All <span>{items.length}</span></button>{['TODO', 'IN_PROGRESS', 'BLOCKED', 'DONE'].map((status) => <button key={status} className={`filter-button ${statusFilter === status ? 'active-filter' : ''}`} onClick={() => setStatusFilter(status)}>{formatStatus(status)} <span>{items.filter((item) => item.status === status).length}</span></button>)}</div><button className="primary-button" onClick={() => setShowCreate((value) => !value)}>{showCreate ? 'Close form' : 'Add work item'}</button></div>
    {query.isError && <div className="inline-error">Could not load sprint work. Check your session or backend connection.</div>}
    {query.isLoading && <div className="panel empty-state">Loading sprint work…</div>}
    {!query.isLoading && filteredItems.length === 0 && <div className="panel empty-state">No work items match this filter.</div>}
    {filteredItems.length > 0 && <div className="panel work-list workspace-list">{filteredItems.map((item, index) => <WorkRowWithTimer key={item.id} item={item} demoMode={demoMode} pending={statusMutation.isPending} rankPending={rankMutation.isPending} activeTimer={activeTimer} timerNow={timerNow} canMove={statusFilter === 'ALL'} canMoveUp={index > 0} canMoveDown={index < filteredItems.length - 1} onMoveUp={() => rankMutation.mutate({ id: item.id, targetPosition: index - 1 })} onMoveDown={() => rankMutation.mutate({ id: item.id, targetPosition: index + 1 })} onStatus={(status) => statusMutation.mutate({ id: item.id, status })} onLogTime={() => openTimeEntry(item.id)} onStartTimer={() => startTimer(item.id)} onStopTimer={stopTimer} />)}</div>}
  </>;
}

function WorkRow({ item, demoMode, pending, onStatus }: { item: WorkItemSummary; demoMode: boolean; pending: boolean; onStatus: (status: string) => void }) {
  const nextStatus = item.status === 'TODO' ? 'IN_PROGRESS' : item.status === 'IN_PROGRESS' ? 'DONE' : item.status === 'BLOCKED' ? 'IN_PROGRESS' : null;
  return <div className="work-item"><span className={`work-status ${item.status === 'IN_PROGRESS' ? 'progress' : item.status === 'BLOCKED' ? 'blocked' : ''}`} /><div className="work-main"><strong>{item.title}</strong><span>{item.workKey} <b>·</b> {formatStatus(item.workType)} <b>·</b> {item.assigneeUserId ? 'Assigned' : 'Unassigned'}</span><small>{item.description || 'No description yet.'}</small></div><span className={`priority ${item.priority.toLowerCase()}`}>{item.priority}</span><span className={`status-pill ${item.status === 'BLOCKED' ? 'warning-pill' : item.status === 'DONE' ? 'active-pill' : 'neutral'}`}>{formatStatus(item.status)}</span>{nextStatus && <button className="update-button" disabled={pending || demoMode} onClick={() => onStatus(nextStatus)}>{demoMode ? 'Demo' : nextStatus === 'DONE' ? 'Complete' : 'Start work'}</button>}</div>;
}

function WorkRowWithTime({ item, demoMode, pending, onStatus, onLogTime }: { item: WorkItemSummary; demoMode: boolean; pending: boolean; onStatus: (status: string) => void; onLogTime: () => void }) {
  const nextStatus = item.status === 'TODO' ? 'IN_PROGRESS' : item.status === 'IN_PROGRESS' ? 'DONE' : item.status === 'BLOCKED' ? 'IN_PROGRESS' : null;
  return <div className="work-item"><span className={`work-status ${item.status === 'IN_PROGRESS' ? 'progress' : item.status === 'BLOCKED' ? 'blocked' : ''}`} /><div className="work-main"><strong>{item.title}</strong><span>{item.workKey} / {formatStatus(item.workType)} / {item.assigneeUserId ? 'Assigned' : 'Unassigned'}</span><small>{item.description || 'No description yet.'}</small></div><span className={`priority ${item.priority.toLowerCase()}`}>{item.priority}</span><span className={`status-pill ${item.status === 'BLOCKED' ? 'warning-pill' : item.status === 'DONE' ? 'active-pill' : 'neutral'}`}>{formatStatus(item.status)}</span><button type="button" className="update-button" onClick={onLogTime}>Log time</button>{nextStatus && <button type="button" className="update-button" disabled={pending || demoMode} onClick={() => onStatus(nextStatus)}>{demoMode ? 'Demo' : nextStatus === 'DONE' ? 'Complete' : 'Start work'}</button>}</div>;
}

function WorkRowWithTimer({ item, demoMode, pending, rankPending, activeTimer, timerNow, canMove, canMoveUp, canMoveDown, onMoveUp, onMoveDown, onStatus, onLogTime, onStartTimer, onStopTimer }: { item: WorkItemSummary; demoMode: boolean; pending: boolean; rankPending: boolean; activeTimer: ActiveTimer | null; timerNow: number; canMove: boolean; canMoveUp: boolean; canMoveDown: boolean; onMoveUp: () => void; onMoveDown: () => void; onStatus: (status: string) => void; onLogTime: () => void; onStartTimer: () => void; onStopTimer: () => void }) {
  const nextStatus = item.status === 'TODO' ? 'IN_PROGRESS' : item.status === 'IN_PROGRESS' ? 'DONE' : item.status === 'BLOCKED' ? 'IN_PROGRESS' : null;
  const isActive = activeTimer?.workItemId === item.id;
  const timerBusy = Boolean(activeTimer) && !isActive;
  return <div className="work-item"><span className={`work-status ${item.status === 'IN_PROGRESS' ? 'progress' : item.status === 'BLOCKED' ? 'blocked' : ''}`} /><div className="work-main"><strong>{item.title}</strong><span>{item.workKey} / {formatStatus(item.workType)} / {item.assigneeUserId ? 'Assigned' : 'Unassigned'}{item.parentWorkItemId ? ' / Child item' : ''}</span><small>{isActive ? `Timer running for ${formatDuration(timerNow - activeTimer.startedAt)}` : item.description || 'No description yet.'}</small>{item.acceptanceCriteria && <small>Acceptance: {item.acceptanceCriteria}</small>}</div><span className={`priority ${item.priority.toLowerCase()}`}>{item.priority}</span><span className={`status-pill ${item.status === 'BLOCKED' ? 'warning-pill' : item.status === 'DONE' ? 'active-pill' : 'neutral'}`}>{formatStatus(item.status)}</span>{canMove && <span className="work-rank-actions" aria-label="Reorder backlog item"><button type="button" className="update-button" aria-label={`Move ${item.workKey} up`} disabled={demoMode || rankPending || !canMoveUp} onClick={onMoveUp}>↑</button><button type="button" className="update-button" aria-label={`Move ${item.workKey} down`} disabled={demoMode || rankPending || !canMoveDown} onClick={onMoveDown}>↓</button></span>}<button type="button" className="update-button" onClick={onLogTime}>Log time</button><button type="button" className="update-button" disabled={timerBusy} onClick={isActive ? onStopTimer : onStartTimer}>{isActive ? 'Stop timer' : 'Start timer'}</button>{nextStatus && <button type="button" className="update-button" disabled={pending || demoMode} onClick={() => onStatus(nextStatus)}>{demoMode ? 'Demo' : nextStatus === 'DONE' ? 'Complete' : 'Start work'}</button>}</div>;
}

function Metric({ label, value, detail, tone }: { label: string; value: string; detail: string; tone: string }) { return <div className={`metric-card ${tone}`}><span>{label}</span><strong>{value}</strong><small>{detail}</small></div>; }
function formatStatus(status: string) { return status.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, (letter) => letter.toUpperCase()); }
