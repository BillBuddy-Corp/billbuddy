import { apiClient } from './client';

export type SignupRequest = {
  fullName: string;
  email: string;
  password: string;
  mobileNumber?: string;
};

export type SignupResponse = {
  id: number;
  email: string;
  fullName: string;
  createdAt: string;
};

export type LoginRequest = {
  email: string;
  password: string;
  deviceId: string;
  deviceName?: string;
};

export type LoginResponse = {
  tokenType: string;
  accessToken: string;
  refreshToken: string;
  userId: number;
  email: string;
  fullName: string;
};

export async function signup(request: SignupRequest): Promise<SignupResponse> {
  const { data } = await apiClient.post<SignupResponse>('/auth/signup', request);
  return data;
}

export async function login(request: LoginRequest): Promise<LoginResponse> {
  const { data } = await apiClient.post<LoginResponse>('/auth/login', request);
  return data;
}

export type LogoutRequest = {
  refreshToken: string;
  deviceId: string;
};

export async function logout(request: LogoutRequest): Promise<void> {
  await apiClient.post('/auth/logout', request);
}
