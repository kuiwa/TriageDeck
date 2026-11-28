import { useMutation } from "@tanstack/react-query";
import type { AiQueryResponse } from "../../../shared/api/types";
import { aiQuery } from "../../../shared/api/apiClient";

interface AiQueryVariables {
  question: string;
  limit?: number;
}

/**
 * AI 自然语言查询（POST），由用户提交触发，用 useMutation。
 */
export function useAiQuery(accessToken: string | null) {
  return useMutation<AiQueryResponse, Error, AiQueryVariables>({
    mutationFn: async ({ question, limit }) => {
      if (!accessToken) throw new Error("NO_TOKEN");
      return await aiQuery(accessToken, question, limit);
    },
  });
}