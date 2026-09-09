import { RouteProp, useNavigation, useRoute } from '@react-navigation/native';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { resendVerificationEmail, verifyEmail } from '../api/auth';
import { getProfile } from '../api/users';
import { Button } from '../components/atoms/Button';
import { Logo } from '../components/atoms/Logo';
import { TextField } from '../components/atoms/TextField';
import { useAuthStore } from '../store/authStore';
import { getErrorMessage } from '../utils/errors';

export type VerifyEmailParams = { token?: string };

export function VerifyEmailScreen() {
  const navigation = useNavigation<any>();
  const route = useRoute<RouteProp<Record<string, VerifyEmailParams>, string>>();
  const hasSession = useAuthStore((state) => Boolean(state.accessToken));
  const setEmailVerified = useAuthStore((state) => state.setEmailVerified);

  const [status, setStatus] = useState<'awaitingToken' | 'pending' | 'success' | 'error'>(
    route.params?.token ? 'pending' : 'awaitingToken'
  );
  const [message, setMessage] = useState('');
  const [tokenInput, setTokenInput] = useState('');
  const [resending, setResending] = useState(false);
  const [resent, setResent] = useState(false);

  const runVerification = async (token: string) => {
    setStatus('pending');
    try {
      const response = await verifyEmail({ token });
      setMessage(response.message);
      setStatus('success');
      if (hasSession) {
        try {
          const profile = await getProfile();
          await setEmailVerified(profile.emailVerified);
        } catch {
          // Non-critical: the banner just won't update immediately.
        }
      }
    } catch (err) {
      setStatus('error');
      setMessage(getErrorMessage(err));
    }
  };

  const handleResend = async () => {
    setResending(true);
    try {
      await resendVerificationEmail();
      setResent(true);
    } catch {
      // Silently ignore — the link just stays available to retry.
    } finally {
      setResending(false);
    }
  };

  useEffect(() => {
    const token = route.params?.token;
    if (token) {
      runVerification(token);
    }
    // Only run once for the token this screen was opened with.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const showResend = hasSession && (status === 'awaitingToken' || status === 'error');

  return (
    <SafeAreaView className="flex-1 items-center justify-center bg-white px-6">
      <Logo />
      <Text className="mt-4 text-lg font-medium text-black">Verify your email</Text>

      {status === 'pending' ? <ActivityIndicator className="mt-6" color="#2F6FED" /> : null}

      {status === 'awaitingToken' ? (
        <View className="mt-6 w-full max-w-xs">
          <TextField
            label="Verification token"
            placeholder="Paste the token from your email"
            value={tokenInput}
            onChangeText={setTokenInput}
          />
          <Button
            label="Verify"
            onPress={() => tokenInput.trim() && runVerification(tokenInput.trim())}
          />
        </View>
      ) : null}

      {status === 'success' || status === 'error' ? (
        <>
          <Text className="mt-3 text-center text-sm text-gray-500">{message}</Text>
          <View className="mt-6 w-full max-w-xs">
            <Button
              label="Continue"
              onPress={() => navigation.navigate(hasSession ? 'Home' : 'Login')}
            />
          </View>
        </>
      ) : null}

      {showResend ? (
        <View className="mt-4 items-center">
          {resending ? (
            <ActivityIndicator color="#2F6FED" />
          ) : (
            <Pressable onPress={handleResend} disabled={resent}>
              <Text className="text-sm text-primary">
                {resent ? 'Verification email sent' : "Didn't get the email? Resend"}
              </Text>
            </Pressable>
          )}
        </View>
      ) : null}
    </SafeAreaView>
  );
}
