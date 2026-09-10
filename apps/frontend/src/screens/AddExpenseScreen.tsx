import { MaterialCommunityIcons } from '@expo/vector-icons';
import { RouteProp, useNavigation, useRoute } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useEffect, useRef, useState } from 'react';
import { KeyboardAvoidingView, Platform, Pressable, ScrollView, Text, TextInput, View } from 'react-native';

import { createExpense, CreateExpenseRequest } from '../api/expenses';
import { Friend, listFriends } from '../api/friends';
import { getGroup, listMembers } from '../api/groups';
import { RootStackParamList } from '../navigation/RootNavigator';
import { displayName, useAddExpenseFormStore } from '../store/addExpenseFormStore';
import { useAuthStore } from '../store/authStore';
import { avatarColor } from '../utils/avatarColor';
import { getErrorMessage } from '../utils/errors';
import { categoryStyle } from '../utils/expenseCategory';

type Route = RouteProp<RootStackParamList, 'AddExpense'>;
type Navigation = NativeStackNavigationProp<RootStackParamList, 'AddExpense'>;

const CATEGORIES = ['Food', 'Groceries', 'Transport', 'Travel', 'Rent', 'Utilities', 'Entertainment', 'Shopping'];

function splitLabel(splitType: string): string {
  if (splitType === 'EXACT') return 'unequally';
  if (splitType === 'PERCENTAGE') return 'by percentages';
  return 'equally';
}

