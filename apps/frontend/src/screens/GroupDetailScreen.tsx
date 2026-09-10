import { MaterialCommunityIcons } from '@expo/vector-icons';
import { RouteProp, useFocusEffect, useNavigation, useRoute } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useCallback, useState } from 'react';
import { ActivityIndicator, Pressable, SectionList, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Expense, listGroupExpenses } from '../api/expenses';
import { getGroup, getGroupBalances, Group } from '../api/groups';
import { RootStackParamList } from '../navigation/RootNavigator';
import { useAuthStore } from '../store/authStore';
import { avatarColor } from '../utils/avatarColor';
import { groupByDate } from '../utils/date';
import { getErrorMessage } from '../utils/errors';
import { categoryStyle } from '../utils/expenseCategory';
import { myImpact } from '../utils/expenseImpact';

type Route = RouteProp<RootStackParamList, 'GroupDetail'>;
type Navigation = NativeStackNavigationProp<RootStackParamList, 'GroupDetail'>;

export function GroupDetailScreen() {
  const navigation = useNavigation<Navigation>();
  const route = useRoute<Route>();
  const { groupId } = route.params;
  const currentUserId = useAuthStore((state) => state.user?.userId);

  const [group, setGroup] = useState<Group | null>(null);
  const [myBalance, setMyBalance] = useState(0);
  const [expenses, setExpenses] = useState<Expense[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    try {
      const [groupData, balances, expenseData] = await Promise.all([
        getGroup(groupId),
        getGroupBalances(groupId).catch(() => []),
        listGroupExpenses(groupId),
      ]);
      setGroup(groupData);
      setMyBalance(balances.find((b) => b.userId === currentUserId)?.netBalance ?? 0);
      setExpenses(expenseData);
      setError('');
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, [groupId, currentUserId]);

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

  if (error || !group) {
    return (
      <SafeAreaView className="flex-1 bg-background px-6">
        <Pressable
          onPress={() => navigation.goBack()}
          className="mt-2 h-9 w-9 items-center justify-center rounded-full bg-surface"
        >
          <MaterialCommunityIcons name="chevron-left" size={24} color="#F5F5F7" />
        </Pressable>
        <View className="flex-1 items-center justify-center">
          <Text className="text-center text-sm text-red-400">{error || 'Group not found'}</Text>
        </View>
      </SafeAreaView>
    );
  }

  const balanceSummary =
    Math.abs(myBalance) < 0.01
      ? { label: "You're settled up", className: 'text-white/80' }
      : myBalance > 0
        ? { label: `You are owed ${myBalance.toFixed(2)} ${group.defaultCurrency}`, className: 'text-white' }
        : {
            label: `You owe ${Math.abs(myBalance).toFixed(2)} ${group.defaultCurrency}`,
            className: 'text-white',
          };

  return (
    <SafeAreaView className="flex-1 bg-background">
      <View className="px-5 pb-5 pt-2" style={{ backgroundColor: avatarColor(group.id) }}>
        <View className="flex-row items-center justify-between">
          <Pressable
            onPress={() => navigation.goBack()}
            className="h-9 w-9 items-center justify-center rounded-full bg-black/20"
          >
            <MaterialCommunityIcons name="chevron-left" size={24} color="white" />
          </Pressable>
          <Pressable
            onPress={() => navigation.navigate('GroupSettings', { groupId })}
            className="h-9 w-9 items-center justify-center rounded-full bg-black/20"
          >
            <MaterialCommunityIcons name="cog-outline" size={20} color="white" />
          </Pressable>
        </View>
        <Text className="mt-4 text-2xl font-semibold text-white">{group.name}</Text>
        <Text className="mt-1 text-sm text-white/80">
          {group.memberCount} {group.memberCount === 1 ? 'member' : 'members'} · {group.defaultCurrency}
        </Text>
        <Text className={`mt-3 text-sm font-medium ${balanceSummary.className}`}>{balanceSummary.label}</Text>
      </View>

      <View className="flex-row gap-3 px-5 py-4">
        <Pressable
          onPress={() => navigation.navigate('SettleUp', { groupId })}
          className="flex-1 items-center rounded-lg bg-primary py-2.5"
        >
          <Text className="text-sm font-medium text-white">Settle up</Text>
        </Pressable>
        <Pressable
          onPress={() => navigation.navigate('Balances', { groupId })}
          className="flex-1 items-center rounded-lg border border-divider py-2.5"
        >
          <Text className="text-sm font-medium text-ink">Balances</Text>
        </Pressable>
      </View>

      <SectionList
        sections={groupByDate(expenses, (e) => e.createdAt)}
        keyExtractor={(item) => String(item.id)}
        renderSectionHeader={({ section }) => (
          <Text className="bg-background px-5 pb-2 pt-3 text-xs font-medium uppercase text-subtle">
            {section.title}
          </Text>
        )}
        renderItem={({ item }) => {
          const style = categoryStyle(item.category);
          const impact = currentUserId ? myImpact(item, currentUserId) : null;
          return (
            <View className="flex-row items-center px-5 py-3">
              <View
                className="mr-3 h-10 w-10 items-center justify-center rounded-full"
                style={{ backgroundColor: `${style.color}33` }}
              >
                <MaterialCommunityIcons name={style.icon} size={18} color={style.color} />
              </View>
              <View className="flex-1 pr-3">
                <Text className="text-sm font-medium text-ink">{item.description}</Text>
                <Text className="text-xs text-subtle">Paid by {item.payers[0]?.fullName ?? '?'}</Text>
              </View>
              <View className="items-end">
                <Text className="text-sm font-medium text-ink">
                  {item.amount.toFixed(2)} {item.currency}
                </Text>
                {impact ? (
                  <Text className={`mt-0.5 text-xs font-medium ${impact.className}`}>{impact.label}</Text>
                ) : null}
              </View>
            </View>
          );
        }}
        ListEmptyComponent={
          <View className="items-center px-6 py-10">
            <Text className="text-sm text-subtle">No expenses yet. Add the first one below.</Text>
          </View>
        }
        contentContainerStyle={{ paddingBottom: 100 }}
      />

      <Pressable
        onPress={() => navigation.navigate('AddExpense', { groupId })}
        className="absolute bottom-6 right-6 h-14 w-14 items-center justify-center rounded-full bg-primary shadow-lg"
      >
        <MaterialCommunityIcons name="plus" size={28} color="white" />
      </Pressable>
    </SafeAreaView>
  );
}
