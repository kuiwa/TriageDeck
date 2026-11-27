import React, { useCallback, useMemo, useState } from "react";
import type {
  AuditAction,
  AuditEventView,
  AuditQueryParams,
  Outcome,
} from "../../../shared/api/types";
import { useAuditEvents } from "../hooks/useAuditEvents";
import { Pagination } from "../../../shared/components/Pagination";
import { exportAuditEvents } from "../../../shared/api/apiClient";

const PAGE_SIZE = 20;

const ACTION_LABELS: Record<AuditAction, string> = {
  LOGIN: "登录",
  LOGOUT: "登出",
  LOGIN_FAILED: "登录失败",
  ALERT_CLEARED: "清除告警",
  ACCESS_DENIED: "越权访问",
};

const OUTCOME_LABELS: Record<Outcome, string> = {
  SUCCESS: "成功",
  DENIED: "拒绝",
  FAILED: "失败",
};

const ALL_ACTIONS: AuditAction[] = [
  "LOGIN",
  "LOGOUT",
  "LOGIN_FAILED",
  "ALERT_CLEARED",
  "ACCESS_DENIED",
];

const ALL_OUTCOMES: Outcome[] = ["SUCCESS", "DENIED", "FAILED"];

interface AuditTableProps {
  accessToken: string | null;
}

