import { Activity } from '../../shared/components/Activity';
import { Metric } from '../../shared/components/Metric';
import { PageHeader } from '../../shared/components/PageHeader';

type View = 'home' | 'my-work' | 'my-team' | 'sprints' | 'projects' | 'control-tower';

const tasks = [
  { title: 'Finalize checkout API contract', project: 'Commerce Platform', sprint: 'Checkout Sprint 12', status: 'In progress', priority: 'High', hours: '6 / 12h' },
  { title: 'Review payment failure states', project: 'Commerce Platform', sprint: 'Checkout Sprint 12', status: 'To do', priority: 'Medium', hours: '0 / 5h' },
  { title: 'Document release rollback steps', project: 'Internal Platform', sprint: 'Release Readiness', status: 'Blocked', priority: 'High', hours: '2 / 4h' },
];

export function HomeView({ onView, userName }: { onView: (view: View) => void; userName: string }) {
  return <>
    <PageHeader eyebrow="MONDAY · 13 OCTOBER 2026" title={`Good morning, ${userName}`} description="Here is what needs your attention today." />
    <section className="hero-card"><div className="hero-glow" /><div className="hero-copy"><span className="hero-kicker">CONTINUE WHERE YOU LEFT OFF</span><h2>Finalize checkout API contract</h2><p>Checkout Sprint 12 <span>·</span> Commerce Platform</p><button className="hero-button" onClick={() => onView('my-work')}>Open task <span>→</span></button></div><div className="hero-progress"><div className="progress-ring"><strong>50</strong><span>%</span></div><span>6h of 12h logged</span></div></section>
    <div className="metric-grid"><Metric label="My work" value="7" meta="3 due this week" tone="blue" /><Metric label="Active sprint" value="68%" meta="Checkout Sprint 12" tone="violet" /><Metric label="Open blockers" value="2" meta="1 needs your update" tone="rose" /><Metric label="Time today" value="4.5h" meta="of 8h expected" tone="cyan" /></div>
    <div className="section-row"><div><h2 className="section-title">Today at a glance</h2><p className="section-subtitle">Your priorities and recent signals</p></div><button className="text-button" onClick={() => onView('my-work')}>View My Work →</button></div>
    <div className="home-grid"><div className="panel task-preview"><div className="panel-head"><h3>Priority work</h3><span className="status-pill neutral">3 items</span></div>{tasks.map((task) => <TaskRow key={task.title} task={task} />)}</div><div className="panel activity-panel"><div className="panel-head"><h3>Recent activity</h3><button className="text-button">View all</button></div><Activity title="You logged 2h on checkout API" meta="Today, 10:42 AM" tone="blue" /><Activity title="Sprint scope changed" meta="Yesterday, 4:15 PM" tone="violet" /><Activity title="You were mentioned in a blocker" meta="Yesterday, 2:30 PM" tone="rose" /></div></div>
  </>;
}

function TaskRow({ task }: { task: typeof tasks[number] }) {
  return <div className="task-row"><div className={`work-status ${task.status === 'In progress' ? 'progress' : task.status === 'Blocked' ? 'blocked' : ''}`} /><div className="task-row-main"><strong>{task.title}</strong><span>{task.project} <b>·</b> {task.hours}</span></div><span className={`priority ${task.priority.toLowerCase()}`}>{task.priority}</span></div>;
}
