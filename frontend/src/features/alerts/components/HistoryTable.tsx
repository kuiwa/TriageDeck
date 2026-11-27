import React from "react";
import type { AlertHistoryView } from "../../../shared/api/types";
import { Pagination } from "../../../shared/components/Pagination";

interface HistoryTableProps {
  items: AlertHistoryView[];
  total: number;
  page: number;
  size: number;
  onPageChange: (page: number) => void;
}

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

export function HistoryTable({ items, total, page, size, onPageChange }: HistoryTableProps) {
  return (
    <section className="alert-card" aria-labelledby="history-title">
      <div className="alert-card-header">
        <div>
          <h2 className="section-title" id="history-title">历史告警</h2>
          <p className="section-caption">
            共 {total} 条 <span aria-hidden="true">·</span> 第 {page + 1} 页 <span aria-hidden="true">·</span> 每页 {size} 条
          </p>
        </div>
      </div>
      <div className="table-scroll" tabIndex={0} aria-label="历史告警列表，可横向滚动">
        <table className="alert-table">
          <caption className="sr-only">历史告警列表</caption>
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
              <th scope="col">清除时间</th>
              <th scope="col">清除人</th>
            </tr>
          </thead>
          <tbody>
            {items.length === 0 ? (
              <tr>
                <td colSpan={10} className="empty-state">
                  暂无历史告警。
                </td>
              </tr>
            ) : (
              items.map((alert) => (
                <tr key={alert.fingerprint}>
                  <td className="cell-mono cell-id">{alert.id}</td>
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
                  <td className="cell-mono">{alert.clearedAt}</td>
                  <td className="cell-mono">{alert.clearedBy}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
      <div className="pagination-bar">
        <Pagination
          page={page}
          size={size}
          total={total}
          onPageChange={onPageChange}
          ariaLabel="历史告警分页"
        />
      </div>
    </section>
  );
}