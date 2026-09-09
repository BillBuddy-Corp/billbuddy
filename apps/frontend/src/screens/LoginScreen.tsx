import { useNavigation } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useState } from 'react';
import { KeyboardAvoidingView, Platform, Pressable, ScrollView, Text, View } from 'react-native';

import { login } from '../api/auth';
import { Button } from '../components/atoms/Button';
import { TextField } from '../components/atoms/TextField';
import { AuthCard } from '../components/molecules/AuthCard';
import { RootStackParamList } from '../navigation/RootNavigator';
import { useAuthStore } from '../store/authStore';
import { getDeviceId } from '../utils/deviceId';
import { getErrorMessage } from '../utils/errors';

type Navigation = NativeStackNavigationProp<RootStackParamList, 'Login'>;

export function LoginScreen() {
  const navigation = useNavigation<Navigation>();
  const setSession = useAuthStore((state) => state.setSession);

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const handleLogin = async () => {
    if (!email.trim() || !password) {
      setError('Enter your email and password');
      return;
    }
    setError(null);
    setLoading(true);
    try {
      const deviceId = await getDeviceId();
      const response = await login({ email: email.trim(), password, deviceId });
      const emailVerified = await setSession({
        user: {
          userId: response.userId,
          email: response.email,
          fullName: response.fullName,
        },
        accessToken: response.accessToken,
        refreshToken: response.refreshToken,
      });
      navigation.reset({
        index: 0,
        routes: [{ name: emailVerified ? 'Home' : 'VerifyEmail' }],
      });
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
        <AuthCard
          activeTab="login"
          onTabChange={(tab) => tab === 'signup' && navigation.navigate('Signup')}
          tagline="Log in to split expenses with your group"
        >
          <TextField
            label="Email"
            placeholder="name@company.com"
            keyboardType="email-address"
            value={email}
            onChangeText={setEmail}
          />
          <TextField
            label="Password"
            placeholder="At least 8 characters"
            secureTextEntry
            value={password}
            onChangeText={setPassword}
          />

          <Pressable
            onPress={() => navigation.navigate('ForgotPassword')}
            className="mb-3.5 items-end"
          >
            <Text className="text-xs text-primary">Forgot password?</Text>
          </Pressable>

          {error ? <Text className="mb-3 text-sm text-red-500">{error}</Text> : null}

          <View className="mb-4">
            <Button label="Log in" onPress={handleLogin} loading={loading} />
          </View>

          <View className="mb-4 flex-row items-center">
            <View className="h-px flex-1 bg-gray-200" />
            <Text className="mx-2.5 text-xs text-gray-400">or</Text>
            <View className="h-px flex-1 bg-gray-200" />
          </View>

          <Button label="Continue with Google" variant="secondary" onPress={() => {}} />
        </AuthCard>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}
