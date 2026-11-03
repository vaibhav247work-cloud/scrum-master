import { FormEvent, useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  WorkHubResponse,
  WorkItemSummary,
  TimeEntrySummary,
  CreateTimeEntryRequest,
  createTimeEntry,
  createWorkItem,
  decideWorkScopeChange,
  getWorkScopeChangeRequests,
  getWorkHub,
  getTimeCategories,
  getCurrentTimesheet,
  submitTimesheet,
  TimesheetSummary,
  requestWorkScopeChange,
  updateWorkItem,
} from './api';
import { ActiveTimer, elapsedMinutes, formatDuration, readTimer, timerKey } from './modules/time-tracking/activeTimer';

const demoWork: WorkItemSummary[] = [
  { id: 'demo-1', workKey: 'W-CHECKOUT', projectId: null, sprintId: null, assigneeUserId: 'demo-user', title: 'Finalize checkout API contract', description: 'Confirm the request and error response shape with the web and mobile teams.', workType: 'TASK', priority: 'HIGH', status: 'IN_PROGRESS', createdAt: new Date().toISOString() },
  { id: 'demo-2', workKey: 'W-PAYMENT', projectId: null, sprintId: null, assigneeUserId: 'demo-user', title: 'Review payment failure states', description: 'Review timeout, declined, and retry states before the next release review.', workType: 'TASK', priority: 'MEDIUM', status: 'TODO', createdAt: new Date().toISOString() },
  { id: 'demo-3', workKey: 'W-ROLLBACK', projectId: null, sprintId: null, assigneeUserId: 'demo-user', title: 'Document release rollback steps', description: 'Capture the safe rollback sequence and ownership for the checkout release.', workType: 'SIDE_TASK', priority: 'HIGH', status: 'BLOCKED', createdAt: new Date().toISOString() },
];

const demoTimeEntries: TimeEntrySummary[] = [
  { id: 'demo-time-1', userId: 'demo-user', workItemId: 'demo-1', projectId: null, sprintId: null, entryDate: new Date().toISOString().slice(0, 10), minutes: 120, description: 'Aligned the checkout request and response contract.', source: 'QUICK_HOURS', createdAt: new Date().toISOString() },
  { id: 'demo-time-2', userId: 'demo-user', workItemId: 'demo-2', projectId: null, sprintId: null, entryDate: new Date().toISOString().slice(0, 10), minutes: 60, description: 'Reviewed payment decline and timeout cases.', source: 'TIMER', createdAt: new Date().toISOString() },
];
const demoTimesheet: TimesheetSummary = {
  id: 'demo-timesheet', userId: 'demo-user', periodStart: new Date().toISOString().slice(0, 10),
  periodEnd: new Date().toISOString().slice(0, 10), cadence: 'DAILY', status: 'OPEN',
  totalMinutes: 180, pendingMinutes: 0, approvedMinutes: 180, rejectedMinutes: 0,
  entryCount: 2, decisionNote: '',
};

