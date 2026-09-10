import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import { Alert, FlatList, Pressable, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { displayName, useAddExpenseFormStore } from '../store/addExpenseFormStore';
import { useAuthStore } from '../store/authStore';
import { avatarColor } from '../utils/avatarColor';

export function WhoPaidScreen() {
  const navigation = useNavigation();
  const currentUserId = useAuthStore((state) => state.user?.userId) ?? -1;
  const participants = useAddExpenseFormStore((state) => state.participants);
  const paidByUserId = useAddExpenseFormStore((state) => state.paidByUserId);
  const setPaidBy = useAddExpenseFormStore((state) => state.setPaidBy);

  return (
    <SafeAreaView className="flex-1 bg-background">
      <FlatList
        data={participants}
        keyExtractor={(item) => String(item.userId)}
        renderItem={({ item }) => {
          const selected = item.userId === paidByUserId;
          return (
            <Pressable
              onPress={() => {
                setPaidBy(item.userId);
                navigation.goBack();
              }}
              className="flex-row items-center border-b border-divider px-5 py-3"
            >
              <View
                className="mr-3 h-10 w-10 items-center justify-center rounded-full"
                style={{ backgroundColor: avatarColor(item.userId) }}
              >
                <Text className="text-sm font-semibold text-white">
                  {displayName(item, currentUserId).charAt(0).toUpperCase()}
                </Text>
              </View>
              <Text className="flex-1 text-sm font-medium text-ink">{displayName(item, currentUserId)}</Text>
              {selected ? <MaterialCommunityIcons name="check" size={20} color="#2F6FED" /> : null}
            </Pressable>
          );
        }}
        ListFooterComponent={
          <Pressable
            onPress={() =>
              Alert.alert('Coming soon', "Splitting a payment between multiple payers isn't available yet.")
            }
            className="flex-row items-center border-b border-divider px-5 py-3 opacity-50"
          >
            <View className="mr-3 h-10 w-10 items-center justify-center rounded-full bg-surface">
              <MaterialCommunityIcons name="account-multiple" size={18} color="#9CA3AF" />
            </View>
            <Text className="flex-1 text-sm font-medium text-ink">Multiple people</Text>
          </Pressable>
        }
      />
    </SafeAreaView>
  );
}
