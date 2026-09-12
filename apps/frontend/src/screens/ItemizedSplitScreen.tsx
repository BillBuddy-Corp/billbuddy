import { MaterialCommunityIcons } from '@expo/vector-icons';
import { RouteProp, useNavigation, useRoute } from '@react-navigation/native';
import { FlatList, Pressable, Text, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { RootStackParamList } from '../navigation/RootNavigator';
import { displayName, ItemDraft, useAddExpenseFormStore } from '../store/addExpenseFormStore';
import { useAuthStore } from '../store/authStore';
import { avatarColor } from '../utils/avatarColor';

type Route = RouteProp<RootStackParamList, 'ItemizedSplit'>;

// item.amount split across its included people, weighted by each person's
// share (typed text, defaulting to 1 -- an unparseable or non-positive entry
// also falls back to 1 rather than silently zeroing someone out).
function shareWeight(raw: string | undefined): number {
  const parsed = Number(raw);
  return raw && Number.isFinite(parsed) && parsed > 0 ? parsed : 1;
}

function perPersonAmounts(item: ItemDraft): Map<number, number> {
  const totalShares = item.assignedUserIds.reduce((sum, userId) => sum + shareWeight(item.shares[userId]), 0);
  const result = new Map<number, number>();
  if (totalShares <= 0) return result;
  item.assignedUserIds.forEach((userId) => {
    result.set(userId, (item.amount * shareWeight(item.shares[userId])) / totalShares);
  });
  return result;
}

export function ItemizedSplitScreen() {
  const navigation = useNavigation();
  const route = useRoute<Route>();
  const { merchant, transactionDate, subtotal, otherDiscount, voucherAmount, discountsNeedReview } =
    route.params ?? {};
  const currentUserId = useAuthStore((state) => state.user?.userId) ?? -1;
  const store = useAddExpenseFormStore();
  const { participants, items } = store;

  const totals = new Map<number, number>();
  items.forEach((item) => {
    perPersonAmounts(item).forEach((amount, userId) => totals.set(userId, (totals.get(userId) ?? 0) + amount));
  });
  const unassignedCount = items.filter((item) => item.assignedUserIds.length === 0).length;

  const hasReceiptDetails =
    merchant || transactionDate || subtotal != null || otherDiscount != null || voucherAmount != null;

  return (
    <SafeAreaView className="flex-1 bg-background">
      {hasReceiptDetails ? (
        <View className="border-b border-divider px-5 py-3">
          {merchant ? <Text className="text-sm font-medium text-ink">{merchant}</Text> : null}
          {transactionDate ? <Text className="mt-0.5 text-xs text-subtle">{transactionDate}</Text> : null}
          {subtotal != null ? (
            <Text className="mt-1 text-xs text-subtle">Subtotal (before discount) {subtotal.toFixed(2)}</Text>
          ) : null}
          {otherDiscount != null ? (
            <Text className="text-xs text-subtle">Receipt discount −{otherDiscount.toFixed(2)}</Text>
          ) : null}
          {voucherAmount != null ? (
            <Text className="text-xs text-subtle">Voucher −{voucherAmount.toFixed(2)}</Text>
          ) : null}
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
              <View className="flex-1 flex-row items-baseline pr-3">
                <Text className="text-sm font-medium text-ink">{item.name}</Text>
                {item.quantity != null ? (
                  <Text className="ml-1.5 text-xs text-subtle">×{item.quantity}</Text>
                ) : null}
              </View>
              <Text className="text-sm text-ink">{item.amount.toFixed(2)}</Text>
            </View>
            <View className="mt-2">
              {participants.map((p) => {
                const assigned = item.assignedUserIds.includes(p.userId);
                return (
                  <View key={p.userId} className="mt-1.5 flex-row items-center">
                    <Pressable
                      onPress={() => store.toggleItemAssignment(index, p.userId)}
                      className="flex-1 flex-row items-center"
                    >
                      <MaterialCommunityIcons
                        name={assigned ? 'checkbox-marked' : 'checkbox-blank-outline'}
                        size={20}
                        color={assigned ? '#2F6FED' : '#9CA3AF'}
                      />
                      <View
                        className="ml-2.5 mr-2 h-7 w-7 items-center justify-center rounded-full"
                        style={{ backgroundColor: avatarColor(p.userId) }}
                      >
                        <Text className="text-xs font-semibold text-white">
                          {displayName(p, currentUserId).charAt(0).toUpperCase()}
                        </Text>
                      </View>
                      <Text className="text-sm text-ink">{displayName(p, currentUserId)}</Text>
                    </Pressable>
                    {assigned ? (
                      <View className="flex-row items-center">
                        <Text className="mr-1.5 text-xs text-subtle">share</Text>
                        <TextInput
                          value={item.shares[p.userId] ?? '1'}
                          onChangeText={(value) => store.setItemShare(index, p.userId, value)}
                          keyboardType="decimal-pad"
                          className="w-12 rounded-lg border border-divider bg-surface px-2 py-1 text-center text-sm text-ink"
                        />
                      </View>
                    ) : null}
                  </View>
                );
              })}
            </View>
            {item.assignedUserIds.length === 0 ? (
              <Text className="mt-1.5 text-xs text-red-400">Assign at least one person</Text>
            ) : null}
          </View>
        )}
        ListHeaderComponent={
          <Text className="px-5 pb-1 pt-4 text-xs font-medium uppercase text-subtle">
            Tick who had each item -- adjust "share" if it wasn't split evenly
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
