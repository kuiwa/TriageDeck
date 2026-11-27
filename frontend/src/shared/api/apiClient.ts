import type {
  AiQueryResponse,
  AlertDetailView,
  AuditEventPage,
  AuditQueryParams,
} from "./types";

export interface WhoAmI {
  username: string;
  subject: string;
  roles: string[];
  permissions: Array<{ obj: string; act: string }>;
}

export async function getWhoami(accessToken: string): Promise<WhoAmI> {
  const response = await fetch("/api/whoami", {
    headers: { Authorization: `Bearer ${accessToken}` }
  });
  if (response.status === 401) {
    throw new Error("UNAUTHORIZED");
  }
  if (!response.ok) {
    throw new Error("WHOAMI_FAILED");
  }
  return response.json() as Promise<WhoAmI>;
}

export async function getAlertDetail(
  accessToken: string,
  id: string
): Promise<AlertDetailView> {
  const response = await fetch(`/api/alerts/${id}`, {
    headers: { Authorization: `Bearer ${accessToken}` }
  });
  if (response.status === 401) {
    throw new Error("UNAUTHORIZED");
  }
  if (response.status === 404) {
    throw new Error("NOT_FOUND");
  }
  if (!response.ok) {
    throw new Error("FETCH_ALERT_DETAIL_FAILED");
  }
  return response.json() as Promise<AlertDetailView>;
}

export async function getAuditEvents(
  accessToken: string,
  params: AuditQueryParams
): Promise<AuditEventPage> {
  const searchParams = new URLSearchParams();
  searchParams.set("from", params.from);
  if (params.to) searchParams.set("to", params.to);
  if (params.actor) searchParams.set("actor", params.actor);
  if (params.action?.length) {
    params.action.forEach((a) => searchParams.append("action", a));
  }
  if (params.outcome?.length) {
    params.outcome.forEach((o) => searchParams.append("outcome", o));
  }
  if (params.page !== undefined) searchParams.set("page", String(params.page));
  if (params.size !== undefined) searchParams.set("size", String(params.size));

  const response = await fetch(`/api/audit/events?${searchParams}`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  if (response.status === 401) {
    throw new Error("UNAUTHORIZED");
  }
  if (!response.ok) {
    throw new Error("FETCH_AUDIT_EVENTS_FAILED");
  }
  return response.json() as Promise<AuditEventPage>;
}

export interface ExportError {
  code: string;
  message: string;
  traceId: string;
}

export async function exportAuditEvents(
  accessToken: string,
  params: AuditQueryParams
): Promise<void> {
  const searchParams = new URLSearchParams();
  searchParams.set("from", params.from);
  if (params.to) searchParams.set("to", params.to);
  if (params.actor) searchParams.set("actor", params.actor);
  if (params.action?.length) {
    params.action.forEach((a) => searchParams.append("action", a));
  }
  if (params.outcome?.length) {
    params.outcome.forEach((o) => searchParams.append("outcome", o));
  }
  searchParams.set("format", "csv");

  const response = await fetch(`/api/audit/events/export?${searchParams}`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });

  if (response.status === 401) {
    throw new Error("UNAUTHORIZED");
  }
  if (response.status === 422) {
    const error = (await response.json()) as ExportError;
    throw new Error(error.code || "EXPORT_TOO_LARGE");
  }
  if (!response.ok) {
    throw new Error("EXPORT_AUDIT_FAILED");
  }

  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  const disposition = response.headers.get("Content-Disposition") || "";
  const match = disposition.match(/filename="?([^";]+)"?/);
  a.download = match ? match[1] : "audit.csv";
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}

export async function aiQuery(
  accessToken: string,
  question: string,
  limit?: number
): Promise<AiQueryResponse> {
  const response = await fetch("/api/ai/query", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ question, limit }),
  });

  if (response.status === 401) {
    throw new Error("UNAUTHORIZED");
  }
  if (response.status === 400) {
    throw new Error("问题不能为空且不超过 500 字");
  }
  if (response.status === 422) {
    const err = (await response.json()) as { code?: string; message?: string };
    throw new Error(err.message || "无法解析该问题，请换一种说法");
  }
  if (response.status === 503) {
    throw new Error("AI 服务暂不可用，请稍后重试");
  }
  if (!response.ok) {
    throw new Error("AI_QUERY_FAILED");
  }
  return response.json() as Promise<AiQueryResponse>;
}
