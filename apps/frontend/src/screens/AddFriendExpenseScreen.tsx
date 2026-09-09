import { RouteProp, useNavigation, useRoute } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useEffect, useState } from 'react';
import { KeyboardAvoidingView, Platform, Pressable, ScrollView, Text, View } from 'react-native';

import { createFriendExpense, Friend, listFriends } from '../api/friends';
import { Button } from '../components/atoms/Button';
import { TextField } from '../components/atoms/TextField';
import { RootStackParamList } from '../navigation/RootNavigator';
import { useAuthStore } from '../store/authStore';
import { getErrorMessage } from '../utils/errors';

type Route = RouteProp<RootStackParamList, 'AddFriendExpense'>;
type Navigation = NativeStackNavigationProp<RootStackParamList, 'AddFriendExpense'>;

export function AddFriendExpenseScreen() {
  const navigation = useNavigation<Navigation>();
  const route = useRoute<Route>();
  const { friendUserId } = route.params;
  const currentUser = useAuthStore((state) => state.user);

  const [friend, setFriend] = useState<Friend | null>(null);
  const [description, setDescription] = useState('');
  const [amount, setAmount] = useState('');
  const [currency, setCurrency] = useState('INR');
  const [paidByMe, setPaidByMe] = useState(true);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    listFriends().then((friends) => {
      setFriend(friends.find((f) => f.userId === friendUserId) ?? null);
    });
  }, [friendUserId]);

  const handleCreate = async () => {
    if (!currentUser) {
      return;
    }
    if (!description.trim()) {
      setError('Enter a description');
      return;
    }
    const parsedAmount = Number(amount);
    if (!amount.trim() || Number.isNaN(parsedAmount) || parsedAmount <= 0) {
      setError('Enter a valid amount');
      return;
    }
    if (!currency.trim()) {
      setError('Enter a currency code, e.g. INR');
      return;
    }
    setError('');
    setLoading(true);
    try {
      await createFriendExpense(friendUserId, currentUser.userId, {
        description: description.trim(),
        amount: parsedAmount,
        currency: currency.trim(),
        paidByUserId: paidByMe ? currentUser.userId : friendUserId,
      });
      navigation.goBack();
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  return (
    <KeyboardAvoidingView
      className="flex-1 bg-white"
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <ScrollView contentContainerStyle={{ padding: 20 }} keyboardShouldPersistTaps="handled">
        <TextField
          label="Description"
          placeholder="Coffee"
          value={description}
          onChangeText={setDescription}
        />
        <TextField
          label="Amount"
          placeholder="20"
          keyboardType="decimal-pad"
          value={amount}
          onChangeText={setAmount}
        />
        <TextField
          label="Currency"
          placeholder="INR"
          autoCapitalize="characters"
          value={currency}
          onChangeText={setCurrency}
        />

        <Text className="mb-1.5 text-sm text-gray-500">Paid by</Text>
        <View className="mb-3.5 flex-row overflow-hidden rounded-lg border border-gray-300">
          <Pressable
            onPress={() => setPaidByMe(true)}
            className={`flex-1 items-center py-2.5 ${paidByMe ? 'bg-primary' : 'bg-white'}`}
          >
            <Text className={`text-sm font-medium ${paidByMe ? 'text-white' : 'text-black'}`}>You</Text>
          </Pressable>
          <Pressable
            onPress={() => setPaidByMe(false)}
            className={`flex-1 items-center py-2.5 ${!paidByMe ? 'bg-primary' : 'bg-white'}`}
          >
            <Text className={`text-sm font-medium ${!paidByMe ? 'text-white' : 'text-black'}`}>
              {friend?.fullName ?? 'Them'}
            </Text>
          </Pressable>
        </View>
        <Text className="mb-3.5 text-xs text-gray-400">Split equally between the two of you.</Text>

        {error ? <Text className="mb-3 text-sm text-red-500">{error}</Text> : null}

        <View className="mt-2">
          <Button label="Add expense" onPress={handleCreate} loading={loading} />
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}
