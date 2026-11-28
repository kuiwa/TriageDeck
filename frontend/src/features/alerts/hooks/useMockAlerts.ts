import { useMemo } from "react";
import type { AlertHistoryView, AlertView } from "../../../shared/api/types";
import { MOCK_ALERTS } from "../../../shared/mockData";
import { MOCK_HISTORY_ALERTS } from "../../../shared/mockData";

export interface MockAlertsResult {
  items: AlertView[];
  total: number;
  page: number;
  size: number;
}

export interface MockHistoryAlertsResult {
  items: AlertHistoryView[];
  total: number;
  page: number;
  size: number;
}

/**
 * 模拟活动告警分页数据。返回结构与 useAlerts 完全一致，供 VITE_MOCK_MODE 使用。
 * 仅做内存分页，不联网。
 */
export function useMockAlerts(page: number, size: number): MockAlertsResult {
  return useMemo(() => {
    const total = MOCK_ALERTS.length;
    const start = page * size;
    const items = start >= total ? [] : MOCK_ALERTS.slice(start, start + size);
    return { items, total, page, size };
  }, [page, size]);
}

/**
 * 模拟历史告警分页数据。返回结构与 useHistoryAlerts 完全一致。
 */
export function useMockHistoryAlerts(page: number, size: number): MockHistoryAlertsResult {
  return useMemo(() => {
    const total = MOCK_HISTORY_ALERTS.length;
    const start = page * size;
    const items = start >= total ? [] : MOCK_HISTORY_ALERTS.slice(start, start + size);
    return { items, total, page, size };
  }, [page, size]);
}
