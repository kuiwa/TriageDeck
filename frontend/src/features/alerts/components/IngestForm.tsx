import React, { useState } from "react";
import { SEVERITY_LABELS, SEVERITY_ORDER } from "../../../shared/severity";
import { useIngestAlert } from "../hooks/useIngestAlert";
import type { IngestRequest } from "../../../shared/api/types";

interface IngestFormProps {
  accessToken: string | null;
  canIngest: boolean;
}

function nowLocalInput(): string {
  const d = new Date();
  d.setMinutes(d.getMinutes() - d.getTimezoneOffset());
  return d.toISOString().slice(0, 16);
}

export function IngestForm({ accessToken, canIngest }: IngestFormProps) {
  const [source, setSource] = useState("");
  const [type, setType] = useState("");
  const [resourceId, setResourceId] = useState("");
  const [eventCode, setEventCode] = useState("");
  const [severity, setSeverity] = useState<string>("MEDIUM");
  const [message, setMessage] = useState("");
  const [occurredAt, setOccurredAt] = useState<string>(nowLocalInput());
  const [payloadText, setPayloadText] = useState("");
  const [formError, setFormError] = useState<string | null>(null);

  const mutation = useIngestAlert(accessToken);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setFormError(null);

    if (!source.trim() || !type.trim() || !resourceId.trim() || !message.trim()) {
      setFormError("source / type / resourceId / message 均为必填");
      return;
    }

    let payload: Record<string, unknown> | undefined;
    if (payloadText.trim()) {
      try {
        const parsed = JSON.parse(payloadText);
        if (typeof parsed !== "object" || parsed === null || Array.isArray(parsed)) {
          setFormError("payload 必须是 JSON 对象（{...}）");
          return;
        }
        payload = parsed as Record<string, unknown>;
      } catch {
        setFormError("payload 不是合法 JSON");
        return;
      }
    }

    const request: IngestRequest = {
      source: source.trim(),
      type: type.trim(),
      resourceId: resourceId.trim(),
      eventCode: eventCode.trim() || undefined,
      severity,
      message: message.trim(),
      occurredAt: new Date(occurredAt).toISOString(),
      payload,
    };
    mutation.mutate(request);
  };

  return (
    <section className="ingest-card" aria-labelledby="ingest-title">
      <div className="audit-card-header">
        <div>
          <h2 className="section-title" id="ingest-title">
            告警上报
          </h2>
          <p className="section-caption">
            手工上报一条告警。相同 source/type/resourceId/eventCode 会被合并为同一条（occurrenceCount 递增）。
          </p>
        </div>
      </div>

      {!canIngest && (
        <div className="audit-hint">当前账号缺少 alert:ingest 权限，无法上报。</div>
      )}

      <form className="ingest-form" onSubmit={handleSubmit}>
        <div className="ingest-grid">
          <label className="ingest-field">
            <span className="filter-label">source *</span>
            <input
              className="filter-input"
              value={source}
              onChange={(e) => setSource(e.target.value)}
              maxLength={128}
              placeholder="如 payment-api"
            />
          </label>
          <label className="ingest-field">
            <span className="filter-label">type *</span>
            <input
              className="filter-input"
              value={type}
              onChange={(e) => setType(e.target.value)}
              maxLength={128}
              placeholder="如 availability"
            />
          </label>
          <label className="ingest-field">
            <span className="filter-label">resourceId *</span>
            <input
              className="filter-input"
              value={resourceId}
              onChange={(e) => setResourceId(e.target.value)}
              maxLength={128}
              placeholder="如 host-01"
            />
          </label>
          <label className="ingest-field">
            <span className="filter-label">eventCode</span>
            <input
              className="filter-input"
              value={eventCode}
              onChange={(e) => setEventCode(e.target.value)}
              maxLength={128}
              placeholder="可选"
            />
          </label>
          <label className="ingest-field">
            <span className="filter-label">severity *</span>
            <select
              className="filter-input"
              value={severity}
              onChange={(e) => setSeverity(e.target.value)}
            >
              {SEVERITY_ORDER.map((s) => (
                <option key={s} value={s}>
                  {SEVERITY_LABELS[s]} ({s})
                </option>
              ))}
            </select>
          </label>
          <label className="ingest-field">
            <span className="filter-label">occurredAt *</span>
            <input
              className="filter-input"
              type="datetime-local"
              value={occurredAt}
              onChange={(e) => setOccurredAt(e.target.value)}
            />
          </label>
        </div>

        <label className="ingest-field">
          <span className="filter-label">message *</span>
          <textarea
            className="dialog-textarea"
            value={message}
            onChange={(e) => setMessage(e.target.value)}
            maxLength={4000}
            rows={2}
            placeholder="告警内容，例如：payment-api 5xx 比例超过 20%"
          />
        </label>

        <label className="ingest-field">
          <span className="filter-label">payload（JSON 对象，可选）</span>
          <textarea
            className="dialog-textarea"
            value={payloadText}
            onChange={(e) => setPayloadText(e.target.value)}
            rows={3}
            placeholder='{"latencyMs": 1200, "endpoint": "/pay"}'
          />
        </label>

        <div className="ingest-actions">
          <button
            type="submit"
            className="button button-primary"
            disabled={!canIngest || mutation.isPending}
          >
            {mutation.isPending ? "上报中…" : "上报告警"}
          </button>
          {formError && <span className="error-inline" role="alert">{formError}</span>}
          {mutation.isError && (
            <span className="error-inline" role="alert">{mutation.error.message}</span>
          )}
        </div>
      </form>

      {mutation.data && (
        <div className="ingest-result" role="status">
          {mutation.data.created ? "已新建告警" : "已合并到已有告警"}：ID{" "}
          <span className="cell-mono">{mutation.data.id}</span>，当前发生次数{" "}
          <strong>{mutation.data.occurrenceCount}</strong>，级别{" "}
          <strong>{mutation.data.severity}</strong>。
        </div>
      )}
    </section>
  );
}