export function AuditTable({ accessToken }: AuditTableProps) {
  const [params, setParams] = useState<AuditQueryParams>({
    from: defaultFrom(),
    size: PAGE_SIZE,
  });
  const [page, setPage] = useState(0);

  const query = useAuditEvents(
    accessToken,
    { ...params, page },
    Boolean(accessToken)
  );

  const data = query.data;
  const items: AuditEventView[] = data?.items ?? [];
  const total = data?.total ?? 0;

  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);

  const handleExport = useCallback(async () => {
    if (!accessToken) return;
    setExporting(true);
    setExportError(null);
    try {
      // 导出不带分页参数，服务端返回全部匹配行（受 50000 行上限保护）
      const { page, size, ...exportParams } = params;
      await exportAuditEvents(accessToken, exportParams);
    } catch (error) {
      const message = error instanceof Error ? error.message : "导出失败";
      if (message === "EXPORT_TOO_LARGE") {
        setExportError("导出行数超过 50000 限制，请缩小时间范围或加筛选条件后重试。");
      } else {
        setExportError(message);
      }
    } finally {
      setExporting(false);
    }
  }, [accessToken, params]);

  const handleFromChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      setParams((p) => ({ ...p, from: e.target.value, page: 0 }));
    },
    []
  );
  const handleToChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      setParams((p) => ({ ...p, to: e.target.value || undefined, page: 0 }));
    },
    []
  );
  const handleActorChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      setParams((p) => ({
        ...p,
        actor: e.target.value || undefined,
        page: 0,
      }));
    },
    []
  );
  const handleActionToggle = useCallback(
    (action: AuditAction) => {
      setParams((p) => {
        const current = p.action ?? [];
        const next = current.includes(action)
          ? current.filter((a) => a !== action)
          : [...current, action];
        return { ...p, action: next, page: 0 };
      });
    },
    []
  );
  const handleOutcomeToggle = useCallback(
    (outcome: Outcome) => {
      setParams((p) => {
        const current = p.outcome ?? [];
        const next = current.includes(outcome)
          ? current.filter((o) => o !== outcome)
          : [...current, outcome];
        return { ...p, outcome: next, page: 0 };
      });
    },
    []
  );
  const handlePageChange = useCallback((nextPage: number) => {
    setPage(nextPage);
    setParams((p) => ({ ...p, page: nextPage }));
  }, []);

  const isActionSelected = useMemo(
    () => (action: AuditAction) => (params.action ?? []).includes(action),
    [params.action]
  );
  const isOutcomeSelected = useMemo(
    () => (outcome: Outcome) => (params.outcome ?? []).includes(outcome),
    [params.outcome]
  );
  return (
    <section className="audit-card" aria-labelledby="audit-title">
      <div className="audit-card-header">
        <div>
          <h2 className="section-title" id="audit-title">
            审计时间线
          </h2>
          <p className="section-caption">
            登录/登出/越权/清除等操作记录，按时间倒序排列。
          </p>
        </div>
      </div>

      <div className="audit-filter">
        <div className="filter-row">
          <label className="filter-label">发生时间</label>
          <input
            className="filter-input"
            type="datetime-local"
            value={params.from}
            onChange={handleFromChange}
            required
          />
          <input
            className="filter-input"
            type="datetime-local"
            value={params.to || ""}
            onChange={handleToChange}
            placeholder="至"
          />
          <label className="filter-label">操作人</label>
          <input
            className="filter-input"
            type="text"
            value={params.actor || ""}
            onChange={handleActorChange}
            placeholder="用户名"
          />
        </div>
        <div className="filter-row">
          <label className="filter-label">动作</label>
          <div className="filter-chips">
            {ALL_ACTIONS.map((action) => (
              <button
                key={action}
                type="button"
                className={`filter-chip ${
                  isActionSelected(action) ? "filter-chip-active" : ""
                }`}
                onClick={() => handleActionToggle(action)}
              >
                {ACTION_LABELS[action]}
              </button>
            ))}
          </div>
          <label className="filter-label">结果</label>
          <div className="filter-chips">
            {ALL_OUTCOMES.map((outcome) => (
              <button
                key={outcome}
                type="button"
                className={`filter-chip ${
                  isOutcomeSelected(outcome) ? "filter-chip-active" : ""
                }`}
                onClick={() => handleOutcomeToggle(outcome)}
              >
                {OUTCOME_LABELS[outcome]}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* 导出操作区 */}
      <div className="audit-export">
        <button
          type="button"
          className="button button-secondary"
          onClick={handleExport}
          disabled={exporting || items.length === 0}
        >
          {exporting ? "导出中…" : "导出 CSV"}
        </button>
        {exportError && (
          <div className="error-toast" role="alert">{exportError}</div>
        )}
      </div>

      {data && !data.eventsCollected && !data.eventsDisabled && (
        <div className="audit-hint">
          未采集到新的审计事件，请稍后重试。
        </div>
      )}
      <div className="table-scroll" tabIndex={0} aria-label="审计事件列表">
        <table className="alert-table">
          <caption className="sr-only">审计时间线列表</caption>
          <thead>
            <tr>
              <th scope="col">发生时间</th>
              <th scope="col">操作人</th>
              <th scope="col">操作人类型</th>
              <th scope="col">动作</th>
              <th scope="col">对象类型</th>
              <th scope="col">对象</th>
              <th scope="col">结果</th>
              <th scope="col">来源</th>
              <th scope="col">客户端网段</th>
              <th scope="col">详情摘要</th>
            </tr>
          </thead>
          <tbody>
            {items.length === 0 ? (
              <tr>
                <td colSpan={10} className="empty-state">
                  暂无审计记录。
                </td>
              </tr>
            ) : (
              items.map((event) => (
                <tr key={event.id}>
                  <td className="cell-mono">{event.occurredAt}</td>
                  <td className="cell-mono">{event.actor}</td>
                  <td className="cell-mono">{event.actorType}</td>
                  <td className="cell-mono">{ACTION_LABELS[event.action]}</td>
                  <td className="cell-mono">{event.targetType}</td>
                  <td className="cell-mono">{event.targetId ?? "—"}</td>
                  <td className={`cell-outcome ${event.outcome === "DENIED" ? "outcome-denied" : ""}`}>
                    {OUTCOME_LABELS[event.outcome]}
                  </td>
                  <td className="cell-mono">{event.source}</td>
                  <td className="cell-mono">{event.clientNet ?? "—"}</td>
                  <td className="cell-message">{formatDetail(event.detail)}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
      <div className="pagination-bar">
        <Pagination
          page={page}
          size={data?.size ?? PAGE_SIZE}
          total={total}
          onPageChange={handlePageChange}
          ariaLabel="审计事件分页"
        />
      </div>
    </section>
  );
}

function defaultFrom(): string {
  const d = new Date();
  d.setDate(d.getDate() - 7);
  return d.toISOString().slice(0, 16);
}

function formatDetail(detail: Record<string, unknown>): string {
  if (!detail || Object.keys(detail).length === 0) return "—";
  return Object.entries(detail)
    .map(([k, v]) => `${k}=${String(v)}`)
    .join("; ");
}