import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useFocusEffect } from '@react-navigation/native';
import { useCallback, useState } from 'react';
import { ActivityIndicator, SectionList, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Expense, listGroupExpenses } from '../api/expenses';
import { listFriendExpenses, listFriends } from '../api/friends';
import { listGroups } from '../api/groups';
import { useAuthStore } from '../store/authStore';
import { groupByDate } from '../utils/date';
import { categoryStyle } from '../utils/expenseCategory';
import { myImpact } from '../utils/expenseImpact';

type ActivityItem = {
  expense: Expense;
  contextLabel: string;
  contextSeed: number;
};

// No backend "global activity feed" endpoint exists yet, so this fans out
// to every group's and friend's own expense list and merges client-side.
// That's an N+1-call approach -- fine for a personal account's handful of
// groups/friends, but would need a real endpoint to scale further.
async function loadActivity(): Promise<ActivityItem[]> {
  const [groups, friends] = await Promise.all([listGroups(), listFriends()]);

  const groupItems = await Promise.all(
    groups.map(async (group) => {
      const expenses = await listGroupExpenses(group.id).catch(() => []);
      return expenses.map((expense) => ({
        expense,
        contextLabel: `in ${group.name}`,
        contextSeed: group.id,
      }));
    })
  );
  const friendItems = await Promise.all(
    friends.map(async (friend) => {
      const expenses = await listFriendExpenses(friend.userId).catch(() => []);
      return expenses.map((expense) => ({
        expense,
        contextLabel: `with ${friend.fullName}`,
        contextSeed: friend.userId,
      }));
    })
  );

  return [...groupItems.flat(), ...friendItems.flat()].sort(
    (a, b) => new Date(b.expense.createdAt).getTime() - new Date(a.expense.createdAt).getTime()
  );
}

export function ActivityScreen() {
  const currentUserId = useAuthStore((state) => state.user?.userId);
  const [items, setItems] = useState<ActivityItem[]>([]);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    const data = await loadActivity().catch(() => []);
    setItems(data);
    setLoading(false);
  }, []);

  useFocusEffect(
    useCallback(() => {
      load();
    }, [load])
  );

  if (loading) {
    return (
      <SafeAreaView className="flex-1 items-center justify-center bg-background">
        <ActivityIndicator color="#2F6FED" />
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView className="flex-1 bg-background">
      <View className="px-4 py-3">
        <Text className="text-xl font-semibold text-ink">Activity</Text>
      </View>

      <SectionList
        sections={groupByDate(items, (item) => item.expense.createdAt)}
        keyExtractor={(item) => String(item.expense.id)}
        renderSectionHeader={({ section }) => (
          <Text className="bg-background px-4 pb-2 pt-3 text-xs font-medium uppercase text-subtle">
            {section.title}
          </Text>
        )}
        renderItem={({ item }) => {
          const style = categoryStyle(item.expense.category);
          const impact = currentUserId ? myImpact(item.expense, currentUserId) : null;
          const payer = item.expense.payers[0];
          const payerLabel = payer && payer.userId === currentUserId ? 'you' : (payer?.fullName ?? '?');
          return (
            <View className="flex-row items-center px-4 py-3">
              <View
                className="mr-3 h-10 w-10 items-center justify-center rounded-full"
                style={{ backgroundColor: `${style.color}33` }}
              >
                <MaterialCommunityIcons name={style.icon} size={18} color={style.color} />
              </View>
              <View className="flex-1 pr-3">
                <Text className="text-sm font-medium text-ink">{item.expense.description}</Text>
                <Text className="text-xs text-subtle">
                  {item.contextLabel} · Paid by {payerLabel}
                </Text>
              </View>
              <View className="items-end">
                <Text className="text-sm font-medium text-ink">
                  {item.expense.amount.toFixed(2)} {item.expense.currency}
                </Text>
                {impact ? (
                  <Text className={`mt-0.5 text-xs font-medium ${impact.className}`}>{impact.label}</Text>
                ) : null}
              </View>
            </View>
          );
        }}
        ListEmptyComponent={
          <View className="items-center px-6 py-16">
            <MaterialCommunityIcons name="receipt" size={32} color="#9CA3AF" />
            <Text className="mt-3 text-sm text-subtle">
              No activity yet. Expenses from your groups and friends will show up here.
            </Text>
          </View>
        }
        contentContainerStyle={{ paddingBottom: 24 }}
      />
    </SafeAreaView>
  );
}
