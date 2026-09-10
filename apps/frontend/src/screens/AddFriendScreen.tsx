import { useNavigation } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useState } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView, Text, View } from 'react-native';

import { addFriend } from '../api/friends';
import { Button } from '../components/atoms/Button';
import { TextField } from '../components/atoms/TextField';
import { RootStackParamList } from '../navigation/RootNavigator';
import { getErrorMessage } from '../utils/errors';

type Navigation = NativeStackNavigationProp<RootStackParamList, 'AddFriend'>;

export function AddFriendScreen() {
  const navigation = useNavigation<Navigation>();

  const [email, setEmail] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleAdd = async () => {
    if (!email.trim()) {
      setError('Enter an email address');
      return;
    }
    setError('');
    setLoading(true);
    try {
      await addFriend(email.trim());
      navigation.goBack();
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
      <ScrollView contentContainerStyle={{ padding: 20 }} keyboardShouldPersistTaps="handled">
        <Text className="mb-4 text-sm text-subtle">
          They must already have a BillBuddy account. Adding a friend is instant, no approval
          needed.
        </Text>
        <TextField
          label="Email"
          placeholder="friend@example.com"
          keyboardType="email-address"
          value={email}
          onChangeText={setEmail}
        />

        {error ? <Text className="mb-3 text-sm text-red-400">{error}</Text> : null}

        <View className="mt-2">
          <Button label="Add friend" onPress={handleAdd} loading={loading} />
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}