export function WorkHubView({ demoMode, userId }: { demoMode: boolean; userId?: string }) {
  const queryClient = useQueryClient();
  const [showLogger, setShowLogger] = useState(false);
  const [showCreate, setShowCreate] = useState(false);
  const [selectedWorkItemId, setSelectedWorkItemId] = useState('');
  const [selectedWorkItem, setSelectedWorkItem] = useState<WorkItemSummary | null>(null);
  const [minutes, setMinutes] = useState(30);
  const [description, setDescription] = useState('');
  const [title, setTitle] = useState('');
  const [workType, setWorkType] = useState('DAILY_TASK');
  const [priority, setPriority] = useState('MEDIUM');
  const [estimateMinutes, setEstimateMinutes] = useState(0);
  const [notice, setNotice] = useState('');
  const [activeTimer, setActiveTimer] = useState<ActiveTimer | null>(() => readTimer(userId));
  const [timerNow, setTimerNow] = useState(() => Date.now());
  const [entrySource, setEntrySource] = useState<CreateTimeEntryRequest['source']>('QUICK_HOURS');
  const [categoryKey, setCategoryKey] = useState('GENERAL');

  const query = useQuery<WorkHubResponse>({ queryKey: ['work-hub'], queryFn: getWorkHub, enabled: !demoMode });
  const categoriesQuery = useQuery({ queryKey: ['tenant-time-categories'], queryFn: getTimeCategories, enabled: !demoMode });
  const timesheetQuery = useQuery({ queryKey: ['current-timesheet'], queryFn: getCurrentTimesheet, enabled: !demoMode });
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['work-hub'] });
  const timeMutation = useMutation({
    mutationFn: createTimeEntry,
    onSuccess: async () => {
      setNotice('Time saved to your central timesheet.');
      setDescription('');
      setSelectedWorkItemId('');
      setCategoryKey('GENERAL');
      setShowLogger(false);
      await refresh();
    },
  });
  const createMutation = useMutation({
    mutationFn: createWorkItem,
    onSuccess: async () => {
      setNotice('Work item created and added to your queue.');
      setTitle('');
      setEstimateMinutes(0);
      setShowCreate(false);
      await refresh();
    },
  });
  const updateMutation = useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) => updateWorkItem(id, { status }),
    onSuccess: async () => {
      setNotice('Work status updated.');
      await refresh();
    },
  });
  const submitTimesheetMutation = useMutation({
    mutationFn: (timesheetId: string) => submitTimesheet(timesheetId),
    onSuccess: async () => { setNotice('Timesheet submitted for manager review.'); await queryClient.invalidateQueries({ queryKey: ['current-timesheet'] }); },
  });

  useEffect(() => {
    if (!activeTimer) {
      localStorage.removeItem(timerKey(userId));
      return;
    }
    localStorage.setItem(timerKey(userId), JSON.stringify(activeTimer));
  }, [activeTimer, userId]);

  useEffect(() => {
    if (!activeTimer) return;
    const interval = window.setInterval(() => setTimerNow(Date.now()), 1000);
    return () => window.clearInterval(interval);
  }, [activeTimer]);

  const workItems = demoMode ? demoWork : query.data?.workItems ?? [];
  const recentEntries = demoMode ? demoTimeEntries : query.data?.recentTimeEntries ?? [];
  const categories = demoMode ? [{ id: 'demo-general', categoryKey: 'GENERAL', name: 'General work', enabled: true }] : (categoriesQuery.data ?? []).filter((category) => category.enabled);
  const totalMinutes = recentEntries.reduce((sum, entry) => sum + entry.minutes, 0);
  const today = new Date().toISOString().slice(0, 10);
  const todayMinutes = recentEntries.filter((entry) => entry.entryDate === today).reduce((sum, entry) => sum + entry.minutes, 0);
  const timesheet = demoMode ? demoTimesheet : timesheetQuery.data;

  function openLogger(workItemId = '', source: CreateTimeEntryRequest['source'] = 'QUICK_HOURS') {
    setSelectedWorkItemId(workItemId);
    setEntrySource(source);
    setShowLogger(true);
    setNotice('');
  }

  function startTimer(workItemId: string) {
    if (activeTimer) {
      setNotice(activeTimer.workItemId === workItemId ? 'This timer is already running.' : 'Stop the active timer before starting another one.');
      return;
    }
    setActiveTimer({ workItemId, startedAt: Date.now() });
    setNotice('Timer started. Keep working; it will survive a page refresh.');
  }

  function stopTimer() {
    if (!activeTimer) return;
    const elapsedMinutesValue = elapsedMinutes(activeTimer.startedAt);
    const workItemId = activeTimer.workItemId;
    setActiveTimer(null);
    setMinutes(elapsedMinutesValue);
    openLogger(workItemId, 'TIMER');
    setNotice('Timer stopped. Add a description and save the recorded time.');
  }

  function submitTime(event: FormEvent) {
    event.preventDefault();
    setNotice('');
    if (demoMode) {
      setNotice('Demo mode: this entry will be sent when the backend session is connected.');
      setShowLogger(false);
      return;
    }
    timeMutation.mutate({
      entryDate: today,
      minutes,
      description: description.trim(),
      source: entrySource,
      categoryKey,
      ...(selectedWorkItemId ? { workItemId: selectedWorkItemId } : {}),
    });
  }

  function submitWork(event: FormEvent) {
    event.preventDefault();
    if (!demoMode) {
      createMutation.mutate({ assigneeUserId: userId, title: title.trim(), description: '', workType, priority, ...(estimateMinutes ? { estimateMinutes } : {}) });
    }
  }

  return <>
    <div className="page-header">
      <div><div className="eyebrow">EMPLOYEE WORK HUB</div><h1>My Work</h1><p>One place for sprint work, daily tasks, side work, and time.</p></div>
      <div className="quick-log-actions"><button className="filter-button" onClick={() => setShowCreate((value) => !value)}>＋ New task</button><button className="primary-button" onClick={() => openLogger()}>＋ Log time</button></div>
    </div>
    {notice && <div className="inline-notice" role="status">{notice}</div>}
    {activeTimer && <div className="panel active-timer-panel"><div><div className="eyebrow">TIMER RUNNING</div><strong>{workItems.find((item) => item.id === activeTimer.workItemId)?.title ?? 'Selected work'}</strong><span>Active for {formatDuration(timerNow - activeTimer.startedAt)}</span></div><button type="button" className="primary-button" onClick={stopTimer}>Stop timer</button></div>}
    {query.isError && <div className="inline-error" role="alert">Could not load live work. Check your session or backend connection.</div>}

    {showCreate && <form className="panel quick-log-panel" onSubmit={submitWork}>
      <div><div className="eyebrow">NEW WORK</div><h3>Add work to your queue</h3><p className="section-subtitle">Create a daily or side task without leaving your work hub.</p></div>
      <label className="quick-log-description">Title <input required maxLength={240} value={title} onChange={(event) => setTitle(event.target.value)} placeholder="Prepare release notes" /></label>
      <label>Type <select value={workType} onChange={(event) => setWorkType(event.target.value)}><option value="DAILY_TASK">Daily task</option><option value="SIDE_TASK">Side task</option><option value="TASK">Task</option><option value="BUG">Bug</option></select></label>
      <label>Priority <select value={priority} onChange={(event) => setPriority(event.target.value)}><option value="LOW">Low</option><option value="MEDIUM">Medium</option><option value="HIGH">High</option><option value="URGENT">Urgent</option></select></label>
      <label>Estimate (minutes, optional) <input type="number" min="0" max="10080" value={estimateMinutes} onChange={(event) => setEstimateMinutes(Number(event.target.value))} /></label>
      {createMutation.isError && <div className="inline-error" role="alert">{(createMutation.error as Error).message}</div>}
      <div className="quick-log-actions"><button type="button" className="filter-button" onClick={() => setShowCreate(false)}>Cancel</button><button className="primary-button" disabled={createMutation.isPending || demoMode}>{createMutation.isPending ? 'Creating…' : demoMode ? 'Demo mode' : 'Create task'}</button></div>
    </form>}

    {showLogger && <form className="panel quick-log-panel" onSubmit={submitTime}>
      <div><div className="eyebrow">QUICK ENTRY</div><h3>What did you work on?</h3><p className="section-subtitle">This writes to the same central time system as timers and task updates.</p></div>
      <label>Work item <select value={selectedWorkItemId} onChange={(event) => setSelectedWorkItemId(event.target.value)}><option value="">Standalone daily or side task</option>{workItems.map((item) => <option key={item.id} value={item.id}>{item.workKey} · {item.title}</option>)}</select></label>{categories.length > 0 && <label>Time category <select value={categoryKey} onChange={(event) => setCategoryKey(event.target.value)}>{categories.map((category) => <option key={category.categoryKey} value={category.categoryKey}>{category.name}</option>)}</select></label>}
      <label>Minutes <input type="number" min="1" max="1440" value={minutes} onChange={(event) => setMinutes(Number(event.target.value))} /></label>
      <label className="quick-log-description">Description <input required value={description} maxLength={4000} onChange={(event) => setDescription(event.target.value)} placeholder="What changed or was completed?" /></label>
      {timeMutation.isError && <div className="inline-error" role="alert">{(timeMutation.error as Error).message}</div>}
      <div className="quick-log-actions"><button type="button" className="filter-button" onClick={() => setShowLogger(false)}>Cancel</button><button className="primary-button" disabled={timeMutation.isPending}>{timeMutation.isPending ? 'Saving…' : 'Save time'}</button></div>
    </form>}

    <div className="metric-grid"><div className="metric-card blue"><span>Assigned work</span><strong>{workItems.length}</strong><small>{workItems.filter((item) => item.status !== 'DONE').length} still open</small></div><div className="metric-card violet"><span>Recent time</span><strong>{formatMinutes(totalMinutes)}</strong><small>{recentEntries.length} entries loaded</small></div><div className="metric-card cyan"><span>Today</span><strong>{formatMinutes(todayMinutes)}</strong><small>quick view from timesheet</small></div><div className="metric-card rose"><span>Focus mode</span><strong>Ready</strong><small>Start from any task</small></div></div>

    {timesheet && <section className="panel timesheet-card"><div className="panel-head"><div><div className="eyebrow">CENTRAL TIMESHEET</div><h3>{timesheet.cadence} period · {timesheet.periodStart} to {timesheet.periodEnd}</h3><p className="section-subtitle">Every quick entry, timer, sprint log, and side task rolls into this period automatically.</p></div><span className={`status-pill ${timesheet.status === 'APPROVED' ? 'active-pill' : timesheet.status === 'REJECTED' ? 'warning-pill' : 'neutral'}`}>{timesheet.status}</span></div><div className="timesheet-summary"><span><strong>{formatMinutes(timesheet.totalMinutes)}</strong>Total</span><span><strong>{timesheet.entryCount}</strong>Entries</span><span><strong>{formatMinutes(timesheet.pendingMinutes)}</strong>Pending</span><span><strong>{formatMinutes(timesheet.approvedMinutes)}</strong>Approved</span></div>{timesheet.decisionNote && <div className="inline-error">Manager note: {timesheet.decisionNote}</div>}{timesheet.status !== 'APPROVED' && timesheet.status !== 'SUBMITTED' && !demoMode && <button className="primary-button" disabled={submitTimesheetMutation.isPending || timesheet.entryCount === 0} onClick={() => submitTimesheetMutation.mutate(timesheet.id)}>{submitTimesheetMutation.isPending ? 'Submitting…' : 'Submit timesheet'}</button>}{demoMode && <button className="primary-button" disabled>Demo mode</button>}{submitTimesheetMutation.isError && <div className="inline-error">{(submitTimesheetMutation.error as Error).message}</div>}</section>}

    <div className="section-row"><div><h2 className="section-title">Your queue</h2><p className="section-subtitle">Open a task, update your progress, or log time without changing screens.</p></div><span className="status-pill neutral">{query.isLoading ? 'Syncing…' : 'Synced just now'}</span></div>
    <div className="panel work-list" aria-live="polite">
      {query.isLoading ? <LoadingState /> : workItems.length === 0 ? <EmptyState text="No assigned work yet." /> : workItems.map((item) => <WorkItemRowWithTimer key={item.id} item={item} activeTimer={activeTimer} timerNow={timerNow} onOpen={() => setSelectedWorkItem(item)} onLog={() => openLogger(item.id)} onStartTimer={() => startTimer(item.id)} onStopTimer={stopTimer} onStatusChange={(status) => { if (!demoMode) updateMutation.mutate({ id: item.id, status }); }} disabled={demoMode || updateMutation.isPending} />)}
    </div>

    {selectedWorkItem && <TaskDetail item={selectedWorkItem} demoMode={demoMode} onClose={() => setSelectedWorkItem(null)} onLog={() => openLogger(selectedWorkItem.id)} />}

    <div className="section-row"><div><h2 className="section-title">Recent hours</h2><p className="section-subtitle">Your latest entries are visible here and remain part of the central timesheet.</p></div><span className="status-pill neutral">{recentEntries.length} entries</span></div>
    <div className="panel recent-time-panel">
      {query.isLoading ? <LoadingState /> : recentEntries.length === 0 ? <EmptyState text="No time entries recorded yet." /> : recentEntries.slice(0, 8).map((entry) => <TimeEntryRow key={entry.id} entry={entry} workItems={workItems} />)}
    </div>
  </>;
}

