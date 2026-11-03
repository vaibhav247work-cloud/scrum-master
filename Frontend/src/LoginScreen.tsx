import { FormEvent, useState } from 'react';
import { CurrentUser, login } from './api';

type Props = {
  onAuthenticated: (user: CurrentUser) => void;
  initialError?: string;
  onCreateAccount: () => void;
};

export function LoginScreen({ onAuthenticated, initialError, onCreateAccount }: Props) {
  const [tenantKey, setTenantKey] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(initialError ?? '');
  const [submitting, setSubmitting] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      onAuthenticated(await login({ tenantKey, email, password }));
    } catch (submissionError) {
      setError(submissionError instanceof Error ? submissionError.message : 'Unable to sign in');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="auth-page">
      <section className="auth-card">
        <div className="brand-mark auth-brand"><span className="brand-orbit">✦</span><span>company<span className="brand-accent">OS</span></span></div>
        <div className="eyebrow">SECURE WORKSPACE</div>
        <h1>Welcome back</h1>
        <p className="auth-subtitle">Sign in to your tenant workspace and continue your work.</p>
        <form onSubmit={submit} className="auth-form">
          <label>Company key<input required value={tenantKey} onChange={(event) => setTenantKey(event.target.value)} placeholder="acme-engineering" autoComplete="organization" /></label>
          <label>Email<input required type="email" value={email} onChange={(event) => setEmail(event.target.value)} placeholder="you@company.com" autoComplete="username" /></label>
          <label>Password<input required type="password" value={password} onChange={(event) => setPassword(event.target.value)} placeholder="Your password" autoComplete="current-password" /></label>
          {error && <div className="auth-error" role="alert">{error}</div>}
          <button className="primary-button auth-submit" disabled={submitting}>{submitting ? 'Signing in…' : 'Sign in'} <span>→</span></button>
        </form>
        <button className="auth-link" type="button" onClick={onCreateAccount}>Create a company account</button>
        <small className="auth-note">Your session is tenant-bound and protected by an HttpOnly cookie.</small>
      </section>
    </main>
  );
}
