import React from "react";
import type { AlertDetailView } from "../../../shared/api/types";
import { severityClass } from "../../../shared/severity";

interface AlertDetailPanelProps {
  detail: AlertDetailView;
  onBack: () => void;
}

function formatPayload(payload: unknown): string {
  if (payload == null) return "—";
  try {
    return JSON.stringify(payload, null, 2);
  } catch {
    return String(payload);
  }
}

export function AlertDetailPanel({
  detail,
  onBack,
}: AlertDetailPanelProps) {
  return (
    <section className="alert-card" aria-labelledby="detail-title">
      <div className="alert-card-header">
        <div>
          <button
            type="button"
            className="button button-quiet button-small"
            onClick={onBack}
            aria-label="返回告警列表"
          >
            ← 返回列表
          </button>
          <h2 className="section-title" id="detail-title">
            告警详情
          </h2>
          <p className="section-caption">
            告警 ID：{detail.id} &nbsp;·&nbsp; 状态：{detail.status}
          </p>
        </div>
        <span
          className={`severity-badge ${severityClass(detail.severity)}`}
          title={detail.severity}
        >
          {detail.severity}
        </span>
      </div>

      <div className="detail-grid">
        <div className="detail-field">
          <dt>级别</dt>
          <dd>{detail.severity}</dd>
        </div>
        <div className="detail-field">
          <dt>来源</dt>
          <dd className="cell-mono">{detail.source}</dd>
        </div>
        <div className="detail-field">
          <dt>类型</dt>
          <dd className="cell-mono">{detail.type}</dd>
        </div>
        <div className="detail-field">
          <dt>资源</dt>
          <dd className="cell-mono">{detail.resourceId}</dd>
        </div>
        <div className="detail-field">
          <dt>事件码</dt>
          <dd className="cell-mono">{detail.eventCode || "—"}</dd>
        </div>
        <div className="detail-field">
          <dt>发生次数</dt>
          <dd>{detail.occurrenceCount}</dd>
        </div>
        <div className="detail-field">
          <dt>指纹</dt>
          <dd className="cell-mono">{detail.fingerprint}</dd>
        </div>
        <div className="detail-field">
          <dt>首次出现</dt>
          <dd className="cell-mono">{detail.firstSeenAt}</dd>
        </div>
        <div className="detail-field">
          <dt>最近出现</dt>
          <dd className="cell-mono">{detail.lastSeenAt}</dd>
        </div>
      </div>

      <div className="detail-section">
        <h3 className="detail-section-title">告警内容</h3>
        <p className="detail-message">{detail.message}</p>
      </div>

      <div className="detail-section">
        <h3 className="detail-section-title">合并四段键</h3>
        <p className="detail-caption">
          同 source/type/resourceId/eventCode 的告警会被合并为一条（mergeRule：
          {detail.mergeRule}）
        </p>
        <div className="detail-grid">
          <div className="detail-field">
            <dt>source</dt>
            <dd className="cell-mono">{detail.mergeFields.source}</dd>
          </div>
          <div className="detail-field">
            <dt>type</dt>
            <dd className="cell-mono">{detail.mergeFields.type}</dd>
          </div>
          <div className="detail-field">
            <dt>resourceId</dt>
            <dd className="cell-mono">{detail.mergeFields.resourceId}</dd>
          </div>
          <div className="detail-field">
            <dt>eventCode</dt>
            <dd className="cell-mono">{detail.mergeFields.eventCode}</dd>
          </div>
        </div>
      </div>

      <div className="detail-section">
        <h3 className="detail-section-title">最新上报载荷</h3>
        <pre className="detail-payload">{formatPayload(detail.latestPayload)}</pre>
      </div>

      {detail.events && detail.events.length > 0 && (
        <div className="detail-section">
          <h3 className="detail-section-title">
            事件证据链（最近 {detail.events.length} 条）
          </h3>
          <div className="table-scroll" tabIndex={0} aria-label="告警事件列表">
            <table className="alert-table">
              <caption className="sr-only">告警事件证据链</caption>
              <thead>
                <tr>
                  <th scope="col">发生时间</th>
                  <th scope="col">级别</th>
                  <th scope="col">入口</th>
                  <th scope="col">事件内容</th>
                  <th scope="col">事件 ID</th>
                </tr>
              </thead>
              <tbody>
                {detail.events.map((ev) => (
                  <tr key={ev.id}>
                    <td className="cell-mono">{ev.occurredAt}</td>
                    <td>
                      <span className={`severity-badge ${severityClass(ev.severity)}`}>
                        {ev.severity}
                      </span>
                    </td>
                    <td className="cell-mono">{ev.ingress}</td>
                    <td className="cell-message">{ev.message}</td>
                    <td className="cell-mono">{ev.id}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {detail.cleared && (
        <div className="detail-section">
          <h3 className="detail-section-title">清除信息</h3>
          <div className="detail-grid">
            <div className="detail-field">
              <dt>清除时间</dt>
              <dd className="cell-mono">{detail.cleared.clearedAt}</dd>
            </div>
            <div className="detail-field">
              <dt>清除人</dt>
              <dd>{detail.cleared.clearedBy}</dd>
            </div>
            <div className="detail-field">
              <dt>清除模式</dt>
              <dd>{detail.cleared.clearMode}</dd>
            </div>
            <div className="detail-field">
              <dt>清除原因</dt>
              <dd>{detail.cleared.clearedReason ?? "—"}</dd>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}