import type { AlertHistoryView, AlertView } from "./api/types";

/**
 * 模拟告警数据（用于 VITE_MOCK_MODE=true 的静态演示/Cloudflare Pages 展示）。
 * 数据结构完全复用 contracts 中的 AlertView / AlertHistoryView，不接任何后端。
 */

export const MOCK_ALERTS: AlertView[] = [
  {
    id: "11111111-1111-1111-1111-111111111001",
    severity: "CRITICAL",
    source: "payment-gateway",
    type: "transaction_timeout",
    resourceId: "res-pay-001",
    eventCode: "TXN_TIMEOUT",
    message: "支付网关交易超时，未在 30s 内收到 upstream 响应",
    firstSeenAt: "2026-11-27T09:12:00Z",
    lastSeenAt: "2026-11-27T10:45:00Z",
    occurrenceCount: 12,
    fingerprint: "fp:payment-gateway:transaction_timeout:res-pay-001",
  },
  {
    id: "11111111-1111-1111-1111-111111111002",
    severity: "HIGH",
    source: "order-service",
    type: "db_connection_exhausted",
    resourceId: "res-db-primary",
    eventCode: "DB_CONN_EXHAUSTED",
    message: "订单服务数据库连接池耗尽，活跃连接数达到上限",
    firstSeenAt: "2026-11-27T08:30:00Z",
    lastSeenAt: "2026-11-27T10:30:00Z",
    occurrenceCount: 7,
    fingerprint: "fp:order-service:db_connection_exhausted:res-db-primary",
  },
  {
    id: "11111111-1111-1111-1111-111111111003",
    severity: "MEDIUM",
    source: "inventory-service",
    type: "stock_low",
    resourceId: "res-sku-998877",
    eventCode: "STOCK_LOW",
    message: "SKU-998877 库存低于安全阈值，建议补货",
    firstSeenAt: "2026-11-27T07:00:00Z",
    lastSeenAt: "2026-11-27T10:15:00Z",
    occurrenceCount: 3,
    fingerprint: "fp:inventory-service:stock_low:res-sku-998877",
  },
  {
    id: "11111111-1111-1111-1111-111111111004",
    severity: "LOW",
    source: "notification-service",
    type: "email_queue_delayed",
    resourceId: "res-email-queue",
    eventCode: "EMAIL_QUEUE_DELAYED",
    message: "邮件队列处理延迟，当前积压 150 封",
    firstSeenAt: "2026-11-27T06:00:00Z",
    lastSeenAt: "2026-11-27T10:00:00Z",
    occurrenceCount: 1,
    fingerprint: "fp:notification-service:email_queue_delayed:res-email-queue",
  },
  {
    id: "11111111-1111-1111-1111-111111111005",
    severity: "INFO",
    source: "auth-service",
    type: "scheduled_maintenance",
    resourceId: "res-auth-cluster",
    eventCode: "SCHEDULED_MAINTENANCE",
    message: "认证集群计划内维护窗口，预计持续 30 分钟",
    firstSeenAt: "2026-11-27T05:00:00Z",
    lastSeenAt: "2026-11-27T09:00:00Z",
    occurrenceCount: 1,
    fingerprint: "fp:auth-service:scheduled_maintenance:res-auth-cluster",
  },
  {
    id: "11111111-1111-1111-1111-111111111006",
    severity: "CRITICAL",
    source: "gateway",
    type: "upstream_502",
    resourceId: "res-gateway-prod",
    eventCode: "UPSTREAM_502",
    message: "网关上游服务返回 502 Bad Gateway，失败率 12%",
    firstSeenAt: "2026-11-27T10:10:00Z",
    lastSeenAt: "2026-11-27T10:50:00Z",
    occurrenceCount: 23,
    fingerprint: "fp:gateway:upstream_502:res-gateway-prod",
  },
];

export const MOCK_HISTORY_ALERTS: AlertHistoryView[] = [
  {
    id: "11111111-1111-1111-1111-111111111007",
    severity: "HIGH",
    source: "payment-gateway",
    type: "transaction_timeout",
    resourceId: "res-pay-002",
    eventCode: "TXN_TIMEOUT",
    message: "支付网关交易超时（已清除）",
    firstSeenAt: "2026-11-27T08:00:00Z",
    lastSeenAt: "2026-11-27T08:45:00Z",
    occurrenceCount: 5,
    fingerprint: "fp:payment-gateway:transaction_timeout:res-pay-002",
    clearedAt: "2026-11-27T09:00:00Z",
    clearedBy: "admin",
    clearedReason: "已处理，故障恢复",
    clearMode: "MANUAL",
  },
];

/** 模拟会话身份（mock 模式下显示在顶部与 profile 卡片） */
export const MOCK_WHOAMI = {
  username: "demo-user",
  subject: "demo-subject-001",
  roles: ["viewer"],
  permissions: [{ obj: "alert", act: "read" }],
} as const;
