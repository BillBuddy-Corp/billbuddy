import { RouteProp, useNavigation, useRoute } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { joinViaInvite } from '../api/invites';
import { Button } from '../components/atoms/Button';
import { Logo } from '../components/atoms/Logo';
import { TextField } from '../components/atoms/TextField';
import { RootStackParamList } from '../navigation/RootNavigator';
import { useAuthStore } from '../store/authStore';
import { extractToken } from '../utils/extractToken';
import { getErrorMessage } from '../utils/errors';

export type JoinGroupParams = { token?: string };

type Navigation = NativeStackNavigationProp<RootStackParamList, 'JoinGroup'>;

export function JoinGroupScreen() {
  const navigation = useNavigation<Navigation>();
  const route = useRoute<RouteProp<Record<string, JoinGroupParams>, string>>();
  const hasSession = useAuthStore((state) => Boolean(state.accessToken));

  const [status, setStatus] = useState<'awaitingToken' | 'pending' | 'error'>(
    route.params?.token ? 'pending' : 'awaitingToken'
  );
  const [error, setError] = useState('');
  const [linkInput, setLinkInput] = useState('');

  const runJoin = async (token: string) => {
    setStatus('pending');
    setError('');
    try {
      const response = await joinViaInvite(token);
      navigation.reset({
        index: 0,
        routes: [{ name: 'GroupDetail', params: { groupId: response.groupId } }],
      });
    } catch (err) {
      setError(getErrorMessage(err));
      setStatus('awaitingToken');
    }
  };

  const handleContinue = () => {
    const token = extractToken(linkInput);
    if (!token) {
      setError('Paste the invite link or token');
      return;
    }
    runJoin(token);
  };

  useEffect(() => {
    const token = route.params?.token;
    if (token) {
      runJoin(token);
    }
    // Only run once for the token this screen was opened with.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (!hasSession) {
    return (
      <SafeAreaView className="flex-1 items-center justify-center bg-white px-6">
        <Logo />
        <Text className="mt-4 text-lg font-medium text-black">Join a group</Text>
        <Text className="mt-1 text-center text-sm text-gray-500">
          Log in or create an account, then reopen this invite to join.
        </Text>
        <View className="mt-6 w-full max-w-xs">
          <View className="mb-3">
            <Button label="Log in" onPress={() => navigation.reset({ index: 0, routes: [{ name: 'Login' }] })} />
          </View>
          <Button
            label="Create account"
            variant="secondary"
            onPress={() => navigation.reset({ index: 0, routes: [{ name: 'Signup' }] })}
          />
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView className="flex-1 items-center justify-center bg-white px-6">
      <Logo />
      <Text className="mt-4 text-lg font-medium text-black">Join a group</Text>
      <Text className="mt-1 text-center text-sm text-gray-500">
        Paste the invite link or token you were sent.
      </Text>

      {status === 'pending' ? <ActivityIndicator className="mt-6" color="#2F6FED" /> : null}

      {status === 'awaitingToken' ? (
        <View className="mt-6 w-full max-w-xs">
          <TextField
            label="Invite link or token"
            placeholder="Paste it here"
            value={linkInput}
            onChangeText={setLinkInput}
            autoCapitalize="none"
          />
          {error ? <Text className="mb-3 text-sm text-red-500">{error}</Text> : null}
          <Button label="Join group" onPress={handleContinue} />
        </View>
      ) : null}
    </SafeAreaView>
  );
}
