import { FormEvent, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  DemandSummary, RequirementSummary, WorkflowSummary, createDemand, createRequirement, createWorkflow,
  decideDemand, decideRequirement, getDemands, getRequirements, getWorkflows, updateRequirementCheck,
} from '../../api';
import { PageHeader } from '../../shared/components/PageHeader';

type Tab = 'demands' | 'requirements' | 'workflows';

const demoDemands: DemandSummary[] = [
  { id: 'demo-demand-1', demandKey: 'CHECKOUT_RETRY', title: 'Retry failed checkout payments', description: 'Customers need a safe retry path after transient gateway failures.', demandType: 'CLIENT', priority: 'HIGH', status: 'APPROVED', requestedBy: 'demo-user', ownerUserId: 'demo-user', decidedBy: 'demo-user', decisionNote: 'Ready for requirements.', decidedAt: new Date().toISOString(), createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() },
];
const demoRequirements: RequirementSummary[] = [
  { id: 'demo-requirement-1', requirementKey: 'REQ_CHECKOUT_RETRY', demandId: 'demo-demand-1', title: 'Expose a recoverable payment retry journey', description: 'The retry journey must preserve cart state.', businessContext: 'Reduce abandoned orders after transient payment failures.', uxContext: 'Show a clear retry action and payment status.', technicalContext: 'Use an idempotent payment-attempt boundary.', acceptanceCriteria: 'A retry never creates a duplicate charge.', status: 'READY', reviewNote: 'Approved for delivery.', reviewedAt: new Date().toISOString(), createdAt: new Date().toISOString(), updatedAt: new Date().toISOString(), checks: [{ id: 'demo-check', checkKey: 'ACCEPTANCE', label: 'Acceptance criteria reviewed', required: true, completed: true, evidence: 'Product review complete', updatedAt: new Date().toISOString() }] },
];

export function PlanningView({ demoMode }: { demoMode: boolean }) {
  const [tab, setTab] = useState<Tab>('demands');
  return <>
    <PageHeader eyebrow="DISCOVERY → DELIVERY" title="Planning" description="Move demand into a development-ready requirement, then apply a workflow that matches how the tenant delivers." action="New demand" onAction={() => setTab('demands')} />
    <div className="planning-tabs" role="tablist" aria-label="Planning modules">
      {([['demands', 'Demand intake'], ['requirements', 'Requirements readiness'], ['workflows', 'Workflows']] as const).map(([id, label]) => <button key={id} role="tab" aria-selected={tab === id} className={tab === id ? 'active' : ''} onClick={() => setTab(id)}>{label}</button>)}
    </div>
    {tab === 'demands' && <DemandPanel demoMode={demoMode} />}
    {tab === 'requirements' && <RequirementsPanel demoMode={demoMode} />}
    {tab === 'workflows' && <WorkflowPanel demoMode={demoMode} />}
  </>;
}

function DemandPanel({ demoMode }: { demoMode: boolean }) {
  const client = useQueryClient();
  const query = useQuery({ queryKey: ['demands'], queryFn: () => getDemands(), enabled: !demoMode });
  const demands = demoMode ? demoDemands : query.data ?? [];
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({ demandKey: '', title: '', description: '', demandType: 'INTERNAL', priority: 'MEDIUM' });
  const create = useMutation({ mutationFn: createDemand, onSuccess: async () => { await client.invalidateQueries({ queryKey: ['demands'] }); setOpen(false); setForm({ demandKey: '', title: '', description: '', demandType: 'INTERNAL', priority: 'MEDIUM' }); } });
  const decide = useMutation({ mutationFn: ({ id, status }: { id: string; status: string }) => decideDemand(id, { status }), onSuccess: () => client.invalidateQueries({ queryKey: ['demands'] }) });
  function submit(event: FormEvent) { event.preventDefault(); if (!demoMode) create.mutate({ ...form, demandKey: form.demandKey.toUpperCase() }); }
  return <>
    <div className="planning-toolbar"><div><strong>Demand queue</strong><span>Capture ideas, incidents, client requests, and internal needs in one prioritized queue.</span></div><button className="primary-button" onClick={() => setOpen((value) => !value)}>＋ Capture demand</button></div>
    {open && <form className="panel planning-form" onSubmit={submit}><label>Demand key<input required maxLength={32} value={form.demandKey} onChange={(event) => setForm({ ...form, demandKey: event.target.value })} placeholder="CHECKOUT_RETRY" /></label><label>Title<input required maxLength={240} value={form.title} onChange={(event) => setForm({ ...form, title: event.target.value })} placeholder="What should the company solve?" /></label><label>Type<select value={form.demandType} onChange={(event) => setForm({ ...form, demandType: event.target.value })}><option>INTERNAL</option><option>CLIENT</option><option>IDEA</option><option>INCIDENT</option></select></label><label>Priority<select value={form.priority} onChange={(event) => setForm({ ...form, priority: event.target.value })}><option>LOW</option><option>MEDIUM</option><option>HIGH</option><option>URGENT</option></select></label><label className="planning-wide">Description<textarea maxLength={8000} value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} placeholder="Context, impact, and desired outcome" /></label><div className="quick-log-actions planning-wide"><button type="button" className="filter-button" onClick={() => setOpen(false)}>Cancel</button><button className="primary-button" disabled={demoMode || create.isPending}>{demoMode ? 'Demo mode' : create.isPending ? 'Saving…' : 'Save demand'}</button></div></form>}
    {query.isError && <div className="inline-error">Could not load demand. Check the enabled feature and your permissions.</div>}
    <div className="planning-list">{demands.map((demand) => <div className="panel planning-card" key={demand.id}><div className="planning-card-main"><div className="eyebrow">{demand.demandKey} · {demand.demandType}</div><h3>{demand.title}</h3><p>{demand.description || 'No description provided.'}</p></div><div className="planning-card-meta"><span className={`status-pill status-${demand.status.toLowerCase()}`}>{formatStatus(demand.status)}</span><span>{demand.priority} priority</span>{demand.status === 'SUBMITTED' && <button className="outline-button" disabled={demoMode || decide.isPending} onClick={() => decide.mutate({ id: demand.id, status: 'IN_REVIEW' })}>Start review</button>}{demand.status === 'IN_REVIEW' && <button className="primary-button" disabled={demoMode || decide.isPending} onClick={() => decide.mutate({ id: demand.id, status: 'APPROVED' })}>Approve</button>}</div></div>)}</div>
    {!query.isLoading && demands.length === 0 && <div className="panel empty-state">No demand has entered the queue yet.</div>}
  </>;
}

