import { useEffect, useState } from 'react';
import { CurrentUser, getCurrentUser } from './api';
import { LoginScreen } from './LoginScreen';
import { ControlTowerView } from './modules/control-tower';
import { AdministrationView } from './modules/administration/AdministrationView';
import { NotificationCenter } from './modules/notification-collaboration/NotificationCenter';
import { HomeView } from './modules/home/HomeView';
import { ProjectsView } from './modules/project-management/ProjectsView';
import { ManagerWorkloadView } from './modules/resource-management';
import { ReleaseRiskView } from './modules/release-risk/ReleaseRiskView';
import { SprintsView } from './modules/sprint-management/SprintsView';
import { SprintWorkspaceView } from './modules/sprint-management/SprintWorkspaceView';
import { WorkHubView } from './modules/work-hub';
import { PlanningView } from './modules/planning/PlanningView';
import { OperationsView } from './modules/quality-operations/OperationsView';
import { ProductManagementView } from './modules/product-management/ProductManagementView';
import { PortfolioManagementView } from './modules/portfolio-management/PortfolioManagementView';
import { MigrationManagementView } from './modules/migration-management/MigrationManagementView';
import { IntegrationManagementView } from './modules/integration-management/IntegrationManagementView';
import { SignupScreen } from './SignupScreen';

export type View = 'home' | 'my-work' | 'my-team' | 'sprints' | 'sprint-workspace' | 'projects' | 'planning' | 'portfolio' | 'migration' | 'integrations' | 'operations' | 'products' | 'control-tower' | 'administration' | 'release-risk';

const navItems: Array<{ id: View; label: string; icon: string }> = [
  { id: 'home', label: 'Home', icon: '⌂' },
  { id: 'my-work', label: 'My Work', icon: '✓' },
  { id: 'my-team', label: 'My Team', icon: '◉' },
  { id: 'sprints', label: 'Sprints', icon: '◷' },
  { id: 'projects', label: 'Projects', icon: '▦' },
  { id: 'planning', label: 'Planning', icon: '✧' },
  { id: 'portfolio', label: 'Portfolios', icon: '◈' },
  { id: 'migration', label: 'Imports', icon: '⇄' },
  { id: 'integrations', label: 'Integrations', icon: '↗' },
  { id: 'operations', label: 'Operations', icon: '!' },
  { id: 'products', label: 'Products', icon: '◇' },
  { id: 'control-tower', label: 'Control tower', icon: '◉' },
  { id: 'release-risk', label: 'Releases & risk', icon: '◇' },
];

const demoUser: CurrentUser = {
  tenantId: 'demo-tenant', userId: 'demo-user', tenantKey: 'Acme Systems',
  email: 'rahul@example.com', displayName: 'Rahul', authorities: ['ROLE_TENANT_ADMIN'],
};

