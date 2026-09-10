"use client";

import { ApiError } from "@/lib/api";
import { useAuth } from "@/components/AuthProvider";
import { ArrowRight, Boxes, Check, Eye, EyeOff, GitBranch, ShieldCheck, Sparkles } from "lucide-react";
import { useRouter } from "next/navigation";
import { FormEvent, useEffect, useState } from "react";

export default function LoginPage() {
  const { user, loading, authenticate } = useAuth();
  const router = useRouter();
  const [mode, setMode] = useState<"login" | "register">("login");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!loading && user) router.replace("/workflows");
  }, [loading, user, router]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setError("");
    try {
      await authenticate(mode, email, password);
      router.push("/workflows");
    } catch (reason) {
      setError(reason instanceof ApiError ? reason.message : "Could not reach DurableFlow. Is the API running?");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="auth-shell">
      <div className="ambient ambient-one" />
      <div className="ambient ambient-two" />
      <section className="auth-story">
        <div className="brand"><span className="brand-mark"><GitBranch size={20} /></span>DurableFlow</div>
        <div className="story-copy">
          <div className="eyebrow"><Sparkles size={14} /> Distributed systems, made visible</div>
          <h1>Workflows that<br /><em>finish what they start.</em></h1>
          <p>Build, break, retry and compensate multi-step operations. Every transition is durable. Every failure tells a story.</p>
          <div className="promise-grid">
            <div><span><ShieldCheck /></span><b>Durable by default</b><small>PostgreSQL-backed state</small></div>
            <div><span><Boxes /></span><b>Worker safe</b><small>Leases and optimistic locks</small></div>
            <div><span><Check /></span><b>Failure aware</b><small>Retries and compensation</small></div>
          </div>
        </div>
        <div className="story-footer"><i /> System operational <span>Java 21 · Spring Boot · PostgreSQL</span></div>
      </section>

      <section className="auth-panel">
        <div className="auth-card">
          <div className="mobile-brand brand"><span className="brand-mark"><GitBranch size={18} /></span>DurableFlow</div>
          <span className="step-label">{mode === "login" ? "WELCOME BACK" : "START BUILDING"}</span>
          <h2>{mode === "login" ? "Sign in to your workspace" : "Create your workspace"}</h2>
          <p className="muted">{mode === "login" ? "Your workflows have been keeping busy." : "Your first durable workflow is a minute away."}</p>

          <form onSubmit={submit}>
            <label>Email address<input type="email" autoComplete="email" placeholder="you@example.com" value={email} onChange={(e) => setEmail(e.target.value)} required /></label>
            <label>Password
              <div className="password-field">
                <input type={showPassword ? "text" : "password"} autoComplete={mode === "login" ? "current-password" : "new-password"} placeholder="At least 8 characters" minLength={8} value={password} onChange={(e) => setPassword(e.target.value)} required />
                <button type="button" aria-label="Toggle password visibility" onClick={() => setShowPassword((value) => !value)}>{showPassword ? <EyeOff /> : <Eye />}</button>
              </div>
            </label>
            {error && <div className="form-error">{error}</div>}
            <button className="primary-button" disabled={submitting}>{submitting ? "Connecting…" : mode === "login" ? "Enter workspace" : "Create account"}<ArrowRight /></button>
          </form>

          <div className="auth-switch">{mode === "login" ? "New to DurableFlow?" : "Already have an account?"}<button onClick={() => { setMode(mode === "login" ? "register" : "login"); setError(""); }}>{mode === "login" ? "Create an account" : "Sign in"}</button></div>
        </div>
      </section>
    </main>
  );
}
