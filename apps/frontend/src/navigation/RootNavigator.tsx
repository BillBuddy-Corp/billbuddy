import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { ActivityIndicator, View } from 'react-native';

import { CreateGroupScreen } from '../screens/CreateGroupScreen';
import { ForgotPasswordScreen } from '../screens/ForgotPasswordScreen';
import { GroupDetailScreen } from '../screens/GroupDetailScreen';
import { LoginScreen } from '../screens/LoginScreen';
import { ResetPasswordParams, ResetPasswordScreen } from '../screens/ResetPasswordScreen';
import { SignupScreen } from '../screens/SignupScreen';
import { VerifyEmailParams, VerifyEmailScreen } from '../screens/VerifyEmailScreen';
import { useAuthStore } from '../store/authStore';
import { MainTabs } from './MainTabs';

// One navigator, not several swapped by a ternary — see the comment history
// in git blame for why: registering the same route name in two separate
// top-level navigators confused React Navigation's `linking` path
// resolution after a login/logout swap. MainTabs (the bottom-tab shell) is
// nested here as a single screen, with GroupDetail/CreateGroup as ordinary
// sibling screens in this same stack, pushed on top of the tabs — the
// standard "stack wraps tabs" pattern for screens that shouldn't show the
// tab bar (a detail view, a modal-like create form).
export type RootStackParamList = {
  Login: undefined;
  Signup: undefined;
  ForgotPassword: undefined;
  MainTabs: undefined;
  GroupDetail: { groupId: number };
  CreateGroup: undefined;
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
        <>
          <Stack.Screen name="MainTabs" component={MainTabs} />
          <Stack.Screen
            name="GroupDetail"
            component={GroupDetailScreen}
            options={{ headerShown: true, title: '' }}
          />
          <Stack.Screen
            name="CreateGroup"
            component={CreateGroupScreen}
            options={{ headerShown: true, title: 'New group', presentation: 'modal' }}
          />
        </>
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
