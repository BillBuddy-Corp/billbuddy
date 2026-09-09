import { Text, TextInput, TextInputProps, View } from 'react-native';

type TextFieldProps = TextInputProps & {
  label: string;
  error?: string;
  // 'light' (default) keeps the original styling used by the still-light
  // auth/onboarding screens; 'dark' is for screens already converted to
  // the Splitwise-style dark theme.
  tone?: 'light' | 'dark';
};

export function TextField({ label, error, tone = 'light', ...inputProps }: TextFieldProps) {
  const isDark = tone === 'dark';

  return (
    <View className="mb-3.5">
      <Text className={`mb-1.5 text-sm ${isDark ? 'text-subtle' : 'text-gray-500'}`}>{label}</Text>
      <TextInput
        className={`h-11 rounded-lg border px-3 text-base ${isDark ? 'bg-surface text-ink' : 'text-black'} ${
          error ? (isDark ? 'border-red-400' : 'border-red-500') : isDark ? 'border-divider' : 'border-gray-300'
        }`}
        placeholderTextColor="#9CA3AF"
        autoCapitalize="none"
        autoCorrect={false}
        {...inputProps}
      />
      {error ? (
        <Text className={`mt-1 text-xs ${isDark ? 'text-red-400' : 'text-red-500'}`}>{error}</Text>
      ) : null}
    </View>
  );
}
