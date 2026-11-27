import React, { useState } from "react";
import { useAiQuery } from "../hooks/useAiQuery";
import { severityClass } from "../../../shared/severity";

interface AiQueryPanelProps {
  accessToken: string | null;
}

const EXAMPLES = [
  "昨天下午有没有支付相关的严重告警",
  "最近7天登录失败",
  "数据库超时的高级别告警",
];

export function AiQueryPanel({ accessToken }: AiQueryPanelProps) {
  const [question, setQuestion] = useState("");
  const mutation = useAiQuery(accessToken);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = question.trim();
    if (!trimmed) return;
    mutation.mutate({ question: trimmed, limit: 20 });
  };

  const fillExample = (text: string) => {
    setQuestion(text);
  };

  return (
    <section className="ai-card" aria-labelledby="ai-query-title">
      <div className="ai-card-header">
        <div>
          <h2 className="section-title" id="ai-query-title">
            AI 自然语言查询
          </h2>
          <p className="section-caption">
            用一句话描述要查的告警，AI 会转换为受控筛选条件（只读，不会修改任何数据）。
          </p>
        </div>
      </div>

      <form className="ai-form" onSubmit={handleSubmit}>
        <input
          className="ai-input"
          type="text"
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          placeholder="例如：昨天下午有没有支付相关的严重告警"
          maxLength={500}
          aria-label="自然语言查询问题"
        />
        <button
          type="submit"
          className="button button-primary"
          disabled={mutation.isPending || !accessToken || !question.trim()}
        >
          {mutation.isPending ? "查询中…" : "查询"}
        </button>
      </form>

      <div className="ai-examples">
        <span className="filter-label">示例：</span>
        {EXAMPLES.map((ex) => (
          <button
            key={ex}
            type="button"
            className="filter-chip"
            onClick={() => fillExample(ex)}
          >
            {ex}
          </button>
        ))}
      </div>

      {mutation.isError && (
        <div className="error-toast" role="alert">
          {mutation.error.message}
        </div>
      )}

      {mutation.data && (
        <div className="ai-result">
          {mutation.data.explanation && (
            <div className="ai-hint">{mutation.data.explanation}</div>
          )}
          {mutation.data.droppedFields.length > 0 && (
            <div className="ai-hint ai-hint-warn">
              已忽略无法映射的字段：{mutation.data.droppedFields.join("、")}
            </div>
          )}

          <details className="ai-filter-details">
            <summary>查看实际使用的筛选条件</summary>
            <div className="detail-grid">
              <div className="detail-field">
                <dt>范围</dt>
                <dd>{mutation.data.filter.scope ?? "—"}</dd>
              </div>
              <div className="detail-field">
                <dt>级别</dt>
                <dd>
                  {mutation.data.filter.severities.length > 0
                    ? mutation.data.filter.severities.join("、")
                    : "—"}
                </dd>
              </div>
              <div className="detail-field">
                <dt>来源</dt>
                <dd>
                  {mutation.data.filter.sources.length > 0
                    ? mutation.data.filter.sources.join("、")
                    : "—"}
                </dd>
              </div>
              <div className="detail-field">
                <dt>关键词</dt>
                <dd>{mutation.data.filter.keyword ?? "—"}</dd>
              </div>
              <div className="detail-field">
                <dt>起</dt>
                <dd className="cell-mono">{mutation.data.filter.from ?? "—"}</dd>
              </div>
              <div className="detail-field">
                <dt>止</dt>
                <dd className="cell-mono">{mutation.data.filter.to ?? "—"}</dd>
              </div>
            </div>
          </details>

          <p className="section-caption">
            共 {mutation.data.total} 条匹配，展示前 {mutation.data.items.length} 条。
          </p>

          <div className="table-scroll" tabIndex={0} aria-label="AI 查询结果列表">
            <table className="alert-table">
              <caption className="sr-only">AI 查询结果</caption>
              <thead>
                <tr>
                  <th scope="col">告警 ID</th>
                  <th scope="col">级别</th>
                  <th scope="col">来源</th>
                  <th scope="col">类型</th>
                  <th scope="col">资源</th>
                  <th scope="col">告警内容</th>
                  <th scope="col">最近发生</th>
                  <th scope="col">发生次数</th>
                </tr>
              </thead>
              <tbody>
                {mutation.data.items.length === 0 ? (
                  <tr>
                    <td colSpan={8} className="empty-state">
                      没有匹配的告警。
                    </td>
                  </tr>
                ) : (
                  mutation.data.items.map((item) => (
                    <tr key={item.fingerprint}>
                      <td className="cell-mono cell-id">{item.id}</td>
                      <td>
                        <span className={`severity-badge ${severityClass(item.severity)}`}>
                          {item.severity}
                        </span>
                      </td>
                      <td className="cell-mono">{item.source}</td>
                      <td className="cell-mono">{item.type}</td>
                      <td className="cell-mono">{item.resourceId}</td>
                      <td className="cell-message">{item.message}</td>
                      <td className="cell-mono">{item.lastSeenAt}</td>
                      <td className="cell-mono">{item.occurrenceCount}</td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </section>
  );
}