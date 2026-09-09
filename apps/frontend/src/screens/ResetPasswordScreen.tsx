import { RouteProp, useNavigation, useRoute } from '@react-navigation/native';
import { useState } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView, Text, View } from 'react-native';

import { resetPassword } from '../api/auth';
import { Button } from '../components/atoms/Button';
import { Logo } from '../components/atoms/Logo';
import { TextField } from '../components/atoms/TextField';
import { useAuthStore } from '../store/authStore';
import { getErrorMessage } from '../utils/errors';

export type ResetPasswordParams = { token?: string };

export function ResetPasswordScreen() {
  const navigation = useNavigation<any>();
  const route = useRoute<RouteProp<Record<string, ResetPasswordParams>, string>>();
  const clearSession = useAuthStore((state) => state.clearSession);
  const hasSession = useAuthStore((state) => Boolean(state.accessToken));

  const [token, setToken] = useState(route.params?.token ?? '');
  const [newPassword, setNewPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [done, setDone] = useState(false);

  const handleSubmit = async () => {
    if (!token.trim()) {
      setError('Paste the token from your reset email');
      return;
    }
    if (newPassword.length < 8) {
      setError('Password must be at least 8 characters');
      return;
    }
    setError(null);
    setLoading(true);
    try {
      await resetPassword({ token: token.trim(), newPassword });
      // Resetting revokes every active session, including this device's if
      // it was logged in — clear local state to match.
      if (hasSession) {
        await clearSession();
      }
      setDone(true);
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  return (
    <KeyboardAvoidingView
      className="flex-1 bg-primary-tint"
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <ScrollView
        contentContainerStyle={{ flexGrow: 1, justifyContent: 'center', padding: 24 }}
        keyboardShouldPersistTaps="handled"
      >
        <View className="w-full rounded-2xl border border-gray-200 bg-white px-6 py-7">
          <View className="mb-7 items-center">
            <Logo />
            <Text className="mt-3 text-lg font-medium text-black">Set a new password</Text>
          </View>

          {done ? (
            <>
              <Text className="mb-5 text-center text-sm text-gray-700">
                Your password has been reset. Log in with your new password.
              </Text>
              <Button label="Go to login" onPress={() => navigation.navigate('Login')} />
            </>
          ) : (
            <>
              {!route.params?.token ? (
                <TextField
                  label="Reset token"
                  placeholder="Paste the token from your email"
                  value={token}
                  onChangeText={setToken}
                />
              ) : null}
              <TextField
                label="New password"
                placeholder="At least 8 characters"
                secureTextEntry
                value={newPassword}
                onChangeText={setNewPassword}
              />

              {error ? <Text className="mb-3 text-sm text-red-500">{error}</Text> : null}

              <Button label="Reset password" onPress={handleSubmit} loading={loading} />
            </>
          )}
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}
