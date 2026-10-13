export type User = {
  id: string;
  username: string;
  displayName: string;
  role?: string;
};

const TOKEN_KEY = "prd-workbench-token";
const USER_KEY = "prd-workbench-user";

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || "";
}

export function getUser(): User | null {
  const raw = localStorage.getItem(USER_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as User;
  } catch {
    return null;
  }
}

export function saveSession(token: string, user: User) {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(USER_KEY, JSON.stringify(user));
}

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}

export function storageKey(suffix: string, userId = getUser()?.id) {
  return userId ? `prd-workbench-${suffix}:${userId}` : `prd-workbench-${suffix}`;
}
