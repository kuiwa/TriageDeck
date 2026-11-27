import { SEVERITY_LABELS, SEVERITY_ORDER } from "../../../shared/severity";
import type { SeveritySummary } from "../hooks/useSeveritySummary";

interface SeverityDashboardProps {
  summary?: SeveritySummary;
  /** 计数接口不可用时给出可读提示，而不是静默空白 */
  unavailable?: boolean;
}

/**
 * 活动告警按级别的实时数量看板（页面最顶端）。
 * 数字每 10 秒自动刷新，来源见 useSeveritySummary。
 */
export function SeverityDashboard({ summary, unavailable = false }: SeverityDashboardProps) {
  const caption = summary
    ? `共 ${summary.total} 条活动告警 · 每 10 秒自动刷新`
    : unavailable
      ? "计数暂不可用，稍后自动重试"
      : "正在加载活动告警数量…";

  return (
    <section className="severity-dashboard" aria-labelledby="severity-dashboard-title">
      <div className="severity-dashboard-head">
        <div>
          <p className="eyebrow">Live overview</p>
          <h2 className="section-title" id="severity-dashboard-title">活动告警实时分布</h2>
          <p className="section-caption">{caption}</p>
        </div>
        <span className="live-indicator">实时</span>
      </div>
      <dl className="severity-grid">
        {SEVERITY_ORDER.map((severity) => (
          <div className="severity-card" data-severity={severity} key={severity}>
            <dt className="severity-card-label">
              {SEVERITY_LABELS[severity]}
              <span className="severity-card-enum">{severity}</span>
            </dt>
            <dd className="severity-card-count">
              {summary ? summary.counts[severity] : "—"}
            </dd>
          </div>
        ))}
      </dl>
    </section>
  );
}
