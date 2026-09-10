import { Pressable, Text, View } from 'react-native';

import { Group } from '../../api/groups';
import { avatarColor } from '../../utils/avatarColor';

type GroupListItemProps = {
  group: Group;
  // null while the balance is still loading for this row
  netBalance: number | null;
  onPress: () => void;
};

export function balanceLine(netBalance: number, currency: string): { label: string; className: string } {
  if (Math.abs(netBalance) < 0.01) {
    return { label: 'settled up', className: 'text-subtle' };
  }
  return netBalance > 0
    ? { label: `you are owed ${netBalance.toFixed(2)} ${currency}`, className: 'text-green-500' }
    : { label: `you owe ${Math.abs(netBalance).toFixed(2)} ${currency}`, className: 'text-red-400' };
}

export function GroupListItem({ group, netBalance, onPress }: GroupListItemProps) {
  const summary = netBalance === null ? null : balanceLine(netBalance, group.defaultCurrency);

  return (
    <Pressable onPress={onPress} className="flex-row items-center border-b border-divider px-4 py-4">
      <View
        className="mr-3 h-12 w-12 items-center justify-center rounded-2xl"
        style={{ backgroundColor: avatarColor(group.id) }}
      >
        <Text className="text-lg font-semibold text-white">{group.name.charAt(0).toUpperCase()}</Text>
      </View>
      <View className="flex-1 pr-3">
        <Text className="text-base font-medium text-ink">{group.name}</Text>
        <Text className="mt-0.5 text-sm text-subtle">
          {group.memberCount} {group.memberCount === 1 ? 'member' : 'members'} · {group.defaultCurrency}
        </Text>
      </View>
      {summary ? (
        <Text className={`text-xs font-medium ${summary.className}`}>{summary.label}</Text>
      ) : null}
    </Pressable>
  );
}
