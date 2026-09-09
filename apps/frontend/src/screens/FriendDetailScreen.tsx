import { RouteProp, useFocusEffect, useNavigation, useRoute } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useCallback, useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Expense } from '../api/expenses';
import {
  Friend,
  FriendBalance,
  getFriendBalance,
  listFriendExpenses,
  listFriends,
} from '../api/friends';
import { Button } from '../components/atoms/Button';
import { RootStackParamList } from '../navigation/RootNavigator';
import { getErrorMessage } from '../utils/errors';

type Route = RouteProp<RootStackParamList, 'FriendDetail'>;
type Navigation = NativeStackNavigationProp<RootStackParamList, 'FriendDetail'>;

function balanceLine(balances: FriendBalance[]): { label: string; className: string } {
  const nonZero = balances.filter((b) => Math.abs(b.amount) >= 0.01);
  if (nonZero.length === 0) {
    return { label: 'You are settled up', className: 'text-gray-400' };
  }
  return {
    label: nonZero
      .map((b) =>
        b.amount > 0
          ? `Owes you ${b.amount.toFixed(2)} ${b.currency}`
          : `You owe ${Math.abs(b.amount).toFixed(2)} ${b.currency}`
      )
      .join('\n'),
    className: nonZero.some((b) => b.amount < 0) ? 'text-red-500' : 'text-green-600',
  };
}

export function FriendDetailScreen() {
  const navigation = useNavigation<Navigation>();
  const route = useRoute<Route>();
  const { friendUserId } = route.params;

  const [friend, setFriend] = useState<Friend | null>(null);
  const [balances, setBalances] = useState<FriendBalance[]>([]);
  const [expenses, setExpenses] = useState<Expense[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    try {
      const [friends, balance, expenseList] = await Promise.all([
        listFriends(),
        getFriendBalance(friendUserId),
        listFriendExpenses(friendUserId),
      ]);
      setFriend(friends.find((f) => f.userId === friendUserId) ?? null);
      setBalances(balance);
      setExpenses(expenseList);
      setError('');
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, [friendUserId]);

  useFocusEffect(
    useCallback(() => {
      load();
    }, [load])
  );

  if (loading) {
    return (
      <SafeAreaView className="flex-1 items-center justify-center bg-white">
        <ActivityIndicator color="#2F6FED" />
      </SafeAreaView>
    );
  }

  if (error || !friend) {
    return (
      <SafeAreaView className="flex-1 items-center justify-center bg-white px-6">
        <Text className="text-center text-sm text-red-500">{error || 'Friend not found'}</Text>
      </SafeAreaView>
    );
  }

  const summary = balanceLine(balances);

  return (
    <SafeAreaView className="flex-1 bg-white">
      <View className="border-b border-gray-100 px-5 py-4">
        <Text className="text-xl font-medium text-black">{friend.fullName}</Text>
        <Text className="mt-1 text-sm text-gray-500">{friend.email}</Text>
        <Text className={`mt-3 text-sm font-medium ${summary.className}`}>{summary.label}</Text>
        <Pressable
          onPress={() => navigation.navigate('AddFriendExpense', { friendUserId })}
          className="mt-3 self-start rounded-lg bg-primary px-4 py-2"
        >
          <Text className="text-sm font-medium text-white">Add expense</Text>
        </Pressable>
      </View>

      <Text className="px-5 pb-2 pt-4 text-xs font-medium uppercase text-gray-400">Expenses</Text>
      <FlatList
        data={expenses}
        keyExtractor={(item) => String(item.id)}
        renderItem={({ item }) => (
          <View className="flex-row items-center justify-between border-b border-gray-100 px-5 py-3">
            <View className="flex-1 pr-3">
              <Text className="text-sm font-medium text-black">{item.description}</Text>
              <Text className="text-xs text-gray-500">Paid by {item.payers[0]?.fullName ?? '?'}</Text>
            </View>
            <Text className="text-sm text-black">
              {item.amount.toFixed(2)} {item.currency}
            </Text>
          </View>
        )}
        ListEmptyComponent={
          <Text className="px-5 pb-6 text-sm text-gray-400">No expenses with {friend.fullName} yet</Text>
        }
      />
    </SafeAreaView>
  );
}
