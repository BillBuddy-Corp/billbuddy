import { RouteProp, useFocusEffect, useRoute } from '@react-navigation/native';
import { useCallback, useState } from 'react';
import { ActivityIndicator, FlatList, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { getGroup, getGroupBalances, Group, GroupBalance } from '../api/groups';
import { RootStackParamList } from '../navigation/RootNavigator';
import { avatarColor } from '../utils/avatarColor';
import { memberBalanceLabel } from '../utils/balance';
import { getErrorMessage } from '../utils/errors';

type Route = RouteProp<RootStackParamList, 'Balances'>;

export function BalancesScreen() {
  const route = useRoute<Route>();
  const { groupId } = route.params;

  const [group, setGroup] = useState<Group | null>(null);
  const [balances, setBalances] = useState<GroupBalance[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    try {
      const [groupData, balanceData] = await Promise.all([getGroup(groupId), getGroupBalances(groupId)]);
      setGroup(groupData);
      setBalances(balanceData);
      setError('');
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, [groupId]);

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
      <SafeAreaView className="flex-1 items-center justify-center bg-background px-6">
        <Text className="text-center text-sm text-red-400">{error || 'Group not found'}</Text>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView className="flex-1 bg-background">
      <FlatList
        data={balances}
        keyExtractor={(item) => String(item.userId)}
        renderItem={({ item }) => {
          const summary = memberBalanceLabel(item.netBalance, group.defaultCurrency);
          return (
            <View className="flex-row items-center border-b border-divider px-5 py-3">
              <View
                className="mr-3 h-10 w-10 items-center justify-center rounded-full"
                style={{ backgroundColor: avatarColor(item.userId) }}
              >
                <Text className="text-sm font-semibold text-white">{item.fullName.charAt(0).toUpperCase()}</Text>
              </View>
              <Text className="flex-1 text-sm font-medium text-ink">{item.fullName}</Text>
              <Text className={`text-sm font-medium ${summary.className}`}>{summary.label}</Text>
            </View>
          );
        }}
        ListEmptyComponent={<Text className="px-5 py-6 text-sm text-subtle">No members yet</Text>}
      />
    </SafeAreaView>
  );
}
