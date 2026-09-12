import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import { useMemo, useState } from 'react';
import { FlatList, Pressable, Text, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { useAddExpenseFormStore } from '../store/addExpenseFormStore';
import { CURRENCIES } from '../constants/currencies';

export function CurrencyPickerScreen() {
  const navigation = useNavigation();
  const currency = useAddExpenseFormStore((state) => state.currency);
  const setCurrency = useAddExpenseFormStore((state) => state.setCurrency);
  const [query, setQuery] = useState('');

  const results = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return CURRENCIES;
    return CURRENCIES.filter((c) => c.code.toLowerCase().includes(q) || c.name.toLowerCase().includes(q));
  }, [query]);

  return (
    <SafeAreaView className="flex-1 bg-background" edges={['bottom']}>
      <View className="border-b border-divider px-5 py-3">
        <View className="flex-row items-center rounded-lg bg-surface px-3 py-2">
          <MaterialCommunityIcons name="magnify" size={18} color="#9CA3AF" />
          <TextInput
            placeholder="Search currency"
            placeholderTextColor="#9CA3AF"
            value={query}
            onChangeText={setQuery}
            autoCapitalize="none"
            autoCorrect={false}
            className="ml-2 flex-1 text-sm text-ink"
          />
        </View>
      </View>
      <FlatList
        data={results}
        keyExtractor={(item) => item.code}
        keyboardShouldPersistTaps="handled"
        renderItem={({ item }) => {
          const selected = item.code === currency;
          return (
            <Pressable
              onPress={() => {
                setCurrency(item.code);
                navigation.goBack();
              }}
              className="flex-row items-center border-b border-divider px-5 py-3"
            >
              <Text className="w-14 text-sm font-semibold text-ink">{item.code}</Text>
              <Text className="flex-1 text-sm text-subtle">{item.name}</Text>
              {selected ? <MaterialCommunityIcons name="check" size={20} color="#2F6FED" /> : null}
            </Pressable>
          );
        }}
        ListEmptyComponent={
          <Text className="px-5 py-6 text-sm text-subtle">No currency matches "{query}"</Text>
        }
      />
    </SafeAreaView>
  );
}