function RequirementsPanel({ demoMode }: { demoMode: boolean }) {
  const client = useQueryClient();
  const query = useQuery({ queryKey: ['requirements'], queryFn: () => getRequirements(), enabled: !demoMode });
  const requirements = demoMode ? demoRequirements : query.data ?? [];
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({ requirementKey: '', title: '', description: '', businessContext: '', uxContext: '', technicalContext: '', acceptanceCriteria: '' });
  const create = useMutation({ mutationFn: createRequirement, onSuccess: async () => { await client.invalidateQueries({ queryKey: ['requirements'] }); setOpen(false); } });
  const check = useMutation({ mutationFn: ({ id, completed }: { id: string; completed: boolean }) => updateRequirementCheck(id, { checkKey: 'ACCEPTANCE_REVIEW', label: 'Acceptance criteria reviewed', required: true, completed, evidence: completed ? 'Reviewed in planning workspace' : '' }), onSuccess: () => client.invalidateQueries({ queryKey: ['requirements'] }) });
  const decide = useMutation({ mutationFn: ({ id, status }: { id: string; status: string }) => decideRequirement(id, { status }), onSuccess: () => client.invalidateQueries({ queryKey: ['requirements'] }) });
  function submit(event: FormEvent) { event.preventDefault(); if (!demoMode) create.mutate({ ...form, requirementKey: form.requirementKey.toUpperCase() }); }
  return <>
    <div className="planning-toolbar"><div><strong>Requirements readiness</strong><span>Keep business, UX, technical, and acceptance context together before work enters development.</span></div><button className="primary-button" onClick={() => setOpen((value) => !value)}>＋ New requirement</button></div>
    {open && <form className="panel planning-form" onSubmit={submit}><label>Requirement key<input required maxLength={32} value={form.requirementKey} onChange={(event) => setForm({ ...form, requirementKey: event.target.value })} placeholder="REQ_CHECKOUT_RETRY" /></label><label>Title<input required maxLength={240} value={form.title} onChange={(event) => setForm({ ...form, title: event.target.value })} placeholder="Development-ready outcome" /></label>{(['businessContext', 'uxContext', 'technicalContext', 'acceptanceCriteria'] as const).map((field) => <label key={field}>{formatStatus(field)}<textarea required value={form[field]} onChange={(event) => setForm({ ...form, [field]: event.target.value })} /></label>)}<label className="planning-wide">Description<textarea value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} /></label><div className="quick-log-actions planning-wide"><button type="button" className="filter-button" onClick={() => setOpen(false)}>Cancel</button><button className="primary-button" disabled={demoMode || create.isPending}>{demoMode ? 'Demo mode' : 'Create requirement'}</button></div></form>}
    <div className="planning-list">{requirements.map((requirement) => { const required = requirement.checks.filter((checkItem) => checkItem.required); const complete = required.filter((checkItem) => checkItem.completed).length; return <div className="panel planning-card" key={requirement.id}><div className="planning-card-main"><div className="eyebrow">{requirement.requirementKey}</div><h3>{requirement.title}</h3><p>{requirement.description || 'Context is captured in the readiness record.'}</p><div className="readiness-meter"><span style={{ width: `${required.length ? Math.round(complete / required.length * 100) : requirement.status === 'READY' ? 100 : 0}%` }} /></div><small>{complete}/{required.length || 1} required readiness checks complete</small></div><div className="planning-card-meta"><span className={`status-pill status-${requirement.status.toLowerCase()}`}>{formatStatus(requirement.status)}</span>{requirement.status !== 'READY' && <button className="outline-button" disabled={demoMode || check.isPending} onClick={() => check.mutate({ id: requirement.id, completed: true })}>Mark acceptance reviewed</button>}{requirement.status !== 'READY' && <button className="primary-button" disabled={demoMode || decide.isPending} onClick={() => decide.mutate({ id: requirement.id, status: 'READY' })}>Mark ready</button>}</div></div>; })}</div>
    {!query.isLoading && requirements.length === 0 && <div className="panel empty-state">No requirements have been created yet.</div>}
  </>;
}

