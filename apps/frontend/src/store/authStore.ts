import * as SecureStore from 'expo-secure-store';
import { create } from 'zustand';

const ACCESS_TOKEN_KEY = 'billbuddy.accessToken';
const REFRESH_TOKEN_KEY = 'billbuddy.refreshToken';
const USER_KEY = 'billbuddy.user';

export type AuthUser = {
  userId: number;
  email: string;
  fullName: string;
};

type Session = {
  user: AuthUser;
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
};

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  accessToken: null,
  refreshToken: null,
  isHydrated: false,

  setSession: async ({ user, accessToken, refreshToken }) => {
    await Promise.all([
      SecureStore.setItemAsync(ACCESS_TOKEN_KEY, accessToken),
      SecureStore.setItemAsync(REFRESH_TOKEN_KEY, refreshToken),
      SecureStore.setItemAsync(USER_KEY, JSON.stringify(user)),
    ]);
    set({ user, accessToken, refreshToken });
  },

  clearSession: async () => {
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
    const user = userJson ? (JSON.parse(userJson) as AuthUser) : null;
    set({
      user,
      accessToken,
      refreshToken,
      isHydrated: true,
    });
  },
}));
