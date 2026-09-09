import * as SecureStore from 'expo-secure-store';
import { create } from 'zustand';

import { getProfile } from '../api/users';
import { setAccessToken } from '../api/tokenHolder';

const ACCESS_TOKEN_KEY = 'billbuddy.accessToken';
const REFRESH_TOKEN_KEY = 'billbuddy.refreshToken';
const USER_KEY = 'billbuddy.user';

export type AuthUser = {
  userId: number;
  email: string;
  fullName: string;
  emailVerified: boolean;
};

type Session = {
  user: Omit<AuthUser, 'emailVerified'>;
  accessToken: string;
  refreshToken: string;
};

type AuthState = {
  user: AuthUser | null;
  accessToken: string | null;
  refreshToken: string | null;
  isHydrated: boolean;
  setSession: (session: Session) => Promise<void>;
  clearSession: () => Promise<void>;
  hydrate: () => Promise<void>;
  setEmailVerified: (emailVerified: boolean) => Promise<void>;
};

async function persistUser(user: AuthUser) {
  await SecureStore.setItemAsync(USER_KEY, JSON.stringify(user));
}

export const useAuthStore = create<AuthState>((set, get) => ({
  user: null,
  accessToken: null,
  refreshToken: null,
  isHydrated: false,

  setSession: async ({ user, accessToken, refreshToken }) => {
    // Set the token first so an authenticated request (the profile fetch
    // below) can actually go out before the store's own state updates.
    setAccessToken(accessToken);

    let emailVerified = false;
    try {
      const profile = await getProfile();
      emailVerified = profile.emailVerified;
    } catch {
      // Non-critical: falls back to "unverified", matching the safer
      // default — a screen relying on this can always re-check later.
    }

    const fullUser: AuthUser = { ...user, emailVerified };
    await Promise.all([
      SecureStore.setItemAsync(ACCESS_TOKEN_KEY, accessToken),
      SecureStore.setItemAsync(REFRESH_TOKEN_KEY, refreshToken),
      persistUser(fullUser),
    ]);
    set({ user: fullUser, accessToken, refreshToken });
  },

  setEmailVerified: async (emailVerified) => {
    const current = get().user;
    if (!current) {
      return;
    }
    const updated: AuthUser = { ...current, emailVerified };
    await persistUser(updated);
    set({ user: updated });
  },

  clearSession: async () => {
    setAccessToken(null);
    await Promise.all([
      SecureStore.deleteItemAsync(ACCESS_TOKEN_KEY),
      SecureStore.deleteItemAsync(REFRESH_TOKEN_KEY),
      SecureStore.deleteItemAsync(USER_KEY),
    ]);
    set({ user: null, accessToken: null, refreshToken: null });
  },

  hydrate: async () => {
    const [accessToken, refreshToken, userJson] = await Promise.all([
      SecureStore.getItemAsync(ACCESS_TOKEN_KEY),
      SecureStore.getItemAsync(REFRESH_TOKEN_KEY),
      SecureStore.getItemAsync(USER_KEY),
    ]);
    setAccessToken(accessToken);
    const user = userJson ? (JSON.parse(userJson) as AuthUser) : null;
    set({
      user,
      accessToken,
      refreshToken,
      isHydrated: true,
    });
  },
}));
