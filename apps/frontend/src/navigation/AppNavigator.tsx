import { createNativeStackNavigator } from '@react-navigation/native-stack';

import { HomeScreen } from '../screens/HomeScreen';
import { ResetPasswordParams, ResetPasswordScreen } from '../screens/ResetPasswordScreen';
import { VerifyEmailParams, VerifyEmailScreen } from '../screens/VerifyEmailScreen';

export type AppStackParamList = {
  Home: undefined;
  ResetPassword: ResetPasswordParams;
  VerifyEmail: VerifyEmailParams;
};

const Stack = createNativeStackNavigator<AppStackParamList>();

export function AppNavigator() {
  return (
    <Stack.Navigator screenOptions={{ headerShown: false }}>
      <Stack.Screen name="Home" component={HomeScreen} />
      <Stack.Screen name="ResetPassword" component={ResetPasswordScreen} />
      <Stack.Screen name="VerifyEmail" component={VerifyEmailScreen} />
    </Stack.Navigator>
  );
}
