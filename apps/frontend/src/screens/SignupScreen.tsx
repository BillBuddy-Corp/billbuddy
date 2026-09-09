import { useNavigation } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useState } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView, Text, View } from 'react-native';

import { login, signup } from '../api/auth';
import { Button } from '../components/atoms/Button';
import { TextField } from '../components/atoms/TextField';
import { AuthCard } from '../components/molecules/AuthCard';
import { RootStackParamList } from '../navigation/RootNavigator';
import { useAuthStore } from '../store/authStore';
import { getDeviceId } from '../utils/deviceId';
import { getErrorMessage } from '../utils/errors';

type Navigation = NativeStackNavigationProp<RootStackParamList, 'Signup'>;

export function SignupScreen() {
  const navigation = useNavigation<Navigation>();
  const setSession = useAuthStore((state) => state.setSession);

  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const handleSignup = async () => {
    if (!fullName.trim() || !email.trim() || !password) {
      setError('Fill in your name, email, and password');
      return;
    }
    if (password.length < 8) {
      setError('Password must be at least 8 characters');
      return;
    }
    setError(null);
    setLoading(true);
    try {
      const trimmedEmail = email.trim();
      await signup({ fullName: fullName.trim(), email: trimmedEmail, password });

      const deviceId = await getDeviceId();
      const response = await login({ email: trimmedEmail, password, deviceId });
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
        routes: [{ name: emailVerified ? 'MainTabs' : 'VerifyEmail' }],
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
          activeTab="signup"
          onTabChange={(tab) => tab === 'login' && navigation.navigate('Login')}
          tagline="Create an account to get started"
        >
          <TextField
            label="Full name"
            placeholder="Jane Doe"
            value={fullName}
            onChangeText={setFullName}
          />
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

          {error ? <Text className="mb-3 text-sm text-red-500">{error}</Text> : null}

          <View className="mb-4">
            <Button label="Create account" onPress={handleSignup} loading={loading} />
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
