import { RouteProp, useNavigation, useRoute } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useState } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView, Text, View } from 'react-native';

import { updateGroup } from '../api/groups';
import { Button } from '../components/atoms/Button';
import { TextField } from '../components/atoms/TextField';
import { RootStackParamList } from '../navigation/RootNavigator';
import { getErrorMessage } from '../utils/errors';

type Route = RouteProp<RootStackParamList, 'EditGroup'>;
type Navigation = NativeStackNavigationProp<RootStackParamList, 'EditGroup'>;

export function EditGroupScreen() {
  const navigation = useNavigation<Navigation>();
  const route = useRoute<Route>();
  const { groupId, name: initialName, description: initialDescription, defaultCurrency: initialCurrency } =
    route.params;

  const [name, setName] = useState(initialName);
  const [description, setDescription] = useState(initialDescription ?? '');
  const [defaultCurrency, setDefaultCurrency] = useState(initialCurrency);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSave = async () => {
    if (!name.trim()) {
      setError('Enter a group name');
      return;
    }
    if (!defaultCurrency.trim()) {
      setError('Enter a currency code, e.g. INR');
      return;
    }
    setError('');
    setLoading(true);
    try {
      await updateGroup(groupId, {
        name: name.trim(),
        description: description.trim() || undefined,
        defaultCurrency: defaultCurrency.trim(),
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
        <TextField label="Group name" placeholder="Goa Trip" value={name} onChangeText={setName} />
        <TextField
          label="Description (optional)"
          placeholder="Beach house squad"
          value={description}
          onChangeText={setDescription}
        />
        <TextField
          label="Default currency"
          placeholder="INR"
          autoCapitalize="characters"
          value={defaultCurrency}
          onChangeText={setDefaultCurrency}
        />

        {error ? <Text className="mb-3 text-sm text-red-400">{error}</Text> : null}

        <View className="mt-2">
          <Button label="Save changes" onPress={handleSave} loading={loading} />
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}
