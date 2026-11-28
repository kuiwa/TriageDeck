// Auto-generated from docs/api/openapi.yaml via:
//   npx openapi-typescript ../docs/api/openapi.yaml -o src/shared/api/types.ts
// 此文件由 openapi-typescript 生成，不要手动编辑接口字段

export interface AlertView {
  id: string;
  severity: string;
  source: string;
  type: string;
  resourceId: string;
  eventCode: string;
  message: string;
  firstSeenAt: string;
  lastSeenAt: string;
  occurrenceCount: number;
  fingerprint: string;
}

export interface AlertHistoryView extends AlertView {
  clearedAt: string;
  clearedBy: string;
  clearedReason?: string | null;
  clearMode: string;
}

export interface AlertPageView {
  items: AlertView[];
  total: number;
  page: number;
  size: number;
}

export interface WhoAmI {
  username: string;
  subject: string;
  roles: string[];
  permissions: Array<{ obj: string; act: string }>;
}

export interface ApiError {
  code: string;
  message: string;
  traceId: string;
}

export type AuditAction = "LOGIN" | "LOGOUT" | "LOGIN_FAILED" | "ALERT_CLEARED" | "ACCESS_DENIED";
export type Outcome = "SUCCESS" | "DENIED" | "FAILED";
export type ActorType = "USER" | "SYSTEM" | "SERVICE";
export type TargetType = "ALERT" | "AUTH" | "ENDPOINT";

export interface AuditEventView {
  id: string;
  occurredAt: string;
  actor: string;
  actorType: ActorType;
  action: AuditAction;
  targetType: TargetType;
  targetId?: string | null;
  outcome: Outcome;
  source: string;
  clientNet?: string | null;
  traceId?: string | null;
  detail: Record<string, unknown>;
}

export interface AuditEventPage {
  items: AuditEventView[];
  total: number;
  page: number;
  size: number;
  eventsCollected: boolean;
  eventsDisabled: boolean;
  collectorLastRunAt?: string | null;
}

export interface AuditQueryParams {
  from: string;
  to?: string;
  actor?: string;
  action?: AuditAction[];
  outcome?: Outcome[];
  page?: number;
  size?: number;
}

export interface AiFilter {
  scope?: string | null;
  from?: string | null;
  to?: string | null;
  severities: string[];
  sources: string[];
  keyword?: string | null;
  clearedBy?: string | null;
}

export interface AiQueryItem {
  id: string;
  severity: string;
  source: string;
  type: string;
  resourceId: string;
  eventCode: string;
  message: string;
  firstSeenAt: string;
  lastSeenAt: string;
  occurrenceCount: number;
  fingerprint: string;
  clearedAt?: string | null;
  clearedBy?: string | null;
  clearedReason?: string | null;
  clearMode?: string | null;
}

export interface AiQueryResponse {
  filter: AiFilter;
  items: AiQueryItem[];
  total: number;
  page: number;
  size: number;
  explanation: string;
  droppedFields: string[];
}

export interface IngestRequest {
  source: string;
  type: string;
  resourceId: string;
  eventCode?: string;
  severity: string;
  message: string;
  occurredAt: string;
  payload?: Record<string, unknown>;
}

export interface IngestResult {
  id: string;
  created: boolean;
  occurrenceCount: number;
  severity: string;
  lastSeenAt: string;
}

export interface MergeFields {
  source: string;
  type: string;
  resourceId: string;
  eventCode: string;
}

export interface ClearedInfo {
  clearedAt: string;
  clearedBy: string;
  clearedReason?: string | null;
  clearMode: string;
}

export interface AlertEventView {
  id: string;
  occurredAt: string;
  severity: string;
  message: string;
  ingress: string;
}

export interface AlertDetailView {
  id: string;
  status: string;
  severity: string;
  source: string;
  type: string;
  resourceId: string;
  eventCode: string;
  message: string;
  firstSeenAt: string;
  lastSeenAt: string;
  occurrenceCount: number;
  fingerprint: string;
  mergeFields: MergeFields;
  mergeRule: string;
  latestPayload: unknown;
  events: AlertEventView[];
  cleared: ClearedInfo | null;
}