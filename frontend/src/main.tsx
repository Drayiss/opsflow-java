import React, { useEffect, useState, useCallback } from "react";
import { createRoot } from "react-dom/client";
import {
  Activity as Pulse,
  ArrowUpRight,
  Bell,
  Check,
  ChevronLeft,
  ChevronRight,
  CircleDot,
  LayoutDashboard,
  LogOut,
  Plus,
  RefreshCw,
  Settings,
  Shield,
  X,
} from "lucide-react";
import { User } from "oidc-client-ts";
import { auth } from "./auth";
import {
  api,
  Org,
  Incident,
  Member,
  Page,
  Summary,
  Notice,
  Activity,
  Delivery,
  Status,
  Severity,
  Role,
} from "./api";
import "./style.css";

const label = (s: string) => s.replaceAll("_", " ").toLowerCase();
const date = (s: string) =>
  new Date(s).toLocaleString(undefined, {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  });

function App() {
  const [user, setUser] = useState<User | null>(null),
    [ready, setReady] = useState(false),
    [error, setError] = useState("");
  const [orgs, setOrgs] = useState<Org[]>([]),
    [tenant, setTenant] = useState("");
  const loadOrgs = useCallback(async () => {
    const rows = await api<Org[]>("/organizations");
    setOrgs(rows);
    setTenant((current) =>
      rows.some((o) => o.id === current) ? current : rows[0]?.id || "",
    );
  }, []);
  useEffect(() => {
    const updated = (next: User) => setUser(next);
    const removed = () => setUser(null);
    auth.events.addUserLoaded(updated);
    auth.events.addUserUnloaded(removed);
    (async () => {
      try {
        const current =
          location.pathname === "/callback"
            ? await auth.signinRedirectCallback()
            : await auth.getUser();
        if (location.pathname === "/callback")
          history.replaceState({}, "", "/");
        if (current && !current.expired) {
          setUser(current);
          await loadOrgs();
        }
      } catch (e) {
        setError((e as Error).message);
      } finally {
        setReady(true);
      }
    })();
    return () => {
      auth.events.removeUserLoaded(updated);
      auth.events.removeUserUnloaded(removed);
    };
  }, [loadOrgs]);
  async function createOrg(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const name = String(new FormData(form).get("name"));
    try {
      const org = await api<Org>("/organizations", "POST", { name });
      await loadOrgs();
      setTenant(org.id);
      form.reset();
      setError("");
    } catch (e) {
      setError((e as Error).message);
    }
  }
  if (!ready) return <div className="loading">Opening your workspace…</div>;
  if (!user)
    return (
      <main className="welcome">
        <div className="brand">
          <Pulse size={28} /> OpsFlow
        </div>
        <div className="eyebrow">CALM IN THE CRITICAL MOMENTS</div>
        <h1>
          One place to
          <br />
          get things back on track.
        </h1>
        <p>
          Track incidents, coordinate your team, and keep every organization in
          its own workspace.
        </p>
        <button
          className="primary"
          onClick={() =>
            void auth.signinRedirect().catch((e) => setError(e.message))
          }
        >
          Sign in to your workspace <ArrowUpRight size={18} />
        </button>
        {error && (
          <p role="alert" className="error">
            {error}
          </p>
        )}
        {auth.settings.authority === "http://localhost:8180/realms/opsflow" && (
          <small>Local demo: alice, bob, or eve · password: demo-password</small>
        )}
      </main>
    );
  const org = orgs.find((o) => o.id === tenant);
  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">
          <Pulse /> OpsFlow
          <span className="brand-dot" />
        </div>
        <div className="eyebrow muted">WORKSPACE</div>
        <label className="sr-only" htmlFor="workspace">
          Organization
        </label>
        <select
          id="workspace"
          value={tenant}
          onChange={(e) => setTenant(e.target.value)}
        >
          {orgs.map((o) => (
            <option key={o.id} value={o.id}>
              {o.name}
            </option>
          ))}
          {!org && <option value="">Choose a workspace</option>}
        </select>
        <div className="workspace-note">
          <Shield size={14} />{" "}
          {org ? `${label(org.role)} access` : "Your organizations"}
        </div>
        <div className="side-bottom">
          <div className="avatar">
            {String(
              user.profile.name || user.profile.preferred_username || "U",
            ).slice(0, 1)}
          </div>
          <div>
            <strong>
              {String(
                user.profile.name || user.profile.preferred_username || "User",
              )}
            </strong>
            <small>Signed in securely</small>
          </div>
          <button
            className="icon"
            aria-label="Sign out"
            onClick={() => void auth.signoutRedirect()}
          >
            <LogOut size={17} />
          </button>
        </div>
      </aside>
      <div className="content">
        {error && (
          <div role="alert" className="error">
            {error}
            <button
              className="icon"
              onClick={() => setError("")}
              aria-label="Dismiss error"
            >
              <X size={16} />
            </button>
          </div>
        )}
        {org ? (
          <Workspace key={tenant} org={org} reloadOrgs={loadOrgs} />
        ) : (
          <div className="empty large">
            <CircleDot size={36} />
            <h1>Your first workspace</h1>
            <p>Create an organization to start tracking incidents.</p>
          </div>
        )}
        <details className="create-workspace">
          <summary>Create another organization</summary>
          <form onSubmit={createOrg}>
            <label>
              Organization name
              <input
                name="name"
                required
                maxLength={120}
                placeholder="e.g. Acme Operations"
              />
            </label>
            <button className="primary">Create workspace</button>
          </form>
        </details>
      </div>
    </div>
  );
}

