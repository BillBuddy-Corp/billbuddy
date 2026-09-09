import { ActivityIndicator, Pressable, Text } from 'react-native';

type ButtonProps = {
  label: string;
  onPress: () => void;
  variant?: 'primary' | 'secondary';
  // 'light' (default) keeps the original white-card styling used by the
  // still-light auth/onboarding screens; 'dark' is for screens already
  // converted to the Splitwise-style dark theme.
  tone?: 'light' | 'dark';
  loading?: boolean;
  disabled?: boolean;
};

export function Button({
  label,
  onPress,
  variant = 'primary',
  tone = 'light',
  loading = false,
  disabled = false,
}: ButtonProps) {
  const isPrimary = variant === 'primary';
  const isDisabled = disabled || loading;
  const isDark = tone === 'dark';

  const secondaryClasses = isDark
    ? 'border border-divider bg-surface'
    : 'border border-gray-300 bg-white';
  const secondaryTextClass = isDark ? 'text-ink' : 'text-black';

  return (
    <Pressable
      onPress={onPress}
      disabled={isDisabled}
      className={`w-full items-center justify-center rounded-lg py-3 ${
        isPrimary ? 'bg-primary' : secondaryClasses
      } ${isDisabled ? 'opacity-50' : ''}`}
    >
      {loading ? (
        <ActivityIndicator color={isPrimary ? 'white' : '#2F6FED'} />
      ) : (
        <Text
          className={`text-sm font-medium ${isPrimary ? 'text-white' : secondaryTextClass}`}
        >
          {label}
        </Text>
      )}
    </Pressable>
  );
}
