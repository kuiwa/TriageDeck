import {
  InMemoryWebStorage,
  UserManager,
  WebStorageStateStore
} from "oidc-client-ts";

export const userManager = new UserManager({
  authority: `${import.meta.env.VITE_KEYCLOAK_URL ?? "http://localhost:8081"}/realms/triagedeck`,
  client_id: "triagedeck-spa",
  redirect_uri: `${window.location.origin}/callback`,
  post_logout_redirect_uri: window.location.origin,
  response_type: "code",
  scope: "openid profile email",
  userStore: new WebStorageStateStore({ store: new InMemoryWebStorage() }),
  automaticSilentRenew: true
});
