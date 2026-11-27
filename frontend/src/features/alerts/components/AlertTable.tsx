import React from "react";
import type { AlertView } from "../../../shared/api/types";
import { Pagination } from "../../../shared/components/Pagination";
import { severityClass } from "../../../shared/severity";

interface AlertsProps {
  items: AlertView[];
  total: number;
  page: number;
  size: number;
  canClear: boolean;
  onClear: (alertId: string, reason?: string | null) => Promise<void>;
  onPageChange: (page: number) => void;
  /** 点击行进入详情页 */
  onRowClick?: (alertId: string) => void;
}

interface ClearDialogProps {
  open: boolean;
  onClose: () => void;
  onConfirm: (reason: string | null) => void;
  alertId: string;
}

function ClearDialog({ open, onClose, onConfirm, alertId }: ClearDialogProps) {
  const [reason, setReason] = React.useState("");
  const [loading, setLoading] = React.useState(false);

  if (!open) return null;

  return (
    <div className="dialog-overlay" onClick={onClose}>
      <div className="dialog" onClick={(e) => e.stopPropagation()}>
        <h3 className="dialog-title">清除告警</h3>
        <p className="dialog-caption">告警 ID：{alertId}</p>
        <label className="dialog-label">清除原因（可选）</label>
        <textarea
          className="dialog-textarea"
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          placeholder="输入清除原因…"
        />
        <div className="dialog-actions">
          <button className="button button-quiet" onClick={onClose} disabled={loading}>
            取消
          </button>
          <button
            className="button button-danger"
            onClick={() => {
              setLoading(true);
              onConfirm(reason || null);
            }}
            disabled={loading}
          >
            {loading ? "清除中…" : "确认清除"}
          </button>
        </div>
      </div>
    </div>
  );
}

export function AlertTable({
  items,
  total,
  page,
  size,
  canClear,
  onClear,
  onPageChange,
  onRowClick,
}: AlertsProps) {
  const [clearDialog, setClearDialog] = React.useState<{
    open: boolean;
    alertId: string;
  } | null>(null);
  const [clearError, setClearError] = React.useState<string | null>(null);

  const handleClearClick = async (alertId: string) => {
    setClearError(null);
    try {
      await onClear(alertId, null);
    } catch (error) {
      setClearError(error instanceof Error ? error.message : "清除失败");
    }
  };

  const handleConfirmClear = async (reason: string | null) => {
    if (!clearDialog) return;
    setClearError(null);
    try {
      await onClear(clearDialog.alertId, reason);
      setClearDialog(null);
    } catch (error) {
      setClearError(error instanceof Error ? error.message : "清除失败");
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
              {canClear && <th scope="col">操作</th>}
            </tr>
          </thead>
          <tbody>
            {items.length === 0 ? (
              <tr>
                <td colSpan={canClear ? 9 : 8} className="empty-state">
                  当前没有活动告警，状态良好。
                </td>
              </tr>
            ) : (
              items.map((alert) => (
                <tr
                  key={alert.fingerprint}
                  onClick={() => onRowClick?.(alert.id)}
                  className={onRowClick ? "row-clickable" : undefined}
                >
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
                  {canClear && (
                    <td className="cell-action">
                      <button
                        className="button button-quiet button-small"
                        onClick={(e) => {
                          e.stopPropagation();
                          setClearDialog({ open: true, alertId: alert.id });
                        }}
                        disabled={!!clearDialog}
                      >
                        清除
                      </button>
                    </td>
                  )}
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
          ariaLabel="活动告警分页"
        />
      </div>
      {clearDialog && (
        <ClearDialog
          open
          alertId={clearDialog.alertId}
          onClose={() => setClearDialog(null)}
          onConfirm={handleConfirmClear}
        />
      )}
      {clearError && (
        <div className="error-toast">{clearError}</div>
      )}
    </section>
  );
}