"use client";

import { useAuth } from "@/components/AuthProvider";
import type { Workflow, WorkflowEvent, WorkflowStatus } from "@/lib/types";
import { Activity, ArrowRight, Ban, Box, Check, ChevronRight, CircleAlert, Clock3, GitBranch, LoaderCircle, LogOut, Plus, RotateCcw, Search, ServerCog, Sparkles, X } from "lucide-react";
import { useRouter } from "next/navigation";
import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";

const scenarios = [
  { name: "happy-path", title: "Happy path", copy: "All steps complete", icon: Check },
  { name: "retry-payment", title: "Retry payment", copy: "Fails once, recovers", icon: RotateCcw },
  { name: "fail-shipping", title: "Compensate", copy: "Refunds and releases", icon: GitBranch },
  { name: "fail-inventory", title: "Terminal failure", copy: "Retries are exhausted", icon: CircleAlert },
];

const statusCopy: Record<WorkflowStatus, string> = {
  RUNNING: "In progress", COMPENSATING: "Compensating", COMPENSATED: "Compensated",
  COMPLETED: "Completed", FAILED: "Failed", CANCELLED: "Cancelled",
};

function pretty(value: string) {
  return value.toLowerCase().split("_").map((word) => word[0].toUpperCase() + word.slice(1)).join(" ");
}

