import { useEffect, useRef, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { userManager } from "./auth/userManager";
import {
  KEYCLOAK_ACCOUNT_PASSWORD_URL,
  KEYCLOAK_ACCOUNT_PROFILE_URL,
  KEYCLOAK_ADMIN_CONSOLE_URL,
} from "./auth/keycloakUrls";
import { getWhoami, type WhoAmI } from "./shared/api/apiClient";
import type { User } from "oidc-client-ts";
import { MOCK_WHOAMI } from "./shared/mockData";
import { AlertTable } from "./features/alerts/components/AlertTable";
import { HistoryTable } from "./features/alerts/components/HistoryTable";
import { SeverityDashboard } from "./features/alerts/components/SeverityDashboard";
import { useAlerts, clearAlert, useHistoryAlerts, usePageClamp } from "./features/alerts/hooks/useAlerts";
import { useSeveritySummary } from "./features/alerts/hooks/useSeveritySummary";
import { useMockAlerts, useMockHistoryAlerts } from "./features/alerts/hooks/useMockAlerts";

let authenticationInitialization: Promise<User | null> | undefined;

const PAGE_SIZE = 20;

function initializeAuthentication(): Promise<User | null> {
  authenticationInitialization ??= (async () => {
    if (window.location.pathname === "/callback") {
      await userManager.signinRedirectCallback();
      window.history.replaceState({}, "", "/");
    }
    const storedUser = await userManager.getUser();
    if (!storedUser || storedUser.expired) {
      await userManager.signinRedirect();
      return null;
    }
    return storedUser;
  })();
  return authenticationInitialization;
}

export function App() {
  const isMockMode = import.meta.env.VITE_MOCK_MODE === "true";

  if (isMockMode) {
    return <MockApp />;
  }

  const [user, setUser] = useState<User | null>(null);
  const [ready, setReady] = useState(false);
  const [authError, setAuthError] = useState(false);
  const [activeTab, setActiveTab] = useState<"active" | "history">("active");
  const [activePage, setActivePage] = useState(0);
  const [historyPage, setHistoryPage] = useState(0);
  const [clearMessage, setClearMessage] = useState<string | null>(null);
  useEffect(() => {
    let active = true;
    void initializeAuthentication()
      .then(initializedUser => {
        if (active && initializedUser) {
          setUser(initializedUser);
          setReady(true);
        }
      })
      .catch(() => {
        if (active) setAuthError(true);
      });
    return () => { active = false; };
  }, []);

  const alerts = useAlerts(
    user?.access_token ?? null,
    { page: activePage, size: PAGE_SIZE },
    async () => {
      try {
        const renewedUser = await userManager.signinSilent();
        if (renewedUser && !renewedUser.expired) {
          setUser(renewedUser);
          return renewedUser.access_token;
        }
      } catch {
        // 静默续签失败（如无 SSO 会话），由下方 useEffect 处理跳转
      }
      return null;
    },
    Boolean(user?.access_token),
    activeTab === "active", // 仅活动告警tab激活时轮询
  );

  // token 失效且无法静默续签时，重定向到 Keycloak 重新登录
  useEffect(() => {
    if (alerts.isError && alerts.error?.message === "UNAUTHORIZED") {
      void userManager.signinRedirect();
    }
  }, [alerts.isError, alerts.error]);

  const historyAlerts = useHistoryAlerts(
    user?.access_token ?? null,
    { page: historyPage, size: PAGE_SIZE },
    async () => {
      try {
        const renewedUser = await userManager.signinSilent();
        if (renewedUser && !renewedUser.expired) {
          setUser(renewedUser);
          return renewedUser.access_token;
        }
      } catch {
        // 静默续签失败，由下方 useEffect 处理跳转
      }
      return null;
    },
    Boolean(user?.access_token),
    activeTab === "history", // 仅历史告警tab激活时轮询
  );

  // 顶部实时分布看板：复用 #1，按级别各取一次 size=1 的 total（不改接口契约）
  const severitySummary = useSeveritySummary(
    user?.access_token ?? null,
    Boolean(user?.access_token),
  );

  usePageClamp(activePage, setActivePage, alerts.data);
  usePageClamp(historyPage, setHistoryPage, historyAlerts.data);

  // 切换tab时刷新对应的数据
  const previousTabRef = useRef<"active" | "history">(activeTab);
  useEffect(() => {
    if (previousTabRef.current !== activeTab && user?.access_token) {
      if (activeTab === "active") {
        void alerts.refetch();
      } else if (activeTab === "history") {
        void historyAlerts.refetch();
      }
    }
    previousTabRef.current = activeTab;
  }, [activeTab, user?.access_token]);

  const whoami = useQuery<WhoAmI>({
    queryKey: ["whoami"],
    queryFn: () => getWhoami(user?.access_token ?? ""),
    enabled: ready && Boolean(user?.access_token),
    retry: false,
  });

  const canClear = whoami.data?.permissions.some(
    (p) => p.obj === "alert" && p.act === "clear",
  ) ?? false;

  const handleClear = async (alertId: string, reason?: string | null) => {
    const token = user?.access_token;
    if (!token) return;
    setClearMessage(null);
    const result = await clearAlert(token, alertId, reason ?? null);
    if (result.alreadyCleared) {
      setClearMessage("该告警已被其他用户清除");
      await alerts.refetch();
      await historyAlerts.refetch();
      await severitySummary.refetch();
    } else if (result.success) {
      setClearMessage("告警已清除");
      await alerts.refetch();
      await historyAlerts.refetch();
      await severitySummary.refetch();
    }
  };

  if (authError) {
    return (
      <main className="auth-state">
        <section className="state-card" aria-labelledby="auth-error-title">
          <div className="brand-mark" aria-hidden="true">TD</div>
          <h1 className="state-title" id="auth-error-title">登录失败</h1>
          <p className="state-description">无法完成身份验证，请重新登录。</p>
          <button
            className="button button-primary"
            onClick={() => void userManager.signinRedirect()}
          >
            重新登录
          </button>
        </section>
      </main>
    );
  }

  if (!ready) {
    return (
      <main className="auth-state" aria-live="polite">
        <section className="state-card">
          <div className="brand-mark" aria-hidden="true">TD</div>
          <p className="state-title">正在连接 TriageDeck</p>
          <p className="state-description">正在验证身份并加载告警工作台…</p>
        </section>
      </main>
    );
  }

  if (whoami.isError) {
    if (whoami.error.message === "UNAUTHORIZED") {
      void userManager.removeUser().then(() => userManager.signinRedirect());
    }
    return (
      <main className="auth-state" role="alert">
        <section className="state-card">
          <h1 className="state-title">身份校验失败</h1>
          <p className="state-description">请刷新页面或重新登录后重试。</p>
        </section>
      </main>
    );
  }

  return (
    <main className="app-shell">
      <nav className="topbar" aria-label="主导航">
        <div className="brand-lockup">
          <div className="brand-mark" aria-hidden="true">TD</div>
          <div>
            <h1 className="brand-name">TriageDeck</h1>
            <p className="brand-description">告警分诊台 · 统一审计控制台</p>
          </div>
        </div>
        <div className="account-actions">
          <span className="account-chip">
            <strong>{whoami.data?.username || "user"}</strong>
            {" · "}
            {whoami.data?.roles.join(", ") || "viewer"}
          </span>
          <div className="topbar-account-links">
            <a
              className="button button-secondary"
              href={KEYCLOAK_ACCOUNT_PROFILE_URL}
              target="_blank"
              rel="noreferrer"
            >
              Profile
            </a>
            <a
              className="button button-secondary"
              href={KEYCLOAK_ACCOUNT_PASSWORD_URL}
              target="_blank"
              rel="noreferrer"
            >
              Password
            </a>
            <a
              className="button button-secondary"
              href={KEYCLOAK_ADMIN_CONSOLE_URL}
              target="_blank"
              rel="noreferrer"
            >
              Keycloak Admin console
            </a>
          </div>
          <button
            onClick={() => void userManager.signoutRedirect()}
            className="button button-quiet"
          >
            退出
          </button>
        </div>
      </nav>

      <SeverityDashboard
        summary={severitySummary.data}
        unavailable={severitySummary.isError}
      />

      <section className="dashboard-heading" aria-labelledby="dashboard-title">
        <div>
          <p className="eyebrow">Alert operations</p>
          <h2 className="page-title" id="dashboard-title">告警总览</h2>
          <p className="page-description">集中查看并跟进当前处于活动状态的告警。</p>
        </div>
        <div className="tab-group" role="tablist" aria-label="告警标签页">
          <button
            role="tab"
            aria-selected={activeTab === "active"}
            className={`tab-button ${activeTab === "active" ? "tab-active" : ""}`}
            onClick={() => setActiveTab("active")}
          >
            活动告警
            {alerts.data && <span className="tab-count">{alerts.data.total}</span>}
          </button>
          <button
            role="tab"
            aria-selected={activeTab === "history"}
            className={`tab-button ${activeTab === "history" ? "tab-active" : ""}`}
            onClick={() => setActiveTab("history")}
          >
            历史告警
            {historyAlerts.data && (
              <span className="tab-count">{historyAlerts.data.total}</span>
            )}
          </button>
        </div>
      </section>

      {clearMessage && (
        <div className="clear-message">{clearMessage}</div>
      )}

      {activeTab === "active" ? (
        <AlertTable
          items={alerts.data?.items ?? []}
          total={alerts.data?.total ?? 0}
          page={activePage}
          size={alerts.data?.size ?? PAGE_SIZE}
          canClear={canClear}
          onClear={handleClear}
          onPageChange={setActivePage}
        />
      ) : (
        <HistoryTable
          items={historyAlerts.data?.items ?? []}
          total={historyAlerts.data?.total ?? 0}
          page={historyPage}
          size={historyAlerts.data?.size ?? PAGE_SIZE}
          onPageChange={setHistoryPage}
        />
      )}

      {whoami.data && (
        <section className="profile-card" aria-labelledby="profile-title">
          <div className="profile-heading">
            <div>
              <h2 className="section-title" id="profile-title">当前会话</h2>
              <p className="profile-caption">已验证的身份与授权信息</p>
            </div>
          </div>
          <dl className="profile-grid">
            <div className="profile-item">
              <dt>用户名</dt>
              <dd>{whoami.data.username}</dd>
            </div>
            <div className="profile-item">
              <dt>角色</dt>
              <dd>{whoami.data.roles.join(", ") || "无"}</dd>
            </div>
            <div className="profile-item profile-item-wide">
              <dt>权限</dt>
              <dd>
                {whoami.data.permissions.map(({ obj, act }) => `${obj}:${act}`).join(", ")}
              </dd>
            </div>
          </dl>
        </section>
      )}
    </main>
  );
}

/**
 * VITE_MOCK_MODE=true 时的静态演示界面。
 * 跳过 Keycloak 认证，直接使用内存模拟数据渲染告警列表、分页、严重度看板与会话卡片，
 * 适合 Cloudflare Pages 等纯静态托管场景下的离线展示。
 */
function MockApp() {
  const PAGE_SIZE = 20;
  const [activeTab, setActiveTab] = useState<"active" | "history">("active");
  const [activePage, setActivePage] = useState(0);
  const [historyPage, setHistoryPage] = useState(0);
  const [clearMessage, setClearMessage] = useState<string | null>(null);

  const activeData = useMockAlerts(activePage, PAGE_SIZE);
  const historyData = useMockHistoryAlerts(historyPage, PAGE_SIZE);

  // 按级别统计（与真实 SeveritySummary 结构一致）
  const severitySummary = activeData.items.reduce<Record<string, number>>(
    (acc, alert) => {
      acc[alert.severity] = (acc[alert.severity] ?? 0) + 1;
      return acc;
    },
    {} as Record<string, number>,
  );

  const canClear = false;

  const handleClear = async () => {
    setClearMessage("模拟模式下不可执行清除操作");
    setTimeout(() => setClearMessage(null), 3000);
  };

  usePageClamp(activePage, setActivePage, activeData);
  usePageClamp(historyPage, setHistoryPage, historyData);

  return (
    <main className="app-shell">
      <nav className="topbar" aria-label="主导航">
        <div className="brand-lockup">
          <div className="brand-mark" aria-hidden="true">TD</div>
          <div>
            <h1 className="brand-name">TriageDeck</h1>
            <p className="brand-description">告警分诊台 · 统一审计控制台</p>
          </div>
        </div>
        <div className="account-actions">
          <span className="account-chip">
            <strong>{MOCK_WHOAMI.username}</strong>
            {" · "}
            {MOCK_WHOAMI.roles.join(", ") || "viewer"}
          </span>
          <span className="mock-badge">模拟演示模式</span>
        </div>
      </nav>

      <SeverityDashboard
        summary={{
          counts: {
            CRITICAL: severitySummary["CRITICAL"] ?? 0,
            HIGH: severitySummary["HIGH"] ?? 0,
            MEDIUM: severitySummary["MEDIUM"] ?? 0,
            LOW: severitySummary["LOW"] ?? 0,
            INFO: severitySummary["INFO"] ?? 0,
          },
          total: activeData.total,
        }}
        unavailable={false}
      />

      <section className="dashboard-heading" aria-labelledby="dashboard-title">
        <div>
          <p className="eyebrow">Alert operations</p>
          <h2 className="page-title" id="dashboard-title">告警总览（模拟数据）</h2>
          <p className="page-description">
            本数据为静态模拟数据，用于演示与静态展示，不接任何后端服务。
          </p>
        </div>
        <div className="tab-group" role="tablist" aria-label="告警标签页">
          <button
            role="tab"
            aria-selected={activeTab === "active"}
            className={`tab-button ${activeTab === "active" ? "tab-active" : ""}`}
            onClick={() => setActiveTab("active")}
          >
            活动告警
            <span className="tab-count">{activeData.total}</span>
          </button>
          <button
            role="tab"
            aria-selected={activeTab === "history"}
            className={`tab-button ${activeTab === "history" ? "tab-active" : ""}`}
            onClick={() => setActiveTab("history")}
          >
            历史告警
            <span className="tab-count">{historyData.total}</span>
          </button>
        </div>
      </section>

      {clearMessage && <div className="clear-message">{clearMessage}</div>}

      {activeTab === "active" ? (
        <AlertTable
          items={activeData.items}
          total={activeData.total}
          page={activePage}
          size={activeData.size}
          canClear={canClear}
          onClear={handleClear}
          onPageChange={setActivePage}
        />
      ) : (
        <HistoryTable
          items={historyData.items}
          total={historyData.total}
          page={historyPage}
          size={historyData.size}
          onPageChange={setHistoryPage}
        />
      )}

      <section className="profile-card" aria-labelledby="profile-title">
        <div className="profile-heading">
          <div>
            <h2 className="section-title" id="profile-title">当前会话</h2>
            <p className="profile-caption">模拟演示身份（未接 Keycloak）</p>
          </div>
        </div>
        <dl className="profile-grid">
          <div className="profile-item">
            <dt>用户名</dt>
            <dd>{MOCK_WHOAMI.username}</dd>
          </div>
          <div className="profile-item">
            <dt>角色</dt>
            <dd>{MOCK_WHOAMI.roles.join(", ") || "无"}</dd>
          </div>
          <div className="profile-item profile-item-wide">
            <dt>权限</dt>
            <dd>
              {MOCK_WHOAMI.permissions.map(({ obj, act }) => `${obj}:${act}`).join(", ")}
            </dd>
          </div>
        </dl>
      </section>
    </main>
  );
}
