import { MaterialCommunityIcons } from '@expo/vector-icons';
import { RouteProp, useFocusEffect, useNavigation, useRoute } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useCallback, useState } from 'react';
import { ActivityIndicator, Alert, FlatList, Pressable, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Expense } from '../api/expenses';
import {
  Friend,
  FriendBalance,
  getFriendBalance,
  listFriendExpenses,
  listFriends,
  removeFriend,
} from '../api/friends';
import { RootStackParamList } from '../navigation/RootNavigator';
import { useAddExpenseFormStore } from '../store/addExpenseFormStore';
import { useAuthStore } from '../store/authStore';
import { getErrorMessage } from '../utils/errors';
import { categoryStyle } from '../utils/expenseCategory';
import { loadExpenseTargetContext } from '../utils/expenseTargetContext';
import { pickAndScanReceipt } from '../utils/receiptScan';
import { showAddExpenseOptions } from '../utils/showAddExpenseOptions';

type Route = RouteProp<RootStackParamList, 'FriendDetail'>;
type Navigation = NativeStackNavigationProp<RootStackParamList, 'FriendDetail'>;

function balanceLine(balances: FriendBalance[]): { label: string; className: string } {
  const nonZero = balances.filter((b) => Math.abs(b.amount) >= 0.01);
  if (nonZero.length === 0) {
    return { label: 'You are settled up', className: 'text-subtle' };
  }
  return {
    label: nonZero
      .map((b) =>
        b.amount > 0
          ? `Owes you ${b.amount.toFixed(2)} ${b.currency}`
          : `You owe ${Math.abs(b.amount).toFixed(2)} ${b.currency}`
      )
      .join('\n'),
    className: nonZero.some((b) => b.amount < 0) ? 'text-red-400' : 'text-green-500',
  };
}

