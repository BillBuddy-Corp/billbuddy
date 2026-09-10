import { ReactNode } from 'react';
import { Pressable, Text, View } from 'react-native';

import { Logo } from '../atoms/Logo';

type AuthCardProps = {
  activeTab: 'login' | 'signup';
  onTabChange: (tab: 'login' | 'signup') => void;
  tagline: string;
  children: ReactNode;
};

export function AuthCard({ activeTab, onTabChange, tagline, children }: AuthCardProps) {
  return (
    <View className="w-full rounded-2xl border border-divider bg-surface px-6 py-7">
      <View className="mb-7 items-center">
        <Logo />
        <Text className="mt-3 text-lg font-medium text-ink">BillBuddy</Text>
        <Text className="mt-1 text-center text-sm text-subtle">{tagline}</Text>
      </View>

      <View className="mb-6 flex-row rounded-lg bg-background p-1">
        <Pressable
          onPress={() => onTabChange('login')}
          className={`flex-1 items-center rounded-md py-2 ${
            activeTab === 'login' ? 'bg-surface' : ''
          }`}
        >
          <Text
            className={`text-sm ${
              activeTab === 'login' ? 'font-medium text-ink' : 'text-subtle'
            }`}
          >
            Log in
          </Text>
        </Pressable>
        <Pressable
          onPress={() => onTabChange('signup')}
          className={`flex-1 items-center rounded-md py-2 ${
            activeTab === 'signup' ? 'bg-surface' : ''
          }`}
        >
          <Text
            className={`text-sm ${
              activeTab === 'signup' ? 'font-medium text-ink' : 'text-subtle'
            }`}
          >
            Sign up
          </Text>
        </Pressable>
      </View>

      {children}
    </View>
  );
}
