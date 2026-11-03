import { FormEvent, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ProjectSummary, createProject, getProducts, getProjects, ProductSummary } from '../../api';
import { PageHeader } from '../../shared/components/PageHeader';

const demoProjects: ProjectSummary[] = [
  { id: 'demo-commerce', projectKey: 'COMMERCE', name: 'Commerce Platform', description: 'Checkout and order experience', status: 'ACTIVE', createdAt: new Date().toISOString(), productId: 'demo-delivery' },
  { id: 'demo-internal', projectKey: 'INTERNAL', name: 'Internal Platform', description: 'Developer productivity foundation', status: 'PLANNING', createdAt: new Date().toISOString(), productId: null },
];

export function ProjectsView({ demoMode }: { demoMode: boolean }) {
  const queryClient = useQueryClient();
  const query = useQuery({ queryKey: ['projects'], queryFn: getProjects, enabled: !demoMode });
  const productsQuery = useQuery({ queryKey: ['products'], queryFn: getProducts, enabled: !demoMode });
  const projects = demoMode ? demoProjects : query.data ?? [];
  const products: ProductSummary[] = demoMode ? [{ id: 'demo-delivery', productKey: 'DELIVERY_OS', name: 'Delivery OS', description: '', status: 'ACTIVE', featureKeys: [], memberCount: 1, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() }] : productsQuery.data ?? [];
  const [showCreate, setShowCreate] = useState(false);
  const [projectKey, setProjectKey] = useState('');
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [productId, setProductId] = useState('');
  const mutation = useMutation({ mutationFn: createProject, onSuccess: async () => { await queryClient.invalidateQueries({ queryKey: ['projects'] }); setShowCreate(false); setProjectKey(''); setName(''); setDescription(''); setProductId(''); } });

  function submit(event: FormEvent) {
    event.preventDefault();
    if (!demoMode) mutation.mutate({ projectKey: projectKey.trim(), name: name.trim(), description: description.trim(), productId: productId || undefined });
  }

  return <>
    <PageHeader eyebrow="DELIVERY PORTFOLIO" title="Projects" description="A focused view of projects within your authorized workspace." action="New project" onAction={() => setShowCreate((value) => !value)} />
    {showCreate && <form className="panel quick-log-panel" onSubmit={submit}><div><div className="eyebrow">NEW PROJECT</div><h3>Create a delivery project</h3><p className="section-subtitle">The project will be created inside your current tenant. Product scope is optional for legacy or cross-product work.</p></div><label>Project key <input required maxLength={20} value={projectKey} onChange={(event) => setProjectKey(event.target.value.toUpperCase())} placeholder="COMMERCE" /></label><label>Name <input required maxLength={240} value={name} onChange={(event) => setName(event.target.value)} placeholder="Commerce Platform" /></label><label>Product scope <select value={productId} onChange={(event) => setProductId(event.target.value)}><option value="">No product scope</option>{products.map((product) => <option key={product.id} value={product.id}>{product.name}</option>)}</select></label><label className="quick-log-description">Description <input maxLength={4000} value={description} onChange={(event) => setDescription(event.target.value)} placeholder="What this project delivers" /></label>{mutation.isError && <div className="inline-error">{(mutation.error as Error).message}</div>}<div className="quick-log-actions"><button type="button" className="filter-button" onClick={() => setShowCreate(false)}>Cancel</button><button className="primary-button" disabled={mutation.isPending || demoMode}>{mutation.isPending ? 'Creating…' : demoMode ? 'Demo mode' : 'Create project'}</button></div></form>}
    {query.isError && <div className="inline-error">Could not load projects. Check your session or backend connection.</div>}
    {query.isLoading && <div className="panel empty-state">Loading authorized projects…</div>}
    {!query.isLoading && projects.length === 0 && <div className="panel empty-state">No projects are available in this workspace.</div>}
    <div className="project-grid">{projects.map((project) => <ProjectCard key={project.id} project={project} products={products} />)}</div>
  </>;
}

function ProjectCard({ project, products }: { project: ProjectSummary; products: ProductSummary[] }) {
  const progress = project.status === 'COMPLETED' ? 100 : project.status === 'ACTIVE' ? 65 : 25;
  const product = products.find((item) => item.id === project.productId);
  return <div className="panel project-card"><div className="project-icon">✦</div><div className="eyebrow">{project.projectKey}</div><h2>{project.name}</h2><p>{project.description || 'No project description provided.'}</p>{product && <span className="status-pill neutral">{product.name}</span>}<div className="load-row"><span>Project status</span><strong>{formatStatus(project.status)}</strong></div><div className="progress-track"><div className="progress-value" style={{ width: `${progress}%` }} /></div><div className="project-footer"><span className={project.status === 'ACTIVE' ? 'healthy-text' : 'muted'}>Created {formatDate(project.createdAt)}</span><button className="text-button">Open →</button></div></div>;
}

function formatStatus(status: string) { return status.replaceAll('_', ' ').toLowerCase().replace(/\b\w/g, (letter) => letter.toUpperCase()); }
function formatDate(value: string) { return new Date(value).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' }); }