function App() {
  const [view, setView] = useState<View>('home');
  const [workspace, setWorkspace] = useState<{ projectId: string; sprintId: string; projectName: string; sprintName: string } | null>(null);
  const [dark, setDark] = useState(true);
  const demoMode = import.meta.env.VITE_DEMO_MODE === 'true';
  const [currentUser, setCurrentUser] = useState<CurrentUser | null>(demoMode ? demoUser : null);
  const [creatingAccount, setCreatingAccount] = useState(false);
  const [checkingSession, setCheckingSession] = useState(!demoMode);

  useEffect(() => {
    if (demoMode) return;
    getCurrentUser()
      .then(setCurrentUser)
      .catch(() => setCurrentUser(null))
      .finally(() => setCheckingSession(false));
  }, [demoMode]);

  if (checkingSession) return <div className="auth-loading">Loading your secure workspace…</div>;
  if (!demoMode && !currentUser) return creatingAccount
    ? <SignupScreen onAuthenticated={setCurrentUser} onBackToLogin={() => setCreatingAccount(false)} />
    : <LoginScreen onAuthenticated={setCurrentUser} onCreateAccount={() => setCreatingAccount(true)} />;

  return (
    <div className={dark ? 'app dark' : 'app light'}>
      <header className="topbar">
        <div className="brand-mark"><span className="brand-orbit">✦</span><span>company<span className="brand-accent">OS</span></span></div>
        <div className="topbar-search"><span>⌕</span><input placeholder="Search work, projects, people..." aria-label="Global search" /><kbd>/</kbd></div>
        <div className="topbar-actions">
          <button className="icon-button" aria-label="Toggle theme" onClick={() => setDark((value) => !value)}>{dark ? '☼' : '☾'}</button>
          <NotificationCenter demoMode={demoMode} />
          <div className="profile-avatar">{initials(currentUser?.displayName ?? 'Rahul')}</div>
        </div>
      </header>

      <div className="layout">
        <aside className="sidebar">
          <div className="tenant-switcher"><div className="tenant-icon">A</div><div><strong>Acme Systems</strong><span>Engineering workspace</span></div><span className="chevron">⌄</span></div>
          <div className="nav-label">WORKSPACE</div>
          <nav aria-label="Workspace navigation">
            {navItems.map((item) => <button key={item.id} className={`nav-item ${view === item.id ? 'active' : ''}`} onClick={() => setView(item.id)}><span className="nav-icon">{item.icon}</span>{item.label}{item.id === 'my-work' && <span className="nav-count">7</span>}</button>)}
          </nav>
          <div className="nav-label nav-label-spaced">MANAGE</div>
          <nav aria-label="Management navigation">
            <button className="nav-item" onClick={() => setView('my-team')}><span className="nav-icon">♙</span>People</button>
            <button className="nav-item" onClick={() => setView('control-tower')}><span className="nav-icon">◉</span>Control tower</button>
            <button className="nav-item"><span className="nav-icon">◫</span>Time &amp; timesheets</button>
            <button className={`nav-item ${view === 'administration' ? 'active' : ''}`} onClick={() => setView('administration')}><span className="nav-icon">⚙</span>Administration</button>
            <button className={`nav-item ${view === 'products' ? 'active' : ''}`} onClick={() => setView('products')}><span className="nav-icon">◇</span>Products</button>
          </nav>
          <div className="sidebar-bottom"><div className="capacity-mini"><div className="capacity-ring"><span>78%</span></div><div><strong>Your capacity</strong><span>Healthy this week</span></div></div><button className="help-link">? Help &amp; shortcuts</button></div>
        </aside>

        <main className="content">
          <div className="mobile-nav">{navItems.map((item) => <button key={item.id} className={view === item.id ? 'active' : ''} onClick={() => setView(item.id)}><span>{item.icon}</span>{item.label}</button>)}<button className={view === 'administration' ? 'active' : ''} onClick={() => setView('administration')}><span>⚙</span>Admin</button></div>
          {view === 'home' && <HomeView onView={setView} userName={currentUser?.displayName ?? 'Rahul'} />}
          {view === 'my-work' && <WorkHubView demoMode={demoMode} userId={currentUser?.userId} />}
          {view === 'my-team' && <ManagerWorkloadView demoMode={demoMode} />}
          {view === 'sprints' && <SprintsView demoMode={demoMode} onOpenWorkspace={(projectId, sprintId, projectName, sprintName) => { setWorkspace({ projectId, sprintId, projectName, sprintName }); setView('sprint-workspace'); }} />}
          {view === 'sprint-workspace' && workspace && <SprintWorkspaceView demoMode={demoMode} {...workspace} userId={currentUser?.userId} onBack={() => setView('sprints')} />}
          {view === 'projects' && <ProjectsView demoMode={demoMode} />}
          {view === 'planning' && <PlanningView demoMode={demoMode} />}
          {view === 'portfolio' && <PortfolioManagementView demoMode={demoMode} />}
          {view === 'migration' && <MigrationManagementView demoMode={demoMode} />}
          {view === 'integrations' && <IntegrationManagementView demoMode={demoMode} />}
          {view === 'operations' && <OperationsView demoMode={demoMode} />}
          {view === 'products' && <ProductManagementView demoMode={demoMode} />}
          {view === 'control-tower' && <ControlTowerView demoMode={demoMode} />}
          {view === 'administration' && <AdministrationView demoMode={demoMode} />}
          {view === 'release-risk' && <ReleaseRiskView demoMode={demoMode} />}
        </main>
      </div>
    </div>
  );
}

function initials(name: string) {
  return name.split(' ').map((part) => part[0]).join('').slice(0, 2).toUpperCase();
}

export default App;
