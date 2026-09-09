// A plain module-level holder for the current access token, kept in sync by
// authStore. Exists so api/client.ts doesn't have to import the store
// directly (which would create a require cycle, since the store needs the
// api layer too, to fetch the profile after establishing a session).
let currentAccessToken: string | null = null;

export function setAccessToken(token: string | null) {
  currentAccessToken = token;
}

export function getAccessToken(): string | null {
  return currentAccessToken;
}
