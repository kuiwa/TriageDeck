import { useMutation } from "@tanstack/react-query";
import type { IngestRequest, IngestResult } from "../../../shared/api/types";
import { ingestAlert } from "../../../shared/api/apiClient";

/**
 * 告警上报（POST /api/alerts/ingest），由用户提交表单触发，用 useMutation。
 */
export function useIngestAlert(accessToken: string | null) {
  return useMutation<IngestResult, Error, IngestRequest>({
    mutationFn: async (request) => {
      if (!accessToken) throw new Error("NO_TOKEN");
      return await ingestAlert(accessToken, request);
    },
  });
}