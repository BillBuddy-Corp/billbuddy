import { MaterialCommunityIcons } from '@expo/vector-icons';
import { RouteProp, useFocusEffect, useNavigation, useRoute } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useCallback, useState } from 'react';
import { ActivityIndicator, Alert, FlatList, Pressable, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { getGroup, getGroupBalances, Group, GroupMember, listMembers } from '../api/groups';
import { RootStackParamList } from '../navigation/RootNavigator';
import { avatarColor } from '../utils/avatarColor';
import { getErrorMessage } from '../utils/errors';

type Route = RouteProp<RootStackParamList, 'GroupSettings'>;
type Navigation = NativeStackNavigationProp<RootStackParamList, 'GroupSettings'>;

function memberBalanceLabel(netBalance: number, currency: string): { label: string; className: string } {
  if (Math.abs(netBalance) < 0.01) {
    return { label: 'settled up', className: 'text-subtle' };
  }
  return netBalance > 0
    ? { label: `gets back ${netBalance.toFixed(2)} ${currency}`, className: 'text-green-500' }
    : { label: `owes ${Math.abs(netBalance).toFixed(2)} ${currency}`, className: 'text-red-400' };
}

export function GroupSettingsScreen() {
  const navigation = useNavigation<Navigation>();
  const route = useRoute<Route>();
  const { groupId } = route.params;

  const [group, setGroup] = useState<Group | null>(null);
  const [members, setMembers] = useState<GroupMember[]>([]);
  const [balances, setBalances] = useState<Map<number, number>>(new Map());
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    try {
      const [groupData, memberData, balanceData] = await Promise.all([
        getGroup(groupId),
        listMembers(groupId),
        getGroupBalances(groupId).catch(() => []),
      ]);
      setGroup(groupData);
      setMembers(memberData);
      setBalances(new Map(balanceData.map((b) => [b.userId, b.netBalance])));
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
      <View className="items-center border-b border-divider px-5 py-6">
        <View
          className="h-16 w-16 items-center justify-center rounded-2xl"
          style={{ backgroundColor: avatarColor(group.id) }}
        >
          <Text className="text-2xl font-semibold text-white">{group.name.charAt(0).toUpperCase()}</Text>
        </View>
        <View className="mt-3 flex-row items-center">
          <Text className="text-lg font-semibold text-ink">{group.name}</Text>
          <Pressable
            className="ml-2"
            onPress={() => Alert.alert('Coming soon', "Editing group details isn't available yet.")}
          >
            <MaterialCommunityIcons name="pencil-outline" size={16} color="#9CA3AF" />
          </Pressable>
        </View>
        <Text className="mt-1 text-sm text-subtle">
          {group.memberCount} {group.memberCount === 1 ? 'member' : 'members'} · {group.defaultCurrency}
        </Text>
      </View>

      <Pressable
        onPress={() => navigation.navigate('Invites', { groupId })}
        className="flex-row items-center border-b border-divider px-5 py-4"
      >
        <MaterialCommunityIcons name="account-plus-outline" size={20} color="#F5F5F7" />
        <Text className="ml-3 flex-1 text-sm font-medium text-ink">Add people to group</Text>
        <MaterialCommunityIcons name="chevron-right" size={20} color="#9CA3AF" />
      </Pressable>
      <Pressable
        onPress={() => navigation.navigate('Invites', { groupId })}
        className="flex-row items-center border-b border-divider px-5 py-4"
      >
        <MaterialCommunityIcons name="link-variant" size={20} color="#F5F5F7" />
        <Text className="ml-3 flex-1 text-sm font-medium text-ink">Invite via link</Text>
        <MaterialCommunityIcons name="chevron-right" size={20} color="#9CA3AF" />
      </Pressable>

      <Text className="px-5 pb-2 pt-6 text-xs font-medium uppercase text-subtle">Group members</Text>
      <FlatList
        data={members}
        keyExtractor={(item) => String(item.userId)}
        renderItem={({ item }) => {
          const summary = memberBalanceLabel(balances.get(item.userId) ?? 0, group.defaultCurrency);
          return (
            <View className="flex-row items-center border-b border-divider px-5 py-3">
              <View
                className="mr-3 h-10 w-10 items-center justify-center rounded-full"
                style={{ backgroundColor: avatarColor(item.userId) }}
              >
                <Text className="text-sm font-semibold text-white">{item.fullName.charAt(0).toUpperCase()}</Text>
              </View>
              <View className="flex-1 pr-3">
                <Text className="text-sm font-medium text-ink">{item.fullName}</Text>
                <Text className="text-xs text-subtle">{item.email}</Text>
              </View>
              <Text className={`text-xs font-medium ${summary.className}`}>{summary.label}</Text>
            </View>
          );
        }}
      />
    </SafeAreaView>
  );
}
