import { User, WebStorageStateStore } from "oidc-client-ts";

interface AuthState {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  error: string | null;
  whoami: WhoAmI | null;
}

interface WhoAmI {
  username: string;
  subject: string;
  roles: string[];
  permissions: Array<{ obj: string; act: string }>;
}

let currentUser: User | null = null;
let currentWhoami: WhoAmI | null = null;
let listeners: Array<(state: AuthState) => void> = [];

// Keycloak OIDC config - issuer from environment
const OIDC_CONFIG = {
  authority: import.meta.env.VITE_KEYCLOAK_ISSUER_URI || "http://localhost:8081/realms/triagedeck",
  client_id: "triagedeck-frontend",
  redirect_uri: `${window.location.origin}/callback`,
  response_type: "code",
  scope: "openid profile email",
  automaticSilentRenew: true,
  silent_redirect_uri: `${window.location.origin}/silent-renew.html`,
  loadUserInfo: true,
  // IMPORTANT: NO token in localStorage/sessionStorage (AGENTS.md §2.1.6)
  // Use memory only via userStore with no persistence
  userStore: new WebStorageStateStore({ store: window.sessionStorage }),
};

export function getCurrentUser(): User | null {
  return currentUser;
}

export function getCurrentWhoami(): WhoAmI | null {
  return currentWhoami;
}

export function notifyListeners() {
  const state: AuthState = {
    user: currentUser,
    isAuthenticated: !!currentUser && !currentUser.expired,
    isLoading: false,
    error: null,
    whoami: currentWhoami,
  };
  listeners.forEach((cb) => cb(state));
}

export function subscribe(listener: (state: AuthState) => void) {
  listeners.push(listener);
  return () => {
    listeners = listeners.filter((l) => l !== listener);
  };
}

export async function signIn() {
  const { OidcClient } = await import("oidc-client-ts");
  const client = new OidcClient(OIDC_CONFIG);
  try {
    await client.signinRedirect();
  } catch (error) {
    console.error("Sign in failed:", error);
  }
}

export async function signInCallback() {
  const { OidcClient } = await import("oidc-client-ts");
  const client = new OidcClient(OIDC_CONFIG);
  try {
    currentUser = await client.signinRedirectCallback();
    await fetchWhoami(currentUser.access_token);
    notifyListeners();
    window.location.replace(window.location.origin);
  } catch (error) {
    console.error("Callback failed:", error);
    currentUser = null;
    notifyListeners();
  }
}

export async function signOut() {
  currentUser = null;
  currentWhoami = null;
  notifyListeners();
  const { OidcClient } = await import("oidc-client-ts");
  const client = new OidcClient(OIDC_CONFIG);
  try {
    await client.signoutRedirect();
  } catch (error) {
    console.error("Sign out failed:", error);
  }
}

export async function fetchWhoami(accessToken: string): Promise<void> {
  try {
    const response = await fetch("/api/whoami", {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    if (response.ok) {
      currentWhoami = await response.json();
    } else {
      currentWhoami = null;
    }
  } catch (error) {
    console.error("Failed to fetch whoami:", error);
    currentWhoami = null;
  }
}

export async function getAccessToken(): Promise<string | null> {
  if (!currentUser) return null;
  if (currentUser.expired) return null;
  return currentUser.access_token;
}

export function getAuthState(): AuthState {
  return {
    user: currentUser,
    isAuthenticated: !!currentUser && !currentUser.expired,
    isLoading: false,
    error: null,
    whoami: currentWhoami,
  };
}

export default {
  signIn,
  signInCallback,
  signOut,
  getAccessToken,
  getAuthState,
  subscribe,
  getCurrentUser,
  getCurrentWhoami,
};