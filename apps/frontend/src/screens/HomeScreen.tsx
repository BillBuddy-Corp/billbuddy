import { useNavigation } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useState } from 'react';
import { Pressable, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { logout } from '../api/auth';
import { Button } from '../components/atoms/Button';
import { Logo } from '../components/atoms/Logo';
import { RootStackParamList } from '../navigation/RootNavigator';
import { useAuthStore } from '../store/authStore';
import { getDeviceId } from '../utils/deviceId';

type Navigation = NativeStackNavigationProp<RootStackParamList, 'Home'>;

export function HomeScreen() {
  const navigation = useNavigation<Navigation>();
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
      navigation.reset({ index: 0, routes: [{ name: 'Login' }] });
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

        {user && !user.emailVerified ? (
          <Pressable onPress={() => navigation.navigate('VerifyEmail', {})} className="mt-2">
            <Text className="text-xs text-primary">Verify your email</Text>
          </Pressable>
        ) : null}

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
