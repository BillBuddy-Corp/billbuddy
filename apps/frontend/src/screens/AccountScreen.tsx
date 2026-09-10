import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useState } from 'react';
import { ActivityIndicator, Alert, Pressable, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { logout } from '../api/auth';
import { RootStackParamList } from '../navigation/RootNavigator';
import { useAuthStore } from '../store/authStore';
import { avatarColor } from '../utils/avatarColor';
import { getDeviceId } from '../utils/deviceId';

// Navigating to VerifyEmail (a root-level screen) works via plain
// navigate() bubbling up through the tab navigator automatically. Logout
// does NOT use an explicit reset here on purpose: this screen is nested
// inside MainTabs, so a reset() called on this local navigation object
// would only reset the tab navigator, not the root stack. clearSession()
// flips hasSession false, which removes MainTabs from the root stack's
// conditional screens entirely — React Navigation's documented behavior
// for this pattern is to fall back to the newly-available default (Login)
// automatically, regardless of how deeply the removed screen was nested.
type Navigation = NativeStackNavigationProp<RootStackParamList, 'MainTabs'>;

function PreferenceRow({
  icon,
  label,
  onPress,
  loading,
  destructive,
}: {
  icon: keyof typeof MaterialCommunityIcons.glyphMap;
  label: string;
  onPress: () => void;
  loading?: boolean;
  destructive?: boolean;
}) {
  return (
    <Pressable
      onPress={onPress}
      disabled={loading}
      className={`flex-row items-center border-b border-divider px-5 py-4 ${loading ? 'opacity-50' : ''}`}
    >
      <MaterialCommunityIcons name={icon} size={20} color={destructive ? '#F87171' : '#F5F5F7'} />
      <Text className={`ml-3 flex-1 text-sm font-medium ${destructive ? 'text-red-400' : 'text-ink'}`}>
        {label}
      </Text>
      {loading ? (
        <ActivityIndicator color="#9CA3AF" />
      ) : (
        <MaterialCommunityIcons name="chevron-right" size={20} color="#9CA3AF" />
      )}
    </Pressable>
  );
}

export function AccountScreen() {
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
    }
  };

  return (
    <SafeAreaView className="flex-1 bg-background">
      <View className="items-center border-b border-divider px-5 py-6">
        <View
          className="h-16 w-16 items-center justify-center rounded-full"
          style={{ backgroundColor: avatarColor(user?.userId ?? 0) }}
        >
          <Text className="text-2xl font-semibold text-white">
            {(user?.fullName ?? '?').charAt(0).toUpperCase()}
          </Text>
        </View>
        <View className="mt-3 flex-row items-center">
          <Text className="text-lg font-semibold text-ink">{user?.fullName ?? 'there'}</Text>
          <Pressable
            className="ml-2"
            onPress={() => Alert.alert('Coming soon', "Editing your profile isn't available yet.")}
          >
            <MaterialCommunityIcons name="pencil-outline" size={16} color="#9CA3AF" />
          </Pressable>
        </View>
        <Text className="mt-1 text-sm text-subtle">{user?.email}</Text>
      </View>

      <Text className="px-5 pb-2 pt-6 text-xs font-medium uppercase text-subtle">Preferences</Text>
      {user && !user.emailVerified ? (
        <PreferenceRow
          icon="email-alert-outline"
          label="Verify your email"
          onPress={() => navigation.navigate('VerifyEmail', {})}
        />
      ) : null}
      <PreferenceRow icon="logout" label="Log out" onPress={handleLogout} loading={loggingOut} destructive />
    </SafeAreaView>
  );
}
