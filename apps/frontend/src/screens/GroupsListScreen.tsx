import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useFocusEffect, useNavigation } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useCallback, useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { getGroupBalances, Group, listGroups } from '../api/groups';
import { FriendBalance, listFriendExpenses, listFriends, nonGroupBalanceFromExpenses } from '../api/friends';
import { balanceLine, GroupListItem } from '../components/molecules/GroupListItem';
import { RootStackParamList } from '../navigation/RootNavigator';
import { useAuthStore } from '../store/authStore';
import { getErrorMessage } from '../utils/errors';

type Navigation = NativeStackNavigationProp<RootStackParamList, 'MainTabs'>;

type GroupWithBalance = { group: Group; netBalance: number | null };

export function GroupsListScreen() {
  const navigation = useNavigation<Navigation>();
  const currentUserId = useAuthStore((state) => state.user?.userId);

  const [rows, setRows] = useState<GroupWithBalance[]>([]);
  const [nonGroupBalances, setNonGroupBalances] = useState<FriendBalance[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    if (!currentUserId) return;
    try {
      const groups = await listGroups();
      const withBalances = await Promise.all(
        groups.map(async (group) => {
          try {
            const balances = await getGroupBalances(group.id);
            const mine = balances.find((b) => b.userId === currentUserId);
            return { group, netBalance: mine?.netBalance ?? 0 };
          } catch {
            return { group, netBalance: null };
          }
        })
      );
      setRows(withBalances);
      setError('');

      // Non-group balances are a secondary aggregate on this screen (the
      // primary content is the group list above) -- a failure here shouldn't
      // blank out an otherwise-successful group list.
      try {
        const friends = await listFriends();
        const friendBalances = await Promise.all(
          friends.map(async (f) => {
            const expenses = await listFriendExpenses(f.userId).catch(() => []);
            return nonGroupBalanceFromExpenses(expenses, currentUserId);
          })
        );
        const merged = new Map<string, number>();
        friendBalances.flat().forEach((b) => merged.set(b.currency, (merged.get(b.currency) ?? 0) + b.amount));
        setNonGroupBalances(Array.from(merged, ([currency, amount]) => ({ currency, amount })));
      } catch {
        setNonGroupBalances([]);
      }
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, [currentUserId]);

  useFocusEffect(
    useCallback(() => {
      load();
    }, [load])
  );

  const overallByCurrency = new Map<string, number>();
  rows.forEach((r) => {
    if (r.netBalance) {
      overallByCurrency.set(
        r.group.defaultCurrency,
        (overallByCurrency.get(r.group.defaultCurrency) ?? 0) + r.netBalance
      );
    }
  });
  nonGroupBalances.forEach((b) => overallByCurrency.set(b.currency, (overallByCurrency.get(b.currency) ?? 0) + b.amount));
  const overallLines = Array.from(overallByCurrency, ([currency, amount]) => balanceLine(amount, currency).label);

  // navigate() bubbles up to the parent Tab.Navigator for a sibling tab name
  // not in RootStackParamList -- cast needed since this screen's navigation
  // prop is typed against the root stack, not the tab navigator directly.
  const goToFriends = () => (navigation as never as { navigate: (name: string) => void }).navigate('Friends');

  return (
    <SafeAreaView className="flex-1 bg-background">
      <View className="flex-row items-center justify-between px-4 py-3">
        <Text className="text-xl font-semibold text-ink">Groups</Text>
        <Pressable
          onPress={() => navigation.navigate('CreateGroup')}
          className="h-9 w-9 items-center justify-center rounded-full bg-primary"
        >
          <Text className="text-lg text-white">+</Text>
        </Pressable>
      </View>

      {!loading && rows.length > 0 ? (
        <View className="flex-row items-center justify-between px-4 pb-3">
          <Text className="text-sm text-subtle">
            {overallLines.length === 0 ? (
              "Overall, you're settled up"
            ) : (
              <>
                Overall, <Text className="font-medium text-ink">{overallLines.join(' · ')}</Text>
              </>
            )}
          </Text>
        </View>
      ) : null}

      {loading ? (
        <View className="flex-1 items-center justify-center">
          <ActivityIndicator color="#2F6FED" />
        </View>
      ) : error ? (
        <View className="flex-1 items-center justify-center px-6">
          <Text className="text-center text-sm text-red-400">{error}</Text>
        </View>
      ) : rows.length === 0 ? (
        <View className="flex-1 items-center justify-center px-6">
          <Text className="text-base font-medium text-ink">No groups yet</Text>
          <Text className="mt-1 text-center text-sm text-subtle">
            Create a group to start splitting expenses with friends.
          </Text>
          <Pressable
            onPress={() => navigation.navigate('CreateGroup')}
            className="mt-5 rounded-lg bg-primary px-5 py-2.5"
          >
            <Text className="text-sm font-medium text-white">Create a group</Text>
          </Pressable>
        </View>
      ) : (
        <FlatList
          data={rows}
          keyExtractor={(item) => String(item.group.id)}
          renderItem={({ item }) => (
            <GroupListItem
              group={item.group}
              netBalance={item.netBalance}
              onPress={() => navigation.navigate('GroupDetail', { groupId: item.group.id })}
            />
          )}
          ListFooterComponent={
            <Pressable onPress={goToFriends} className="flex-row items-center border-b border-divider px-4 py-4">
              <View className="mr-3 h-12 w-12 items-center justify-center rounded-2xl bg-surface">
                <MaterialCommunityIcons name="account-multiple" size={22} color="#9CA3AF" />
              </View>
              <View className="flex-1 pr-3">
                <Text className="text-base font-medium text-ink">Non-group expenses</Text>
                <Text className="mt-0.5 text-sm text-subtle">Balances with friends outside any group</Text>
              </View>
              {nonGroupBalances.length > 0 ? (
                <Text
                  className={`text-xs font-medium ${
                    nonGroupBalances.some((b) => b.amount < 0) ? 'text-red-400' : 'text-green-500'
                  }`}
                >
                  {nonGroupBalances
                    .map((b) => balanceLine(b.amount, b.currency).label)
                    .join(' · ')}
                </Text>
              ) : (
                <Text className="text-xs font-medium text-subtle">settled up</Text>
              )}
            </Pressable>
          }
        />
      )}
    </SafeAreaView>
  );
}
