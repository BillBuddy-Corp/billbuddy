import { RouteProp, useFocusEffect, useRoute } from '@react-navigation/native';
import { useCallback, useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { getGroup, Group } from '../api/groups';
import { createSettlement, getSimplifiedBalances, SimplifiedBalance } from '../api/settlements';
import { Button } from '../components/atoms/Button';
import { TextField } from '../components/atoms/TextField';
import { RootStackParamList } from '../navigation/RootNavigator';
import { getErrorMessage } from '../utils/errors';

type Route = RouteProp<RootStackParamList, 'SettleUp'>;

export function SettleUpScreen() {
  const route = useRoute<Route>();
  const { groupId } = route.params;

  const [group, setGroup] = useState<Group | null>(null);
  const [suggestions, setSuggestions] = useState<SimplifiedBalance[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [expandedIndex, setExpandedIndex] = useState<number | null>(null);
  const [amountText, setAmountText] = useState('');
  const [note, setNote] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState('');

  const load = useCallback(async () => {
    try {
      const [groupData, suggestionData] = await Promise.all([
        getGroup(groupId),
        getSimplifiedBalances(groupId),
      ]);
      setGroup(groupData);
      setSuggestions(suggestionData);
      setError('');
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, [groupId]);

  useFocusEffect(
    useCallback(() => {
      load();
    }, [load])
  );

  const toggleRow = (index: number, suggestion: SimplifiedBalance) => {
    if (expandedIndex === index) {
      setExpandedIndex(null);
      return;
    }
    setExpandedIndex(index);
    setAmountText(suggestion.amount.toFixed(2));
    setNote('');
    setFormError('');
  };

  const handleRecord = async (suggestion: SimplifiedBalance) => {
    const parsedAmount = Number(amountText);
    if (!amountText.trim() || Number.isNaN(parsedAmount) || parsedAmount <= 0) {
      setFormError('Enter a valid amount');
      return;
    }
    if (!group) return;
    setFormError('');
    setSubmitting(true);
    try {
      await createSettlement(groupId, {
        paidByUserId: suggestion.fromUserId,
        paidToUserId: suggestion.toUserId,
        amount: parsedAmount,
        currency: group.defaultCurrency,
        note: note.trim() || undefined,
      });
      setExpandedIndex(null);
      await load();
    } catch (err) {
      setFormError(getErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <SafeAreaView className="flex-1 items-center justify-center bg-background">
        <ActivityIndicator color="#2F6FED" />
      </SafeAreaView>
    );
  }

  if (error || !group) {
    return (
      <SafeAreaView className="flex-1 items-center justify-center bg-background px-6">
        <Text className="text-center text-sm text-red-400">{error || 'Group not found'}</Text>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView className="flex-1 bg-background">
      <FlatList
        data={suggestions}
        keyExtractor={(_, index) => String(index)}
        contentContainerStyle={{ padding: 20 }}
        renderItem={({ item, index }) => {
          const expanded = expandedIndex === index;
          return (
            <View className="mb-3 rounded-xl border border-divider bg-surface p-4">
              <Pressable
                onPress={() => toggleRow(index, item)}
                className="flex-row items-center justify-between"
              >
                <Text className="flex-1 pr-3 text-sm font-medium text-ink">
                  {item.fromName} pays {item.toName}
                </Text>
                <Text className="text-sm font-semibold text-ink">
                  {item.amount.toFixed(2)} {group.defaultCurrency}
                </Text>
              </Pressable>
              {expanded ? (
                <View className="mt-4">
                  <TextField
                    label={`Amount (${group.defaultCurrency})`}
                    tone="dark"
                    keyboardType="decimal-pad"
                    value={amountText}
                    onChangeText={setAmountText}
                  />
                  <TextField
                    label="Note (optional)"
                    tone="dark"
                    placeholder="Cash at dinner"
                    value={note}
                    onChangeText={setNote}
                  />
                  {formError ? <Text className="mb-3 text-sm text-red-400">{formError}</Text> : null}
                  <Button
                    label="Record payment"
                    tone="dark"
                    onPress={() => handleRecord(item)}
                    loading={submitting}
                  />
                </View>
              ) : null}
            </View>
          );
        }}
        ListEmptyComponent={
          <Text className="px-1 py-6 text-sm text-subtle">Everyone's settled up already</Text>
        }
      />
    </SafeAreaView>
  );
}
