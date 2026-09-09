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

export type ForgotPasswordRequest = {
  email: string;
};

export type MessageResponse = {
  message: string;
};

export async function forgotPassword(
  request: ForgotPasswordRequest
): Promise<MessageResponse> {
  const { data } = await apiClient.post<MessageResponse>('/auth/forgot-password', request);
  return data;
}

export type ResetPasswordRequest = {
  token: string;
  newPassword: string;
};

export async function resetPassword(
  request: ResetPasswordRequest
): Promise<MessageResponse> {
  const { data } = await apiClient.post<MessageResponse>('/auth/reset-password', request);
  return data;
}

export type VerifyEmailRequest = {
  token: string;
};

export async function verifyEmail(request: VerifyEmailRequest): Promise<MessageResponse> {
  const { data } = await apiClient.post<MessageResponse>('/auth/verify-email', request);
  return data;
}

export async function resendVerificationEmail(): Promise<MessageResponse> {
  const { data } = await apiClient.post<MessageResponse>('/auth/verify-email/resend');
  return data;
}
