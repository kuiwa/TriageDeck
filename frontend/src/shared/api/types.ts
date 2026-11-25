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