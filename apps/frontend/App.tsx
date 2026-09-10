import './global.css';

import { DarkTheme, NavigationContainer, LinkingOptions } from '@react-navigation/native';
import * as Linking from 'expo-linking';
import { StatusBar } from 'expo-status-bar';
import { useEffect } from 'react';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { RootNavigator } from './src/navigation/RootNavigator';
import { useAuthStore } from './src/store/authStore';

const linking: LinkingOptions<Record<string, unknown>> = {
  prefixes: [Linking.createURL('/'), 'billbuddy://'],
  config: {
    screens: {
      ResetPassword: 'reset-password',
      VerifyEmail: 'verify-email',
      JoinGroup: 'join-group',
    },
  },
};

// The app is dark-theme-only (no light/dark toggle), so the navigator's own
// theme is fixed to a dark palette too -- otherwise the brief background
// shown during screen transitions/gestures defaults to white.
const navigationTheme = {
  ...DarkTheme,
  colors: {
    ...DarkTheme.colors,
    primary: '#2F6FED',
    background: '#0D0D0D',
    card: '#0D0D0D',
    border: '#2C2C2E',
    text: '#F5F5F7',
  },
};

export default function App() {
  const hydrate = useAuthStore((state) => state.hydrate);

  useEffect(() => {
    hydrate();
  }, [hydrate]);

  return (
    <SafeAreaProvider>
      <NavigationContainer linking={linking} theme={navigationTheme}>
        <RootNavigator />
      </NavigationContainer>
      <StatusBar style="light" />
    </SafeAreaProvider>
  );
}
