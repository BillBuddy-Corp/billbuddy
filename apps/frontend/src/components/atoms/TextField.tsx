import { Text, TextInput, TextInputProps, View } from 'react-native';

type TextFieldProps = TextInputProps & {
  label: string;
  error?: string;
};

export function TextField({ label, error, ...inputProps }: TextFieldProps) {
  return (
    <View className="mb-3.5">
      <Text className="mb-1.5 text-sm text-subtle">{label}</Text>
      <TextInput
        className={`h-11 rounded-lg border bg-surface px-3 text-base text-ink ${
          error ? 'border-red-400' : 'border-divider'
        }`}
        placeholderTextColor="#9CA3AF"
        autoCapitalize="none"
        autoCorrect={false}
        {...inputProps}
      />
      {error ? <Text className="mt-1 text-xs text-red-400">{error}</Text> : null}
    </View>
  );
}
