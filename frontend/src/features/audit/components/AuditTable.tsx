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

  const applyRange = useCallback((days: number) => {
    const now = new Date();
    const from = new Date(now.getTime() - days * 24 * 60 * 60 * 1000);
    setParams((p) => ({
      ...p,
      from: toLocalInput(from),
      to: toLocalInput(now),
      page: 0,
    }));
    setPage(0);
  }, []);

  const handleReset = useCallback(() => {
    setParams({ from: defaultFrom(), size: PAGE_SIZE, page: 0 });
    setPage(0);
  }, []);

  const activeFilterSummary = useMemo(() => {
    const parts: string[] = [];
    if (params.actor) parts.push(`操作人=${params.actor}`);
    if (params.action?.length) {
      parts.push(`动作=${params.action.map((a) => ACTION_LABELS[a]).join("/")}`);
    }
    if (params.outcome?.length) {
      parts.push(`结果=${params.outcome.map((o) => OUTCOME_LABELS[o]).join("/")}`);
    }
    return parts;
  }, [params.actor, params.action, params.outcome]);

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
        <div className="audit-intro">
          <p>
            审计时间线记录「谁在什么时候做了什么」：清除告警、越权访问、登录/登出/登录失败等。
          </p>
          <p className="audit-intro-sub">
            下方「动作」「结果」是可多选的过滤项，不选表示不限；时间跨度最大 90 天。
          </p>
        </div>

        <div className="filter-row">
          <span className="filter-label">快捷时间</span>
          <div className="filter-chips">
            <button type="button" className="filter-chip" onClick={() => applyRange(0)}>
              今天
            </button>
            <button type="button" className="filter-chip" onClick={() => applyRange(7)}>
              近 7 天
            </button>
            <button type="button" className="filter-chip" onClick={() => applyRange(30)}>
              近 30 天
            </button>
            <button type="button" className="filter-chip" onClick={() => applyRange(90)}>
              近 90 天
            </button>
          </div>
          <button
            type="button"
            className="button button-quiet button-small"
            onClick={handleReset}
          >
            重置筛选
          </button>
        </div>

        <div className="filter-row">
          <span className="filter-label">起（必填）</span>
          <input
            className="filter-input"
            type="datetime-local"
            value={params.from}
            onChange={handleFromChange}
            required
          />
          <span className="filter-label">止</span>
          <input
            className="filter-input"
            type="datetime-local"
            value={params.to || ""}
            onChange={handleToChange}
          />
          <span className="filter-label">操作人</span>
          <input
            className="filter-input"
            type="text"
            value={params.actor || ""}
            onChange={handleActorChange}
            placeholder="用户名（精确匹配）"
          />
        </div>

        <div className="filter-row">
          <span className="filter-label">动作</span>
          <div className="filter-chips" role="group" aria-label="按动作筛选">
            {ALL_ACTIONS.map((action) => {
              const selected = isActionSelected(action);
              return (
                <button
                  key={action}
                  type="button"
                  aria-pressed={selected}
                  className={`filter-chip ${selected ? "filter-chip-active" : ""}`}
                  onClick={() => handleActionToggle(action)}
                >
                  {selected ? "✓ " : ""}
                  {ACTION_LABELS[action]}
                </button>
              );
            })}
          </div>
        </div>

        <div className="filter-row">
          <span className="filter-label">结果</span>
          <div className="filter-chips" role="group" aria-label="按结果筛选">
            {ALL_OUTCOMES.map((outcome) => {
              const selected = isOutcomeSelected(outcome);
              return (
                <button
                  key={outcome}
                  type="button"
                  aria-pressed={selected}
                  className={`filter-chip ${selected ? "filter-chip-active" : ""}`}
                  onClick={() => handleOutcomeToggle(outcome)}
                >
                  {selected ? "✓ " : ""}
                  {OUTCOME_LABELS[outcome]}
                </button>
              );
            })}
          </div>
        </div>

        {activeFilterSummary.length > 0 && (
          <p className="audit-active-filters">
            已选条件：{activeFilterSummary.join("；")}
          </p>
        )}
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

      {query.isError && (
        <div className="error-toast" role="alert">
          查询失败：{query.error.message}
        </div>
      )}

      {data && (
        <div className="audit-summary">
          共 <strong>{total}</strong> 条
          {activeFilterSummary.length > 0 ? "（已应用筛选）" : ""}
          {total === 0 && !data.eventsCollected && !data.eventsDisabled && (
            <span className="audit-summary-hint">
              {" "}
              · 登录类事件依赖 Keycloak 采集器，当前采集可能未运行
            </span>
          )}
        </div>
      )}

      {data && total === 0 && (
        <div className="audit-empty-hint">
          <strong>该时间范围内没有符合条件的审计事件。</strong>
          <ul>
            <li>点「近 30 天 / 近 90 天」放宽时间范围；</li>
            <li>「动作 / 结果」不选表示不限，可点「重置筛选」清空；</li>
            <li>用 admin 清除一条活动告警后再刷新，应出现「清除告警 / 成功」；</li>
            <li>登录类事件来自 Keycloak 采集器，需要正确配置 Admin 客户端。</li>
          </ul>
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
                  该时间范围内暂无审计记录。
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
  return toLocalInput(d);
}

function toLocalInput(date: Date): string {
  const d = new Date(date.getTime());
  d.setMinutes(d.getMinutes() - d.getTimezoneOffset());
  return d.toISOString().slice(0, 16);
}

function formatDetail(detail: Record<string, unknown>): string {
  if (!detail || Object.keys(detail).length === 0) return "—";
  return Object.entries(detail)
    .map(([k, v]) => `${k}=${String(v)}`)
    .join("; ");
}