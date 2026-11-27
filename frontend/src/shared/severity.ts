/**
 * 严重级别展示辅助（枚举与顺序见 docs/02b-contract.md §1.1）。
 * 表格徽标、顶部计数看板共用，避免各写一份映射。
 */

export const SEVERITY_ORDER = ["CRITICAL", "HIGH", "MEDIUM", "LOW", "INFO"] as const;

export type Severity = (typeof SEVERITY_ORDER)[number];

/** 中文短标签，与契约 §1.1「中文」列一致。 */
export const SEVERITY_LABELS: Record<Severity, string> = {
  CRITICAL: "严重",
  HIGH: "高",
  MEDIUM: "中",
  LOW: "低",
  INFO: "提示",
};

/** 表格徽标样式类；未知值退化为默认样式。 */
export function severityClass(severity: string): string {
  switch (severity) {
    case "CRITICAL": return "severity-critical";
    case "HIGH": return "severity-high";
    case "MEDIUM": return "severity-medium";
    case "LOW": return "severity-low";
    case "INFO": return "severity-info";
    default: return "severity-default";
  }
}
