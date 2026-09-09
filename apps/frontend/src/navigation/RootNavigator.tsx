import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { ActivityIndicator, View } from 'react-native';

import { ForgotPasswordScreen } from '../screens/ForgotPasswordScreen';
import { HomeScreen } from '../screens/HomeScreen';
import { LoginScreen } from '../screens/LoginScreen';
import { ResetPasswordParams, ResetPasswordScreen } from '../screens/ResetPasswordScreen';
import { SignupScreen } from '../screens/SignupScreen';
import { VerifyEmailParams, VerifyEmailScreen } from '../screens/VerifyEmailScreen';
import { useAuthStore } from '../store/authStore';

// One navigator, not two swapped by a ternary. Login/Signup/ForgotPassword
// and Home are mutually exclusive (conditionally included based on session),
// while ResetPassword and VerifyEmail are always present — either flow can
// be reached whether or not the user currently has a session. Registering
// the same route name in two separate top-level navigators (the earlier
// approach) confused React Navigation's `linking` path resolution after a
// login/logout swap; this is the pattern React Navigation's own docs
// recommend for authentication flows specifically to avoid that.
export type RootStackParamList = {
  Login: undefined;
  Signup: undefined;
  ForgotPassword: undefined;
  Home: undefined;
  ResetPassword: ResetPasswordParams;
  VerifyEmail: VerifyEmailParams;
};

const Stack = createNativeStackNavigator<RootStackParamList>();

export function RootNavigator() {
  const isHydrated = useAuthStore((state) => state.isHydrated);
  const hasSession = useAuthStore((state) => Boolean(state.accessToken));

  if (!isHydrated) {
    return (
      <View className="flex-1 items-center justify-center bg-white">
        <ActivityIndicator color="#2F6FED" />
      </View>
    );
  }

  return (
    <Stack.Navigator screenOptions={{ headerShown: false }}>
      {hasSession ? (
        <Stack.Screen name="Home" component={HomeScreen} />
      ) : (
        <>
          <Stack.Screen name="Login" component={LoginScreen} />
          <Stack.Screen name="Signup" component={SignupScreen} />
          <Stack.Screen name="ForgotPassword" component={ForgotPasswordScreen} />
        </>
      )}
      <Stack.Screen name="ResetPassword" component={ResetPasswordScreen} />
      <Stack.Screen name="VerifyEmail" component={VerifyEmailScreen} />
    </Stack.Navigator>
  );
}
