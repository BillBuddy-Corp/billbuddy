import { MaterialCommunityIcons } from '@expo/vector-icons';
import { RouteProp, useRoute } from '@react-navigation/native';
import { useState } from 'react';
import { Image, Modal, Pressable, ScrollView, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { authenticatedImageSource } from '../api/files';
import { RootStackParamList } from '../navigation/RootNavigator';
import { avatarColor } from '../utils/avatarColor';
import { categoryStyle } from '../utils/expenseCategory';

type Route = RouteProp<RootStackParamList, 'ExpenseDetail'>;

export function ExpenseDetailScreen() {
  const { expense } = useRoute<Route>().params;
  const [viewerOpen, setViewerOpen] = useState(false);
  const style = categoryStyle(expense.category);
  const payerNames = expense.payers.map((p) => p.fullName).join(', ');

  return (
    <SafeAreaView className="flex-1 bg-background" edges={['bottom']}>
      <ScrollView contentContainerStyle={{ padding: 20 }}>
        <View className="mb-5 flex-row items-center">
          <View
            className="mr-3 h-11 w-11 items-center justify-center rounded-full"
            style={{ backgroundColor: `${style.color}33` }}
          >
            <MaterialCommunityIcons name={style.icon} size={20} color={style.color} />
          </View>
          <View className="flex-1">
            <Text className="text-lg font-medium text-ink">{expense.description}</Text>
            <Text className="mt-0.5 text-xs text-subtle">Paid by {payerNames || '?'}</Text>
          </View>
          <Text className="text-lg font-semibold text-ink">
            {expense.amount.toFixed(2)} {expense.currency}
          </Text>
        </View>

        {expense.receiptUrl ? (
          <Pressable onPress={() => setViewerOpen(true)} className="mb-5">
            <Image
              source={authenticatedImageSource(expense.receiptUrl)}
              className="h-48 w-full rounded-lg bg-surface"
              resizeMode="cover"
            />
            <Text className="mt-1.5 text-xs text-subtle">Tap to view receipt</Text>
          </Pressable>
        ) : null}

        {expense.splitType === 'ITEMIZED' ? (
          <View>
            <Text className="mb-2 text-xs font-medium uppercase text-subtle">Items</Text>
            {expense.items.map((item) => (
              <View key={item.id} className="mb-3 border-b border-divider pb-3">
                <View className="flex-row items-center justify-between">
                  <Text className="text-sm font-medium text-ink">{item.name}</Text>
                  <Text className="text-sm text-ink">{item.amount.toFixed(2)}</Text>
                </View>
                {item.assignments.map((a) => (
                  <View key={a.userId} className="mt-1.5 flex-row items-center">
                    <View
                      className="mr-2 h-6 w-6 items-center justify-center rounded-full"
                      style={{ backgroundColor: avatarColor(a.userId) }}
                    >
                      <Text className="text-[10px] font-semibold text-white">
                        {a.fullName.charAt(0).toUpperCase()}
                      </Text>
                    </View>
                    <Text className="flex-1 text-xs text-subtle">
                      {a.fullName} · share {a.share}
                    </Text>
                    <Text className="text-xs font-medium text-ink">{a.amountOwed.toFixed(2)}</Text>
                  </View>
                ))}
              </View>
            ))}
          </View>
        ) : (
          <View>
            <Text className="mb-2 text-xs font-medium uppercase text-subtle">Split</Text>
            {expense.splits.map((split) => (
              <View key={split.userId} className="mb-2 flex-row items-center">
                <View
                  className="mr-2.5 h-7 w-7 items-center justify-center rounded-full"
                  style={{ backgroundColor: avatarColor(split.userId) }}
                >
                  <Text className="text-xs font-semibold text-white">{split.fullName.charAt(0).toUpperCase()}</Text>
                </View>
                <Text className="flex-1 text-sm text-ink">{split.fullName}</Text>
                <Text className="text-sm text-subtle">{split.amountOwed.toFixed(2)}</Text>
              </View>
            ))}
          </View>
        )}
      </ScrollView>

      <Modal visible={viewerOpen} transparent animationType="fade" onRequestClose={() => setViewerOpen(false)}>
        <Pressable
          className="flex-1 items-center justify-center bg-black/90"
          onPress={() => setViewerOpen(false)}
        >
          {expense.receiptUrl ? (
            <Image
              source={authenticatedImageSource(expense.receiptUrl)}
              className="h-full w-full"
              resizeMode="contain"
            />
          ) : null}
        </Pressable>
      </Modal>
    </SafeAreaView>
  );
}
