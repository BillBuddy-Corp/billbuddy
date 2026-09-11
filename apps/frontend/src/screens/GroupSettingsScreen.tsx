import { MaterialCommunityIcons } from '@expo/vector-icons';
import { RouteProp, useFocusEffect, useNavigation, useRoute } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useCallback, useState } from 'react';
import { ActivityIndicator, Alert, FlatList, Pressable, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { addFriend, listFriends } from '../api/friends';
import { getGroup, getGroupBalances, Group, GroupMember, listMembers, removeMember } from '../api/groups';
import { RootStackParamList } from '../navigation/RootNavigator';
import { useAuthStore } from '../store/authStore';
import { avatarColor } from '../utils/avatarColor';
import { memberBalanceLabel } from '../utils/balance';
import { getErrorMessage } from '../utils/errors';

type Route = RouteProp<RootStackParamList, 'GroupSettings'>;
type Navigation = NativeStackNavigationProp<RootStackParamList, 'GroupSettings'>;

export function GroupSettingsScreen() {
  const navigation = useNavigation<Navigation>();
  const route = useRoute<Route>();
  const { groupId } = route.params;
  const currentUserId = useAuthStore((state) => state.user?.userId);

  const [group, setGroup] = useState<Group | null>(null);
  const [members, setMembers] = useState<GroupMember[]>([]);
  const [balances, setBalances] = useState<Map<number, number>>(new Map());
  const [friendIds, setFriendIds] = useState<Set<number>>(new Set());
  const [addingFriendId, setAddingFriendId] = useState<number | null>(null);
  const [removingMemberId, setRemovingMemberId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    try {
      const [groupData, memberData, balanceData, friends] = await Promise.all([
        getGroup(groupId),
        listMembers(groupId),
        getGroupBalances(groupId).catch(() => []),
        listFriends().catch(() => []),
      ]);
      setGroup(groupData);
      setMembers(memberData);
      setBalances(new Map(balanceData.map((b) => [b.userId, b.netBalance])));
      setFriendIds(new Set(friends.map((f) => f.userId)));
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

  const handleAddFriend = async (member: GroupMember) => {
    setAddingFriendId(member.userId);
    try {
      await addFriend(member.email);
      setFriendIds((prev) => new Set(prev).add(member.userId));
    } catch (err) {
      Alert.alert('Could not add friend', getErrorMessage(err));
    } finally {
      setAddingFriendId(null);
    }
  };

  const handleRemoveMember = (member: GroupMember) => {
    const isSettled = Math.abs(balances.get(member.userId) ?? 0) < 0.01;
    Alert.alert(
      'Remove from group?',
      isSettled
        ? `${member.fullName} will be removed from ${group?.name}. This can't be undone from here, but you can add them back later.`
        : `${member.fullName} still has an unsettled balance in this group. Removing them won't settle it.`,
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Remove',
          style: 'destructive',
          onPress: async () => {
            setRemovingMemberId(member.userId);
            try {
              await removeMember(groupId, member.userId);
              await load();
            } catch (err) {
              Alert.alert('Could not remove member', getErrorMessage(err));
            } finally {
              setRemovingMemberId(null);
            }
          },
        },
      ]
    );
  };

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
          {group.currentUserRole === 'ADMIN' ? (
            <Pressable
              className="ml-2"
              onPress={() =>
                navigation.navigate('EditGroup', {
                  groupId,
                  name: group.name,
                  description: group.description,
                  defaultCurrency: group.defaultCurrency,
                })
              }
            >
              <MaterialCommunityIcons name="pencil-outline" size={16} color="#9CA3AF" />
            </Pressable>
          ) : null}
        </View>
        <Text className="mt-1 text-sm text-subtle">
          {group.memberCount} {group.memberCount === 1 ? 'member' : 'members'} · {group.defaultCurrency}
        </Text>
      </View>

      <Pressable
        onPress={() => navigation.navigate('Invites', { groupId, isAdmin: group.currentUserRole === 'ADMIN' })}
        className="flex-row items-center border-b border-divider px-5 py-4"
      >
        <MaterialCommunityIcons name="account-plus-outline" size={20} color="#F5F5F7" />
        <Text className="ml-3 flex-1 text-sm font-medium text-ink">Add people to group</Text>
        <MaterialCommunityIcons name="chevron-right" size={20} color="#9CA3AF" />
      </Pressable>
      <Pressable
        onPress={() => navigation.navigate('Invites', { groupId, isAdmin: group.currentUserRole === 'ADMIN' })}
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
          const isSelf = item.userId === currentUserId;
          const isFriend = friendIds.has(item.userId);
          const canRemove = !isSelf && group.currentUserRole === 'ADMIN';
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
              <View className="items-end">
                <Text className={`text-xs font-medium ${summary.className}`}>{summary.label}</Text>
                {!isSelf && !isFriend ? (
                  <Pressable
                    onPress={() => handleAddFriend(item)}
                    disabled={addingFriendId === item.userId}
                    className="mt-1.5 flex-row items-center"
                  >
                    {addingFriendId === item.userId ? (
                      <ActivityIndicator size="small" color="#2F6FED" />
                    ) : (
                      <>
                        <MaterialCommunityIcons name="account-plus-outline" size={14} color="#2F6FED" />
                        <Text className="ml-1 text-xs font-medium text-primary">Add friend</Text>
                      </>
                    )}
                  </Pressable>
                ) : null}
              </View>
              {canRemove ? (
                <Pressable
                  onPress={() => handleRemoveMember(item)}
                  disabled={removingMemberId === item.userId}
                  className="ml-3 p-1"
                >
                  {removingMemberId === item.userId ? (
                    <ActivityIndicator size="small" color="#F87171" />
                  ) : (
                    <MaterialCommunityIcons name="account-remove-outline" size={18} color="#F87171" />
                  )}
                </Pressable>
              ) : null}
            </View>
          );
        }}
      />
    </SafeAreaView>
  );
}
