// Plain module-level holders for the current session's tokens, kept in sync
// by authStore, plus a couple of callback slots. Exists so api/client.ts
// doesn't have to import the store directly (which would create a require
// cycle, since the store needs the api layer too, to fetch the profile
// after establishing a session).
let currentAccessToken: string | null = null;
let currentRefreshToken: string | null = null;

export function setAccessToken(token: string | null) {
  currentAccessToken = token;
}

export function getAccessToken(): string | null {
  return currentAccessToken;
}

export function setRefreshToken(token: string | null) {
  currentRefreshToken = token;
}

export function getRefreshToken(): string | null {
  return currentRefreshToken;
}

// authStore registers these once at startup, so client.ts's response
// interceptor can hand a freshly-rotated token pair back for authStore to
// persist, or signal "the refresh token itself is dead, log out" -- without
// ever importing the store.
let onTokensRefreshed: ((accessToken: string, refreshToken: string) => void) | null = null;
let onSessionExpired: (() => void) | null = null;

export function setOnTokensRefreshed(handler: (accessToken: string, refreshToken: string) => void) {
  onTokensRefreshed = handler;
}

export function notifyTokensRefreshed(accessToken: string, refreshToken: string) {
  currentAccessToken = accessToken;
  currentRefreshToken = refreshToken;
  onTokensRefreshed?.(accessToken, refreshToken);
}

export function setOnSessionExpired(handler: () => void) {
  onSessionExpired = handler;
}

export function notifySessionExpired() {
  onSessionExpired?.();
}