function WorkflowPanel({ demoMode }: { demoMode: boolean }) {
  const client = useQueryClient();
  const query = useQuery({ queryKey: ['workflows'], queryFn: getWorkflows, enabled: !demoMode });
  const workflows: WorkflowSummary[] = demoMode ? [{ id: 'demo-workflow', workflowKey: 'DEFAULT', name: 'Default delivery workflow', methodology: 'HYBRID', status: 'ACTIVE', statuses: [{ key: 'TODO', label: 'To do', terminal: false, position: 10 }, { key: 'IN_PROGRESS', label: 'In progress', terminal: false, position: 20 }, { key: 'DONE', label: 'Done', terminal: true, position: 30 }], transitions: [{ fromStatus: 'TODO', toStatus: 'IN_PROGRESS' }, { fromStatus: 'IN_PROGRESS', toStatus: 'DONE' }], createdAt: new Date().toISOString() }] : query.data ?? [];
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({ workflowKey: '', name: '', methodology: 'HYBRID' });
  const create = useMutation({ mutationFn: () => createWorkflow({ ...form, workflowKey: form.workflowKey.toUpperCase(), statuses: [{ key: 'TODO', label: 'To do', terminal: false, position: 10 }, { key: 'IN_PROGRESS', label: 'In progress', terminal: false, position: 20 }, { key: 'DONE', label: 'Done', terminal: true, position: 30 }], transitions: [{ fromStatus: 'TODO', toStatus: 'IN_PROGRESS' }, { fromStatus: 'IN_PROGRESS', toStatus: 'DONE' }] }), onSuccess: async () => { await client.invalidateQueries({ queryKey: ['workflows'] }); setOpen(false); } });
  return <>
    <div className="planning-toolbar"><div><strong>Workflow catalog</strong><span>Configure tenant-specific status language and allowed movement without changing work-item code.</span></div><button className="primary-button" onClick={() => setOpen((value) => !value)}>＋ New workflow</button></div>
    {open && <form className="panel planning-form" onSubmit={(event) => { event.preventDefault(); if (!demoMode) create.mutate(); }}><label>Workflow key<input required maxLength={32} value={form.workflowKey} onChange={(event) => setForm({ ...form, workflowKey: event.target.value })} placeholder="SUPPORT_FLOW" /></label><label>Name<input required maxLength={160} value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} placeholder="Support delivery flow" /></label><label>Methodology<select value={form.methodology} onChange={(event) => setForm({ ...form, methodology: event.target.value })}><option>SCRUM</option><option>KANBAN</option><option>HYBRID</option><option>CUSTOM</option></select></label><div className="quick-log-actions planning-wide"><button type="button" className="filter-button" onClick={() => setOpen(false)}>Cancel</button><button className="primary-button" disabled={demoMode || create.isPending}>{demoMode ? 'Demo mode' : 'Create workflow'}</button></div></form>}
    <div className="workflow-grid">{workflows.map((workflow) => <div className="panel workflow-card" key={workflow.id}><div className="eyebrow">{workflow.workflowKey} · {workflow.methodology}</div><h3>{workflow.name}</h3><div className="workflow-statuses">{workflow.statuses.map((status) => <span key={status.key}>{status.label}</span>)}</div><p>{workflow.transitions.length} allowed transitions · {workflow.status === 'ACTIVE' ? 'Active for assignment' : 'Archived'}</p></div>)}</div>
    {!query.isLoading && workflows.length === 0 && <div className="panel empty-state">No workflow is available for this tenant.</div>}
  </>;
}

function formatStatus(status: string) { return status.replaceAll('_', ' ').replace(/\b\w/g, (letter) => letter.toUpperCase()); }
