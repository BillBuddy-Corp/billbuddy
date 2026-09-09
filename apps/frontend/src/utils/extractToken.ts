// Accepts either a bare token or a full link (billbuddy://..., exp://..., or
// a plain https:// URL) and returns just the `token` query param value — or
// the trimmed input itself if no `token=` param is found. Avoids the
// built-in URL/URLSearchParams classes, which aren't reliably available in
// React Native without a polyfill.
export function extractToken(input: string): string {
  const trimmed = input.trim();
  const match = trimmed.match(/[?&]token=([^&]+)/);
  if (!match) {
    return trimmed;
  }
  try {
    return decodeURIComponent(match[1]);
  } catch {
    return match[1];
  }
}
