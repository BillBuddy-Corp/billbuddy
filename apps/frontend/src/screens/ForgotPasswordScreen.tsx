import { useNavigation } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useState } from 'react';
import { KeyboardAvoidingView, Platform, Pressable, ScrollView, Text, View } from 'react-native';

import { forgotPassword } from '../api/auth';
import { Button } from '../components/atoms/Button';
import { TextField } from '../components/atoms/TextField';
import { Logo } from '../components/atoms/Logo';
import { RootStackParamList } from '../navigation/RootNavigator';
import { getErrorMessage } from '../utils/errors';

type Navigation = NativeStackNavigationProp<RootStackParamList, 'ForgotPassword'>;

export function ForgotPasswordScreen() {
  const navigation = useNavigation<Navigation>();

  const [email, setEmail] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [sent, setSent] = useState<string | null>(null);

  const handleSubmit = async () => {
    if (!email.trim()) {
      setError('Enter your email');
      return;
    }
    setError(null);
    setLoading(true);
    try {
      const response = await forgotPassword({ email: email.trim() });
      setSent(response.message);
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  return (
    <KeyboardAvoidingView
      className="flex-1 bg-background"
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <ScrollView
        contentContainerStyle={{ flexGrow: 1, justifyContent: 'center', padding: 24 }}
        keyboardShouldPersistTaps="handled"
      >
        <View className="w-full rounded-2xl border border-divider bg-surface px-6 py-7">
          <View className="mb-7 items-center">
            <Logo />
            <Text className="mt-3 text-lg font-medium text-ink">Reset your password</Text>
            <Text className="mt-1 text-center text-sm text-subtle">
              Enter your email and we'll send you a reset link
            </Text>
          </View>

          {sent ? (
            <>
              <Text className="mb-5 text-center text-sm text-ink">{sent}</Text>
              <Button label="Back to login" onPress={() => navigation.navigate('Login', {})} />
            </>
          ) : (
            <>
              <TextField
                label="Email"
                placeholder="name@company.com"
                keyboardType="email-address"
                value={email}
                onChangeText={setEmail}
              />

              {error ? <Text className="mb-3 text-sm text-red-400">{error}</Text> : null}

              <View className="mb-4">
                <Button label="Send reset link" onPress={handleSubmit} loading={loading} />
              </View>

              <Pressable onPress={() => navigation.navigate('Login', {})} className="items-center">
                <Text className="text-sm text-primary">Back to login</Text>
              </Pressable>
            </>
          )}
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}
