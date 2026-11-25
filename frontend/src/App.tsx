import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { userManager } from "./auth/userManager";
import { getWhoami, type WhoAmI } from "./shared/api/apiClient";
import type { User } from "oidc-client-ts";
import { AlertTable } from "./features/alerts/components/AlertTable";
import { useAlerts } from "./features/alerts/hooks/useAlerts";
import { getAccessToken } from "./features/auth/hooks/useAuth";

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
    items: any[];
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
      <main>
        <p>登录失败，请重新登录。</p>
        <button onClick={() => void userManager.signinRedirect()}>重新登录</button>
      </main>
    );
  }

  if (!ready) {
    return <main>TriageDeck 登录中...</main>;
  }

  if (whoami.isError) {
    if (whoami.error.message === "UNAUTHORIZED") {
      void userManager.removeUser().then(() => userManager.signinRedirect());
    }
    return <main>身份校验失败</main>;
  }

  return (
    <main className="max-w-7xl mx-auto px-6 py-8">
      <nav className="flex justify-between items-center mb-6 border-b border-gray-200 pb-4">
        <div>
          <h1 className="text-xl font-bold text-gray-900">TriageDeck</h1>
          <p className="text-sm text-gray-500">
            告警分诊台 - 统一审计控制台
          </p>
        </div>
        <div className="flex items-center gap-4">
          <span className="text-sm text-gray-600">
            {whoami.data?.username || "user"} (
            {whoami.data?.roles.join(", ") || "viewer"})
          </span>
          <button
            onClick={() => void userManager.signoutRedirect()}
            className="text-sm text-red-600 hover:text-red-800 underline"
          >
            退出
          </button>
        </div>
      </nav>

      <AlertTable
        items={alertsData?.items ?? []}
        total={alertsData?.total ?? 0}
        page={alertsData?.page ?? 0}
        size={alertsData?.size ?? 20}
      />

      {whoami.data && (
        <section className="mt-8 p-4 bg-gray-50 rounded-lg border border-gray-200">
          <h3 className="text-sm font-medium text-gray-600 mb-2">用户信息</h3>
          <p className="text-sm text-gray-800">用户名：{whoami.data.username}</p>
          <p className="text-sm text-gray-800">角色：{whoami.data.roles.join(", ") || "无"}</p>
          <p className="text-sm text-gray-800">
            权限：{whoami.data.permissions.map(({ obj, act }) => `${obj}:${act}`).join(", ")}
          </p>
        </section>
      )}
    </main>
  );
}