function WorkItemRow({ item, onOpen, onLog, onStatusChange, disabled }: { item: WorkItemSummary; onOpen: () => void; onLog: () => void; onStatusChange: (status: string) => void; disabled: boolean }) {
  const status = item.status.toLowerCase().replace('_', ' ');
  return <div className="work-item"><div className={`work-status ${status.includes('progress') ? 'progress' : status.includes('block') ? 'blocked' : ''}`} /><div className="work-main"><button className="work-title-button" onClick={onOpen}>{item.title}</button><span>{item.workKey} · {item.workType.replace('_', ' ')}</span></div><span className={`priority ${item.priority.toLowerCase()}`}>{item.priority}</span><select className="filter-button" value={item.status} disabled={disabled} onChange={(event) => onStatusChange(event.target.value)} aria-label={`Update status for ${item.title}`}><option value="TODO">To do</option><option value="IN_PROGRESS">In progress</option><option value="BLOCKED">Blocked</option><option value="DONE">Done</option><option value="CANCELLED">Cancelled</option></select><button type="button" className="update-button" onClick={onLog}>Log time</button></div>;
}

function TaskDetail({ item, demoMode, onClose, onLog }: { item: WorkItemSummary; demoMode: boolean; onClose: () => void; onLog: () => void }) {
  const queryClient = useQueryClient();
  const [showChange, setShowChange] = useState(false);
  const [requestedPriority, setRequestedPriority] = useState(item.priority);
  const [reason, setReason] = useState('');
  const [emergency, setEmergency] = useState(false);
  const requestsQuery = useQuery({ queryKey: ['scope-change-requests', item.id], queryFn: () => getWorkScopeChangeRequests(item.id), enabled: !demoMode });
  const requestMutation = useMutation({ mutationFn: () => requestWorkScopeChange(item.id, { requestedPriority: requestedPriority === item.priority ? undefined : requestedPriority, emergency, reason: reason.trim() }), onSuccess: async () => { await queryClient.invalidateQueries({ queryKey: ['scope-change-requests', item.id] }); setShowChange(false); setReason(''); setEmergency(false); } });
  return <section className="panel task-detail-panel" aria-labelledby="selected-task-title"><div className="panel-head"><div><div className="eyebrow">TASK DETAILS</div><h3 id="selected-task-title">{item.title}</h3></div><button type="button" className="more-button" aria-label="Close task details" onClick={onClose}>×</button></div><p className="task-description">{item.description || 'No task description has been added yet.'}</p><div className="task-detail-meta"><span>{item.workKey}</span><span>{item.workType.replace('_', ' ')}</span><span className={`priority ${item.priority.toLowerCase()}`}>{item.priority} priority</span><span>{item.status.replace('_', ' ')}</span></div><div className="quick-log-actions"><button type="button" className="primary-button" onClick={onLog}>Log time on this task</button><button type="button" className="filter-button" onClick={() => setShowChange((value) => !value)} disabled={demoMode}>{showChange ? 'Close request' : 'Request scope change'}</button></div>{showChange && <form className="scope-change-form" onSubmit={(event) => { event.preventDefault(); requestMutation.mutate(); }}><div className="eyebrow">CONTROLLED CHANGE</div><h4>Request priority change</h4><label>Requested priority <select value={requestedPriority} onChange={(event) => setRequestedPriority(event.target.value)}><option>LOW</option><option>MEDIUM</option><option>HIGH</option><option>URGENT</option></select></label><label>Reason <textarea required maxLength={4000} value={reason} onChange={(event) => setReason(event.target.value)} placeholder="Explain the business or delivery impact" /></label><label className="telegram-toggle"><span>Emergency request</span><input type="checkbox" checked={emergency} onChange={(event) => setEmergency(event.target.checked)} /></label>{requestMutation.isError && <div className="inline-error">{(requestMutation.error as Error).message}</div>}<button className="primary-button" disabled={requestMutation.isPending || requestedPriority === item.priority}>{requestMutation.isPending ? 'Submitting…' : 'Submit for approval'}</button></form>}{!demoMode && <div className="scope-change-history"><strong>Change requests</strong>{requestsQuery.isLoading ? <span>Loading…</span> : (requestsQuery.data ?? []).length ? requestsQuery.data?.map((request) => <div className="scope-change-row" key={request.id}><span className={request.emergency ? 'risk-value' : ''}>{request.emergency ? 'Emergency' : 'Standard'} · {request.status}</span><span>{request.requestedPriority ?? 'Sprint scope'} · {request.reason}</span></div>) : <span>No requests for this task.</span>}</div>}</section>;
}

