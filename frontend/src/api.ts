import { auth } from "./auth";
export type Role = "ADMIN" | "RESPONDER" | "VIEWER";
export type Status = "OPEN" | "ACKNOWLEDGED" | "RESOLVED";
export type Severity = "SEV1" | "SEV2" | "SEV3" | "SEV4";
export interface Org {
  id: string;
  name: string;
  role: Role;
}
export interface Member {
  subject: string;
  displayName: string;
  role: Role;
}
export interface Incident {
  id: string;
  organizationId: string;
  title: string;
  description: string;
  severity: Severity;
  status: Status;
  assignee: string | null;
  createdBy: string;
  version: number;
  createdAt: string;
  updatedAt: string;
}
export interface Activity {
  id: string;
  actor: string;
  message: string;
  createdAt: string;
}
export interface Summary {
  total: number;
  open: number;
  acknowledged: number;
  resolved: number;
  critical: number;
}
export interface Notice {
  id: string;
  incidentId: string;
  message: string;
  readAt: string | null;
  createdAt: string;
}
export interface Delivery {
  id: string;
  attempts: number;
  published_at: string | null;
  dead_at: string | null;
  last_error: string | null;
}
export interface Page {
  items: Incident[];
  page: number;
  size: number;
  hasMore: boolean;
}
export async function api<T>(
  path: string,
  method = "GET",
  body?: unknown,
  signal?: AbortSignal,
): Promise<T> {
  let user = await auth.getUser();
  if (user?.expired) user = await auth.signinSilent();
  if (!user) throw new Error("Your session ended. Sign in again.");
  const response = await fetch(
    `${import.meta.env.VITE_API_URL || "/api"}${path}`,
    {
      method,
      signal,
      headers: {
        Authorization: `Bearer ${user.access_token}`,
        ...(body === undefined ? {} : { "Content-Type": "application/json" }),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    },
  );
  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new Error(
      error.detail ||
        (response.status === 401
          ? "Your session ended. Sign in again."
          : `Request failed (${response.status})`),
    );
  }
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}
