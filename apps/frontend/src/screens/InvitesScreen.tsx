import { RouteProp, useFocusEffect, useRoute } from '@react-navigation/native';
import { useCallback, useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, Share, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import {
  createEmailInvite,
  disableLink,
  generateLink,
  GroupInvite,
  listInvites,
  revokeInvite,
} from '../api/invites';
import { Button } from '../components/atoms/Button';
import { TextField } from '../components/atoms/TextField';
import { RootStackParamList } from '../navigation/RootNavigator';
import { getErrorMessage } from '../utils/errors';

type Route = RouteProp<RootStackParamList, 'Invites'>;

function buildJoinLink(token: string): string {
  return `billbuddy://join-group?token=${token}`;
}

export function InvitesScreen() {
  const route = useRoute<Route>();
  const { groupId } = route.params;

  const [invites, setInvites] = useState<GroupInvite[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [linkBusy, setLinkBusy] = useState(false);
  const [email, setEmail] = useState('');
  const [emailBusy, setEmailBusy] = useState(false);
  const [emailError, setEmailError] = useState('');
  const [emailSent, setEmailSent] = useState(false);

  const loadInvites = useCallback(async () => {
    try {
      const data = await listInvites(groupId);
      setInvites(data);
      setError('');
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setLoading(false);
    }
  }, [groupId]);

  useFocusEffect(
    useCallback(() => {
      loadInvites();
    }, [loadInvites])
  );

  const activeLink = invites.find((invite) => invite.type === 'LINK' && !invite.revoked);
  const pendingInvites = invites.filter((invite) => !invite.revoked);

  const handleGenerateLink = async () => {
    setLinkBusy(true);
    setError('');
    try {
      await generateLink(groupId);
      await loadInvites();
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setLinkBusy(false);
    }
  };

  const handleShareLink = async () => {
    if (!activeLink?.token) return;
    try {
      await Share.share({ message: buildJoinLink(activeLink.token) });
    } catch {
      // User cancelled the share sheet, nothing to do.
    }
  };

  const handleDisableLink = async () => {
    setLinkBusy(true);
    setError('');
    try {
      await disableLink(groupId);
      await loadInvites();
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setLinkBusy(false);
    }
  };

  const handleRevoke = async (inviteId: number) => {
    try {
      await revokeInvite(groupId, inviteId);
      await loadInvites();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  const handleSendEmailInvite = async () => {
    if (!email.trim()) {
      setEmailError('Enter an email address');
      return;
    }
    setEmailError('');
    setEmailBusy(true);
    setEmailSent(false);
    try {
      await createEmailInvite(groupId, email.trim());
      setEmail('');
      setEmailSent(true);
      await loadInvites();
    } catch (err) {
      setEmailError(getErrorMessage(err));
    } finally {
      setEmailBusy(false);
    }
  };

  if (loading) {
    return (
      <SafeAreaView className="flex-1 items-center justify-center bg-white">
        <ActivityIndicator color="#2F6FED" />
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView className="flex-1 bg-white">
      <FlatList
        data={pendingInvites}
        keyExtractor={(item) => String(item.id)}
        ListHeaderComponent={
          <View className="px-5 pt-4">
            <Text className="text-xs font-medium uppercase text-gray-400">Shareable link</Text>
            {activeLink?.token ? (
              <View className="mt-2 rounded-lg border border-gray-200 p-3">
                <Text className="text-xs text-gray-500" numberOfLines={1}>
                  {buildJoinLink(activeLink.token)}
                </Text>
                <View className="mt-3 flex-row gap-2">
                  <View className="flex-1">
                    <Button label="Share" onPress={handleShareLink} />
                  </View>
                  <View className="flex-1">
                    <Button
                      label="Disable"
                      variant="secondary"
                      onPress={handleDisableLink}
                      loading={linkBusy}
                    />
                  </View>
                </View>
              </View>
            ) : (
              <View className="mt-2">
                <Button label="Generate link" onPress={handleGenerateLink} loading={linkBusy} />
              </View>
            )}

            <Text className="mt-6 text-xs font-medium uppercase text-gray-400">
              Invite by email
            </Text>
            <View className="mt-2">
              <TextField
                label="Email"
                placeholder="friend@example.com"
                keyboardType="email-address"
                value={email}
                onChangeText={(value) => {
                  setEmail(value);
                  setEmailSent(false);
                }}
              />
              {emailError ? <Text className="mb-3 text-sm text-red-500">{emailError}</Text> : null}
              {emailSent ? (
                <Text className="mb-3 text-sm text-green-600">Invite sent</Text>
              ) : null}
              <Button label="Send invite" onPress={handleSendEmailInvite} loading={emailBusy} />
            </View>

            {error ? <Text className="mt-4 text-sm text-red-500">{error}</Text> : null}

            <Text className="mb-2 mt-6 text-xs font-medium uppercase text-gray-400">
              Pending invites
            </Text>
          </View>
        }
        renderItem={({ item }) => (
          <View className="flex-row items-center justify-between border-b border-gray-100 px-5 py-3">
            <View className="flex-1 pr-3">
              <Text className="text-sm font-medium text-black">
                {item.type === 'EMAIL' ? item.email : 'Shareable link'}
              </Text>
              <Text className="text-xs text-gray-500">Invited by {item.invitedByName}</Text>
            </View>
            <Pressable onPress={() => handleRevoke(item.id)}>
              <Text className="text-sm text-red-500">Revoke</Text>
            </Pressable>
          </View>
        )}
        ListEmptyComponent={
          <Text className="px-5 pb-6 text-sm text-gray-400">No pending invites</Text>
        }
      />
    </SafeAreaView>
  );
}
