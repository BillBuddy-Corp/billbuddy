import { RouteProp, useNavigation, useRoute } from '@react-navigation/native';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { verifyEmail } from '../api/auth';
import { getProfile } from '../api/users';
import { Button } from '../components/atoms/Button';
import { Logo } from '../components/atoms/Logo';
import { useAuthStore } from '../store/authStore';
import { getErrorMessage } from '../utils/errors';

export type VerifyEmailParams = { token?: string };

export function VerifyEmailScreen() {
  const navigation = useNavigation<any>();
  const route = useRoute<RouteProp<Record<string, VerifyEmailParams>, string>>();
  const hasSession = useAuthStore((state) => Boolean(state.accessToken));
  const setEmailVerified = useAuthStore((state) => state.setEmailVerified);

  const [status, setStatus] = useState<'pending' | 'success' | 'error'>('pending');
  const [message, setMessage] = useState('');

  useEffect(() => {
    const token = route.params?.token;
    if (!token) {
      setStatus('error');
      setMessage('Missing verification token.');
      return;
    }
    (async () => {
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
    })();
    // Only run once for the token this screen was opened with.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <SafeAreaView className="flex-1 items-center justify-center bg-white px-6">
      <Logo />
      <Text className="mt-4 text-lg font-medium text-black">Verify your email</Text>

      {status === 'pending' ? (
        <ActivityIndicator className="mt-6" color="#2F6FED" />
      ) : (
        <>
          <Text className="mt-3 text-center text-sm text-gray-500">{message}</Text>
          <View className="mt-6 w-full max-w-xs">
            <Button
              label="Continue"
              onPress={() => navigation.navigate(hasSession ? 'Home' : 'Login')}
            />
          </View>
        </>
      )}
    </SafeAreaView>
  );
}
