import { createNativeStackNavigator } from '@react-navigation/native-stack';

import { HomeScreen } from '../screens/HomeScreen';
import { ResetPasswordParams, ResetPasswordScreen } from '../screens/ResetPasswordScreen';
import { VerifyEmailParams, VerifyEmailScreen } from '../screens/VerifyEmailScreen';
import { useAuthStore } from '../store/authStore';

export type AppStackParamList = {
  Home: undefined;
  ResetPassword: ResetPasswordParams;
  VerifyEmail: VerifyEmailParams;
};

const Stack = createNativeStackNavigator<AppStackParamList>();

export function AppNavigator() {
  // Read once at mount: a session's emailVerified is already resolved by
  // the time this navigator mounts (setSession awaits the profile fetch),
  // so this reliably reflects reality rather than the "false" default.
  const startUnverified = useAuthStore((state) => state.user?.emailVerified === false);

  return (
    <Stack.Navigator
      screenOptions={{ headerShown: false }}
      initialRouteName={startUnverified ? 'VerifyEmail' : 'Home'}
    >
      <Stack.Screen name="Home" component={HomeScreen} />
      <Stack.Screen name="ResetPassword" component={ResetPasswordScreen} />
      <Stack.Screen name="VerifyEmail" component={VerifyEmailScreen} />
    </Stack.Navigator>
  );
}
