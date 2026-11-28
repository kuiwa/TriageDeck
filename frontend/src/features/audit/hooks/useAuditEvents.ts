import { useQuery } from "@tanstack/react-query";
import type { AuditEventPage, AuditQueryParams } from "../../../shared/api/types";
import { getAuditEvents } from "../../../shared/api/apiClient";

export function useAuditEvents(
  accessToken: string | null,
  params: AuditQueryParams,
  enabled: boolean = true
) {
  return useQuery<AuditEventPage>({
    queryKey: ["audit", "events", params],
    queryFn: async () => {
      if (!accessToken) throw new Error("NO_TOKEN");
      return await getAuditEvents(accessToken, params);
    },
    enabled: !!accessToken && enabled,
    staleTime: 10_000,
  });
}
