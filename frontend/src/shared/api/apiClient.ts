export interface WhoAmI {
  username: string;
  subject: string;
  roles: string[];
  permissions: Array<{ obj: string; act: string }>;
}

export async function getWhoami(accessToken: string): Promise<WhoAmI> {
  const response = await fetch("/api/whoami", {
    headers: { Authorization: `Bearer ${accessToken}` }
  });
  if (response.status === 401) {
    throw new Error("UNAUTHORIZED");
  }
  if (!response.ok) {
    throw new Error("WHOAMI_FAILED");
  }
  return response.json() as Promise<WhoAmI>;
}
