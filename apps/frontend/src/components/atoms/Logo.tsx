import { MaterialCommunityIcons } from '@expo/vector-icons';
import { Text, View } from 'react-native';

type LogoProps = {
  size?: number;
  showWordmark?: boolean;
};

export function Logo({ size = 44, showWordmark = false }: LogoProps) {
  return (
    <View className="items-center">
      <View
        className="items-center justify-center rounded-xl bg-primary"
        style={{ width: size, height: size }}
      >
        <MaterialCommunityIcons name="receipt" size={size * 0.5} color="white" />
      </View>
      {showWordmark ? (
        <Text className="mt-2 text-lg font-medium text-black">BillBuddy</Text>
      ) : null}
    </View>
  );
}