export function AddExpenseScreen() {
  const navigation = useNavigation<Navigation>();
  const route = useRoute<Route>();
  const target = route.params;
  const currentUser = useAuthStore((state) => state.user);

  const store = useAddExpenseFormStore();
  const initialized = useRef(false);

  const [headerLabel, setHeaderLabel] = useState('');
  const [headerColorSeed, setHeaderColorSeed] = useState(0);
  const [currencyEditable, setCurrencyEditable] = useState(false);
  const [description, setDescription] = useState('');
  const [category, setCategory] = useState<string | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!currentUser || initialized.current) return;
    initialized.current = true;

    (async () => {
      if ('groupId' in target) {
        const [group, members] = await Promise.all([getGroup(target.groupId), listMembers(target.groupId)]);
        setHeaderLabel(`With ${group.name}`);
        setHeaderColorSeed(target.groupId);
        setCurrencyEditable(false);
        store.init(
          members.map((m) => ({ userId: m.userId, fullName: m.fullName })),
          group.defaultCurrency,
          currentUser.userId
        );
      } else {
        const friends = await listFriends();
        const friend = friends.find((f: Friend) => f.userId === target.friendUserId);
        setHeaderLabel(`With you and ${friend?.fullName ?? 'them'}`);
        setHeaderColorSeed(target.friendUserId);
        setCurrencyEditable(true);
        store.init(
          [
            { userId: currentUser.userId, fullName: currentUser.fullName },
            { userId: target.friendUserId, fullName: friend?.fullName ?? 'Friend' },
          ],
          'INR',
          currentUser.userId
        );
      }
    })();
  }, [currentUser, target, store]);

  const payer = store.participants.find((p) => p.userId === store.paidByUserId);

  const handleCreate = async () => {
    if (!description.trim()) {
      setError('Enter a description');
      return;
    }
    const parsedAmount = Number(store.amount);
    if (!store.amount.trim() || Number.isNaN(parsedAmount) || parsedAmount <= 0) {
      setError('Enter a valid amount');
      return;
    }
    if (!store.paidByUserId) {
      setError('Choose who paid');
      return;
    }
    if (store.splitParticipantIds.length === 0) {
      setError('Choose at least one person to split with');
      return;
    }

    const base = {
      description: description.trim(),
      amount: parsedAmount,
      currency: store.currency,
      category: category ?? undefined,
      paidByUserId: store.paidByUserId,
    };

    let request: CreateExpenseRequest;
    if (store.splitType === 'EQUAL') {
      request = { ...base, splitType: 'EQUAL', participantUserIds: store.splitParticipantIds };
    } else if (store.splitType === 'EXACT') {
      const exactAmounts = store.splitParticipantIds.map((userId) => ({
        userId,
        amount: Number(store.exactAmounts[userId] ?? '0'),
      }));
      const sum = exactAmounts.reduce((s, e) => s + e.amount, 0);
      if (Math.abs(sum - parsedAmount) > 0.01) {
        setError(`Amounts must add up to ${parsedAmount.toFixed(2)} (currently ${sum.toFixed(2)})`);
        return;
      }
      request = { ...base, splitType: 'EXACT', exactAmounts };
    } else {
      const percentages = store.splitParticipantIds.map((userId) => ({
        userId,
        percentage: Number(store.percentages[userId] ?? '0'),
      }));
      const sum = percentages.reduce((s, e) => s + e.percentage, 0);
      if (Math.abs(sum - 100) > 0.01) {
        setError(`Percentages must add up to 100 (currently ${sum.toFixed(1)})`);
        return;
      }
      request = { ...base, splitType: 'PERCENTAGE', percentages };
    }

    setError('');
    setLoading(true);
    try {
      await createExpense(target, request);
      store.reset();
      navigation.goBack();
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };

  return (
    <KeyboardAvoidingView
      className="flex-1 bg-background"
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <ScrollView contentContainerStyle={{ padding: 20 }} keyboardShouldPersistTaps="handled">
        <View className="mb-5 flex-row items-center">
          <View
            className="mr-3 h-10 w-10 items-center justify-center rounded-full"
            style={{ backgroundColor: avatarColor(headerColorSeed) }}
          >
            <MaterialCommunityIcons name="account-multiple" size={18} color="white" />
          </View>
          <Text className="flex-1 text-sm font-medium text-ink">{headerLabel}</Text>
        </View>

        <View className="mb-5 flex-row items-center border-b border-divider pb-2">
          <MaterialCommunityIcons name="receipt" size={20} color="#9CA3AF" />
          <TextInput
            placeholder="Description"
            placeholderTextColor="#9CA3AF"
            value={description}
            onChangeText={setDescription}
            className="ml-3 flex-1 text-base text-ink"
          />
        </View>

        <View className="mb-5 flex-row items-center border-b border-divider pb-2">
          {currencyEditable ? (
            <TextInput
              value={store.currency}
              onChangeText={store.setCurrency}
              autoCapitalize="characters"
              maxLength={3}
              className="w-14 text-lg font-medium text-subtle"
            />
          ) : (
            <Text className="text-lg font-medium text-subtle">{store.currency}</Text>
          )}
          <TextInput
            placeholder="0.00"
            placeholderTextColor="#9CA3AF"
            keyboardType="decimal-pad"
            value={store.amount}
            onChangeText={store.setAmount}
            className="ml-3 flex-1 text-2xl font-semibold text-ink"
          />
        </View>

        <View className="mb-6 flex-row flex-wrap items-center">
          <Text className="text-sm text-subtle">Paid by</Text>
          <Pressable
            onPress={() => navigation.navigate('WhoPaid')}
            className="mx-1.5 rounded-full bg-surface px-3 py-1"
          >
            <Text className="text-sm font-medium text-primary">
              {payer ? displayName(payer, currentUser?.userId ?? -1) : '...'}
            </Text>
          </Pressable>
          <Text className="text-sm text-subtle">and split</Text>
          <Pressable
            onPress={() => navigation.navigate('AdjustSplit')}
            className="ml-1.5 rounded-full bg-surface px-3 py-1"
          >
            <Text className="text-sm font-medium text-primary">{splitLabel(store.splitType)}</Text>
          </Pressable>
        </View>

        <Text className="mb-2 text-sm text-subtle">Category (optional)</Text>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} className="mb-6">
          <View className="flex-row gap-2">
            {CATEGORIES.map((c) => {
              const selected = category === c;
              const style = categoryStyle(c);
              return (
                <Pressable
                  key={c}
                  onPress={() => setCategory(selected ? null : c)}
                  className="flex-row items-center rounded-full border px-3 py-1.5"
                  style={{
                    backgroundColor: selected ? style.color : 'transparent',
                    borderColor: selected ? style.color : '#2C2C2E',
                  }}
                >
                  <MaterialCommunityIcons
                    name={style.icon}
                    size={14}
                    color={selected ? 'white' : style.color}
                  />
                  <Text
                    className={`ml-1.5 text-xs font-medium ${selected ? 'text-white' : 'text-ink'}`}
                  >
                    {c}
                  </Text>
                </Pressable>
              );
            })}
          </View>
        </ScrollView>

        {error ? <Text className="mb-3 text-sm text-red-400">{error}</Text> : null}

        <Pressable
          onPress={handleCreate}
          disabled={loading}
          className={`items-center rounded-lg bg-primary py-3 ${loading ? 'opacity-50' : ''}`}
        >
          <Text className="text-sm font-medium text-white">{loading ? 'Adding...' : 'Add expense'}</Text>
        </Pressable>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}
