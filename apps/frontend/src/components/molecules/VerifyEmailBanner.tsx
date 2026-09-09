import { useState } from 'react';
import { ActivityIndicator, Pressable, Text, View } from 'react-native';

import { resendVerificationEmail } from '../../api/auth';

export function VerifyEmailBanner() {
  const [sending, setSending] = useState(false);
  const [sent, setSent] = useState(false);

  const handleResend = async () => {
    setSending(true);
    try {
      await resendVerificationEmail();
      setSent(true);
    } catch {
      // Silently ignore — the button just stays available to retry.
    } finally {
      setSending(false);
    }
  };

  return (
    <View className="w-full flex-row items-center justify-between rounded-lg bg-primary-tint px-4 py-3">
      <Text className="flex-1 pr-3 text-sm text-primary-dark">
        {sent ? 'Verification email sent — check your inbox.' : 'Verify your email address.'}
      </Text>
      {sending ? (
        <ActivityIndicator color="#2F6FED" />
      ) : (
        <Pressable onPress={handleResend} disabled={sent}>
          <Text className="text-sm font-medium text-primary">{sent ? 'Sent' : 'Resend'}</Text>
        </Pressable>
      )}
    </View>
  );
}