export function FriendDetailScreen() {
  const navigation = useNavigation<Navigation>();
  const route = useRoute<Route>();
  const { friendUserId } = route.params;
  const currentUser = useAuthStore((state) => state.user);
  const formStore = useAddExpenseFormStore();

  const [friend, setFriend] = useState<Friend | null>(null);
  const [balances, setBalances] = useState<FriendBalance[]>([]);
  const [expenses, setExpenses] = useState<Expense[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [removing, setRemoving] = useState(false);
  const [scanning, setScanning] = useState(false);

  const load = useCallback(async () => {
    try {
      const [friends, balance, expenseList] = await Promise.all([
        listFriends(),
        getFriendBalance(friendUserId),
        listFriendExpenses(friendUserId),
      ]);
      setFriend(friends.find((f) => f.userId === friendUserId) ?? null);
      setBalances(balance);
      setExpenses(expenseList);
      setError('');
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, [friendUserId]);

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

  if (error || !friend) {
    return (
      <SafeAreaView className="flex-1 items-center justify-center bg-background px-6">
        <Text className="text-center text-sm text-red-400">{error || 'Friend not found'}</Text>
      </SafeAreaView>
    );
  }

  const summary = balanceLine(balances);
  const isSettled = balances.every((b) => Math.abs(b.amount) < 0.01);

  const handleRemoveFriend = () => {
    Alert.alert(
      'Remove friend?',
      isSettled
        ? `${friend.fullName} will be removed from your friends list. Your past expenses together are kept, but you'll need to add them again to split future ones.`
        : `You still have an unsettled balance with ${friend.fullName}. Removing them won't settle it, and they'll disappear from your Friends and Activity tabs until you add them again.`,
      [
        { text: 'Cancel', style: 'cancel' },
        {
          text: 'Remove',
          style: 'destructive',
          onPress: async () => {
            setRemoving(true);
            try {
              await removeFriend(friendUserId);
              navigation.goBack();
            } catch (err) {
              Alert.alert('Could not remove friend', getErrorMessage(err));
              setRemoving(false);
            }
          },
        },
      ]
    );
  };

  const handleScanReceipt = async () => {
    if (!currentUser || scanning) return;
    setScanning(true);
    try {
      const result = await pickAndScanReceipt();
      if (!result) return;
      const context = await loadExpenseTargetContext({ friendUserId }, currentUser);
      formStore.init(context.participants, context.currency, currentUser.userId, context.groupDefaultCurrency);
      formStore.setDescription(result.scan.merchant ?? '');
      formStore.setAmount(String(result.scan.amount));
      if (result.scan.currency) formStore.setCurrency(result.scan.currency);
      formStore.setItemsFromScan(
        result.scan.items.map((item) => ({ name: item.name, amount: item.amount, quantity: item.quantity }))
      );
      formStore.setSplitType('ITEMIZED');
      formStore.setReceiptFileId(result.fileId);
      navigation.navigate('AddExpense', {
        friendUserId,
        skipInit: true,
        headerLabel: context.headerLabel,
        headerColorSeed: context.headerColorSeed,
      });
      navigation.navigate('ItemizedSplit', {
        merchant: result.scan.merchant ?? undefined,
        transactionDate: result.scan.transactionDate ?? undefined,
        subtotal: result.scan.subtotal ?? undefined,
        otherDiscount: result.scan.otherDiscount ?? undefined,
        voucherAmount: result.scan.voucherAmount ?? undefined,
        discountsNeedReview: result.scan.discountsNeedReview,
      });
    } finally {
      setScanning(false);
    }
  };

  return (
    <SafeAreaView className="flex-1 bg-background">
      <View className="border-b border-divider px-5 py-4">
        <View className="flex-row items-start justify-between">
          <Text className="text-xl font-medium text-ink">{friend.fullName}</Text>
          <Pressable onPress={handleRemoveFriend} disabled={removing} className="p-1">
            {removing ? (
              <ActivityIndicator size="small" color="#F87171" />
            ) : (
              <MaterialCommunityIcons name="account-remove-outline" size={20} color="#F87171" />
            )}
          </Pressable>
        </View>
        <Text className="mt-1 text-sm text-subtle">{friend.email}</Text>
        <Text className={`mt-3 text-sm font-medium ${summary.className}`}>{summary.label}</Text>
        <Pressable
          onPress={() => showAddExpenseOptions(handleScanReceipt, () => navigation.navigate('AddExpense', { friendUserId }))}
          disabled={scanning}
          className="mt-3 flex-row items-center self-start rounded-lg bg-primary px-4 py-2"
        >
          {scanning ? <ActivityIndicator size="small" color="white" className="mr-2" /> : null}
          <Text className="text-sm font-medium text-white">Add expense</Text>
        </Pressable>
      </View>

      <Text className="px-5 pb-2 pt-4 text-xs font-medium uppercase text-subtle">Expenses</Text>
      <FlatList
        data={expenses}
        keyExtractor={(item) => String(item.id)}
        renderItem={({ item }) => {
          const style = categoryStyle(item.category);
          return (
            <Pressable
              onPress={() => navigation.navigate('ExpenseDetail', { expense: item })}
              className="flex-row items-center border-b border-divider px-5 py-3"
            >
              <View
                className="mr-3 h-10 w-10 items-center justify-center rounded-full"
                style={{ backgroundColor: `${style.color}33` }}
              >
                <MaterialCommunityIcons name={style.icon} size={18} color={style.color} />
              </View>
              <View className="flex-1 pr-3">
                <Text className="text-sm font-medium text-ink">{item.description}</Text>
                <Text className="text-xs text-subtle">Paid by {item.payers[0]?.fullName ?? '?'}</Text>
              </View>
              <Text className="text-sm text-ink">
                {item.amount.toFixed(2)} {item.currency}
              </Text>
            </Pressable>
          );
        }}
        ListEmptyComponent={
          <Text className="px-5 pb-6 text-sm text-subtle">No expenses with {friend.fullName} yet</Text>
        }
      />
    </SafeAreaView>
  );
}
