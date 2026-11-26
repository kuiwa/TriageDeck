import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { userManager } from "./auth/userManager";
import { getWhoami, type WhoAmI } from "./shared/api/apiClient";
import type { User } from "oidc-client-ts";
import { AlertTable } from "./features/alerts/components/AlertTable";
import { useAlerts } from "./features/alerts/hooks/useAlerts";
import { getAccessToken } from "./features/auth/hooks/useAuth";
import type { AlertView } from "./shared/api/types";

let authenticationInitialization: Promise<User | null> | undefined;

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
  const [user, setUser] = useState<User | null>(null);
  const [ready, setReady] = useState(false);
  const [authError, setAuthError] = useState(false);
  const [alertsData, setAlertsData] = useState<{
    items: AlertView[];
    total: number;
    page: number;
    size: number;
  } | null>(null);

  useEffect(() => {
    let active = true;
    void initializeAuthentication()
      .then(initializedUser => {
        if (active && initializedUser) {
          setUser(initializedUser);
          setReady(true);
          void fetchAlerts();
        }
      })
      .catch(() => {
        if (active) setAuthError(true);
      });
    return () => { active = false; };
  }, []);

  async function fetchAlerts() {
    if (!user) return;
    const token = user.access_token;
    const response = await fetch("/api/alerts/active?page=0&size=20", {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!response.ok) throw new Error("Failed to fetch alerts");
    const data = await response.json();
    setAlertsData(data);
  }

  const whoami = useQuery<WhoAmI>({
    queryKey: ["whoami"],
    queryFn: () => getWhoami(user?.access_token ?? ""),
    enabled: ready && Boolean(user?.access_token),
    retry: false,
  });

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
          <button
            onClick={() => void userManager.signoutRedirect()}
            className="button button-quiet"
          >
            退出
          </button>
        </div>
      </nav>

      <section className="dashboard-heading" aria-labelledby="dashboard-title">
        <div>
          <p className="eyebrow">Alert operations</p>
          <h2 className="page-title" id="dashboard-title">告警总览</h2>
          <p className="page-description">集中查看并跟进当前处于活动状态的告警。</p>
        </div>
        <span className="live-indicator">活动告警</span>
      </section>

      <AlertTable
        items={alertsData?.items ?? []}
        total={alertsData?.total ?? 0}
        page={alertsData?.page ?? 0}
        size={alertsData?.size ?? 20}
      />

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
