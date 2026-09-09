import { MaterialCommunityIcons } from '@expo/vector-icons';
import { RouteProp, useNavigation, useRoute } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { getGroup, Group } from '../api/groups';
import { RootStackParamList } from '../navigation/RootNavigator';
import { getErrorMessage } from '../utils/errors';

type Route = RouteProp<RootStackParamList, 'GroupDetail'>;
type Navigation = NativeStackNavigationProp<RootStackParamList, 'GroupDetail'>;

export function GroupDetailScreen() {
  const navigation = useNavigation<Navigation>();
  const route = useRoute<Route>();
  const { groupId } = route.params;

  const [group, setGroup] = useState<Group | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    (async () => {
      try {
        const groupData = await getGroup(groupId);
        setGroup(groupData);
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
      <View className="flex-row items-start justify-between border-b border-gray-100 px-5 py-4">
        <View className="flex-1 pr-3">
          <Text className="text-xl font-medium text-black">{group.name}</Text>
          {group.description ? (
            <Text className="mt-1 text-sm text-gray-500">{group.description}</Text>
          ) : null}
          <Text className="mt-2 text-xs text-gray-400">
            {group.memberCount} {group.memberCount === 1 ? 'member' : 'members'} · {group.defaultCurrency}
          </Text>
        </View>
        <Pressable onPress={() => navigation.navigate('GroupSettings', { groupId })}>
          <MaterialCommunityIcons name="cog-outline" size={24} color="#374151" />
        </Pressable>
      </View>
    </SafeAreaView>
  );
}
