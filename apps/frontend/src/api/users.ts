import { apiClient } from './client';

export type UserProfile = {
  id: number;
  fullName: string;
  email: string;
  emailVerified: boolean;
  pendingEmail: string | null;
  mobileNumber: string | null;
  mobileVerified: boolean;
  profilePicUrl: string | null;
  defaultCurrency: string;
  createdAt: string;
};

export async function getProfile(): Promise<UserProfile> {
  const { data } = await apiClient.get<UserProfile>('/users/me');
  return data;
}
