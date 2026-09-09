import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { ActivityIndicator, View } from 'react-native';

import { AddFriendScreen } from '../screens/AddFriendScreen';
import { AddFriendExpenseScreen } from '../screens/AddFriendExpenseScreen';
import { CreateGroupScreen } from '../screens/CreateGroupScreen';
import { ForgotPasswordScreen } from '../screens/ForgotPasswordScreen';
import { FriendDetailScreen } from '../screens/FriendDetailScreen';
import { GroupDetailScreen } from '../screens/GroupDetailScreen';
import { GroupSettingsScreen } from '../screens/GroupSettingsScreen';
import { InvitesScreen } from '../screens/InvitesScreen';
import { JoinGroupParams, JoinGroupScreen } from '../screens/JoinGroupScreen';
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
  Login: { token?: string };
  Signup: { token?: string };
  ForgotPassword: undefined;
  MainTabs: undefined;
  GroupDetail: { groupId: number };
  CreateGroup: undefined;
  FriendDetail: { friendUserId: number };
  AddFriend: undefined;
  AddFriendExpense: { friendUserId: number };
  GroupSettings: { groupId: number };
  Invites: { groupId: number };
  ResetPassword: ResetPasswordParams;
  VerifyEmail: VerifyEmailParams;
  JoinGroup: JoinGroupParams;
};

const Stack = createNativeStackNavigator<RootStackParamList>();

// Header options for screens already converted to the Splitwise-style dark
// theme, so the native header bar matches their dark content instead of
// React Navigation's default light header.
const darkHeaderOptions = {
  headerShown: true,
  headerStyle: { backgroundColor: '#0D0D0D' },
  headerTintColor: '#F5F5F7',
  headerShadowVisible: false,
} as const;

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
          <Stack.Screen
            name="FriendDetail"
            component={FriendDetailScreen}
            options={{ headerShown: true, title: '' }}
          />
          <Stack.Screen
            name="AddFriend"
            component={AddFriendScreen}
            options={{ headerShown: true, title: 'Add friend', presentation: 'modal' }}
          />
          <Stack.Screen
            name="AddFriendExpense"
            component={AddFriendExpenseScreen}
            options={{ headerShown: true, title: 'Add expense', presentation: 'modal' }}
          />
          <Stack.Screen
            name="GroupSettings"
            component={GroupSettingsScreen}
            options={{ ...darkHeaderOptions, title: 'Group settings' }}
          />
          <Stack.Screen
            name="Invites"
            component={InvitesScreen}
            options={{ ...darkHeaderOptions, title: 'Invite people' }}
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
      <Stack.Screen name="JoinGroup" component={JoinGroupScreen} />
    </Stack.Navigator>
  );
}
