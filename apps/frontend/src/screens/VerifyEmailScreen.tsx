import { RouteProp, useNavigation, useRoute } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { resendVerificationEmail, verifyEmail } from '../api/auth';
import { Button } from '../components/atoms/Button';
import { Logo } from '../components/atoms/Logo';
import { TextField } from '../components/atoms/TextField';
import { RootStackParamList } from '../navigation/RootNavigator';
import { useAuthStore } from '../store/authStore';
import { extractToken } from '../utils/extractToken';
import { getErrorMessage } from '../utils/errors';

export type VerifyEmailParams = { token?: string };

type Navigation = NativeStackNavigationProp<RootStackParamList, 'VerifyEmail'>;

export function VerifyEmailScreen() {
  const navigation = useNavigation<Navigation>();
  const route = useRoute<RouteProp<Record<string, VerifyEmailParams>, string>>();
  const hasSession = useAuthStore((state) => Boolean(state.accessToken));
  const setEmailVerified = useAuthStore((state) => state.setEmailVerified);

  const [status, setStatus] = useState<'awaitingToken' | 'pending' | 'success' | 'error'>(
    route.params?.token ? 'pending' : 'awaitingToken'
  );
  const [error, setError] = useState('');
  const [linkInput, setLinkInput] = useState('');
  const [resending, setResending] = useState(false);
  const [resent, setResent] = useState(false);

  const goHome = () =>
    navigation.reset({ index: 0, routes: [{ name: hasSession ? 'Home' : 'Login' }] });

  const runVerification = async (token: string, { navigateOnSuccess } = { navigateOnSuccess: false }) => {
    setStatus('pending');
    setError('');
    try {
      await verifyEmail({ token });
      if (hasSession) {
        await setEmailVerified(true);
      }
      if (navigateOnSuccess) {
        goHome();
        return;
      }
      setStatus('success');
    } catch (err) {
      setError(getErrorMessage(err));
      // The manual-entry path stays on the input screen so the user can
      // fix a typo and retry; the deep-link path shows a dedicated
      // error state since there's no input to correct.
      setStatus(navigateOnSuccess ? 'awaitingToken' : 'error');
    }
  };

  const handleContinue = () => {
    const token = extractToken(linkInput);
    if (!token) {
      setError('Paste the verification link or token from your email');
      return;
    }
    runVerification(token, { navigateOnSuccess: true });
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

  return (
    <SafeAreaView className="flex-1 items-center justify-center bg-white px-6">
      <Logo />
      <Text className="mt-4 text-lg font-medium text-black">Verify your email</Text>
      <Text className="mt-1 text-center text-sm text-gray-500">
        We sent a verification link to your email. Paste it below, or the token from it.
      </Text>

      {status === 'pending' ? <ActivityIndicator className="mt-6" color="#2F6FED" /> : null}

      {status === 'awaitingToken' ? (
        <View className="mt-6 w-full max-w-xs">
          <TextField
            label="Verification link or token"
            placeholder="Paste it here"
            value={linkInput}
            onChangeText={setLinkInput}
            autoCapitalize="none"
          />
          {error ? <Text className="mb-3 text-sm text-red-500">{error}</Text> : null}
          <Button label="Continue" onPress={handleContinue} />

          {hasSession ? (
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

          {hasSession ? (
            <Pressable onPress={goHome} className="mt-6 items-center">
              <Text className="text-sm text-gray-400">Skip for now</Text>
            </Pressable>
          ) : null}
        </View>
      ) : null}

      {status === 'success' || status === 'error' ? (
        <>
          <Text className="mt-3 text-center text-sm text-gray-500">
            {status === 'success' ? 'Email verified successfully.' : error}
          </Text>
          <View className="mt-6 w-full max-w-xs">
            <Button label="Continue" onPress={goHome} />
          </View>
        </>
      ) : null}
    </SafeAreaView>
  );
}
