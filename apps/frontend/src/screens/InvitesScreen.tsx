import { MaterialCommunityIcons } from '@expo/vector-icons';
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

function LinkRow({
  icon,
  label,
  onPress,
  busy,
  destructive,
}: {
  icon: keyof typeof MaterialCommunityIcons.glyphMap;
  label: string;
  onPress: () => void;
  busy?: boolean;
  destructive?: boolean;
}) {
  return (
    <Pressable
      onPress={onPress}
      disabled={busy}
      className={`flex-row items-center border-b border-divider py-4 ${busy ? 'opacity-50' : ''}`}
    >
      <MaterialCommunityIcons name={icon} size={20} color={destructive ? '#F87171' : '#F5F5F7'} />
      <Text className={`ml-3 flex-1 text-sm font-medium ${destructive ? 'text-red-400' : 'text-ink'}`}>
        {label}
      </Text>
      {busy ? <ActivityIndicator color="#9CA3AF" /> : null}
    </Pressable>
  );
}

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
      <SafeAreaView className="flex-1 items-center justify-center bg-background">
        <ActivityIndicator color="#2F6FED" />
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView className="flex-1 bg-background">
      <FlatList
        data={pendingInvites}
        keyExtractor={(item) => String(item.id)}
        ListHeaderComponent={
          <View className="px-5 pt-4">
            <Text className="text-xs font-medium uppercase text-subtle">Invite link</Text>
            {activeLink?.token ? (
              <View className="mt-2">
                <View className="border-b border-divider py-3">
                  <Text className="text-sm text-ink" numberOfLines={1}>
                    {buildJoinLink(activeLink.token)}
                  </Text>
                </View>
                <LinkRow icon="share-variant-outline" label="Share link" onPress={handleShareLink} />
                <LinkRow
                  icon="autorenew"
                  label="Change link"
                  onPress={handleGenerateLink}
                  busy={linkBusy}
                />
                <LinkRow
                  icon="link-off"
                  label="Disable link"
                  onPress={handleDisableLink}
                  busy={linkBusy}
                  destructive
                />
              </View>
            ) : (
              <View className="mt-2">
                <Button
                  label="Generate link"
                  tone="dark"
                  onPress={handleGenerateLink}
                  loading={linkBusy}
                />
              </View>
            )}

            <Text className="mb-2 mt-6 text-xs font-medium uppercase text-subtle">
              Invite by email
            </Text>
            <TextField
              label="Email"
              tone="dark"
              placeholder="friend@example.com"
              keyboardType="email-address"
              value={email}
              onChangeText={(value) => {
                setEmail(value);
                setEmailSent(false);
              }}
            />
            {emailError ? <Text className="mb-3 text-sm text-red-400">{emailError}</Text> : null}
            {emailSent ? <Text className="mb-3 text-sm text-green-500">Invite sent</Text> : null}
            <Button label="Send invite" tone="dark" onPress={handleSendEmailInvite} loading={emailBusy} />

            {error ? <Text className="mt-4 text-sm text-red-400">{error}</Text> : null}

            <Text className="mb-2 mt-6 text-xs font-medium uppercase text-subtle">
              Pending invites
            </Text>
          </View>
        }
        renderItem={({ item }) => (
          <View className="flex-row items-center justify-between border-b border-divider px-5 py-3">
            <View className="flex-1 pr-3">
              <Text className="text-sm font-medium text-ink">
                {item.type === 'EMAIL' ? item.email : 'Shareable link'}
              </Text>
              <Text className="text-xs text-subtle">Invited by {item.invitedByName}</Text>
            </View>
            <Pressable onPress={() => handleRevoke(item.id)}>
              <Text className="text-sm text-red-400">Revoke</Text>
            </Pressable>
          </View>
        )}
        ListEmptyComponent={
          <Text className="px-5 pb-6 text-sm text-subtle">No pending invites</Text>
        }
      />
    </SafeAreaView>
  );
}
