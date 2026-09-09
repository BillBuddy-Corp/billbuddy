import { useFocusEffect, useNavigation } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useCallback, useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Friend, FriendBalance, getFriendBalance, listFriends } from '../api/friends';
import { RootStackParamList } from '../navigation/RootNavigator';
import { getErrorMessage } from '../utils/errors';

type Navigation = NativeStackNavigationProp<RootStackParamList, 'MainTabs'>;

type FriendWithBalance = Friend & { balances: FriendBalance[] };

function balanceSummary(balances: FriendBalance[]): { label: string; className: string } {
  const nonZero = balances.filter((b) => Math.abs(b.amount) >= 0.01);
  if (nonZero.length === 0) {
    return { label: 'Settled up', className: 'text-gray-400' };
  }
  return {
    label: nonZero
      .map((b) =>
        b.amount > 0
          ? `Owes you ${b.amount.toFixed(2)} ${b.currency}`
          : `You owe ${Math.abs(b.amount).toFixed(2)} ${b.currency}`
      )
      .join(' · '),
    className: nonZero.some((b) => b.amount < 0) ? 'text-red-500' : 'text-green-600',
  };
}

export function FriendsListScreen() {
  const navigation = useNavigation<Navigation>();

  const [friends, setFriends] = useState<FriendWithBalance[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const loadFriends = useCallback(async () => {
    try {
      const list = await listFriends();
      const withBalances = await Promise.all(
        list.map(async (friend) => ({
          ...friend,
          balances: await getFriendBalance(friend.userId).catch(() => []),
        }))
      );
      setFriends(withBalances);
      setError('');
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, []);

  useFocusEffect(
    useCallback(() => {
      loadFriends();
    }, [loadFriends])
  );

  return (
    <SafeAreaView className="flex-1 bg-white">
      <View className="flex-row items-center justify-between border-b border-gray-100 px-4 py-3">
        <Text className="text-xl font-medium text-black">Friends</Text>
        <Pressable
          onPress={() => navigation.navigate('AddFriend')}
          className="h-9 w-9 items-center justify-center rounded-full bg-primary"
        >
          <Text className="text-lg text-white">+</Text>
        </Pressable>
      </View>

      {loading ? (
        <View className="flex-1 items-center justify-center">
          <ActivityIndicator color="#2F6FED" />
        </View>
      ) : error ? (
        <View className="flex-1 items-center justify-center px-6">
          <Text className="text-center text-sm text-red-500">{error}</Text>
        </View>
      ) : friends.length === 0 ? (
        <View className="flex-1 items-center justify-center px-6">
          <Text className="text-base font-medium text-black">No friends yet</Text>
          <Text className="mt-1 text-center text-sm text-gray-500">
            Add a friend to split expenses with them directly, no group needed.
          </Text>
          <Pressable
            onPress={() => navigation.navigate('AddFriend')}
            className="mt-5 rounded-lg bg-primary px-5 py-2.5"
          >
            <Text className="text-sm font-medium text-white">Add a friend</Text>
          </Pressable>
        </View>
      ) : (
        <FlatList
          data={friends}
          keyExtractor={(item) => String(item.userId)}
          renderItem={({ item }) => {
            const summary = balanceSummary(item.balances);
            return (
              <Pressable
                onPress={() => navigation.navigate('FriendDetail', { friendUserId: item.userId })}
                className="flex-row items-center justify-between border-b border-gray-100 px-4 py-4"
              >
                <View className="flex-1 pr-3">
                  <Text className="text-base font-medium text-black">{item.fullName}</Text>
                  <Text className="mt-0.5 text-sm text-gray-500">{item.email}</Text>
                </View>
                <Text className={`text-xs font-medium ${summary.className}`}>{summary.label}</Text>
              </Pressable>
            );
          }}
        />
      )}
    </SafeAreaView>
  );
}
