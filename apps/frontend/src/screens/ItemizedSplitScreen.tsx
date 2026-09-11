import { RouteProp, useNavigation, useRoute } from '@react-navigation/native';
import { FlatList, Pressable, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { RootStackParamList } from '../navigation/RootNavigator';
import { displayName, useAddExpenseFormStore } from '../store/addExpenseFormStore';
import { useAuthStore } from '../store/authStore';
import { avatarColor } from '../utils/avatarColor';

type Route = RouteProp<RootStackParamList, 'ItemizedSplit'>;

export function ItemizedSplitScreen() {
  const navigation = useNavigation();
  const route = useRoute<Route>();
  const { merchant, discountsNeedReview } = route.params ?? {};
  const currentUserId = useAuthStore((state) => state.user?.userId) ?? -1;
  const store = useAddExpenseFormStore();
  const { participants, items } = store;

  const totals = new Map<number, number>();
  items.forEach((item) => {
    if (item.assignedUserIds.length === 0) return;
    const share = item.amount / item.assignedUserIds.length;
    item.assignedUserIds.forEach((userId) => totals.set(userId, (totals.get(userId) ?? 0) + share));
  });
  const unassignedCount = items.filter((item) => item.assignedUserIds.length === 0).length;

  return (
    <SafeAreaView className="flex-1 bg-background">
      {merchant || discountsNeedReview ? (
        <View className="border-b border-divider px-5 py-3">
          {merchant ? <Text className="text-sm font-medium text-ink">{merchant}</Text> : null}
          {discountsNeedReview ? (
            <Text className="mt-1 text-xs text-yellow-500">
              Discounts on this receipt didn't fully reconcile -- item prices are still correct, but double-check
              the total.
            </Text>
          ) : null}
        </View>
      ) : null}

      <FlatList
        data={items}
        keyExtractor={(_, index) => String(index)}
        contentContainerStyle={{ paddingBottom: 16 }}
        renderItem={({ item, index }) => (
          <View className="border-b border-divider px-5 py-3">
            <View className="flex-row items-center justify-between">
              <Text className="flex-1 pr-3 text-sm font-medium text-ink">{item.name}</Text>
              <Text className="text-sm text-ink">{item.amount.toFixed(2)}</Text>
            </View>
            <View className="mt-2 flex-row flex-wrap gap-2">
              {participants.map((p) => {
                const assigned = item.assignedUserIds.includes(p.userId);
                return (
                  <Pressable
                    key={p.userId}
                    onPress={() => store.toggleItemAssignment(index, p.userId)}
                    className="h-8 w-8 items-center justify-center rounded-full"
                    style={{
                      backgroundColor: assigned ? avatarColor(p.userId) : 'transparent',
                      borderWidth: assigned ? 0 : 1,
                      borderColor: '#2C2C2E',
                    }}
                  >
                    <Text className={`text-xs font-semibold ${assigned ? 'text-white' : 'text-subtle'}`}>
                      {displayName(p, currentUserId).charAt(0).toUpperCase()}
                    </Text>
                  </Pressable>
                );
              })}
            </View>
            {item.assignedUserIds.length === 0 ? (
              <Text className="mt-1 text-xs text-red-400">Assign at least one person</Text>
            ) : null}
          </View>
        )}
        ListHeaderComponent={
          <Text className="px-5 pb-1 pt-4 text-xs font-medium uppercase text-subtle">
            Tap a person to include them in an item
          </Text>
        }
        ListFooterComponent={
          totals.size > 0 ? (
            <View className="px-5 pt-4">
              <Text className="mb-2 text-xs font-medium uppercase text-subtle">Per person</Text>
              {participants
                .filter((p) => totals.has(p.userId))
                .map((p) => (
                  <View key={p.userId} className="flex-row items-center justify-between py-1">
                    <Text className="text-sm text-ink">{displayName(p, currentUserId)}</Text>
                    <Text className="text-sm text-subtle">{(totals.get(p.userId) ?? 0).toFixed(2)}</Text>
                  </View>
                ))}
            </View>
          ) : null
        }
      />

      <View className="flex-row items-center justify-between border-t border-divider px-5 py-4">
        <Text className={`text-sm font-medium ${unassignedCount === 0 ? 'text-subtle' : 'text-red-400'}`}>
          {unassignedCount === 0 ? `${items.length} items` : `${unassignedCount} item(s) need someone assigned`}
        </Text>
        <Pressable
          onPress={() => navigation.goBack()}
          disabled={unassignedCount > 0}
          className={`rounded-lg bg-primary px-5 py-2.5 ${unassignedCount > 0 ? 'opacity-50' : ''}`}
        >
          <Text className="text-sm font-medium text-white">Done</Text>
        </Pressable>
      </View>
    </SafeAreaView>
  );
}