function WorkItemRowWithTimer({ item, activeTimer, timerNow, onOpen, onLog, onStartTimer, onStopTimer, onStatusChange, disabled }: { item: WorkItemSummary; activeTimer: ActiveTimer | null; timerNow: number; onOpen: () => void; onLog: () => void; onStartTimer: () => void; onStopTimer: () => void; onStatusChange: (status: string) => void; disabled: boolean }) {
  const status = item.status.toLowerCase().replace('_', ' ');
  const isActive = activeTimer?.workItemId === item.id;
  const timerBusy = Boolean(activeTimer) && !isActive;
  return <div className="work-item"><div className={`work-status ${status.includes('progress') ? 'progress' : status.includes('block') ? 'blocked' : ''}`} /><div className="work-main"><button className="work-title-button" onClick={onOpen}>{item.title}</button><span>{item.workKey} · {item.workType.replace('_', ' ')}</span>{isActive && <small>Timer running for {formatDuration(timerNow - activeTimer.startedAt)}</small>}</div><span className={`priority ${item.priority.toLowerCase()}`}>{item.priority}</span><select className="filter-button" value={item.status} disabled={disabled} onChange={(event) => onStatusChange(event.target.value)} aria-label={`Update status for ${item.title}`}><option value="TODO">To do</option><option value="IN_PROGRESS">In progress</option><option value="BLOCKED">Blocked</option><option value="DONE">Done</option><option value="CANCELLED">Cancelled</option></select><button type="button" className="update-button" onClick={onLog}>Log time</button><button type="button" className="update-button" disabled={timerBusy} onClick={isActive ? onStopTimer : onStartTimer}>{isActive ? 'Stop timer' : 'Start timer'}</button></div>;
}

function TimeEntryRow({ entry, workItems }: { entry: TimeEntrySummary; workItems: WorkItemSummary[] }) {
  const workItem = workItems.find((item) => item.id === entry.workItemId);
  return <div className="time-entry-row"><div className="activity-dot" /><div><strong>{workItem?.title ?? 'Standalone work'}</strong><span>{entry.entryDate} · {entry.source.replace('_', ' ')} · {formatMinutes(entry.minutes)}</span></div><p>{entry.description || 'Time logged without a description.'}</p></div>;
}

function LoadingState() { return <div className="empty-state" role="status">Loading your work…</div>; }
function EmptyState({ text }: { text: string }) { return <div className="empty-state">{text}</div>; }
function formatMinutes(minutes: number) { return minutes >= 60 ? `${Math.floor(minutes / 60)}h ${minutes % 60 ? `${minutes % 60}m` : ''}`.trim() : `${minutes}m`; }
