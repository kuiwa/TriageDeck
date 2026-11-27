import { useQuery } from "@tanstack/react-query";
import { SEVERITY_ORDER, type Severity } from "../../../shared/severity";
import { fetchAlerts } from "./useAlerts";

export interface SeveritySummary {
  /** 每个严重级别的活动告警数量，键顺序见 SEVERITY_ORDER */
  counts: Record<Severity, number>;
  /** 活动告警总数（各级别之和） */
  total: number;
}

/**
 * 当前活动告警按严重级别的数量（顶部实时看板）。
 *
 * 复用 `#1 GET /api/alerts/active`：按 `severity` 各查一次 `size=1`，只读 `total`；
 * 不新增接口、不改契约（见 docs/03-scope-v1.md US-B8）。
 * 轮询 10 秒，页面不可见时 TanStack Query 自动停止后台刷新。
 */
export function useSeveritySummary(
  accessToken: string | null,
  enabled: boolean = true,
) {
  return useQuery({
    queryKey: ["alerts", "severity-summary"],
    queryFn: async (): Promise<SeveritySummary> => {
      const pages = await Promise.all(
        SEVERITY_ORDER.map((severity) => fetchAlerts(accessToken, { severity, size: 1 })),
      );

      const counts: Record<Severity, number> = {
        CRITICAL: 0,
        HIGH: 0,
        MEDIUM: 0,
        LOW: 0,
        INFO: 0,
      };
      SEVERITY_ORDER.forEach((severity, index) => {
        counts[severity] = pages[index].total;
      });

      const total = SEVERITY_ORDER.reduce((sum, severity) => sum + counts[severity], 0);
      return { counts, total };
    },
    enabled: !!accessToken && enabled,
    staleTime: 10_000,
    refetchInterval: 10_000,
    // 刷新期间保留上一次计数，避免数字闪成占位符
    placeholderData: (previousData) => previousData,
  });
}
