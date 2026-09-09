import { useState } from 'react';
import { Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { logout } from '../api/auth';
import { Button } from '../components/atoms/Button';
import { Logo } from '../components/atoms/Logo';
import { useAuthStore } from '../store/authStore';
import { getDeviceId } from '../utils/deviceId';

export function HomeScreen() {
  const user = useAuthStore((state) => state.user);
  const refreshToken = useAuthStore((state) => state.refreshToken);
  const clearSession = useAuthStore((state) => state.clearSession);
  const [loggingOut, setLoggingOut] = useState(false);

  const handleLogout = async () => {
    setLoggingOut(true);
    try {
      if (refreshToken) {
        const deviceId = await getDeviceId();
        await logout({ refreshToken, deviceId });
      }
    } catch {
      // Best-effort: the user can still log out locally even if the
      // revoke call fails (offline, expired token, etc).
    } finally {
      await clearSession();
      setLoggingOut(false);
    }
  };

  return (
    <SafeAreaView className="flex-1 bg-white">
      <View className="flex-1 items-center justify-center px-6">
        <Logo />
        <Text className="mt-4 text-lg font-medium text-black">
          Welcome, {user?.fullName ?? 'there'}
        </Text>
        <Text className="mt-1 text-sm text-gray-500">{user?.email}</Text>

        <View className="mt-8 w-full max-w-xs">
          <Button
            label="Log out"
            variant="secondary"
            onPress={handleLogout}
            loading={loggingOut}
          />
        </View>
      </View>
    </SafeAreaView>
  );
}