function Workspace({
  org,
  reloadOrgs,
}: {
  org: Org;
  reloadOrgs: () => Promise<void>;
}) {
  const root = `/organizations/${org.id}`;
  const [tab, setTab] = useState("incidents"),
    [filter, setFilter] = useState(""),
    [page, setPage] = useState(0);
  const [data, setData] = useState<Page>({
      items: [],
      page: 0,
      size: 20,
      hasMore: false,
    }),
    [summary, setSummary] = useState<Summary | null>(null);
  const [members, setMembers] = useState<Member[]>([]),
    [notices, setNotices] = useState<Notice[]>([]),
    [deliveries, setDeliveries] = useState<Delivery[]>([]);
  const [selected, setSelected] = useState<Incident | null>(null),
    [activity, setActivity] = useState<Activity[]>([]),
    [creating, setCreating] = useState(false);
  const [error, setError] = useState(""),
    [busy, setBusy] = useState(false),
    [loading, setLoading] = useState(true),
    [revision, setRevision] = useState(0);
  const canWrite = org.role !== "VIEWER";
  const refresh = () => setRevision((r) => r + 1);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    Promise.all([
      api<Page>(
        `${root}/incidents?page=${page}${filter ? `&status=${filter}` : ""}`,
        "GET",
        undefined,
        controller.signal,
      ),
      api<Summary>(`${root}/summary`, "GET", undefined, controller.signal),
      api<Member[]>(`${root}/members`, "GET", undefined, controller.signal),
      api<Notice[]>(
        `${root}/notifications`,
        "GET",
        undefined,
        controller.signal,
      ),
      org.role === "ADMIN"
        ? api<Delivery[]>(
            `${root}/deliveries`,
            "GET",
            undefined,
            controller.signal,
          )
        : Promise.resolve([]),
    ])
      .then(([rows, stats, team, inbox, jobs]) => {
        if (!controller.signal.aborted) {
          setData(rows);
          setSummary(stats);
          setMembers(team);
          setNotices(inbox);
          setDeliveries(jobs);
        }
      })
      .catch((e) => {
        if (!controller.signal.aborted) setError(e.message);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [root, org.role, page, filter, revision]);
  useEffect(() => {
    const timer = setInterval(() => setRevision((r) => r + 1), 15000);
    return () => clearInterval(timer);
  }, []);
  useEffect(() => {
    if (!selected) {
      setActivity([]);
      return;
    }
    const controller = new AbortController();
    api<Activity[]>(
      `${root}/incidents/${selected.id}/activity`,
      "GET",
      undefined,
      controller.signal,
    )
      .then(setActivity)
      .catch((e) => {
        if (!controller.signal.aborted) setError(e.message);
      });
    return () => controller.abort();
  }, [selected, root, revision]);
  async function run(action: () => Promise<void>) {
    setBusy(true);
    setError("");
    try {
      await action();
      refresh();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  async function openIncident(id: string) {
    await run(async () => {
      setSelected(await api<Incident>(`${root}/incidents/${id}`));
      setTab("incidents");
    });
  }
  const name = (subject: string | null) =>
    members.find((m) => m.subject === subject)?.displayName ||
    subject ||
    "Unassigned";
  return (
    <>
      <header className="topbar">
        <div>
          <span className="breadcrumb">
            WORKSPACE / {org.name.toUpperCase()}
          </span>
          <h1>
            {tab === "incidents"
              ? "Incident overview"
              : tab === "notifications"
                ? "Your inbox"
                : tab === "team"
                  ? "Workspace settings"
                  : "Event deliveries"}
          </h1>
          <p>
            {tab === "incidents"
              ? "A clear picture of what needs your attention."
              : "Keep your team and your response connected."}
          </p>
        </div>
        <div className="header-actions">
          <button
            className="secondary"
            onClick={refresh}
            aria-label="Refresh workspace"
          >
            <RefreshCw size={16} />
          </button>
          {canWrite && (
            <button className="primary" onClick={() => setCreating(true)}>
              <Plus size={16} /> New incident
            </button>
          )}
        </div>
      </header>
      <nav className="tabs" aria-label="Workspace sections">
        <button
          className={tab === "incidents" ? "active" : ""}
          onClick={() => setTab("incidents")}
        >
          <LayoutDashboard size={17} />
          Overview
        </button>
        <button
          className={tab === "notifications" ? "active" : ""}
          onClick={() => setTab("notifications")}
        >
          <Bell size={17} />
          Inbox <span>{notices.filter((n) => !n.readAt).length}</span>
        </button>
        <button
          className={tab === "team" ? "active" : ""}
          onClick={() => setTab("team")}
        >
          <Settings size={17} />
          Team & settings
        </button>
        {org.role === "ADMIN" && (
          <button
            className={tab === "deliveries" ? "active" : ""}
            onClick={() => setTab("deliveries")}
          >
            Deliveries
          </button>
        )}
      </nav>
      {error && (
        <div className="error" role="alert">
          {error}
          <button
            className="icon"
            aria-label="Dismiss error"
            onClick={() => setError("")}
          >
            <X size={16} />
          </button>
        </div>
      )}
      {tab === "incidents" && (
        <>
          <section className="metrics" aria-label="Incident statistics">
            {[
              [
                "Active incidents",
                (summary?.open || 0) + (summary?.acknowledged || 0),
                "Across your workspace",
              ],
              ["Critical", summary?.critical || 0, "SEV1 · needs attention"],
              [
                "Acknowledged",
                summary?.acknowledged || 0,
                "Your team is on it",
              ],
              ["Resolved", summary?.resolved || 0, "Back to normal"],
            ].map(([title, value, hint], i) => (
              <div className={`metric metric-${i}`} key={String(title)}>
                <div>
                  {title}
                  <CircleDot size={15} />
                </div>
                <strong>{summary ? value : "—"}</strong>
                <small>{hint}</small>
              </div>
            ))}
          </section>
          <section className="panel">
            <div className="panel-title">
              <h2>
                Incident queue <span>{summary?.total || 0}</span>
              </h2>
              <label className="filter">
                Status
                <select
                  aria-label="Filter by status"
                  value={filter}
                  onChange={(e) => {
                    setFilter(e.target.value);
                    setPage(0);
                  }}
                >
                  <option value="">All statuses</option>
                  <option value="OPEN">Open</option>
                  <option value="ACKNOWLEDGED">Acknowledged</option>
                  <option value="RESOLVED">Resolved</option>
                </select>
              </label>
            </div>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Incident</th>
                    <th>Severity</th>
                    <th>Status</th>
                    <th>Assigned to</th>
                    <th>Opened</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {data.items.map((incident) => (
                    <tr key={incident.id}>
                      <td>
                        <button
                          className="incident-link"
                          onClick={() => setSelected(incident)}
                        >
                          {incident.title}
                        </button>
                        <small className="reference">
                          INC-{incident.id.slice(-8).toUpperCase()}
                        </small>
                      </td>
                      <td>
                        <span
                          className={`severity ${incident.severity.toLowerCase()}`}
                        >
                          {incident.severity}
                        </span>
                      </td>
                      <td>
                        <span
                          className={`status ${incident.status.toLowerCase()}`}
                        >
                          <span />
                          {label(incident.status)}
                        </span>
                      </td>
                      <td>{name(incident.assignee)}</td>
                      <td className="nowrap">{date(incident.createdAt)}</td>
                      <td>
                        <button
                          className="icon"
                          aria-label={`Open ${incident.title}`}
                          onClick={() => setSelected(incident)}
                        >
                          <ArrowUpRight size={16} />
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {!data.items.length && (
              <div className="empty">
                <Check size={28} />
                <h3>
                  {loading
                    ? "Loading incidents…"
                    : filter
                      ? "No incidents with this status"
                      : "A quiet workspace"}
                </h3>
                <p>
                  {loading
                    ? "Fetching your latest updates."
                    : "New incidents will appear here."}
                </p>
              </div>
            )}
            <div className="pagination">
              <small>
                {loading
                  ? "Updating…"
                  : `Page ${page + 1} · ${data.items.length} incidents`}
              </small>
              <div>
                <button
                  className="secondary"
                  aria-label="Previous page"
                  disabled={page === 0}
                  onClick={() => setPage((p) => p - 1)}
                >
                  <ChevronLeft size={16} />
                </button>
                <button
                  className="secondary"
                  aria-label="Next page"
                  disabled={!data.hasMore}
                  onClick={() => setPage((p) => p + 1)}
                >
                  <ChevronRight size={16} />
                </button>
              </div>
            </div>
          </section>
        </>
      )}
      {tab === "notifications" && (
        <section className="panel inbox">
          {notices.length ? (
            notices.map((n) => (
              <article key={n.id} className={n.readAt ? "read" : ""}>
                <Bell size={18} />
                <div>
                  <button
                    className="incident-link"
                    onClick={() => void openIncident(n.incidentId)}
                  >
                    {n.message}
                  </button>
                  <small>{date(n.createdAt)}</small>
                </div>
                {!n.readAt && (
                  <button
                    className="secondary"
                    disabled={busy}
                    onClick={() =>
                      void run(async () => {
                        await api(
                          `${root}/notifications/${n.id}/read`,
                          "PATCH",
                        );
                      })
                    }
                  >
                    Mark read
                  </button>
                )}
              </article>
            ))
          ) : (
            <div className="empty">
              <Bell />
              <h3>You’re all caught up</h3>
              <p>Incident updates arrive here automatically.</p>
            </div>
          )}
        </section>
      )}
      {tab === "team" && (
        <section className="panel settings">
          <h2>Team members</h2>
          <p className="subtle">
            Roles apply only to {org.name}. Responders manage incidents; viewers
            can read them.
          </p>
          <div className="member-list">
            {members.map((m) => (
              <div key={m.subject}>
                <span className="avatar light">{m.displayName[0]}</span>
                <div>
                  <strong>{m.displayName}</strong>
                  <small>{m.subject}</small>
                </div>
                <span className="role">{label(m.role)}</span>
              </div>
            ))}
          </div>
          {org.role === "ADMIN" && (
            <>
              <h3>Add a member or change a role</h3>
              <form
                onSubmit={(e) => {
                  e.preventDefault();
                  const form = e.currentTarget;
                  const f = new FormData(form);
                  void run(async () => {
                    await api(`${root}/members`, "PUT", {
                      subject: f.get("subject"),
                      displayName: f.get("displayName"),
                      role: f.get("role"),
                    });
                    await reloadOrgs();
                    form.reset();
                  });
                }}
              >
                <label>
                  OIDC subject ID
                  <input
                    name="subject"
                    required
                    maxLength={200}
                    placeholder="Copy the subject from the user’s identity provider"
                  />
                </label>
                <label>
                  Display name
                  <input name="displayName" required maxLength={120} />
                </label>
                <label>
                  Role
                  <select name="role">
                    <option value="VIEWER">Viewer</option>
                    <option value="RESPONDER">Responder</option>
                    <option value="ADMIN">Administrator</option>
                  </select>
                </label>
                <button className="primary" disabled={busy}>
                  Save member
                </button>
              </form>
              <h3>Organization name</h3>
              <form
                onSubmit={(e) => {
                  e.preventDefault();
                  const f = new FormData(e.currentTarget);
                  void run(async () => {
                    await api(root, "PUT", { name: f.get("name") });
                    await reloadOrgs();
                  });
                }}
              >
                <label>
                  Name
                  <input
                    name="name"
                    defaultValue={org.name}
                    required
                    maxLength={120}
                  />
                </label>
                <button className="secondary" disabled={busy}>
                  Update name
                </button>
              </form>
            </>
          )}
        </section>
      )}
      {tab === "deliveries" && org.role === "ADMIN" && (
        <section className="panel settings">
          <h2>Notification delivery</h2>
          <p className="subtle">
            Published events are accepted by the transport. Failed outbox events
            can be retried here. Broker dead letters are managed in Azure
            Service Bus Explorer.
          </p>
          {deliveries.length ? (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Event</th>
                    <th>Attempts</th>
                    <th>State</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {deliveries.map((d) => (
                    <tr key={d.id}>
                      <td>{d.id.slice(-8)}</td>
                      <td>{d.attempts}</td>
                      <td>
                        {d.dead_at
                          ? "Failed"
                          : d.published_at
                            ? "Published"
                            : "Pending"}
                        {d.last_error && <small>{d.last_error}</small>}
                      </td>
                      <td>
                        {d.dead_at && (
                          <button
                            className="secondary"
                            disabled={busy}
                            onClick={() =>
                              void run(async () => {
                                await api(
                                  `${root}/deliveries/${d.id}/retry`,
                                  "POST",
                                );
                              })
                            }
                          >
                            Retry
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <div className="empty">No events yet.</div>
          )}
        </section>
      )}
      <footer>
        <span>
          <span className="live-dot" /> Workspace updates every 15 seconds
        </span>
        <span>OpsFlow · Keep the response moving</span>
      </footer>
      {creating && (
        <Modal title="New incident" close={() => setCreating(false)}>
          <form
            onSubmit={(e) => {
              e.preventDefault();
              const f = new FormData(e.currentTarget);
              void run(async () => {
                const next = await api<Incident>(`${root}/incidents`, "POST", {
                  title: f.get("title"),
                  description: f.get("description"),
                  severity: f.get("severity"),
                });
                setCreating(false);
                setSelected(next);
                setTab("incidents");
                setPage(0);
                setFilter("");
              });
            }}
          >
            <label>
              Title
              <input
                name="title"
                required
                maxLength={160}
                autoFocus
                placeholder="What’s happening?"
              />
            </label>
            <label>
              Severity
              <select name="severity" defaultValue="SEV3">
                {(["SEV1", "SEV2", "SEV3", "SEV4"] as Severity[]).map(
                  (s, i) => (
                    <option key={s} value={s}>
                      {s} · {["Critical", "High", "Medium", "Low"][i]}
                    </option>
                  ),
                )}
              </select>
            </label>
            <label>
              Description
              <textarea
                name="description"
                maxLength={8000}
                rows={5}
                placeholder="Impact, symptoms, and any useful context"
              />
            </label>
            <button className="primary" disabled={busy}>
              Create incident
            </button>
            {error && (
              <p role="alert" className="error">
                {error}
              </p>
            )}
          </form>
        </Modal>
      )}
      {selected && (
        <Modal title={selected.title} close={() => setSelected(null)}>
          <div className="detail-meta">
            <span className={`severity ${selected.severity.toLowerCase()}`}>
              {selected.severity}
            </span>
            <span className={`status ${selected.status.toLowerCase()}`}>
              {label(selected.status)}
            </span>
            <small>INC-{selected.id.slice(-8).toUpperCase()}</small>
          </div>
          <p className="description">
            {selected.description || "No description provided."}
          </p>
          {canWrite && (
            <form
              onSubmit={(e) => {
                e.preventDefault();
                const f = new FormData(e.currentTarget);
                void run(async () => {
                  setSelected(
                    await api<Incident>(
                      `${root}/incidents/${selected.id}`,
                      "PATCH",
                      {
                        status: f.get("status"),
                        assignee: f.get("assignee") || null,
                        version: selected.version,
                      },
                    ),
                  );
                });
              }}
            >
              <div className="form-row">
                <label>
                  Status
                  <select
                    name="status"
                    aria-label="Status"
                    key={selected.status}
                    defaultValue={selected.status}
                  >
                    {(
                      [
                        selected.status,
                        ...(selected.status === "OPEN"
                          ? ["ACKNOWLEDGED"]
                          : selected.status === "ACKNOWLEDGED"
                            ? ["RESOLVED"]
                            : org.role === "ADMIN"
                              ? ["OPEN"]
                              : []),
                      ] as Status[]
                    ).map((s) => (
                      <option key={s} value={s}>
                        {label(s)}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Assignee
                  <select
                    name="assignee"
                    aria-label="Assignee"
                    key={selected.assignee}
                    defaultValue={selected.assignee || ""}
                  >
                    <option value="">Unassigned</option>
                    {members
                      .filter((m) => m.role !== "VIEWER")
                      .map((m) => (
                        <option value={m.subject} key={m.subject}>
                          {m.displayName}
                        </option>
                      ))}
                  </select>
                </label>
              </div>
              <button className="primary" disabled={busy}>
                Save changes
              </button>
              <button
                className="secondary"
                type="button"
                disabled={busy}
                onClick={() => void openIncident(selected.id)}
              >
                Reload incident
              </button>
            </form>
          )}
          {error && (
            <p className="error" role="alert">
              {error}
            </p>
          )}
          <h3>Activity</h3>
          {canWrite && (
            <form
              onSubmit={(e) => {
                e.preventDefault();
                const form = e.currentTarget;
                const f = new FormData(form);
                void run(async () => {
                  await api(
                    `${root}/incidents/${selected.id}/comments`,
                    "POST",
                    { message: f.get("message") },
                  );
                  form.reset();
                });
              }}
            >
              <label className="sr-only" htmlFor="comment">
                Comment
              </label>
              <textarea
                id="comment"
                name="message"
                required
                maxLength={2000}
                rows={2}
                placeholder="Add an update for the team"
              />
              <button className="secondary" disabled={busy}>
                Post update
              </button>
            </form>
          )}
          <div className="timeline">
            {activity.map((a) => (
              <article key={a.id}>
                <span />
                <div>
                  <strong>{name(a.actor)}</strong>
                  <small>{date(a.createdAt)}</small>
                  <p>{a.message}</p>
                </div>
              </article>
            ))}
          </div>
        </Modal>
      )}
    </>
  );
}
function Modal({
  title,
  close,
  children,
}: {
  title: string;
  close: () => void;
  children: React.ReactNode;
}) {
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.key === "Escape") close();
    };
    document.addEventListener("keydown", handler);
    const prior = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", handler);
      document.body.style.overflow = prior;
    };
  }, [close]);
  return (
    <div
      className="overlay"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) close();
      }}
    >
      <section
        className="modal"
        role="dialog"
        aria-modal="true"
        aria-label={title}
      >
        <header>
          <h2>{title}</h2>
          <button className="icon" aria-label="Close dialog" onClick={close}>
            <X />
          </button>
        </header>
        {children}
      </section>
    </div>
  );
}
createRoot(document.getElementById("root")!).render(<App />);
