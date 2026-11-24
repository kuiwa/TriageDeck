import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { userManager } from "./auth/userManager";
import { getWhoami, type WhoAmI } from "./shared/api/apiClient";
import type { User } from "oidc-client-ts";

export function App() {
  const [user, setUser] = useState<User | null>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    const onLoad = async () => {
      if (window.location.pathname === "/callback") {
        await userManager.signinRedirectCallback();
        window.history.replaceState({}, "", "/");
      }
      const storedUser = await userManager.getUser();
      if (!storedUser || storedUser.expired) {
        await userManager.signinRedirect();
        return;
      }
      setUser(storedUser);
      setReady(true);
    };
    void onLoad();
  }, []);

  const whoami = useQuery<WhoAmI>({
    queryKey: ["whoami"],
    queryFn: () => getWhoami(user?.access_token ?? ""),
    enabled: ready && Boolean(user?.access_token),
    retry: false
  });

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
    <main>
      <h1>TriageDeck</h1>
      {whoami.data && (
        <section>
          <p>用户名：{whoami.data.username}</p>
          <p>角色：{whoami.data.roles.join(", ") || "无"}</p>
          <p>权限：{whoami.data.permissions.map(({ obj, act }) => `${obj}:${act}`).join(", ")}</p>
        </section>
      )}
      <button onClick={() => void userManager.signoutRedirect()}>退出登录</button>
    </main>
  );
}
