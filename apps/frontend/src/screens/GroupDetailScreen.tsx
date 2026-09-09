import { RouteProp, useNavigation, useRoute } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useEffect, useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { getGroup, Group, GroupMember, listMembers } from '../api/groups';
import { RootStackParamList } from '../navigation/RootNavigator';
import { getErrorMessage } from '../utils/errors';

type Route = RouteProp<RootStackParamList, 'GroupDetail'>;
type Navigation = NativeStackNavigationProp<RootStackParamList, 'GroupDetail'>;

export function GroupDetailScreen() {
  const navigation = useNavigation<Navigation>();
  const route = useRoute<Route>();
  const { groupId } = route.params;

  const [group, setGroup] = useState<Group | null>(null);
  const [members, setMembers] = useState<GroupMember[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    (async () => {
      try {
        const [groupData, memberData] = await Promise.all([
          getGroup(groupId),
          listMembers(groupId),
        ]);
        setGroup(groupData);
        setMembers(memberData);
      } catch (err) {
        setError(getErrorMessage(err));
      } finally {
        setLoading(false);
      }
    })();
  }, [groupId]);

  if (loading) {
    return (
      <SafeAreaView className="flex-1 items-center justify-center bg-white">
        <ActivityIndicator color="#2F6FED" />
      </SafeAreaView>
    );
  }

  if (error || !group) {
    return (
      <SafeAreaView className="flex-1 items-center justify-center bg-white px-6">
        <Text className="text-center text-sm text-red-500">
          {error || 'Group not found'}
        </Text>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView className="flex-1 bg-white">
      <View className="border-b border-gray-100 px-5 py-4">
        <Text className="text-xl font-medium text-black">{group.name}</Text>
        {group.description ? (
          <Text className="mt-1 text-sm text-gray-500">{group.description}</Text>
        ) : null}
        <Text className="mt-2 text-xs text-gray-400">
          {group.memberCount} {group.memberCount === 1 ? 'member' : 'members'} · {group.defaultCurrency}
        </Text>
        {group.currentUserRole === 'ADMIN' ? (
          <Pressable
            onPress={() => navigation.navigate('Invites', { groupId })}
            className="mt-3 self-start rounded-lg bg-primary px-4 py-2"
          >
            <Text className="text-sm font-medium text-white">Invite people</Text>
          </Pressable>
        ) : null}
      </View>

      <Text className="px-5 pb-2 pt-4 text-xs font-medium uppercase text-gray-400">
        Members
      </Text>
      <FlatList
        data={members}
        keyExtractor={(item) => String(item.userId)}
        renderItem={({ item }) => (
          <View className="flex-row items-center justify-between border-b border-gray-100 px-5 py-3">
            <View>
              <Text className="text-sm font-medium text-black">{item.fullName}</Text>
              <Text className="text-xs text-gray-500">{item.email}</Text>
            </View>
            {item.role === 'ADMIN' ? (
              <View className="rounded-full bg-primary-tint px-2.5 py-1">
                <Text className="text-xs font-medium text-primary-dark">Admin</Text>
              </View>
            ) : null}
          </View>
        )}
      />
    </SafeAreaView>
  );
}
