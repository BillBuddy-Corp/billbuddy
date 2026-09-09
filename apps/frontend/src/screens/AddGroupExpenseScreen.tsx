import { MaterialCommunityIcons } from '@expo/vector-icons';
import { RouteProp, useNavigation, useRoute } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useEffect, useState } from 'react';
import { KeyboardAvoidingView, Platform, Pressable, ScrollView, Text, View } from 'react-native';

import { createGroupExpense } from '../api/expenses';
import { getGroup, Group, GroupMember, listMembers } from '../api/groups';
import { Button } from '../components/atoms/Button';
import { TextField } from '../components/atoms/TextField';
import { RootStackParamList } from '../navigation/RootNavigator';
import { useAuthStore } from '../store/authStore';
import { getErrorMessage } from '../utils/errors';

type Route = RouteProp<RootStackParamList, 'AddGroupExpense'>;
type Navigation = NativeStackNavigationProp<RootStackParamList, 'AddGroupExpense'>;

export function AddGroupExpenseScreen() {
  const navigation = useNavigation<Navigation>();
  const route = useRoute<Route>();
  const { groupId } = route.params;
  const currentUser = useAuthStore((state) => state.user);

  const [group, setGroup] = useState<Group | null>(null);
  const [members, setMembers] = useState<GroupMember[]>([]);
  const [description, setDescription] = useState('');
  const [amount, setAmount] = useState('');
  const [paidByUserId, setPaidByUserId] = useState<number | null>(null);
  const [participantIds, setParticipantIds] = useState<Set<number>>(new Set());
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    (async () => {
      const [groupData, memberData] = await Promise.all([getGroup(groupId), listMembers(groupId)]);
      setGroup(groupData);
      setMembers(memberData);
      setParticipantIds(new Set(memberData.map((m) => m.userId)));
      if (currentUser) setPaidByUserId(currentUser.userId);
    })();
  }, [groupId, currentUser]);

  const toggleParticipant = (userId: number) => {
    setParticipantIds((prev) => {
      const next = new Set(prev);
      if (next.has(userId)) next.delete(userId);
      else next.add(userId);
      return next;
    });
  };

  const handleCreate = async () => {
    if (!description.trim()) {
      setError('Enter a description');
      return;
    }
    const parsedAmount = Number(amount);
    if (!amount.trim() || Number.isNaN(parsedAmount) || parsedAmount <= 0) {
      setError('Enter a valid amount');
      return;
    }
    if (!paidByUserId) {
      setError('Choose who paid');
      return;
    }
    if (participantIds.size === 0) {
      setError('Choose at least one person to split with');
      return;
    }
    setError('');
    setLoading(true);
    try {
      await createGroupExpense(groupId, {
        description: description.trim(),
        amount: parsedAmount,
        currency: group?.defaultCurrency ?? 'USD',
        paidByUserId,
        participantUserIds: Array.from(participantIds),
      });
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
        <TextField
          label="Description"
          tone="dark"
          placeholder="Cabin rental"
          value={description}
          onChangeText={setDescription}
        />
        <TextField
          label={`Amount (${group?.defaultCurrency ?? '...'})`}
          tone="dark"
          placeholder="0.00"
          keyboardType="decimal-pad"
          value={amount}
          onChangeText={setAmount}
        />

        <Text className="mb-1.5 text-sm text-subtle">Paid by</Text>
        <View className="mb-4 flex-row flex-wrap gap-2">
          {members.map((member) => (
            <Pressable
              key={member.userId}
              onPress={() => setPaidByUserId(member.userId)}
              className={`rounded-full px-3.5 py-2 ${
                paidByUserId === member.userId ? 'bg-primary' : 'border border-divider bg-surface'
              }`}
            >
              <Text
                className={`text-sm font-medium ${
                  paidByUserId === member.userId ? 'text-white' : 'text-ink'
                }`}
              >
                {member.fullName}
              </Text>
            </Pressable>
          ))}
        </View>

        <Text className="mb-1.5 text-sm text-subtle">Split equally between</Text>
        <View className="mb-4">
          {members.map((member) => {
            const checked = participantIds.has(member.userId);
            return (
              <Pressable
                key={member.userId}
                onPress={() => toggleParticipant(member.userId)}
                className="flex-row items-center border-b border-divider py-3"
              >
                <MaterialCommunityIcons
                  name={checked ? 'checkbox-marked' : 'checkbox-blank-outline'}
                  size={20}
                  color={checked ? '#2F6FED' : '#9CA3AF'}
                />
                <Text className="ml-3 text-sm font-medium text-ink">{member.fullName}</Text>
              </Pressable>
            );
          })}
        </View>

        {error ? <Text className="mb-3 text-sm text-red-400">{error}</Text> : null}

        <View className="mt-2">
          <Button label="Add expense" tone="dark" onPress={handleCreate} loading={loading} />
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}
