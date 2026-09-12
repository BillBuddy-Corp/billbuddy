import axios from 'axios';

import { ENV } from '../config/env';
import { getAccessToken, getRefreshToken, notifySessionExpired, notifyTokensRefreshed } from './tokenHolder';
import { getDeviceId } from '../utils/deviceId';

export const apiClient = axios.create({
  baseURL: ENV.API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

apiClient.interceptors.request.use((config) => {
  const accessToken = getAccessToken();
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`;
  }
  return config;
});

// Access tokens are short-lived (30 minutes) by design; the backend already
// issues a 7-day refresh token at login specifically so the app doesn't have
// to force a fresh login every 30 minutes, so use it. Concurrent requests
// that all 403 around the same moment must share one refresh attempt --
// the refresh endpoint rotates (invalidates) the old refresh token, so a
// second concurrent call using that same now-stale token would itself fail.
let refreshInFlight: Promise<string | null> | null = null;

async function refreshAccessToken(): Promise<string | null> {
  const refreshToken = getRefreshToken();
  if (!refreshToken) return null;
  const deviceId = await getDeviceId();
  // A bare axios call, not apiClient -- this must not go through the
  // request interceptor's (stale) Authorization header or the response
  // interceptor below, which would recurse.
  const { data } = await axios.post(`${ENV.API_BASE_URL}/auth/refreshtoken`, { refreshToken, deviceId });
  notifyTokensRefreshed(data.accessToken, data.refreshToken);
  return data.accessToken as string;
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const original = error.config;
    const status = error.response?.status;
    // The backend has no custom AuthenticationEntryPoint, so an expired or
    // missing token surfaces as a plain 403 (Spring's default), same as a
    // genuine permission error -- there's no way to tell them apart from
    // the status code alone. Retrying a real permission error costs one
    // extra round trip (refresh succeeds, retry 403s again for the real
    // reason, and is not retried a second time); that's the tradeoff for
    // not silently eating every real 403 without an original also existing.
    const isAuthRoute = typeof original?.url === 'string' && original.url.includes('/auth/');
    if ((status === 401 || status === 403) && original && !original._retriedAfterRefresh && !isAuthRoute) {
      original._retriedAfterRefresh = true;
      try {
        refreshInFlight = refreshInFlight ?? refreshAccessToken();
        const newAccessToken = await refreshInFlight;
        refreshInFlight = null;
        if (newAccessToken) {
          original.headers = original.headers ?? {};
          original.headers.Authorization = `Bearer ${newAccessToken}`;
          return apiClient(original);
        }
      } catch {
        refreshInFlight = null;
      }
      // Refresh itself failed (refresh token expired/revoked too) -- there's
      // no recovering this session short of a fresh login.
      notifySessionExpired();
    }
    return Promise.reject(error);
  }
);
