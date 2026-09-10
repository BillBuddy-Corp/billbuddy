import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import { FlatList, Pressable, Text, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { displayName, SplitMode, useAddExpenseFormStore } from '../store/addExpenseFormStore';
import { useAuthStore } from '../store/authStore';
import { avatarColor } from '../utils/avatarColor';

const TABS: { mode: SplitMode; label: string }[] = [
  { mode: 'EQUAL', label: 'Equally' },
  { mode: 'EXACT', label: 'Unequally' },
  { mode: 'PERCENTAGE', label: 'By percentages' },
];

export function AdjustSplitScreen() {
  const navigation = useNavigation();
  const currentUserId = useAuthStore((state) => state.user?.userId) ?? -1;
  const store = useAddExpenseFormStore();
  const { participants, splitType, splitParticipantIds, amount, exactAmounts, percentages } = store;

  const totalAmount = Number(amount) || 0;
  const included = participants.filter((p) => splitParticipantIds.includes(p.userId));

  const exactSum = included.reduce((sum, p) => sum + (Number(exactAmounts[p.userId]) || 0), 0);
  const percentageSum = included.reduce((sum, p) => sum + (Number(percentages[p.userId]) || 0), 0);

  let footerLabel = '';
  let footerOk = true;
  if (splitType === 'EQUAL') {
    const perPerson = included.length > 0 ? totalAmount / included.length : 0;
    footerLabel = `${perPerson.toFixed(2)}/person (${included.length} ${included.length === 1 ? 'person' : 'people'})`;
  } else if (splitType === 'EXACT') {
    footerOk = Math.abs(exactSum - totalAmount) < 0.01;
    footerLabel = `${exactSum.toFixed(2)} of ${totalAmount.toFixed(2)} assigned`;
  } else {
    footerOk = Math.abs(percentageSum - 100) < 0.01;
    footerLabel = `${percentageSum.toFixed(1)}% of 100% assigned`;
  }

  return (
    <SafeAreaView className="flex-1 bg-background">
      <View className="flex-row border-b border-divider px-2">
        {TABS.map((tab) => {
          const active = splitType === tab.mode;
          return (
            <Pressable
              key={tab.mode}
              onPress={() => store.setSplitType(tab.mode)}
              className={`flex-1 items-center border-b-2 py-3 ${active ? 'border-primary' : 'border-transparent'}`}
            >
              <Text className={`text-sm font-medium ${active ? 'text-primary' : 'text-subtle'}`}>
                {tab.label}
              </Text>
            </Pressable>
          );
        })}
      </View>

      <FlatList
        data={participants}
        keyExtractor={(item) => String(item.userId)}
        renderItem={({ item }) => {
          const checked = splitParticipantIds.includes(item.userId);
          return (
            <View className="flex-row items-center border-b border-divider px-5 py-3">
              <Pressable
                onPress={() => store.toggleSplitParticipant(item.userId)}
                className="flex-1 flex-row items-center"
              >
                <MaterialCommunityIcons
                  name={checked ? 'checkbox-marked' : 'checkbox-blank-outline'}
                  size={20}
                  color={checked ? '#2F6FED' : '#9CA3AF'}
                />
                <View
                  className="ml-3 mr-3 h-8 w-8 items-center justify-center rounded-full"
                  style={{ backgroundColor: avatarColor(item.userId) }}
                >
                  <Text className="text-xs font-semibold text-white">
                    {displayName(item, currentUserId).charAt(0).toUpperCase()}
                  </Text>
                </View>
                <Text className="text-sm font-medium text-ink">{displayName(item, currentUserId)}</Text>
              </Pressable>

              {checked && splitType === 'EXACT' ? (
                <TextInput
                  value={exactAmounts[item.userId] ?? ''}
                  onChangeText={(value) => store.setExactAmount(item.userId, value)}
                  keyboardType="decimal-pad"
                  placeholder="0.00"
                  placeholderTextColor="#9CA3AF"
                  className="w-20 rounded-lg border border-divider bg-surface px-2 py-1.5 text-right text-sm text-ink"
                />
              ) : null}
              {checked && splitType === 'PERCENTAGE' ? (
                <View className="flex-row items-center">
                  <TextInput
                    value={percentages[item.userId] ?? ''}
                    onChangeText={(value) => store.setPercentage(item.userId, value)}
                    keyboardType="decimal-pad"
                    placeholder="0"
                    placeholderTextColor="#9CA3AF"
                    className="w-16 rounded-lg border border-divider bg-surface px-2 py-1.5 text-right text-sm text-ink"
                  />
                  <Text className="ml-1 text-sm text-subtle">%</Text>
                </View>
              ) : null}
            </View>
          );
        }}
      />

      <View className="flex-row items-center justify-between border-t border-divider px-5 py-4">
        <Text className={`text-sm font-medium ${footerOk ? 'text-subtle' : 'text-red-400'}`}>
          {footerLabel}
        </Text>
        <Pressable onPress={() => navigation.goBack()} className="rounded-lg bg-primary px-5 py-2.5">
          <Text className="text-sm font-medium text-white">Done</Text>
        </Pressable>
      </View>
    </SafeAreaView>
  );
}
