import type {
  AiQueryResponse,
  AlertDetailView,
  AuditEventPage,
  AuditQueryParams,
  BriefingDonePayload,
  IngestRequest,
  IngestResult,
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

export async function ingestAlert(
  accessToken: string,
  request: IngestRequest
): Promise<IngestResult> {
  const response = await fetch("/api/alerts/ingest", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(request),
  });

  if (response.status === 401) {
    throw new Error("UNAUTHORIZED");
  }
  if (response.status === 403) {
    throw new Error("无权上报告警（缺少 alert:ingest 权限）");
  }
  if (response.status === 400) {
    throw new Error("请求参数不合法，请检查必填字段与格式");
  }
  if (!response.ok) {
    throw new Error("INGEST_ALERT_FAILED");
  }
  return response.json() as Promise<IngestResult>;
}

/**
 * 告警简报（SSE over POST）。EventSource 不支持 POST/自定义 header，
 * 故用 fetch + ReadableStream 解析 text/event-stream。
 */
export async function streamBriefing(
  accessToken: string,
  alertId: string,
  handlers: {
    onDelta: (text: string) => void;
    onDone: (payload: BriefingDonePayload) => void;
  }
): Promise<void> {
  const response = await fetch(`/api/ai/briefing/${alertId}`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      Accept: "text/event-stream",
    },
  });

  if (response.status === 401) throw new Error("UNAUTHORIZED");
  if (response.status === 403) throw new Error("无权生成简报（缺少 ai:briefing 权限）");
  if (response.status === 404) throw new Error("ALERT_NOT_FOUND");
  if (response.status === 503) throw new Error("AI 服务暂不可用，请稍后重试");
  if (!response.ok || !response.body) throw new Error("BRIEFING_FAILED");

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });

    let idx: number;
    while ((idx = buffer.indexOf("\n\n")) >= 0) {
      const frame = buffer.slice(0, idx);
      buffer = buffer.slice(idx + 2);
      const parsed = parseSseFrame(frame);
      if (parsed.event === "delta" && parsed.data) {
        try {
          const obj = JSON.parse(parsed.data) as { text?: string };
          if (obj.text) handlers.onDelta(obj.text);
        } catch {
          // 忽略非 JSON delta
        }
      } else if (parsed.event === "done" && parsed.data) {
        try {
          handlers.onDone(JSON.parse(parsed.data) as BriefingDonePayload);
        } catch {
          // 忽略非法 done
        }
      }
    }
  }
}

function parseSseFrame(frame: string): { event?: string; data?: string } {
  let event: string | undefined;
  const dataLines: string[] = [];
  for (const line of frame.split("\n")) {
    if (line.startsWith("event:")) {
      event = line.slice(6).trim();
    } else if (line.startsWith("data:")) {
      dataLines.push(line.slice(5).trim());
    }
  }
  return { event, data: dataLines.length > 0 ? dataLines.join("\n") : undefined };
}
