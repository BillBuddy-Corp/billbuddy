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
    <View className="w-full rounded-2xl border border-gray-200 bg-white px-6 py-7">
      <View className="mb-7 items-center">
        <Logo />
        <Text className="mt-3 text-lg font-medium text-black">BillBuddy</Text>
        <Text className="mt-1 text-center text-sm text-gray-500">{tagline}</Text>
      </View>

      <View className="mb-6 flex-row rounded-lg bg-gray-100 p-1">
        <Pressable
          onPress={() => onTabChange('login')}
          className={`flex-1 items-center rounded-md py-2 ${
            activeTab === 'login' ? 'bg-white' : ''
          }`}
        >
          <Text
            className={`text-sm ${
              activeTab === 'login' ? 'font-medium text-black' : 'text-gray-500'
            }`}
          >
            Log in
          </Text>
        </Pressable>
        <Pressable
          onPress={() => onTabChange('signup')}
          className={`flex-1 items-center rounded-md py-2 ${
            activeTab === 'signup' ? 'bg-white' : ''
          }`}
        >
          <Text
            className={`text-sm ${
              activeTab === 'signup' ? 'font-medium text-black' : 'text-gray-500'
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
