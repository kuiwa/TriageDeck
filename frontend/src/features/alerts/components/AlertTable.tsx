import React from "react";
import type { AlertView } from "../../../shared/api/types";

interface AlertsProps {
  items: AlertView[];
  total: number;
  page: number;
  size: number;
}


export function AlertTable({ items, total, page, size }: AlertsProps) {
  const severityClass = (severity: string): string => {
    switch (severity) {
      case "CRITICAL": return "severity-critical";
      case "HIGH": return "severity-high";
      case "MEDIUM": return "severity-medium";
      case "LOW": return "severity-low";
      case "INFO": return "severity-info";
      default: return "severity-default";
    }
  };

  return (
    <section className="alert-card" aria-labelledby="alerts-title">
      <div className="alert-card-header">
        <div>
          <h2 className="section-title" id="alerts-title">活动告警</h2>
          <p className="section-caption">
            共 {total} 条 <span aria-hidden="true">·</span> 第 {page + 1} 页 <span aria-hidden="true">·</span> 每页 {size} 条
          </p>
        </div>
      </div>
      <div className="table-scroll" tabIndex={0} aria-label="告警列表，可横向滚动">
        <table className="alert-table">
          <caption className="sr-only">活动告警列表</caption>
          <thead>
            <tr>
              <th scope="col">告警 ID</th>
              <th scope="col">级别</th>
              <th scope="col">来源</th>
              <th scope="col">类型</th>
              <th scope="col">资源</th>
              <th scope="col">告警内容</th>
              <th scope="col">最近发生</th>
              <th scope="col">发生次数</th>
            </tr>
          </thead>
          <tbody>
            {items.length === 0 ? (
              <tr>
                <td colSpan={8} className="empty-state">
                  当前没有活动告警，状态良好。
                </td>
              </tr>
            ) : (
              items.map((alert) => (
                <tr key={alert.fingerprint}>
                  <td className="cell-mono cell-id">
                    {alert.id}
                  </td>
                  <td>
                    <span className={`severity-badge ${severityClass(alert.severity)}`}>
                      {alert.severity}
                    </span>
                  </td>
                  <td className="cell-mono">{alert.source}</td>
                  <td className="cell-mono">{alert.type}</td>
                  <td className="cell-mono">{alert.resourceId}</td>
                  <td className="cell-message">{alert.message}</td>
                  <td className="cell-mono">{alert.lastSeenAt}</td>
                  <td className="cell-mono">{alert.occurrenceCount}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </section>
  );
}