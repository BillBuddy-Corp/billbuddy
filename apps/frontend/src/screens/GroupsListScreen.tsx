import { useFocusEffect, useNavigation } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useCallback, useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Group, listGroups } from '../api/groups';
import { GroupListItem } from '../components/molecules/GroupListItem';
import { RootStackParamList } from '../navigation/RootNavigator';
import { getErrorMessage } from '../utils/errors';

type Navigation = NativeStackNavigationProp<RootStackParamList, 'MainTabs'>;

export function GroupsListScreen() {
  const navigation = useNavigation<Navigation>();

  const [groups, setGroups] = useState<Group[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const loadGroups = useCallback(async () => {
    try {
      const data = await listGroups();
      setGroups(data);
      setError('');
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, []);

  // Re-fetch every time this tab regains focus (e.g. after creating a
  // group, or coming back from a group's detail screen) rather than only
  // on first mount.
  useFocusEffect(
    useCallback(() => {
      loadGroups();
    }, [loadGroups])
  );

  return (
    <SafeAreaView className="flex-1 bg-white">
      <View className="flex-row items-center justify-between border-b border-gray-100 px-4 py-3">
        <Text className="text-xl font-medium text-black">Groups</Text>
        <Pressable
          onPress={() => navigation.navigate('CreateGroup')}
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
      ) : groups.length === 0 ? (
        <View className="flex-1 items-center justify-center px-6">
          <Text className="text-base font-medium text-black">No groups yet</Text>
          <Text className="mt-1 text-center text-sm text-gray-500">
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
          data={groups}
          keyExtractor={(item) => String(item.id)}
          renderItem={({ item }) => (
            <GroupListItem
              group={item}
              onPress={() => navigation.navigate('GroupDetail', { groupId: item.id })}
            />
          )}
        />
      )}
    </SafeAreaView>
  );
}
