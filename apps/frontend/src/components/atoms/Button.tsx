import { ActivityIndicator, Pressable, Text } from 'react-native';

type ButtonProps = {
  label: string;
  onPress: () => void;
  variant?: 'primary' | 'secondary';
  loading?: boolean;
  disabled?: boolean;
};

export function Button({
  label,
  onPress,
  variant = 'primary',
  loading = false,
  disabled = false,
}: ButtonProps) {
  const isPrimary = variant === 'primary';
  const isDisabled = disabled || loading;

  return (
    <Pressable
      onPress={onPress}
      disabled={isDisabled}
      className={`w-full items-center justify-center rounded-lg py-3 ${
        isPrimary ? 'bg-primary' : 'border border-gray-300 bg-white'
      } ${isDisabled ? 'opacity-50' : ''}`}
    >
      {loading ? (
        <ActivityIndicator color={isPrimary ? 'white' : '#2F6FED'} />
      ) : (
        <Text
          className={`text-sm font-medium ${isPrimary ? 'text-white' : 'text-black'}`}
        >
          {label}
        </Text>
      )}
    </Pressable>
  );
}
