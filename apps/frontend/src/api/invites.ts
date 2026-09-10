import { apiClient } from './client';
import { GroupRole } from './groups';

export type InviteType = 'EMAIL' | 'LINK';

export type GroupInvite = {
  id: number;
  type: InviteType;
  email: string | null;
  token: string | null;
  expiresAt: string | null;
  acceptedAt: string | null;
  revoked: boolean;
  invitedByName: string;
  createdAt: string;
};

export type JoinGroupResponse = {
  groupId: number;
  groupName: string;
  role: GroupRole;
  message: string;
};

export type MyInvite = {
  id: number;
  groupId: number;
  groupName: string;
  invitedByName: string;
  createdAt: string;
};

export async function createEmailInvite(groupId: number, email: string): Promise<void> {
  await apiClient.post(`/groups/${groupId}/invites/email`, { email });
}

export async function listInvites(groupId: number): Promise<GroupInvite[]> {
  const { data } = await apiClient.get<GroupInvite[]>(`/groups/${groupId}/invites`);
  return data;
}

export async function revokeInvite(groupId: number, inviteId: number): Promise<void> {
  await apiClient.delete(`/groups/${groupId}/invites/${inviteId}`);
}

export async function generateLink(groupId: number): Promise<GroupInvite> {
  const { data } = await apiClient.post<GroupInvite>(`/groups/${groupId}/invites/link/generate`);
  return data;
}

export async function disableLink(groupId: number): Promise<void> {
  await apiClient.delete(`/groups/${groupId}/invites/link`);
}

export async function joinViaInvite(token: string): Promise<JoinGroupResponse> {
  const { data } = await apiClient.post<JoinGroupResponse>('/invites/join', { token });
  return data;
}

export async function listMyInvites(): Promise<MyInvite[]> {
  const { data } = await apiClient.get<MyInvite[]>('/invites/mine');
  return data;
}

export async function acceptMyInvite(inviteId: number): Promise<JoinGroupResponse> {
  const { data } = await apiClient.post<JoinGroupResponse>(`/invites/${inviteId}/accept`);
  return data;
}

export async function declineMyInvite(inviteId: number): Promise<void> {
  await apiClient.post(`/invites/${inviteId}/decline`);
}