function relativeTime(value: string) {
  const seconds = Math.max(0, Math.floor((Date.now() - new Date(value).getTime()) / 1000));
  if (seconds < 60) return `${seconds}s ago`;
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ago`;
  return `${Math.floor(seconds / 3600)}h ago`;
}

export default function WorkflowsPage() {
  const { user, loading, logout, request } = useAuth();
  const router = useRouter();
  const [workflows, setWorkflows] = useState<Workflow[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [events, setEvents] = useState<WorkflowEvent[]>([]);
  const [creating, setCreating] = useState(false);
  const [newName, setNewName] = useState("happy-path");
  const [search, setSearch] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  const loadWorkflows = useCallback(async () => {
    const data = await request<Workflow[]>("/api/workflows");
    setWorkflows(data);
    setSelectedId((current) => current ?? data[0]?.id ?? null);
  }, [request]);

  const loadEvents = useCallback(async (id: string) => {
    setEvents(await request<WorkflowEvent[]>(`/api/workflows/${id}/events`));
  }, [request]);

  useEffect(() => {
    if (!loading && !user) router.replace("/login");
  }, [loading, user, router]);

  useEffect(() => {
    if (!user) return;
    const initial = window.setTimeout(() => loadWorkflows().catch(() => setError("Could not load workflows.")), 0);
    const timer = window.setInterval(() => loadWorkflows().catch(() => undefined), 2000);
    return () => { window.clearTimeout(initial); window.clearInterval(timer); };
  }, [user, loadWorkflows]);

  useEffect(() => {
    if (!selectedId) return;
    const initial = window.setTimeout(() => loadEvents(selectedId).catch(() => undefined), 0);
    const timer = window.setInterval(() => loadEvents(selectedId).catch(() => undefined), 2000);
    return () => { window.clearTimeout(initial); window.clearInterval(timer); };
  }, [selectedId, loadEvents]);

  const selected = workflows.find((workflow) => workflow.id === selectedId) ?? null;
  const filtered = useMemo(() => workflows.filter((workflow) => workflow.name.toLowerCase().includes(search.toLowerCase())), [workflows, search]);
  const activeCount = workflows.filter((item) => item.status === "RUNNING" || item.status === "COMPENSATING").length;

  async function createWorkflow(event: FormEvent) {
    event.preventDefault();
    if (!newName.trim()) return;
    setBusy(true);
    setError("");
    try {
      const created = await request<Workflow>("/api/workflows", { method: "POST", body: JSON.stringify({ name: newName.trim() }) });
      setCreating(false);
      setSelectedId(created.id);
      await loadWorkflows();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Could not start workflow.");
    } finally { setBusy(false); }
  }

  async function cancelSelected() {
    if (!selected) return;
    setBusy(true);
    try {
      await request(`/api/workflows/${selected.id}/cancel`, { method: "POST" });
      await Promise.all([loadWorkflows(), loadEvents(selected.id)]);
    } finally { setBusy(false); }
  }

  async function signOut() {
    await logout();
    router.replace("/login");
  }

  if (loading || !user) return <main className="loading-screen"><span className="brand-mark"><GitBranch /></span><LoaderCircle className="spin" /></main>;

  return (
    <main className="workspace-shell">
      <aside className="sidebar">
        <div className="brand"><span className="brand-mark"><GitBranch size={20} /></span>DurableFlow</div>
        <nav>
          <span className="nav-label">WORKSPACE</span>
          <button className="nav-item active"><Activity /> Workflows <span>{workflows.length}</span></button>
          <button className="nav-item"><ServerCog /> Workers <i>Live</i></button>
        </nav>
        <div className="sidebar-note"><Sparkles /><div><b>Built for failure</b><p>Watch each retry and compensation become part of the timeline.</p></div></div>
        <div className="profile"><span>{user.email.slice(0, 2).toUpperCase()}</span><div><b>{user.email.split("@")[0]}</b><small>{user.email}</small></div><button aria-label="Sign out" onClick={signOut}><LogOut /></button></div>
      </aside>

      <section className="workspace-main">
        <header className="workspace-header">
          <div><p className="eyebrow">ORCHESTRATION SPACE</p><h1>Your workflows</h1><span>Observe every step from intent to outcome.</span></div>
          <div className="header-actions"><div className="live-stat"><i /> {activeCount} active</div><button className="primary-button compact" onClick={() => setCreating(true)}><Plus /> New workflow</button></div>
        </header>

        <div className="workspace-grid">
          <section className="workflow-list-panel">
            <div className="list-tools"><div className="search-box"><Search /><input aria-label="Search workflows" placeholder="Search workflows" value={search} onChange={(e) => setSearch(e.target.value)} /></div><span>{filtered.length} runs</span></div>
            <div className="workflow-list">
              {filtered.map((workflow) => (
                <button key={workflow.id} onClick={() => setSelectedId(workflow.id)} className={`workflow-row ${selectedId === workflow.id ? "selected" : ""}`}>
                  <span className={`status-icon status-${workflow.status.toLowerCase()}`}>{workflow.status === "COMPLETED" ? <Check /> : workflow.status === "RUNNING" ? <LoaderCircle className="spin" /> : workflow.status === "COMPENSATED" || workflow.status === "COMPENSATING" ? <RotateCcw /> : workflow.status === "CANCELLED" ? <Ban /> : <CircleAlert />}</span>
                  <span className="workflow-identity"><b>{workflow.name}</b><small>{workflow.id.slice(0, 8)} · {relativeTime(workflow.createdAt)}</small></span>
                  <span className={`status-pill status-${workflow.status.toLowerCase()}`}>{statusCopy[workflow.status]}</span>
                  <ChevronRight className="row-chevron" />
                </button>
              ))}
              {!filtered.length && <div className="empty-list"><GitBranch /><b>No workflows yet</b><p>Start one and its durable history will appear here.</p><button onClick={() => setCreating(true)}>Create first workflow <ArrowRight /></button></div>}
            </div>
          </section>

          <section className="workflow-detail-panel">
            {selected ? <>
              <div className="detail-heading"><div><span className="step-label">WORKFLOW DETAIL</span><h2>{selected.name}</h2><code>{selected.id}</code></div>{(selected.status === "RUNNING") && <button className="cancel-button" onClick={cancelSelected} disabled={busy}><X /> Cancel</button>}</div>
              <div className="detail-metrics">
                <div><small>STATUS</small><b className={`metric-status status-${selected.status.toLowerCase()}`}><i />{statusCopy[selected.status]}</b></div>
                <div><small>CURRENT STEP</small><b>{pretty(selected.currentStep)}</b></div>
                <div><small>VERSION</small><b>v{selected.version}</b></div>
              </div>
              {selected.failureReason && <div className="failure-callout"><CircleAlert /><div><b>Failure context</b><p>{selected.failureReason}</p></div></div>}
              <div className="timeline-header"><div><h3>Execution timeline</h3><span>Live event history</span></div><span className="live-label"><i /> LIVE</span></div>
              <div className="timeline">
                {[...events].reverse().map((event, index) => (
                  <div className="timeline-item" key={event.id}>
                    <div className={`timeline-node ${index === 0 ? "latest" : ""}`}>{event.eventType.includes("FAILED") ? <CircleAlert /> : event.eventType.includes("SCHEDULED") || event.eventType.includes("STARTED") ? <Clock3 /> : event.eventType.includes("COMPENS") ? <RotateCcw /> : <Check />}</div>
                    <div><b>{pretty(event.eventType)}</b><p>{event.details}</p><small>{new Date(event.createdAt).toLocaleString()}</small></div>
                  </div>
                ))}
              </div>
            </> : <div className="detail-empty"><Box /><h2>Select a workflow</h2><p>Its state, current step and complete event timeline will appear here.</p></div>}
          </section>
        </div>
      </section>

      {creating && <div className="modal-backdrop" onMouseDown={() => setCreating(false)}><div className="create-modal" onMouseDown={(event) => event.stopPropagation()}>
        <button className="modal-close" onClick={() => setCreating(false)}><X /></button>
        <span className="step-label">NEW EXECUTION</span><h2>Start a durable workflow</h2><p>Choose a scenario to see how the engine behaves under real failure conditions.</p>
        <form onSubmit={createWorkflow}>
          <div className="scenario-grid">{scenarios.map((scenario) => { const Icon = scenario.icon; return <button type="button" key={scenario.name} className={newName === scenario.name ? "chosen" : ""} onClick={() => setNewName(scenario.name)}><Icon /><b>{scenario.title}</b><small>{scenario.copy}</small></button>; })}</div>
          <label>Workflow name<input value={newName} onChange={(event) => setNewName(event.target.value)} maxLength={150} autoFocus /></label>
          {error && <div className="form-error">{error}</div>}
          <button className="primary-button" disabled={busy}>{busy ? "Starting…" : "Start workflow"}<ArrowRight /></button>
        </form>
      </div></div>}
    </main>
  );
}
