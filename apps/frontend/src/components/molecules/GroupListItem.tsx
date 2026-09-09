import { Pressable, Text, View } from 'react-native';

import { Group } from '../../api/groups';

type GroupListItemProps = {
  group: Group;
  onPress: () => void;
};

export function GroupListItem({ group, onPress }: GroupListItemProps) {
  const isAdmin = group.currentUserRole === 'ADMIN';

  return (
    <Pressable
      onPress={onPress}
      className="flex-row items-center justify-between border-b border-gray-100 px-4 py-4"
    >
      <View className="flex-1 pr-3">
        <Text className="text-base font-medium text-black">{group.name}</Text>
        <Text className="mt-0.5 text-sm text-gray-500">
          {group.memberCount} {group.memberCount === 1 ? 'member' : 'members'} · {group.defaultCurrency}
        </Text>
      </View>
      {isAdmin ? (
        <View className="rounded-full bg-primary-tint px-2.5 py-1">
          <Text className="text-xs font-medium text-primary-dark">Admin</Text>
        </View>
      ) : null}
    </Pressable>
  );
}
