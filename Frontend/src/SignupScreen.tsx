import { FormEvent, useState } from 'react';
import { CurrentUser, login, onboardTenant } from './api';

type Props = { onAuthenticated: (user: CurrentUser) => void; onBackToLogin: () => void };
const starterFeatures = ['PROJECTS', 'SCRUM', 'WORK_MANAGEMENT', 'RESOURCE_MANAGEMENT', 'TIME_TRACKING', 'RELEASE_MANAGEMENT', 'REPORTING', 'NOTIFICATIONS'];

export function SignupScreen({ onAuthenticated, onBackToLogin }: Props) {
  const [tenantKey, setTenantKey] = useState('');
  const [companyName, setCompanyName] = useState('');
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError('');
    if (password !== confirmPassword) { setError('Passwords do not match.'); return; }
    setSubmitting(true);
    const normalizedTenantKey = tenantKey.trim().toLowerCase();
    const normalizedEmail = email.trim().toLowerCase();
    try {
      await onboardTenant({ tenantKey: normalizedTenantKey, legalName: companyName.trim(), displayName: companyName.trim(), timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || 'Asia/Kolkata', ownerEmail: normalizedEmail, ownerDisplayName: name.trim(), ownerPassword: password, enabledFeatures: starterFeatures });
      onAuthenticated(await login({ tenantKey: normalizedTenantKey, email: normalizedEmail, password }));
    } catch (submissionError) { setError(submissionError instanceof Error ? submissionError.message : 'Unable to create the company account.'); }
    finally { setSubmitting(false); }
  }

  return <main className="auth-page"><section className="auth-card auth-card-wide">
    <div className="brand-mark auth-brand"><span className="brand-orbit">✦</span><span>company<span className="brand-accent">OS</span></span></div>
    <div className="eyebrow">CREATE YOUR WORKSPACE</div><h1>Start your company account</h1>
    <p className="auth-subtitle">Create your tenant and become its first Company Admin. You can configure modules and invite employees after signing in.</p>
    <form onSubmit={submit} className="auth-form signup-form"><div className="signup-grid">
      <label>Company name<input required maxLength={240} value={companyName} onChange={(event) => setCompanyName(event.target.value)} placeholder="Acme Engineering" /></label>
      <label>Company key<input required pattern="[a-z0-9-]+" maxLength={80} value={tenantKey} onChange={(event) => setTenantKey(event.target.value)} placeholder="acme-engineering" /></label>
      <label>Your name<input required maxLength={200} value={name} onChange={(event) => setName(event.target.value)} placeholder="Rahul Sharma" /></label>
      <label>Work email<input required type="email" maxLength={320} value={email} onChange={(event) => setEmail(event.target.value)} placeholder="rahul@acme.com" /></label>
      <label>Password<input required type="password" minLength={12} maxLength={128} value={password} onChange={(event) => setPassword(event.target.value)} placeholder="At least 12 characters" /></label>
      <label>Confirm password<input required type="password" minLength={12} maxLength={128} value={confirmPassword} onChange={(event) => setConfirmPassword(event.target.value)} placeholder="Repeat your password" /></label>
    </div>{error && <div className="auth-error" role="alert">{error}</div>}
    <button className="primary-button auth-submit" disabled={submitting}>{submitting ? 'Creating workspace…' : 'Create company account'} <span>→</span></button></form>
    <button className="auth-link" type="button" onClick={onBackToLogin}>Already have an account? Sign in</button>
    <small className="auth-note">The account owner is created as a tenant-local Company Admin. Passwords are stored as BCrypt hashes; the browser receives only a tenant-bound session.</small>
  </section></main>;
}
