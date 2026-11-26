import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  AlertHistoryView,
  AlertView,
} from "../../../shared/api/types";

interface AlertsQueryParams {
  page?: number;
  size?: number;
  severity?: string;
  source?: string;
  keyword?: string;
  from?: string;
  to?: string;
}

async function fetchAlerts(
  accessToken: string | null,
  params: AlertsQueryParams = {},
): Promise<{ items: AlertView[]; total: number; page: number; size: number }> {
  const searchParams = new URLSearchParams();
  if (params.page !== undefined) searchParams.set("page", String(params.page));
  if (params.size !== undefined) searchParams.set("size", String(params.size));
  if (params.severity) searchParams.set("severity", params.severity);
  if (params.source) searchParams.set("source", params.source);
  if (params.keyword) searchParams.set("keyword", params.keyword);
  if (params.from) searchParams.set("from", params.from);
  if (params.to) searchParams.set("to", params.to);

  const response = await fetch(`/api/alerts/active?${searchParams}`, {
    headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {},
  });

  if (response.status === 401) throw new Error("UNAUTHORIZED");
  if (!response.ok) throw new Error("FETCH_ALERTS_FAILED");

  return response.json();
}

export interface ClearAlertResult {
  success: boolean;
  alreadyCleared: boolean;
}

export async function clearAlert(
  accessToken: string,
  alertId: string,
  reason?: string | null,
): Promise<ClearAlertResult> {
  const searchParams = new URLSearchParams();
  if (reason) searchParams.set("reason", reason);

  const response = await fetch(`/api/alerts/${alertId}/clear?${searchParams}`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${accessToken}`,
    },
  });

  if (response.status === 401) throw new Error("UNAUTHORIZED");
  if (response.status === 409) {
    return { success: false, alreadyCleared: true };
  }
  if (!response.ok) throw new Error("CLEAR_ALERT_FAILED");

  return { success: true, alreadyCleared: false };
}

async function fetchHistoryAlerts(
  accessToken: string | null,
  params: AlertsQueryParams = {},
): Promise<{
  items: AlertHistoryView[];
  total: number;
  page: number;
  size: number;
}> {
  const searchParams = new URLSearchParams();
  if (params.page !== undefined) searchParams.set("page", String(params.page));
  if (params.size !== undefined) searchParams.set("size", String(params.size));
  if (params.severity) searchParams.set("severity", params.severity);
  if (params.source) searchParams.set("source", params.source);
  if (params.keyword) searchParams.set("keyword", params.keyword);
  if (params.from) searchParams.set("from", params.from);
  if (params.to) searchParams.set("to", params.to);

  const response = await fetch(`/api/alerts/history?${searchParams}`, {
    headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {},
  });

  if (response.status === 401) throw new Error("UNAUTHORIZED");
  if (!response.ok) throw new Error("FETCH_HISTORY_ALERTS_FAILED");

  return response.json();
}

export function useAlerts(
  accessToken: string | null,
  params: AlertsQueryParams = {},
  onUnauthorized?: () => Promise<string | null>,
  enabled: boolean = true,
) {
  return useQuery({
    queryKey: ["alerts", "active", params],
    queryFn: async () => {
      try {
        return await fetchAlerts(accessToken, params);
      } catch (error) {
        if (error instanceof Error && error.message === "UNAUTHORIZED" && onUnauthorized) {
          const newToken = await onUnauthorized();
          if (newToken) {
            return await fetchAlerts(newToken, params);
          }
        }
        throw error;
      }
    },
    enabled: !!accessToken && enabled,
    staleTime: 10_000,
    refetchInterval: enabled ? 30_000 : false,
  });
}

export function useHistoryAlerts(
  accessToken: string | null,
  params: AlertsQueryParams = {},
  onUnauthorized?: () => Promise<string | null>,
  enabled: boolean = true,
) {
  const queryClient = useQueryClient();

  return useQuery({
    queryKey: ["alerts", "history", params],
    queryFn: async () => {
      try {
        return await fetchHistoryAlerts(accessToken, params);
      } catch (error) {
        if (error instanceof Error && error.message === "UNAUTHORIZED" && onUnauthorized) {
          const newToken = await onUnauthorized();
          if (newToken) {
            return await fetchHistoryAlerts(newToken, params);
          }
        }
        throw error;
      }
    },
    enabled: !!accessToken && enabled,
    staleTime: 10_000,
    refetchInterval: enabled ? 30_000 : false,
  });
}