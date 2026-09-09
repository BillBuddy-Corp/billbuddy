import { ActivityIndicator, View } from 'react-native';

import { useAuthStore } from '../store/authStore';
import { AppNavigator } from './AppNavigator';
import { AuthNavigator } from './AuthNavigator';

export function RootNavigator() {
  const isHydrated = useAuthStore((state) => state.isHydrated);
  const accessToken = useAuthStore((state) => state.accessToken);

  if (!isHydrated) {
    return (
      <View className="flex-1 items-center justify-center bg-white">
        <ActivityIndicator color="#2F6FED" />
      </View>
    );
  }

  return accessToken ? <AppNavigator /> : <AuthNavigator />;
}
