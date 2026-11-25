import React from "react";

interface AlertRow {
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

interface AlertsProps {
  items: AlertRow[];
  total: number;
  page: number;
  size: number;
}

export function AlertTable({ items, total, page, size }: AlertsProps) {
  const severityColor = (severity: string): string => {
    switch (severity) {
      case "CRITICAL": return "text-red-600 font-bold";
      case "HIGH": return "text-orange-600";
      case "MEDIUM": return "text-yellow-600";
      case "LOW": return "text-blue-600";
      case "INFO": return "text-gray-600";
      default: return "text-gray-500";
    }
  };

  return (
    <div className="bg-white rounded-lg border border-gray-200 shadow-sm overflow-hidden">
      <div className="px-4 py-3 bg-gray-50 border-b border-gray-200">
        <h2 className="text-lg font-semibold text-gray-900">活动告警</h2>
        <p className="text-sm text-gray-600">共 {total} 条，第 {page + 1} 页，每页 {size}</p>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full text-left text-sm">
          <thead className="bg-gray-100 text-gray-700 uppercase text-xs">
            <tr>
              <th className="px-4 py-2 font-medium">ID</th>
              <th className="px-4 py-2 font-medium">Severity</th>
              <th className="px-4 py-2 font-medium">Source</th>
              <th className="px-4 py-2 font-medium">Type</th>
              <th className="px-4 py-2 font-medium">Resource</th>
              <th className="px-4 py-2 font-medium">Message</th>
              <th className="px-4 py-2 font-medium">Last Seen</th>
              <th className="px-4 py-2 font-medium">Count</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-200">
            {items.length === 0 ? (
              <tr>
                <td colSpan={8} className="px-4 py-8 text-center text-gray-500">
                  当前没有活动告警
                </td>
              </tr>
            ) : (
              items.map((alert) => (
                <tr key={alert.fingerprint} className="hover:bg-gray-50 transition-colors">
                  <td className="px-4 py-3 font-mono text-xs text-gray-900">
                    {alert.id}
                  </td>
                  <td className="px-4 py-3">
                    <span className={`px-2 py-1 rounded-full ${severityColor(alert.severity)}`}>
                      {alert.severity}
                    </span>
                  </td>
                  <td className="px-4 py-3 font-mono text-xs text-gray-600">{alert.source}</td>
                  <td className="px-4 py-3 font-mono text-xs text-gray-600">{alert.type}</td>
                  <td className="px-4 py-3 font-mono text-xs text-gray-600">{alert.resourceId}</td>
                  <td className="px-4 py-3 text-gray-800">{alert.message}</td>
                  <td className="px-4 py-3 font-mono text-xs text-gray-600">{alert.lastSeenAt}</td>
                  <td className="px-4 py-3 font-mono text-xs text-gray-800">{alert.occurrenceCount}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}